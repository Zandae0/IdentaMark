import subprocess
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
import matlab.engine

# Global variable
processed_ids = set()
last_checked = datetime.now(timezone.utc)

logging.basicConfig(level=logging.INFO)

def get_file_from_firestore(file_path):
    blob = bucket.blob(file_path)
    if not blob.exists():
        raise Exception(f"File {file_path} tidak ditemukan di Firebase Storage.")
    # SIMPAN file dengan nama YANG SAMA DENGAN DI STORAGE/FIRESTORE!
    temp_path = os.path.join(tempfile.gettempdir(), os.path.basename(file_path))
    blob.download_to_filename(temp_path)
    return temp_path


def save_results_to_firestore(doc_id, hasil):
    try:
        doc_ref = db.collection("processed_embedding_audio").document(doc_id)
        doc_ref.set(hasil)
        print(f"[SUCCESS] Results for {doc_id} saved to Firestore")
    except Exception as e:
        print(f"[ERROR] Failed to save results to Firestore: {e}")

def blob_exists(path):
    blobs = list(bucket.list_blobs(prefix=path))
    return any(blob.name == path for blob in blobs)

def wait_until_all_ready(audio_path, timeout=10, interval=1.0):
    full_audio_path = f"audio_embedding/{audio_path}"
    start = time.time()
    while time.time() - start < timeout:
        if blob_exists(full_audio_path):
            return True
        time.sleep(interval)
    return False

