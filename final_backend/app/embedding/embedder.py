import subprocess
import sys
import firebase_admin
from firebase_admin import firestore, storage
import os
import json
import tempfile
import time
from datetime import datetime, timezone
import traceback
import logging
import re
import glob
from fractions import Fraction
from app.firebase.firebase_client import bucket, db  # Menggunakan firebase_client.py yang sudah ada

# Global variable
processed_ids = set()
last_checked = datetime.now(timezone.utc)
logging.basicConfig(level=logging.INFO)

def get_file_from_firestore(file_path):
    blob = bucket.blob(file_path)
    if not blob.exists():
        raise Exception(f"File {file_path} tidak ditemukan di Firebase Storage.")
    # Simpan dengan nama yang sama di temp folder
    temp_path = os.path.join(tempfile.gettempdir(), os.path.basename(file_path))
    blob.download_to_filename(temp_path)
    return temp_path

def save_results_to_firestore(doc_id, hasil):
    try:
        doc_ref = db.collection("processed_embedding").document(doc_id)
        doc_ref.set(hasil)
        print(f"[SUCCESS] Results for {doc_id} saved to Firestore")
    except Exception as e:
        print(f"[ERROR] Failed to save results to Firestore: {e}")

def wait_until_all_ready(image_path, timeout=10, interval=1.0):
    full_image_path = f"image_embedding/{image_path}"
    start = time.time()
    while time.time() - start < timeout:
        blobs = list(bucket.list_blobs(prefix=full_image_path))
        if any(blob.name == full_image_path for blob in blobs):
            return True
        time.sleep(interval)
    return False

def process_new_image_embedding_requests(doc):
    doc_id = doc.id
    data = doc.to_dict()
    
    # Skip if already completed or processing
    if data.get("status") in ["completed", "processing"]:
        print(f"[SKIP] Document {doc_id} already processed or being processed")
        return
    # Mark as processing immediately
    doc.reference.update({"status": "processing"})

    try:
        # Mark as processing immediately
        doc.reference.update({"status": "processing"})
        image_path = data.get("main_file_name")
        method = data.get("method", "1")

        if not image_path:
            print(f"[SKIP] Incomplete data for {doc_id}")
            doc.reference.update({"status": "failed", "error": "Missing main_file_name"})
            
            return
        
        print(f"[FIRESTORE] Processing image embedding request: {doc_id}")

        if not wait_until_all_ready(image_path):
            print(f"[TIMEOUT] Files not ready for {doc_id}")
            doc.reference.update({"status": "failed", "error": "File not ready after timeout"})
            
            return
        
        # Set parameter berdasarkan method
        if method == "1":
            G = "20"
            isint = "0"
            coding_rate = "1/3"
        elif method == "0":
            G = "20"
            isint = "1"
            coding_rate = "1/3"
        else:
            G = "20"
            isint = "0"
            coding_rate = "1/3"

        # Download image file
        image_temp = get_file_from_firestore(f"image_embedding/{image_path}")

        run_embed_path = r"D:\TA\mfile_image CD\mfile_image CD\run_embed.py"



        print(f"[INFO] Running image embed with parameters:")
        print(f"  image_path  : {image_temp}")
        print(f"  G          : {G}")
        print(f"  coding_type: {method}")
        print(f"  isint      : {isint}")
        print(f"  coding_rate: {coding_rate}")

        result = subprocess.run([
            sys.executable, run_embed_path,
            image_temp,         # path ke gambar
            str(G),             # misal "18"
            str(method),   # misal "1"
            str(isint),         # misal "0"
            str(coding_rate)    # misal "0.5"
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        print(result.stdout)
        if result.stderr:
            print("Error:", result.stderr)

        # Upload hasil output folder ke Firebase Storage
        output_dir = r"D:\TA\mfile_image CD\mfile_image CD\output_embed"
        filename_noext = os.path.splitext(os.path.basename(image_temp))[0]
        uploaded_output_paths = []
        
        output_pattern = os.path.join(output_dir, f"Iw_reversible_{filename_noext}.bmp")
        output_files = glob.glob(output_pattern)
        output_embed_file = os.path.join(output_dir, f"embed_result_{filename_noext}.txt")

        storage_embed_path = f"processed/image_embedding/output_embed_{doc_id}.txt"

        for f in output_files:
            fname = os.path.basename(f)
            dest_path = f"processed/image_embedding/output_{doc_id}_{fname}"
            bucket.blob(dest_path).upload_from_filename(f)
            logging.info(f"[UPLOAD] Uploaded {f} to {dest_path}")
            uploaded_output_paths.append(dest_path)

        if not output_files:
            logging.warning(f"[MISSING] No output files found in {output_dir}")

        if os.path.exists(output_embed_file):
            bucket.blob(storage_embed_path).upload_from_filename(output_embed_file)
            logging.info(f"[UPLOAD] Uploaded {output_embed_file} to {storage_embed_path}")
        else:
            logging.warning(f"[MISSING] {output_embed_file} not found in {output_dir}")

        # Simpan hasil ke Firestore
        hasil = {
            "main_image": f"image_embedding/{image_path}",
            "output_embed_path": storage_embed_path if os.path.exists(output_embed_file) else None,
            "watermarked_image_path": uploaded_output_paths[0] if uploaded_output_paths else None,
            "timestamp": firestore.SERVER_TIMESTAMP
        }
        save_results_to_firestore(doc_id, hasil)
        
        # Mark as completed
        doc.reference.update({"status": "completed"})
        processed_ids.add(doc_id)

    except Exception as e:
            print(f"[ERROR] Failed to process image embedding {doc_id}: {e}")
            traceback.print_exc()
            doc.reference.update({
                "status": "failed",
                "error": str(e)[:500]  # Limit error message length
            })

def setup_realtime_embedding_listener():
    def on_snapshot(col_snapshot, changes, read_time):
        # Cara yang benar untuk format timestamp Firestore
        timestamp_str = read_time.strftime("%Y-%m-%d %H:%M:%S")  # Format dasar
        print(f"Received {len(changes)} changes at {timestamp_str}")
        
        for change in changes:
            if change.type.name == "ADDED":
                data = change.document.to_dict()
                if data.get("status") == "pending":
                    process_new_image_embedding_requests(change.document)

    query = db.collection("embedding_steps").where("status", "==", "pending")
    query_watch = query.on_snapshot(on_snapshot)
    print("🔥 Realtime listener activated")
    return query_watch

def watch_loop():
    print("👀 Starting realtime listener for image embedding...")
    
    # Process any pending documents on startup
    pending_docs = db.collection("embedding_steps")\
                   .where("status", "==", "pending")\
                   .limit(5)\
                   .stream()
    
    for doc in pending_docs:
        if doc.id not in processed_ids:
            process_new_image_embedding_requests(doc)
    
    # Start realtime listener
    watch = setup_realtime_embedding_listener()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down listener...")
    except Exception as e:
        print(f"❌ Error: {e}")
        traceback.print_exc()

if __name__ == "__main__":
    watch_loop()