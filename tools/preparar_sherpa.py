"""Descarga Sherpa ONNX verificado y elimina SU copia de libonnxruntime.so.

El AAR original se conserva localmente con SHA-256 oficial y los bindings
sherpa-onnx-jni siguen intactos. ONNX Runtime Android suministra la única
libonnxruntime.so compatible con su API Java. No se modifica código nativo.
"""
import hashlib
import pathlib
import urllib.request
import zipfile

VERSION = "2.0.1"
SHA256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-{VERSION}.aar"
ROOT = pathlib.Path(__file__).resolve().parents[1] / "app" / "libs"
TARGET = ROOT / f"sherpa-onnx-{VERSION}.aar"
ORIGINAL = ROOT / f"sherpa-onnx-{VERSION}.official.aar"
MARKER = "META-INF/pbt-runtime-split.txt"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepared(path):
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            return (
                MARKER in names
                and any("libsherpa-onnx-jni.so" in name for name in names)
                and not any(name.endswith("/libonnxruntime.so") for name in names)
            )
    except (OSError, zipfile.BadZipFile):
        return False


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    if ORIGINAL.exists() and digest(ORIGINAL) != SHA256:
        ORIGINAL.unlink()
    if not ORIGINAL.exists():
        if TARGET.exists() and digest(TARGET) == SHA256:
            TARGET.replace(ORIGINAL)
        elif prepared(TARGET):
            print("Sherpa ONNX con runtime nativo independiente ya preparado.")
            return
        else:
            temporary = ORIGINAL.with_suffix(".descarga")
            try:
                with urllib.request.urlopen(URL, timeout=90) as source, temporary.open("wb") as destination:
                    total = 0
                    while block := source.read(1024 * 1024):
                        total += len(block)
                        if total > 64 * 1024 * 1024:
                            raise ValueError("El runtime supera el tamaño esperado.")
                        destination.write(block)
                if digest(temporary) != SHA256:
                    raise ValueError("SHA-256 de Sherpa ONNX incorrecto.")
                temporary.replace(ORIGINAL)
            finally:
                temporary.unlink(missing_ok=True)

    if not TARGET.exists() or not prepared(TARGET):
        temporary = TARGET.with_suffix(".prepared")
        try:
            count = 0
            with zipfile.ZipFile(ORIGINAL) as source, zipfile.ZipFile(temporary, "w") as destination:
                for item in source.infolist():
                    if item.filename.endswith("/libonnxruntime.so"):
                        count += 1
                        continue
                    destination.writestr(item, source.read(item.filename))
                destination.writestr(MARKER, b"ORT Android supplies libonnxruntime.so\n")
            if count < 2 or not prepared(temporary):
                raise ValueError("No se pudieron separar bibliotecas nativas de Sherpa.")
            temporary.replace(TARGET)
        finally:
            temporary.unlink(missing_ok=True)
    print("Sherpa ONNX validado; libonnxruntime.so unica desde ORT Android.")


if __name__ == "__main__":
    main()
