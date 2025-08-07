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
import sys

processed_ids = set()
last_checked = datetime.now(timezone.utc)
logging.basicConfig(level=logging.INFO)

def get_file_from_firestore(file_path, desired_filename=None):
    blob = bucket.blob(file_path)
    if not blob.exists():
        raise Exception(f"File {file_path} tidak ditemukan di Firebase Storage.")
    if desired_filename:
        temp_path = os.path.join(tempfile.gettempdir(), desired_filename)
    else:
        temp_path = tempfile.mktemp(suffix=".png")
    blob.download_to_filename(temp_path)
    return temp_path

def map_jenis_parameter_to_bat_args(jenis, parameter):
    mapping = {
        ("1", "0"): ("1", "0"),
        ("2", "50"): ("2", "50"),
        ("2", "70"): ("2", "70"),
        ("2", "90"): ("2", "90"),      
        ("3", "2"): ("3", "2"),
        ("3", "5"): ("3", "5"),
        ("3", "8"): ("3", "8"),
        ("6", "0.8"): ("6", "0.8"),
        ("6", "0.9"): ("6", "0.9"),
        ("6", "1.2"): ("6", "1.2"),
        ("7", "{[0.8 1.0]}"): ("7", "{[0.8 1.0]}"),
        ("7", "{[0.9 0.7]}"): ("7", "{[0.9 0.7]}"),
        ("7", "{[1.0 1.2]}"): ("7", "{[1.0 1.2]}"),
        ("9", "{[5 0.85]}"): ("9", "{[5 0.85]}"),
        ("9", "{[8 0.95]}"): ("9", "{[8 0.95]}"),
        ("9", "{[10 0.80]}"): ("9", "{[10 0.80]}"),
        ("11", "40"): ("11", "40"),
        ("11", "60"): ("11", "60"),
        ("11", "80"): ("11", "80"),
        ("12", "3"): ("12", "3"),
        ("12", "5"): ("12", "5"),
        ("12", "7"): ("12", "7"),
        ("17", "0.001"): ("17", "0.001"),
        ("17", "0.005"): ("17", "0.005"),
        ("17", "0.01"): ("17", "0.01"),
        ("21", "3"): ("21", "3"),
        ("21", "5"): ("21", "5"),
        ("21", "7"): ("21", "7"),
    }
    return mapping.get((jenis, parameter), ("0", "0"))


