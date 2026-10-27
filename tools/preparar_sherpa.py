"""Descarga el runtime oficial fijado; jamás descarga modelos ni código de paquetes de voz."""
import hashlib
import pathlib
import urllib.request

VERSION = "2.0.1"
SHA256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-{VERSION}.aar"
TARGET = pathlib.Path(__file__).resolve().parents[1] / "app" / "libs" / f"sherpa-onnx-{VERSION}.aar"


def main():
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    if TARGET.exists() and hashlib.sha256(TARGET.read_bytes()).hexdigest() == SHA256:
        print("Runtime Sherpa ONNX verificado.")
        return
    temporary = TARGET.with_suffix(".descarga")
    try:
        with urllib.request.urlopen(URL, timeout=90) as source, temporary.open("wb") as destination:
            total = 0
            while block := source.read(1024 * 1024):
                total += len(block)
                if total > 64 * 1024 * 1024:
                    raise ValueError("El runtime supera el tamaño esperado.")
                destination.write(block)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != SHA256:
            raise ValueError("SHA-256 del runtime incorrecto; no se utilizará.")
        temporary.replace(TARGET)
        print("Runtime oficial Sherpa ONNX descargado y verificado.")
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
