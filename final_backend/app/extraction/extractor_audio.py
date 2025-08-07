import subprocess
import os
import time
from firebase_admin import firestore, storage
import tempfile
import traceback
import matlab.engine
import logging
from datetime import datetime, timezone
from app.firebase.firebase_client import db, bucket
import glob

# Global variable
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
        doc_ref = db.collection("processed_extraction_audio").document(doc_id)
        doc_ref.set(hasil)
        print(f"[SUCCESS] Results for {doc_id} saved to Firestore")
    except Exception as e:
        print(f"[ERROR] Failed to save results to Firestore: {e}")

def blob_exists(path):
    blobs = list(bucket.list_blobs(prefix=path))
    return any(blob.name == path for blob in blobs)

def wait_until_all_ready(storage_path, timeout=10, interval=1.0):
    # Tunggu file ada di storage (misal: attacked audio)
    start = time.time()
    while time.time() - start < timeout:
        if blob_exists(storage_path):
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

def find_attack_params_and_method_by_watermarked_filename(filename):
    # Cari dokumen di attacking_steps_audio untuk jenis & parameter
    jenis = "0"
    parameter = "0"
    docs_attack = db.collection("attacking_steps_audio").stream()
    for doc in docs_attack:
        data = doc.to_dict()
        if data.get("watermarked_file_name") == filename:
            jenis = data.get("jenis", "0")
            parameter = data.get("parameter", "0")
            break

    # Cari dokumen di embedding_steps_audio untuk method
    method = "default_method"  # default nilai method jika tidak ditemukan
    docs_embed = db.collection("embedding_steps_audio").stream()
    for doc in docs_embed:
        data = doc.to_dict()
        if data.get("main_file_name") == filename:
            method = data.get("method", method)
            break

    return jenis, parameter, method