def process_new_image_attacking_requests(doc):
    doc_id = doc.id
    doc_data = doc.to_dict()
    
    # Skip if already completed or processing
    if doc_data.get("status") in ["completed", "processing"]:
        logging.warning(f"[SKIP] Document {doc_id} already processed or being processed")
        return

    try:
        # Mark as processing immediately
        doc.reference.update({"status": "processing"})
        
        original_filename = doc_data.get("watermarked_file_name")
        jenis = doc_data.get("jenis")
        parameter = doc_data.get("parameter")

        if not original_filename:
            logging.warning(f"[SKIP] original_filename missing in attacking_steps_image/{doc_id}")
            doc.reference.update({"status": "failed", "error": "Missing watermarked_file_name"})
            return

        print(f"[FIRESTORE] Processing attacking request: {doc_id}")
      
        bat_arg1, bat_arg2 = map_jenis_parameter_to_bat_args(jenis, parameter)

        # Download host image (main)
        host_temp = get_file_from_firestore(f"watermarked_image/{original_filename}", desired_filename=original_filename)

        # Jalankan proses attack via .bat (input hanya host image)
        run_attack_path = r"D:\TA\mfile_image CD\mfile_image CD\run_attack.py"
        

        logging.info(f"[INFO] Running attack with parameters:")
        logging.info(f"  jenis attacking     : {bat_arg1}")
        logging.info(f"  parameter           : {bat_arg2}")
        logging.info(f"  file                : {host_temp}")

        result = subprocess.run(
            [sys.executable, run_attack_path, host_temp, bat_arg1, bat_arg2],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )


        if result.stderr:
            logging.error(f"Error executing EXE: {result.stderr}")

        # ===== Ambil hasil dari folder lokal attacker =====
        output_dir = r"D:\TA\mfile_image CD\mfile_image CD\output_attack"
        filename_noext = os.path.splitext(os.path.basename(host_temp))[0]

        search_prefix = f"Iw3_attacked_{filename_noext}-"
        all_bmp_files = glob.glob(os.path.join(output_dir, f"{search_prefix}*.bmp"))

        attacked_files = []

        def normalize_param(p):
            return str(float(p)).rstrip('0').rstrip('.') if '.' in str(p) else str(p)

        # Normalisasi parameter
        if isinstance(parameter, (list, tuple)):
            param_str = "_".join([normalize_param(p) for p in parameter])
        elif isinstance(parameter, str):
            cleaned = parameter.replace("{", "").replace("}", "").replace("[", "").replace("]", "").replace(" ", "_")
            split_parts = cleaned.split("_")
            try:
                param_str = "_".join([normalize_param(p) for p in split_parts])
            except:
                param_str = cleaned
        else:
            param_str = normalize_param(parameter)

        jenis_str = str(jenis)

        for fpath in all_bmp_files:
            fname = os.path.basename(fpath)
            suffix = fname.replace(search_prefix, "").replace(".bmp", "")
            parts = suffix.split("_")
            if not parts or len(parts) < 2:
                continue
            jenis_candidate = parts[0]
            param_candidate = "_".join([normalize_param(p) for p in parts[1:]])

            if jenis_candidate == jenis_str and param_candidate == param_str:
                attacked_files.append(fpath)

        if not attacked_files:
            logging.warning(f"[MISSING] No attacked image found for {filename_noext} with jenis={jenis} and parameter={parameter}")
            attacked_files = []



        uploaded_attacked_paths = []
        for af in attacked_files:
            fname = os.path.basename(af)
            dest_path = f"processed/image_attacking/output_{doc_id}_{fname}"
            bucket.blob(dest_path).upload_from_filename(af)
            logging.info(f"[UPLOAD] Uploaded {af} to {dest_path}")
            uploaded_attacked_paths.append(dest_path)

        if not attacked_files:
            logging.warning(f"[MISSING] No attacked image found for {filename_noext} in {output_dir}")

        hasil = {
            "attacked_image_paths": uploaded_attacked_paths[0] if uploaded_attacked_paths else None,
            "timestamp": firestore.SERVER_TIMESTAMP,
            "original_file": f"watermarked_image/{original_filename}"
        }

        db.collection("processed_attack").document(doc_id).set(hasil)
        logging.info(f"[SUCCESS] Saved attack results for {doc_id} to Firestore")
        
        # Mark as completed
        doc.reference.update({"status": "completed"})
        processed_ids.add(doc_id)

    except Exception as e:
        logging.error(f"Failed to process image attacking {doc_id}: {e}")
        traceback.print_exc()
        doc.reference.update({
            "status": "failed",
            "error": str(e)[:500]  # Limit error message length
        })

def setup_realtime_attacking_listener():
    def on_snapshot(col_snapshot, changes, read_time):
        timestamp_str = read_time.strftime("%Y-%m-%d %H:%M:%S")
        logging.info(f"Received {len(changes)} attacking changes at {timestamp_str}")
        
        for change in changes:
            if change.type.name == "ADDED":
                data = change.document.to_dict()
                if data.get("status") == "pending":
                    process_new_image_attacking_requests(change.document)

    query = db.collection("attacking_steps").where("status", "==", "pending")
    query_watch = query.on_snapshot(on_snapshot)
    logging.info("🔥 Realtime attacking listener activated")
    return query_watch

def watch_attacking_loop():
    logging.info("👀 Starting realtime listener for image attacking...")
    
    # Process any pending documents on startup
    pending_docs = db.collection("attacking_steps")\
                   .where("status", "==", "pending")\
                   .limit(5)\
                   .stream()
    
    for doc in pending_docs:
        if doc.id not in processed_ids:
            process_new_image_attacking_requests(doc)
    
    # Start realtime listener
    watch = setup_realtime_attacking_listener()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logging.info("\nShutting down attacking listener...")
    except Exception as e:
        logging.error(f"❌ Attacking listener error: {e}")
        traceback.print_exc()

if __name__ == "__main__":
    watch_attacking_loop()