def process_new_audio_embedding_requests(doc):
    doc_id = doc.id
    data = doc.to_dict()
    
    # Skip if already completed or processing
    if data.get("status") in ["completed", "processing"]:
        logging.warning(f"[SKIP] Document {doc_id} already processed or being processed")
        return

    try:
        # Mark as processing immediately
        doc.reference.update({"status": "processing"})
        
        audio_path = data.get("main_file_name")
        method = data.get("method")

        if not audio_path:
            print(f"[SKIP] Incomplete data for {doc_id}")
            doc.reference.update({"status": "failed", "error": "Missing main_file_name"})
            return
        
        print(f"[FIRESTORE] Processing audio embedding request: {doc_id}")

        if not wait_until_all_ready(audio_path):
            print(f"[TIMEOUT] Files not ready for {doc_id}")
            doc.reference.update({"status": "failed", "error": "Files not ready after timeout"})
            return

        # ===== Parameter selection =====
        audio_filename = os.path.basename(audio_path).lower()
        
        if method == "0":  # Method Convolutional Coding
            coding_rate = "1/3"
            if "voice_cutted" in audio_filename:
                alfa = "0.007"
                alfasync = "0.008"
            elif "africa-toto_cutted" in audio_filename:
                alfa = "0.016"
                alfasync = "0.015"
            elif "around_the_world-atc_cutted" in audio_filename:
                alfa = "0.019"
                alfasync = "0.009"
            elif "evangeline-matthew_sweet_cutted" in audio_filename:
                alfa = "0.011"
                alfasync = "0.008"
            else:  # Default untuk method 0
                alfa = "0.001"
                alfasync = "0.001"
                
        elif method == "1":  # Method BCH Coding
            coding_rate = "1/3"
            if "voice_cutted" in audio_filename:
                alfa = "0.006"
                alfasync = "0.014"
            elif "africa-toto_cutted" in audio_filename:
                alfa = "0.013"
                alfasync = "0.013"
            elif "around_the_world-atc_cutted." in audio_filename:
                alfa = "0.016"
                alfasync = "0.009"
            elif "evangeline-matthew_sweet_cutted" in audio_filename:
                alfa = "0.011"
                alfasync = "0.017"
            else:  # Default untuk method 1
                alfa = "0.002"
                alfasync = "0.002"
                
        else:  # Default jika method tidak dikenali
            coding_rate = "1/3"
            alfa = "0.003"
            alfasync = "0.003"

        # Download audio dari Storage
        audio_temp = get_file_from_firestore(f"audio_embedding/{audio_path}")
        base_dir = os.path.dirname(audio_temp)

        # Start MATLAB Engine (hanya sekali)
        if 'eng' not in globals():
            eng = matlab.engine.start_matlab()
            eng.addpath(r'D:\TA\mfile_audio_exe\AWM_script', nargout=0)

        # Konversi parameter
        alfa_float = float(alfa)
        alfasync_float = float(alfasync)
        coding_rate_str = str(Fraction(coding_rate))  # Contoh: '1/2'
        isecc_int = int(method)
        jbsf_int = 5  # default dari kamu
        LN_int = 3072
        coding_type_int = int(method)

        print(f"[MATLAB] Calling aplikasi_embed with:")
        print(f"  alfa        : {alfa_float}")
        print(f"  alfasync    : {alfasync_float}")
        print(f"  file_host   : {audio_temp}")
        print(f"  coding_rate : {coding_rate_str}")
        print(f"  method      : {method} (ECC & coding_type)")
        print(f"  LN          : {LN_int}, jbsf: {jbsf_int}")

        # Panggil aplikasi_embed.m langsung
        try:
            eng.aplikasi_embed(
                alfa_float,
                LN_int,
                alfasync_float,
                audio_temp,
                isecc_int,
                coding_rate_str,
                jbsf_int,
                coding_type_int,
                nargout=0
            )
            print(f"[MATLAB] aplikasi_embed selesai dipanggil.")
        except Exception as matlab_error:
            logging.error(f"[MATLAB ERROR] {matlab_error}")
            doc.reference.update({"status": "failed", "error": f"MATLAB error: {matlab_error}"})
            return

        # ===== Handle output files =====
        output_dir = r"D:\TA\mfile_audio_exe\Data\watermarked_audio"
        filename_noext = os.path.splitext(os.path.basename(audio_temp))[0]
        watermark_pattern = os.path.join(output_dir, f"w*-{filename_noext}.wav")
        watermarked_files = glob.glob(watermark_pattern)

        # Upload watermarked audio files
        uploaded_watermarked_paths = []
        for wf in watermarked_files:
            fname = os.path.basename(wf)
            dest_path = f"processed/audio_embedding/output_{doc_id}_{fname}"
            bucket.blob(dest_path).upload_from_filename(wf)
            logging.info(f"[UPLOAD] Uploaded {wf} to {dest_path}")
            uploaded_watermarked_paths.append(dest_path)

        if not watermarked_files:
            logging.warning(f"[MISSING] No watermarked audio found for {filename_noext} in {output_dir}")

        # Upload embed_data.mat & codec_data.mat
        output_dir_embed_data = r"D:\TA\mfile_audio_exe\Data\watermarked_audio"
        output_dir_codec_data = r"D:\TA\mfile_audio_exe\AWM_script\codec_data"
        embed_output_dir = r"D:\TA\mfile_audio_exe\AWM_script"
        
        output_embed_file = os.path.join(embed_output_dir, "output_embed.txt")
        embed_data_file = os.path.join(output_dir_embed_data, f"embed_data_{filename_noext}.mat")
        codec_data_file = os.path.join(output_dir_codec_data, f"codec_data_{filename_noext}.mat")

        storage_embed_data_path = f"processed/audio_embedding/embed_data_{doc_id}.mat"
        storage_codec_data_path = f"processed/audio_embedding/codec_data_{doc_id}.mat"
        storage_embed_path = f"processed/audio_embedding/output_embed_{doc_id}.txt"

        # Upload files if they exist
        upload_results = {
            "embed_data": False,
            "codec_data": False,
            "embed_output": False
        }

        if os.path.exists(embed_data_file):
            bucket.blob(storage_embed_data_path).upload_from_filename(embed_data_file)
            logging.info(f"[UPLOAD] Uploaded {embed_data_file} to {storage_embed_data_path}")
            upload_results["embed_data"] = True

        if os.path.exists(codec_data_file):
            bucket.blob(storage_codec_data_path).upload_from_filename(codec_data_file)
            logging.info(f"[UPLOAD] Uploaded {codec_data_file} to {storage_codec_data_path}")
            upload_results["codec_data"] = True

        if os.path.exists(output_embed_file):
            bucket.blob(storage_embed_path).upload_from_filename(output_embed_file)
            logging.info(f"[UPLOAD] Uploaded {output_embed_file} to {storage_embed_path}")
            upload_results["embed_output"] = True

        # Save results to Firestore
        hasil = {
            "main_audio": f"audio_embedding/{audio_path}",
            "method": method,
            "watermarked_audio_paths": uploaded_watermarked_paths[0] if uploaded_watermarked_paths else None,
            "output_embed_path": storage_embed_path if upload_results["embed_output"] else None,
            "embed_data_path": storage_embed_data_path if upload_results["embed_data"] else None,
            "codec_data_path": storage_codec_data_path if upload_results["codec_data"] else None,
            "timestamp": firestore.SERVER_TIMESTAMP
        }
        
        db.collection("processed_embedding_audio").document(doc_id).set(hasil)
        logging.info(f"[SUCCESS] Saved audio embedding results for {doc_id}")
        
        # Mark as completed
        doc.reference.update({"status": "completed"})
        processed_ids.add(doc_id)

    except Exception as e:
        logging.error(f"Failed to process audio embedding {doc_id}: {e}")
        traceback.print_exc()
        doc.reference.update({
            "status": "failed",
            "error": str(e)[:500]
        })

def setup_realtime_audio_embedding_listener():
    def on_snapshot(col_snapshot, changes, read_time):
        timestamp_str = read_time.strftime("%Y-%m-%d %H:%M:%S")
        logging.info(f"Received {len(changes)} audio embedding changes at {timestamp_str}")
        
        for change in changes:
            if change.type.name == "ADDED":
                data = change.document.to_dict()
                if data.get("status") == "pending":
                    process_new_audio_embedding_requests(change.document)

    query = db.collection("embedding_steps_audio").where("status", "==", "pending")
    query_watch = query.on_snapshot(on_snapshot)
    logging.info("🎧 Realtime audio embedding listener activated")
    return query_watch

def watch_audio_embedding_loop():
    logging.info("👂 Starting realtime listener for audio embedding...")
    
    # Process any pending documents on startup
    pending_docs = db.collection("embedding_steps_audio")\
                   .where("status", "==", "pending")\
                   .limit(5)\
                   .stream()
    
    for doc in pending_docs:
        if doc.id not in processed_ids:
            process_new_audio_embedding_requests(doc)
    
    # Start realtime listener
    watch = setup_realtime_audio_embedding_listener()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logging.info("\nShutting down audio embedding listener...")
    except Exception as e:
        logging.error(f"❌ Audio embedding listener error: {e}")
        traceback.print_exc()

if __name__ == "__main__":
    watch_audio_embedding_loop()