def process_audio_extraction_requests(doc):
    doc_id = doc.id
    data = doc.to_dict()
    
    # Skip if already completed or processing
    if data.get("status") in ["completed", "processing"]:
        logging.warning(f"[SKIP] Document {doc_id} already processed or being processed")
        return

    try:
        # Mark as processing immediately
        doc.reference.update({"status": "processing"})
        
        original_filename = data.get("attacked_file_name")

        # Pastikan semua ada
        if not original_filename:
            logging.warning(f"[SKIP] original_filename missing in extraction_steps_audio/{doc_id}")
            doc.reference.update({"status": "failed", "error": "Missing attacked_file_name"})
            return

        print(f"[FIRESTORE] Processing audio extraction request: {doc_id}")

        # Cari dokumen processed_embedding_audio yang main_audio-nya sama dengan original_filename
        embed_doc = find_processed_embedding_by_original_filename(original_filename)        
        if not embed_doc:
            print(f"[SKIP] processed_embedding_audio with main_audio={original_filename} not found")
            doc.reference.update({"status": "failed", "error": "No matching embedding document found"})
            return
            
        print(f"[FOUND] processed_embedding_audio document matched: {embed_doc.id} for main_audio: {original_filename}")
       
        embed_data = embed_doc.to_dict()
        main_audio_path = embed_data.get("main_audio")
        
        if not main_audio_path:
            print(f"[SKIP] Incomplete data for {doc_id}")
            doc.reference.update({"status": "failed", "error": "Incomplete embedding data"})
            return

        jenis, parameter, method = find_attack_params_and_method_by_watermarked_filename(original_filename)

        # Download attacked audio dari storage
        attacked_local = get_file_from_firestore(main_audio_path, desired_filename=original_filename)
     
        # Start MATLAB Engine sekali di awal
        if 'eng' not in globals():
            eng = matlab.engine.start_matlab()
            eng.addpath(r'D:\TA\mfile_audio_exe\AWM_script', nargout=0)

        # Konversi parameter
        jenis1 = int(jenis)
        jenis2 = int(parameter)
        file_host = attacked_local
        isecc = int(method) if method.isdigit() else 0
        coding_type = int(method) if method.isdigit() else 0
        jbsf = 5  # sesuaikan jika kamu menyimpan nilainya di Firestore
        LN = 3072

        print(f"[MATLAB] Memanggil aplikasi_extract dengan:")
        print(f"  jenis1       : {jenis1}")
        print(f"  jenis2       : {jenis2}")
        print(f"  file_host    : {file_host}")
        print(f"  isecc        : {isecc}")
        print(f"  coding_type  : {coding_type}")
        print(f"  jbsf         : {jbsf}")
        print(f"  LN           : {LN}")

        try:
            eng.aplikasi_extract(
                jenis1,
                jenis2,
                file_host,
                isecc,
                coding_type,
                jbsf,
                LN,
                nargout=0
            )
            print("[MATLAB] aplikasi_extract berhasil dipanggil.")
        except Exception as matlab_error:
            logging.error(f"[MATLAB ERROR] {matlab_error}")
            doc.reference.update({"status": "failed", "error": f"MATLAB error: {matlab_error}"})
            return

        # Ambil hasil output_extract.txt dari folder hasil extracted
        extract_output_dir = r"D:\TA\mfile_audio_exe\AWM_script"
        output_extract_file = os.path.join(extract_output_dir, "output_extract.txt")

        storage_extract_path = f"processed/audio_extraction/output_extract_{doc_id}.txt"

        upload_success = False
        if os.path.exists(output_extract_file):
            bucket.blob(storage_extract_path).upload_from_filename(output_extract_file)
            logging.info(f"[UPLOAD] Uploaded {output_extract_file} to {storage_extract_path}")
            upload_success = True
        else:
            logging.warning(f"[MISSING] {output_extract_file} not found in {extract_output_dir}")

        # Save results to Firestore
        hasil = {
            "output_extract_path": storage_extract_path if upload_success else None,
            "timestamp": firestore.SERVER_TIMESTAMP
        }
        
        db.collection("processed_extraction_audio").document(doc_id).set(hasil)
        logging.info(f"[SUCCESS] Saved audio extraction results for {doc_id}")
        
        # Mark as completed
        doc.reference.update({"status": "completed"})
        processed_ids.add(doc_id)

    except Exception as e:
        logging.error(f"Failed to process audio extraction {doc_id}: {e}")
        traceback.print_exc()
        doc.reference.update({
            "status": "failed",
            "error": str(e)[:500]
        })

def setup_realtime_audio_extraction_listener():
    def on_snapshot(col_snapshot, changes, read_time):
        timestamp_str = read_time.strftime("%Y-%m-%d %H:%M:%S")
        logging.info(f"Received {len(changes)} audio extraction changes at {timestamp_str}")
        
        for change in changes:
            if change.type.name == "ADDED":
                data = change.document.to_dict()
                if data.get("status") == "pending":
                    process_audio_extraction_requests(change.document)

    query = db.collection("extraction_steps_audio").where("status", "==", "pending")
    query_watch = query.on_snapshot(on_snapshot)
    logging.info("🎧 Realtime audio extraction listener activated")
    return query_watch

def watch_audio_extraction_loop():
    logging.info("👂 Starting realtime listener for audio extraction...")
    
    # Process any pending documents on startup
    pending_docs = db.collection("extraction_steps_audio")\
                   .where("status", "==", "pending")\
                   .limit(5)\
                   .stream()
    
    for doc in pending_docs:
        if doc.id not in processed_ids:
            process_audio_extraction_requests(doc)
    
    # Start realtime listener
    watch = setup_realtime_audio_extraction_listener()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logging.info("\nShutting down audio extraction listener...")
    except Exception as e:
        logging.error(f"❌ Audio extraction listener error: {e}")
        traceback.print_exc()

if __name__ == "__main__":
    watch_audio_extraction_loop()