# main.py
from fastapi import FastAPI
import threading
from app.embedding.embedder import setup_realtime_embedding_listener as embedding_realtime
from app.embedding.embedder_audio import setup_realtime_audio_embedding_listener as embedding_audio_realtime
from app.extraction.extractor import setup_realtime_extraction_listener as extraction_realtime
from app.extraction.extractor_audio import setup_realtime_audio_extraction_listener as extraction_audio_realtime
from app.attacking.attacker import setup_realtime_attacking_listener as attacking_realtime
from app.attacking.attacker_audio import setup_realtime_audio_attacking_listener as attacking_audio_realtime
from app.router.upload_api import router as upload_router

app = FastAPI()

app.include_router(upload_router)

def run_realtime_listener(listener_func):
    """Helper function to run realtime listeners"""
    try:
        listener_func()  # Ini akan memulai listener dan block
    except Exception as e:
        print(f"Listener error: {e}")

@app.on_event("startup")
def start_watchers():
    # Start all realtime listeners in separate threads
    threading.Thread(
        target=run_realtime_listener,
        args=(embedding_realtime,),
        daemon=True
    ).start()
    
    threading.Thread(
        target=run_realtime_listener,
        args=(embedding_audio_realtime,),
        daemon=True
    ).start()
    
    threading.Thread(
        target=run_realtime_listener,
        args=(extraction_realtime,),
        daemon=True
    ).start()
    
    threading.Thread(
        target=run_realtime_listener,
        args=(extraction_audio_realtime,),
        daemon=True
    ).start()
    
    threading.Thread(
        target=run_realtime_listener,
        args=(attacking_realtime,),
        daemon=True
    ).start()
    
    threading.Thread(
        target=run_realtime_listener,
        args=(attacking_audio_realtime,),
        daemon=True
    ).start()

@app.get("/")
def root():
    return {"message": "Server is running with Firestore realtime listeners"}