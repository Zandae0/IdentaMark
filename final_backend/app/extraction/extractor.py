import subprocess
import os
import time
from firebase_admin import firestore, storage
import tempfile
import traceback
import logging
from datetime import datetime, timezone
from app.firebase.firebase_client import db, bucket
import glob
import sys

processed_ids = set()
last_checked = datetime.now(timezone.utc)
logging.basicConfig(level=logging.INFO)

def get_file_from_firestore(file_path, desired_filename=None):
    blob = bucket.blob(file_path)
    if not blob.exists():
        raise Exception(f"File {file_path} tidak ditemukan di Firebase Storage.")
    if desired_filename:
        temp_dir = tempfile.gettempdir()
        temp_path = os.path.join(temp_dir, desired_filename)
    else:
        temp_path = tempfile.mktemp(suffix=".png")
    blob.download_to_filename(temp_path)
    return temp_path

def save_results_to_firestore(doc_id, hasil):
    try:
        doc_ref = db.collection("processed_extraction").document(doc_id)
        doc_ref.set(hasil)
        print(f"[SUCCESS] Results for {doc_id} saved to Firestore")
    except Exception as e:
        print(f"[ERROR] Failed to save results to Firestore: {e}")


def find_closest_file_by_timestamp(folder_path, timestamp, prefix="extract_result_", ext=".txt"):
    candidates = []
    for f in os.listdir(folder_path):
        if f.startswith(prefix) and f.endswith(ext):
            full_path = os.path.join(folder_path, f)
            mtime = datetime.fromtimestamp(os.path.getmtime(full_path), tz=timezone.utc)
            diff = abs((mtime - timestamp).total_seconds())
            candidates.append((diff, full_path))
    if not candidates:
        return None
    candidates.sort(key=lambda x: x[0])
    return candidates[0][1]

def process_image_extraction_requests(doc):
    doc_id = doc.id
    doc_data = doc.to_dict()
    
    # Skip if already completed or processing
    if doc_data.get("status") in ["completed", "processing"]:
        logging.warning(f"[SKIP] Document {doc_id} already processed or being processed")
        return

    try:
        # Mark as processing immediately
        doc.reference.update({"status": "processing"})
        
        attacked_filename = doc_data.get("attacked_file_name")
        timestamp = doc_data.get("timestamp", datetime.now(timezone.utc))

        if not attacked_filename:
            logging.warning(f"[SKIP] attacked_file_name missing in extraction_steps_image/{doc_id}")
            doc.reference.update({"status": "failed", "error": "Missing attacked_file_name"})
            return

        print(f"[FIRESTORE] Processing extraction request: {doc_id}")

        # Download attacked image
        attacked_local = get_file_from_firestore(f"attacked_image/{attacked_filename}", desired_filename=attacked_filename)

        # Run extraction process
        run_extract_path = r"D:\TA\mfile_image CD\mfile_image CD\run_extract.py"
        result = subprocess.run(
            [sys.executable, run_extract_path, attacked_local],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )


        if result.stderr:
            logging.error(f"Error executing EXE: {result.stderr}")

        # Handle output files
        extract_output_dir = r"D:\TA\mfile_image CD\mfile_image CD\output_extract"
        output_extract_file = find_closest_file_by_timestamp(extract_output_dir, timestamp)
        storage_extract_path = f"processed/image_extraction/output_extract_{doc_id}.txt"

        if os.path.exists(output_extract_file):
            bucket.blob(storage_extract_path).upload_from_filename(output_extract_file)
            logging.info(f"[UPLOAD] Uploaded {output_extract_file} to {storage_extract_path}")
        else:
            logging.warning(f"[MISSING] {output_extract_file} not found in {extract_output_dir}")

        # Save results
        hasil = {
            "output_extract_path": storage_extract_path if os.path.exists(output_extract_file) else None,
            "timestamp": firestore.SERVER_TIMESTAMP,
            "original_file": f"attacked_image/{attacked_filename}"
        }
        
        db.collection("processed_extraction").document(doc_id).set(hasil)
        logging.info(f"[SUCCESS] Saved extraction results for {doc_id} to Firestore")
        
        # Mark as completed
        doc.reference.update({"status": "completed"})
        processed_ids.add(doc_id)

    except Exception as e:
        logging.error(f"Failed to process image extraction {doc_id}: {e}")
        traceback.print_exc()
        doc.reference.update({
            "status": "failed",
            "error": str(e)[:500]  # Limit error message length
        })

def setup_realtime_extraction_listener():
    def on_snapshot(col_snapshot, changes, read_time):
        timestamp_str = read_time.strftime("%Y-%m-%d %H:%M:%S")
        logging.info(f"Received {len(changes)} extraction changes at {timestamp_str}")
        
        for change in changes:
            if change.type.name == "ADDED":
                data = change.document.to_dict()
                if data.get("status") == "pending":
                    process_image_extraction_requests(change.document)

    query = db.collection("extraction_steps").where("status", "==", "pending")
    query_watch = query.on_snapshot(on_snapshot)
    logging.info("🔎 Realtime extraction listener activated")
    return query_watch

def watch_extraction_loop():
    logging.info("👀 Starting realtime listener for image extraction...")
    
    # Process any pending documents on startup
    pending_docs = db.collection("extraction_steps")\
                   .where("status", "==", "pending")\
                   .limit(5)\
                   .stream()
    
    for doc in pending_docs:
        if doc.id not in processed_ids:
            process_image_extraction_requests(doc)
    
    # Start realtime listener
    watch = setup_realtime_extraction_listener()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logging.info("\nShutting down extraction listener...")
    except Exception as e:
        logging.error(f"❌ Extraction listener error: {e}")
        traceback.print_exc()

if __name__ == "__main__":
    watch_extraction_loop()