"""Fixture de inferencia real para CI, excluido de Git y de la APK principal. Sin datos del usuario."""
import hashlib
import io
import json
import pathlib
import tarfile
import urllib.request
import zipfile

URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ljspeech-medium.tar.bz2"
SHA256 = "3dfb4b759d8be032a4903a9538d128b0fda2a06ab1de6cbc2d93a97e2dd83dba"
ROOT = pathlib.Path(__file__).resolve().parents[1]


def main():
    target = ROOT / "app/src/androidTest/assets/modelo-prueba.zip"
    cache = ROOT / ".gradle-user/modelo-prueba.tar.bz2"
    cache.parent.mkdir(exist_ok=True)
    if not cache.exists() or hashlib.sha256(cache.read_bytes()).hexdigest() != SHA256:
        with urllib.request.urlopen(URL, timeout=90) as source, cache.open("wb") as output:
            count = 0
            while block := source.read(1024 * 1024):
                count += len(block)
                if count > 80 * 1024 * 1024:
                    raise ValueError("El modelo supera el tamaño esperado.")
                output.write(block)
    if hashlib.sha256(cache.read_bytes()).hexdigest() != SHA256:
        raise ValueError("Hash del modelo de prueba incorrecto.")
    with tarfile.open(cache, "r:bz2") as archive:
        model = next(entry for entry in archive if entry.name.endswith(".onnx"))
        tokens = next(entry for entry in archive.getmembers() if entry.name.endswith("/tokens.txt"))
        files = {"model.onnx": archive.extractfile(model).read(), "tokens.txt": archive.extractfile(tokens).read(), "lexicon.tsv": "hello\thəloʊ\n".encode()}
    metadata = {"version": 1, "name": "Fixture LJSpeech medium (inferencia, no validación acústica)",
                "format": "sherpa-piper-phonemes-v1", "engine": "sherpa-onnx", "engineVersion": "2.0.1",
                "language": "en-US", "license": "Modelo MIT; fuente y datos indicados en MODEL_CARD original",
                "source": "https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/ljspeech/medium/MODEL_CARD",
                "sampleText": "hello", "minRamMb": 512, "rightsConfirmed": True,
                "sha256": {name: hashlib.sha256(content).hexdigest() for name, content in files.items()}}
    files["voz.json"] = json.dumps(metadata, ensure_ascii=False).encode()
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in files.items():
            archive.writestr(name, content)
    print("Fixture con modelo real preparado solo para la APK de pruebas; no se incluye en la aplicación.")


if __name__ == "__main__":
    main()
