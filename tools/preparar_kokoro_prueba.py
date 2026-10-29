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


def prepare_sharvard():
    root = Path(__file__).resolve().parents[1] / 'app/src/androidTest/assets/piper-es'
    root.mkdir(parents=True, exist_ok=True)
    revision = '5041a7a49ae5b3a0f30e6837769eb03fdbb231bd'
    resources = {
        'es_ES-sharvard-medium.onnx': (76733615, '40febfb1679c69a4505ff311dc136e121e3419a13a290ef264fdf43ddedd0fb1'),
        'es_ES-sharvard-medium.onnx.json': (4903, '7438c9b699c72b0c3388dae1b68d3f364dc66a2150fe554a1c11f03372957b2c'),
        'MODEL_CARD': (392, '37655314d08fe8a0900abc628d3bcbee48ffbdd8fad9306b89525a0ada20fefc'),
    }
    for name, (size, expected) in resources.items():
        target = root / name
        if target.exists():
            with target.open('rb') as source:
                if target.stat().st_size == size and hashlib.file_digest(source, 'sha256').hexdigest() == expected:
                    continue
        temporary = target.with_suffix('.descarga')
        try:
            url = f'https://huggingface.co/rhasspy/piper-voices/resolve/{revision}/es/es_ES/sharvard/medium/{name}'
            count = 0
            with urllib.request.urlopen(url, timeout=120) as source, temporary.open('wb') as output:
                while block := source.read(65536):
                    count += len(block)
                    if count > size:
                        raise ValueError('Sharvard excede el limite')
                    output.write(block)
            with temporary.open('rb') as source:
                if count != size or hashlib.file_digest(source, 'sha256').hexdigest() != expected:
                    raise ValueError('SHA-256 Sharvard incorrecto')
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
    prepare_sharvard()
    print('Kokoro v1.0 real, revisión fija y SHA-256 comprobados (solo tests).')


if __name__ == '__main__':
    main()
