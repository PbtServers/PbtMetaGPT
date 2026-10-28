"""Prepara Sherpa verificado sin mezclar su ABI nativa con ORT de RVC.

Sherpa 2.0.1 necesita OrtGetApiBase de SU runtime. La biblioteca publicada por
onnxruntime-android para RVC no lo exporta; quitar la copia propia de Sherpa
provoca SIG/UnsatisfiedLinkError al cargar el JNI. Se separan los SONAME y
DT_NEEDED de los binarios originales verificados, sin compilar ni ejecutar
pesos de modelos. Solo se ejecuta patchelf en archivos ELF del AAR oficial.
"""
import hashlib
import pathlib
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from copy import copy

VERSION = "2.0.1"
SHA256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-{VERSION}.aar"
ROOT = pathlib.Path(__file__).resolve().parents[1] / "app" / "libs"
TARGET = ROOT / f"sherpa-onnx-{VERSION}.aar"
ORIGINAL = ROOT / f"sherpa-onnx-{VERSION}.official.aar"
MARKER = "META-INF/pbt-runtime-split-v2.txt"
SHERPA_ORT = "libpbt_sherpa_ort.so"
OLD_ORT = "libonnxruntime.so"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepared(path):
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            return (
                MARKER in names
                and any("libsherpa-onnx-jni.so" in name for name in names)
                and not any(name.endswith("/" + OLD_ORT) for name in names)
                and sum(name.endswith("/" + SHERPA_ORT) for name in names) >= 2
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
            print("Sherpa ONNX: SONAME nativo separado ya preparado.")
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
        patchelf = shutil.which("patchelf")
        if patchelf is None:
            raise RuntimeError("Falta patchelf para separar la ABI nativa de Sherpa; no se publicará una APK incompatible.")
        temporary = TARGET.with_suffix(".prepared")
        try:
            count = 0
            with tempfile.TemporaryDirectory() as folder, zipfile.ZipFile(ORIGINAL) as source, zipfile.ZipFile(temporary, "w") as destination:
                binary = pathlib.Path(folder) / "runtime.so"

                def patch(*args):
                    subprocess.run([patchelf, *args, str(binary)], check=True, capture_output=True)

                def needed():
                    return subprocess.run([patchelf, "--print-needed", str(binary)],
                                          check=True, capture_output=True, text=True).stdout.splitlines()

                for item in source.infolist():
                    data = source.read(item.filename)
                    if item.filename.endswith(".so") and data[:4] == b"\x7fELF":
                        binary.write_bytes(data)
                        dependencies = needed()
                        if item.filename.endswith("/" + OLD_ORT):
                            patch("--set-soname", SHERPA_ORT)
                            item = copy(item)
                            item.filename = item.filename.rsplit("/", 1)[0] + "/" + SHERPA_ORT
                            count += 1
                        else:
                            if OLD_ORT in dependencies:
                                patch("--replace-needed", OLD_ORT, SHERPA_ORT)
                            elif item.filename.endswith("/libsherpa-onnx-jni.so"):
                                # Some Sherpa variants use unresolved C API symbols
                                # without DT_NEEDED; ensure the matching runtime loads.
                                patch("--add-needed", SHERPA_ORT)
                        data = binary.read_bytes()
                    destination.writestr(item, data)
                destination.writestr(MARKER, b"Sherpa uses private libpbt_sherpa_ort.so; RVC uses ORT Android.\n")
            if count < 2 or not prepared(temporary):
                raise ValueError("No se pudieron separar los SONAME nativos de Sherpa.")
            temporary.replace(TARGET)
        finally:
            temporary.unlink(missing_ok=True)
    print("Sherpa ONNX validado: ABI nativa independiente de ORT Android RVC.")


if __name__ == "__main__":
    main()
