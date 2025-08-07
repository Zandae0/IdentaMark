# upload_api.py
from fastapi import APIRouter, UploadFile, File, Form
from fastapi.responses import FileResponse
from app.firebase.firebase_client import bucket, db
from datetime import datetime
import uuid
import tempfile
import shutil
import tempfile
import os

router = APIRouter()

@router.post("/upload-embedding")
async def upload_embedding(
    main_image: UploadFile = File(...),
    method: str = Form(...),
    status: str = Form(...)
    ):
    try:
        # Generate nama file temp unik SEKALI
        with tempfile.NamedTemporaryFile(delete=False, suffix=".bmp") as temp_main:
            main_filename = os.path.basename(temp_main.name)  # contoh: tmpabcd1234.wav
            shutil.copyfileobj(main_image.file, temp_main)
            temp_main.flush()
            bucket.blob(f"image_embedding/{main_filename}").upload_from_filename(temp_main.name)
        
        # Catat ke Firestore
        doc_ref = db.collection("embedding_steps").document()
        doc_ref.set({
            "main_file_name": main_filename,
            "method": method,
            "status": status,
            "timestamp": datetime.utcnow()
        })

        os.unlink(temp_main.name)  # bersihkan file lokal

        return {
            "status": "success",
            "doc_id": doc_ref.id,
            "main_image": main_filename,
            "method": method
        }
    except Exception as e:
        return {"status": "error", "message": str(e)}


@router.post("/upload-extraction")
async def upload_extraction(
    attacked_image: UploadFile = File(...),
    status:str=Form(...)
):
    try:
        
        wm_filename = attacked_image.filename

        # Simpan file ke Firebase Storage
        with tempfile.NamedTemporaryFile(delete=False) as temp_wm:
            shutil.copyfileobj(attacked_image.file, temp_wm)
            temp_wm.flush()

            # Upload ke Firebase Storage dgn content_type
            blob = bucket.blob(f"attacked_image/{wm_filename}")
            blob.upload_from_filename(temp_wm.name, content_type=attacked_image.content_type)

        # Simpan metadata ke Firestore
        doc_ref = db.collection("extraction_steps").document()
        doc_ref.set({
            "attacked_file_name": wm_filename,
            "timestamp": datetime.utcnow(),
            "status": status
        })

        return {
            "status": "success",
            "doc_id": doc_ref.id,
            "attacked_image": wm_filename
        }

    except Exception as e:
        return {"status": "error", "message": str(e)}

@router.post("/upload-attacking")
async def upload_attacking(
    watermarked_image: UploadFile = File(...),
    jenis: str = Form(...),
    parameter: str = Form(...),
    status:str=Form(...)
):
   try:
        # Ambil nama file asli
        original_filename = os.path.basename(watermarked_image.filename)

        # Simpan file sementara dan upload ke Firebase Storage
        with tempfile.NamedTemporaryFile(delete=False) as temp_audio:
            shutil.copyfileobj(watermarked_image.file, temp_audio)
            temp_audio.flush()
            blob = bucket.blob(f"watermarked_image/{original_filename}")
            blob.upload_from_filename(temp_audio.name, content_type=watermarked_image.content_type)

        # Simpan metadata ke Firestore
        doc_ref = db.collection("attacking_steps").document()
        doc_ref.set({
            "watermarked_file_name": original_filename,
            "timestamp": datetime.utcnow(),
            "jenis" : jenis,
            "parameter": parameter,
            "status": status
        })

        return {
            "status": "success",
            "doc_id": doc_ref.id,
            "watermarked_image": original_filename,
        }
   except Exception as e:
        return {"status": "error", "message": str(e)}

