import subprocess
import firebase_admin
from firebase_admin import firestore
import os
import tempfile
import time
from datetime import datetime, timezone
import traceback
import logging
import glob
from app.firebase.firebase_client import bucket, db
import matlab.engine

# Global variable untuk track yang sudah diproses
processed_ids = set()
last_checked = datetime.now(timezone.utc)

logging.basicConfig(level=logging.INFO)

def get_file_from_firestore(file_path, desired_filename=None):
    blob = bucket.blob(file_path)
    if not blob.exists():
        raise Exception(f"File {file_path} tidak ditemukan di Firebase Storage.")
    
    if desired_filename:
        # Simpan di temp folder dengan nama sesuai desired_filename
        temp_dir = tempfile.gettempdir()
        temp_path = os.path.join(temp_dir, desired_filename)
    else:
        temp_path = tempfile.mktemp(suffix=".wav")
    
    blob.download_to_filename(temp_path)
    return temp_path


def save_results_to_firestore(doc_id, hasil):
    try:
        doc_ref = db.collection("processed_attack_audio").document(doc_id)
        doc_ref.set(hasil)
        print(f"[SUCCESS] Results for {doc_id} saved to Firestore")
    except Exception as e:
        print(f"[ERROR] Failed to save results to Firestore: {e}")

def blob_exists(path):
    blobs = list(bucket.list_blobs(prefix=path))
    return any(blob.name == path for blob in blobs)

def wait_until_all_ready(audio_path, timeout=10, interval=1.0):
    full_audio_path = f"watermarked_audio/{audio_path}"
    start = time.time()
    while time.time() - start < timeout:
        if blob_exists(full_audio_path):
            return True
        time.sleep(interval)
    return False

def find_processed_embedding_by_original_filename(original_filename):
    docs = db.collection("processed_embedding_audio").stream()
    for doc in docs:
        data = doc.to_dict()
        main_audio_path = data.get("main_audio", "")
        # Ambil basename dari main_audio_path
        main_audio_basename = os.path.basename(main_audio_path)
        if main_audio_basename == original_filename:
            return doc
    return None

def map_jenis_parameter_to_bat_args(jenis, parameter):
    mapping = {
        ("1", "3000"): ("1", "3000"),
        ("1", "6000"): ("1", "6000"),
        ("1", "9000"): ("1", "9000"),
        ("2", "1"): ("2", "1"),
        ("2", "2"): ("2", "2"),
        ("2", "3"): ("2", "3"),
        ("2", "4"): ("2", "4"),
        ("2", "5"): ("2", "5"),
        ("3", "8"): ("3", "8"),
        ("5", "10"): ("5", "10"),
        ("5", "20"): ("5", "20"),
        ("5", "30"): ("5", "30"),
        ("6", "1"): ("6", "1"),
        ("6", "2"): ("6", "2"),
        ("6", "3"): ("6", "3"),
        ("6", "4"): ("6", "4"),
        ("7", "1"): ("7", "1"),
        ("7", "2"): ("7", "2"),
        ("7", "3"): ("7", "3"),
        ("7", "4"): ("7", "4"),
        ("8", "1"): ("8", "1"),
        ("8", "2"): ("8", "2"),
        ("8", "3"): ("8", "3"),
        ("9", "1"): ("9", "1"),
        ("9", "2"): ("9", "2"),
        ("9", "3"): ("9", "3"),
        ("9", "4"): ("9", "4"),
        ("10", "1"): ("10", "1"),
        ("11", "1"): ("11", "1"),
        ("13", "32"): ("13", "32"),
        ("13", "64"): ("13", "64"),
        ("13", "96"): ("13", "96"),
        ("13", "128"): ("13", "128"),
        ("13", "192"): ("13", "192"),
    }
    return mapping.get((jenis, parameter), ("0", "0"))


