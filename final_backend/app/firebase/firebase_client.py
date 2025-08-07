import firebase_admin
from firebase_admin import credentials, firestore, storage
import os
from dotenv import load_dotenv
import tempfile

load_dotenv()

# Inisialisasi Firebase Admin SDK
cred = credentials.Certificate("capstone-2025-1-firebase-adminsdk-fbsvc-393ca919cc.json")
firebase_admin.initialize_app(cred, {
    'storageBucket': os.getenv("FIREBASE_BUCKET")  # Firebase storage bucket name
})

db = firestore.client()
bucket = storage.bucket()

def get_file_from_firestore(file_path):
    """Mendownload file dari Firebase Storage berdasarkan path yang diberikan"""
    blob = bucket.blob(file_path)
    if not blob.exists():
        raise Exception(f"File {file_path} tidak ditemukan di Firebase Storage.")
    temp_path = tempfile.mktemp(suffix=".tmp")
    blob.download_to_filename(temp_path)
    return temp_path

def get_audio_metadata_from_firestore(doc_id):
    """Mengambil metadata file dari Firestore berdasarkan document ID"""
    doc_ref = db.collection("embedding_steps_audio").document(doc_id)
    doc = doc_ref.get()
    if doc.exists:
        return doc.to_dict()  # Kembalikan metadata file
    else:
        raise Exception(f"Document {doc_id} tidak ditemukan di Firestore.")