@router.post("/upload-audio-embedding")
async def upload_audio_embedding(
    main_audio: UploadFile = File(...),
    method: str = Form(...),
):
    try:
        # Pakai nama asli file user langsung
        original_filename = main_audio.filename
        
        # Simpan file sementara dengan nama asli
        temp_path = os.path.join(tempfile.gettempdir(), original_filename)
        with open(temp_path, "wb") as f:
            shutil.copyfileobj(main_audio.file, f)
        
        # Upload ke Firebase dengan nama asli
        bucket.blob(f"audio_embedding/{original_filename}").upload_from_filename(temp_path)
        
        # Catat ke Firestore
        doc_ref = db.collection("embedding_steps_audio").document()
        doc_ref.set({
            "main_file_name": original_filename,  # pakai nama asli
            "method": method,
            "timestamp": datetime.utcnow()
        })

        os.unlink(temp_path)  # bersihkan file lokal

        return {
            "status": "success",
            "doc_id": doc_ref.id,
            "main_audio": original_filename,
            "method": method
        }
    except Exception as e:
        if 'temp_path' in locals() and os.path.exists(temp_path):
            os.unlink(temp_path)
        return {"status": "error", "message": str(e)}
    
@router.post("/upload-extraction-audio")
async def upload_extraction_audio(
    attacked_audio: UploadFile = File(...),
):
    try:
        wm_filename = attacked_audio.filename

        # Simpan file audio ke temporary file
        with tempfile.NamedTemporaryFile(delete=False) as temp_wm:
            shutil.copyfileobj(attacked_audio.file, temp_wm)
            temp_wm.flush()

            # Upload ke Firebase Storage
            blob = bucket.blob(f"attacked_audio/{wm_filename}")
            blob.upload_from_filename(temp_wm.name, content_type=attacked_audio.content_type)

        # Simpan metadata ke Firestore
        doc_ref = db.collection("extraction_steps_audio").document()
        doc_ref.set({
            "attacked_file_name": wm_filename,
            "timestamp": datetime.utcnow()
        })

        return {
            "status": "success",
            "doc_id": doc_ref.id,
            "attacked_audio": wm_filename
        }

    except Exception as e:
        return {"status": "error", "message": str(e)}

@router.post("/upload-attacking-audio")
async def upload_attacking_audio(
    watermarked_audio: UploadFile = File(...),
    jenis: str = Form(...),
    parameter: str = Form(...)
):
    try:
        # Ambil nama file asli
        original_filename = os.path.basename(watermarked_audio.filename)

        # Simpan file sementara dan upload ke Firebase Storage
        with tempfile.NamedTemporaryFile(delete=False) as temp_audio:
            shutil.copyfileobj(watermarked_audio.file, temp_audio)
            temp_audio.flush()
            blob = bucket.blob(f"watermarked_audio/{original_filename}")
            blob.upload_from_filename(temp_audio.name, content_type=watermarked_audio.content_type)

        # Simpan metadata ke Firestore
        doc_ref = db.collection("attacking_steps_audio").document()
        doc_ref.set({
            "watermarked_file_name": original_filename,
            "jenis": jenis,
            "parameter": parameter,
            "timestamp": datetime.utcnow()
        })

        return {
            "status": "success",
            "doc_id": doc_ref.id,
            "watermarked_audio": original_filename,
            "jenis": jenis,
            "parameter": parameter
        }

    except Exception as e:
        return {"status": "error", "message": str(e)}

@router.get("/download-audio/{filename}")
async def download_audio(filename: str):
    blob = bucket.blob(f"processed/audio_embedding/{filename}")
    if not blob.exists():
        raise HTTPException(status_code=404, detail="File not found")

    temp_file = tempfile.mktemp(suffix=".wav")
    blob.download_to_filename(temp_file)
    return FileResponse(temp_file, filename=filename)

@router.get("/download-audio-embedding/{filename}")
async def download_audio_embedding(filename: str):
    blob = bucket.blob(f"audio_embedding/{filename}")
    if not blob.exists():
        raise BaseException(status_code=404, detail="File not found")

    temp_file = tempfile.mktemp(suffix=".wav")
    blob.download_to_filename(temp_file)
    return FileResponse(temp_file, filename=filename)