def process_new_audio_attacking_requests(doc):
    doc_id = doc.id
    data = doc.to_dict()
    
    # Skip if already completed or processing
    if data.get("status") in ["completed", "processing"]:
        logging.warning(f"[SKIP] Document {doc_id} already processed or being processed")
        return

    try:
        # Mark as processing immediately
        doc.reference.update({"status": "processing"})
        
        original_filename = data.get("watermarked_file_name")
        jenis = data.get("jenis")
        parameter = data.get("parameter")

        # Validate required fields
        if not all([original_filename, jenis, parameter]):
            missing_fields = []
            if not original_filename: missing_fields.append("watermarked_file_name")
            if not jenis: missing_fields.append("jenis")
            if not parameter: missing_fields.append("parameter")
            
            error_msg = f"Missing required fields: {', '.join(missing_fields)}"
            logging.warning(f"[SKIP] {error_msg} in attacking_steps_audio/{doc_id}")
            doc.reference.update({"status": "failed", "error": error_msg})
            return

        print(f"[FIRESTORE] Processing audio attacking request: {doc_id}")

        # Find matching embedding document
        embed_doc = find_processed_embedding_by_original_filename(original_filename)
        if not embed_doc:
            error_msg = f"processed_embedding_audio with main_audio={original_filename} not found"
            print(f"[SKIP] {error_msg}")
            doc.reference.update({"status": "failed", "error": error_msg})
            return
            
        print(f"[FOUND] processed_embedding_audio document matched: {embed_doc.id} for main_audio: {original_filename}")
        
        embed_data = embed_doc.to_dict()
        main_audio_path = embed_data.get("main_audio")
        watermarked_audio_path = embed_data.get("watermarked_audio_paths")

        if not main_audio_path or not watermarked_audio_path:
            error_msg = "Incomplete embedding data (missing main_audio or watermarked_audio_paths)"
            print(f"[SKIP] {error_msg}")
            doc.reference.update({"status": "failed", "error": error_msg})
            return

        # Map attack type and parameters to .bat arguments
        bat_arg1, bat_arg2 = map_jenis_parameter_to_bat_args(jenis, parameter)

        # Download host audio (main) using original filename
        host_temp = get_file_from_firestore(main_audio_path, desired_filename=original_filename)

        print(f"[INFO] Running attack with parameters:")
        print(f"  attack type     : {bat_arg1}")
        print(f"  parameter       : {bat_arg2}")
        print(f"  file            : {host_temp}")

        # Start MATLAB Engine (sekali saja)
        if 'eng' not in globals():
            eng = matlab.engine.start_matlab()
            eng.addpath(r'D:\TA\mfile_audio_exe\AWM_script', nargout=0)

        # Konversi parameter untuk MATLAB
        jenis1 = int(bat_arg1)
        jenis2 = int(bat_arg2)
        audio_input_path = host_temp  # path file hasil watermarking

        print(f"[MATLAB] Memanggil aplikasi_attack dengan:")
        print(f"  jenis1    : {jenis1}")
        print(f"  jenis2    : {jenis2}")
        print(f"  file      : {audio_input_path}")

        try:
            eng.aplikasi_attack(
                jenis1,
                jenis2,
                audio_input_path,
                nargout=0
            )
            print("[MATLAB] aplikasi_attack berhasil dipanggil.")
        except Exception as matlab_error:
            logging.error(f"[MATLAB ERROR] {matlab_error}")
            doc.reference.update({"status": "failed", "error": f"MATLAB error: {matlab_error}"})
            return

        # Handle output files
        output_dir = r"D:\TA\mfile_audio_exe\Data\attacked_watermarked_audio"
        filename_noext = os.path.splitext(os.path.basename(host_temp))[0]
        attack_pattern = os.path.join(output_dir, f"{jenis}{parameter}-{filename_noext}.wav")
        attacked_files = glob.glob(attack_pattern)

        # Upload attacked audio files
        uploaded_attacked_paths = []
        for af in attacked_files:
            fname = os.path.basename(af)
            dest_path = f"processed/audio_attacking/output_{doc_id}_{fname}"
            bucket.blob(dest_path).upload_from_filename(af)
            logging.info(f"[UPLOAD] Uploaded {af} to {dest_path}")
            uploaded_attacked_paths.append(dest_path)

        if not attacked_files:
            logging.warning(f"[MISSING] No attacked audio found for {filename_noext} in {output_dir}")

        # Save results to Firestore
        hasil = {
            "attacked_audio_paths":  uploaded_attacked_paths[0] if uploaded_attacked_paths else None,
            "jenis": jenis,
            "parameter": parameter,
            "original_watermarked_file": original_filename,
            "timestamp": firestore.SERVER_TIMESTAMP
        }
        
        db.collection("processed_attack_audio").document(doc_id).set(hasil)
        logging.info(f"[SUCCESS] Saved attack results for {doc_id}")
        
        # Mark as completed
        doc.reference.update({"status": "completed"})
        processed_ids.add(doc_id)

    except Exception as e:
        logging.error(f"Failed to process audio attacking {doc_id}: {e}")
        traceback.print_exc()
        doc.reference.update({
            "status": "failed",
            "error": str(e)[:500]
        })

def setup_realtime_audio_attacking_listener():
    def on_snapshot(col_snapshot, changes, read_time):
        timestamp_str = read_time.strftime("%Y-%m-%d %H:%M:%S")
        logging.info(f"Received {len(changes)} audio attacking changes at {timestamp_str}")
        
        for change in changes:
            if change.type.name == "ADDED":
                data = change.document.to_dict()
                if data.get("status") == "pending":
                    process_new_audio_attacking_requests(change.document)

    query = db.collection("attacking_steps_audio").where("status", "==", "pending")
    query_watch = query.on_snapshot(on_snapshot)
    logging.info("🔫 Realtime audio attacking listener activated")
    return query_watch

def watch_audio_attacking_loop():
    logging.info("👂 Starting realtime listener for audio attacking...")
    
    # Process any pending documents on startup
    pending_docs = db.collection("attacking_steps_audio")\
                   .where("status", "==", "pending")\
                   .limit(5)\
                   .stream()
    
    for doc in pending_docs:
        if doc.id not in processed_ids:
            process_new_audio_attacking_requests(doc)
    
    # Start realtime listener
    watch = setup_realtime_audio_attacking_listener()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logging.info("\nShutting down audio attacking listener...")
    except Exception as e:
        logging.error(f"❌ Audio attacking listener error: {e}")
        traceback.print_exc()

if __name__ == "__main__":
    watch_audio_attacking_loop()