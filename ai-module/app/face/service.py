"""Reconnaissance faciale : détection YuNet + embeddings SFace (modèles ONNX OpenCV Zoo)."""
import base64
import binascii
import os
import threading
from typing import List

import cv2
import numpy as np

MODELS_DIR = os.getenv("FACE_MODELS_DIR", os.path.join(os.path.dirname(__file__), "models"))
DETECTOR_MODEL = "face_detection_yunet_2023mar.onnx"
RECOGNIZER_MODEL = "face_recognition_sface_2021dec.onnx"
# Seuil de similarité cosinus recommandé par OpenCV pour SFace.
COSINE_THRESHOLD = float(os.getenv("FACE_COSINE_THRESHOLD", "0.363"))
MAX_IMAGE_BYTES = 4 * 1024 * 1024
MAX_SIDE = 1280


class FaceError(ValueError):
    """Erreur fonctionnelle (image illisible, aucun visage, plusieurs visages…)."""


def decode_image(data: str) -> np.ndarray:
    if "," in data and data.strip().startswith("data:"):
        data = data.split(",", 1)[1]
    try:
        raw = base64.b64decode(data, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise FaceError("Image invalide (base64 attendu)") from exc
    if len(raw) > MAX_IMAGE_BYTES:
        raise FaceError("Image trop volumineuse")
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise FaceError("Image illisible")
    h, w = img.shape[:2]
    if max(h, w) > MAX_SIDE:
        ratio = MAX_SIDE / max(h, w)
        img = cv2.resize(img, (int(w * ratio), int(h * ratio)))
    return img


class FaceEngine:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._detector = None
        self._recognizer = None

    def available(self) -> bool:
        return all(os.path.isfile(os.path.join(MODELS_DIR, m)) for m in (DETECTOR_MODEL, RECOGNIZER_MODEL))

    def _load(self) -> None:
        if self._detector is None:
            if not self.available():
                raise RuntimeError(f"Modèles de reconnaissance faciale absents de {MODELS_DIR}")
            self._detector = cv2.FaceDetectorYN.create(
                os.path.join(MODELS_DIR, DETECTOR_MODEL), "", (320, 320), 0.8, 0.3, 5000)
            self._recognizer = cv2.FaceRecognizerSF.create(
                os.path.join(MODELS_DIR, RECOGNIZER_MODEL), "")

    def encode(self, img: np.ndarray) -> List[float]:
        # Le détecteur YuNet est à état (setInputSize) : accès sérialisé.
        with self._lock:
            self._load()
            h, w = img.shape[:2]
            self._detector.setInputSize((w, h))
            _, faces = self._detector.detect(img)
            if faces is None or len(faces) == 0:
                raise FaceError("Aucun visage détecté, placez-vous face à la caméra")
            if len(faces) > 1:
                raise FaceError("Plusieurs visages détectés, une seule personne doit être face à la caméra")
            aligned = self._recognizer.alignCrop(img, faces[0])
            feature = self._recognizer.feature(aligned).flatten().astype(np.float64)
        norm = np.linalg.norm(feature)
        if norm == 0:
            raise FaceError("Visage non exploitable")
        return (feature / norm).tolist()

    @staticmethod
    def similarity(a: List[float], b: List[float]) -> float:
        va, vb = np.asarray(a, dtype=np.float64), np.asarray(b, dtype=np.float64)
        if va.shape != vb.shape or va.size == 0:
            raise FaceError("Encodage de référence invalide")
        denom = np.linalg.norm(va) * np.linalg.norm(vb)
        return float(np.dot(va, vb) / denom) if denom else 0.0


engine = FaceEngine()
