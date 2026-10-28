"""Construye los datos eSpeak desde la misma revisión que su biblioteca Android.

Solo se ejecuta en el host de compilación (CMake + Git + compilador C).
La aplicación no descarga ni ejecuta código al instalar un modelo.
"""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

REVISION = '4870adfa25b1a32b4361592f1be8a40337c58d6c'  # eSpeak NG 1.52.0
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/build/native-sources/espeak'
BUILD = ROOT / 'app/build/espeak-host'
ASSETS = ROOT / 'app/build/generated/phonemes-assets'


def run(*args):
    subprocess.run(args, check=True)


def main():
    SOURCE.parent.mkdir(parents=True, exist_ok=True)
    if not (SOURCE / '.git').exists():
        run('git', 'init', str(SOURCE))
        run('git', '-C', str(SOURCE), 'remote', 'add', 'origin', 'https://github.com/espeak-ng/espeak-ng.git')
    run('git', '-C', str(SOURCE), 'fetch', '--depth=1', 'origin', REVISION)
    run('git', '-C', str(SOURCE), 'checkout', '--detach', REVISION)
    actual = subprocess.check_output(['git', '-C', str(SOURCE), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != REVISION:
        raise ValueError('Revisión inesperada de eSpeak')
    run('cmake', '-S', str(SOURCE), '-B', str(BUILD), '-DCOMPILE_INTONATIONS=ON',
        '-DENABLE_TESTS=OFF', '-DUSE_ASYNC=OFF', '-DUSE_MBROLA=OFF',
        '-DUSE_LIBPCAUDIO=OFF', '-DUSE_LIBSONIC=OFF', '-DUSE_SPEECHPLAYER=OFF')
    run('cmake', '--build', str(BUILD), '--target', 'data', '--parallel', '2')
    target = ASSETS / 'espeak-ng-data'
    target.mkdir(parents=True, exist_ok=True)
    # Conjunto mínimo español, sin diccionarios de idiomas ajenos ni ejecutables.
    for name in ['phondata', 'phonindex', 'phontab', 'intonations', 'es_dict', 'en_dict']:
        shutil.copyfile(BUILD / 'espeak-ng-data' / name, target / name)
    language = target / 'lang/roa/es'
    language.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SOURCE / 'espeak-ng-data/lang/roa/es', language)
    english = target / 'lang/gmw/en'
    english.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SOURCE / 'espeak-ng-data/lang/gmw/en', english)
    shutil.copyfile(SOURCE / 'COPYING', ASSETS / 'espeak-COPYING.txt')
    hashes = {str(p.relative_to(ASSETS)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in ASSETS.rglob('*') if p.is_file() and p.name != 'phonemes-manifest.json'}
    (ASSETS / 'phonemes-manifest.json').write_text(json.dumps(hashes, sort_keys=True), encoding='utf-8')
    print('Datos españoles eSpeak verificados por revisión y SHA-256; GPL-3.0-or-later.')


if __name__ == '__main__':
    main()
