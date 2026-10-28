"""Pesos reales autorizados para instrumentadas; nunca en la APK de producción."""
from pathlib import Path
import hashlib
import urllib.request

REVISION = 'f7b96bb6bef5c5da4d3aa4f4e0498fbbf62dc78b'
FILES = {
    'model.onnx': (325560556, 'b40f62b166ac8164b0627ef48a0b358eda0985e272fb03ef5252e7206305da11'),
    'voices.bin': (28200960, '1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29'),
}


def prepare_spain():
    root = Path(__file__).resolve().parents[1] / 'app/src/androidTest/assets/piper-es'
    root.mkdir(parents=True, exist_ok=True)
    name = 'es_ES-davefx-medium.onnx'
    size = 63201294
    expected = '6658b03b1a6c316ee4c265a9896abc1393353c2d9e1bca7d66c2c442e222a917'
    target = root / name
    if target.exists():
        with target.open('rb') as data:
            if target.stat().st_size == size and hashlib.file_digest(data, 'sha256').hexdigest() == expected:
                return
    temporary = target.with_suffix('.descarga')
    try:
        url = 'https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/es/es_ES/davefx/medium/' + name
        count = 0
        with urllib.request.urlopen(url, timeout=120) as source, temporary.open('wb') as output:
            while block := source.read(65536):
                count += len(block)
                if count > size:
                    raise ValueError('Piper excede el tamaño esperado')
                output.write(block)
        with temporary.open('rb') as data:
            if count != size or hashlib.file_digest(data, 'sha256').hexdigest() != expected:
                raise ValueError('SHA-256 de Piper incorrecto')
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    root = Path(__file__).resolve().parents[1] / 'app/src/androidTest/assets/kokoro'
    root.mkdir(parents=True, exist_ok=True)
    for name, (size, expected) in FILES.items():
        target = root / name
        if target.exists() and target.stat().st_size == size and hashlib.file_digest(target.open('rb'), 'sha256').hexdigest() == expected:
            continue
        temporary = target.with_suffix('.descarga')
        try:
            url = f'https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/resolve/{REVISION}/{name}'
            count = 0
            with urllib.request.urlopen(url, timeout=120) as source, temporary.open('wb') as output:
                while block := source.read(65536):
                    count += len(block)
                    if count > size:
                        raise ValueError('Tamaño Kokoro inesperado')
                    output.write(block)
            with temporary.open('rb') as data:
                actual = hashlib.file_digest(data, 'sha256').hexdigest()
            if count != size or actual != expected:
                raise ValueError('SHA-256 Kokoro no válido')
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
    prepare_spain()
    print('Kokoro v1.0 real, revisión fija y SHA-256 comprobados (solo tests).')


if __name__ == '__main__':
    main()
