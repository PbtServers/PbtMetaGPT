"""Vosk español oficial, solo fixture de instrumentación; sin voz ni datos del usuario."""
import hashlib
import pathlib
import urllib.request

SHA256 = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"
URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
ROOT = pathlib.Path(__file__).resolve().parents[1]


def main():
    target = ROOT / "app/src/androidTest/assets/activacion-prueba.zip"
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == SHA256:
        print("Fixture Vosk español ya verificada; solo APK de pruebas.")
        return
    temporary = target.with_suffix(".descarga")
    try:
        with urllib.request.urlopen(URL, timeout=120) as source, temporary.open("wb") as output:
            count = 0
            while block := source.read(65536):
                count += len(block)
                if count > 64 * 1024 * 1024:
                    raise ValueError("El modelo Vosk excede el límite.")
                output.write(block)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != SHA256:
            raise ValueError("El modelo Vosk no coincide con la revisión verificada.")
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    print("Vosk español verificado para inferencia de comandos en la APK de tests, sin incluirlo en la app.")


if __name__ == "__main__":
    main()
