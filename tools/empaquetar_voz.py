"""Empaqueta datos aportados por su propietario; no convierte, entrena ni ejecuta modelos."""
import argparse
import hashlib
import json
import pathlib
import re
import zipfile


def package_voice(model, tokens, lexicon, output, *, name, language, license_name, source, sample_text, min_ram_mb, rights):
    if not rights:
        raise ValueError("Confirma que tienes autorización para utilizar la voz y su modelo.")
    if not (512 <= min_ram_mb <= 8192) or not re.fullmatch(r"[a-z]{2,3}(-[A-Za-z]{2,8})?", language):
        raise ValueError("Idioma o requisitos no válidos.")
    fields = [name, license_name, source, sample_text]
    if any(not value or len(value) > 1000 or re.search(r"sk-[A-Za-z0-9_-]{20,}|(?i:bearer\s+[A-Za-z0-9_.-]{16,})", value) for value in fields):
        raise ValueError("Faltan metadatos o contienen patrones de secretos.")
    paths = {"model.onnx": pathlib.Path(model), "tokens.txt": pathlib.Path(tokens), "lexicon.tsv": pathlib.Path(lexicon)}
    limits = {"model.onnx": 256 * 1024 * 1024, "tokens.txt": 2 * 1024 * 1024, "lexicon.tsv": 16 * 1024 * 1024}
    hashes = {}
    for target, path in paths.items():
        if not path.is_file() or not 0 < path.stat().st_size <= limits[target]:
            raise ValueError("Falta un archivo o supera el límite del formato.")
        digest = hashlib.sha256()
        with path.open("rb") as data:
            while block := data.read(65536):
                digest.update(block)
        hashes[target] = digest.hexdigest()
    metadata = {"version": 1, "name": name, "format": "sherpa-piper-phonemes-v1", "engine": "sherpa-onnx", "engineVersion": "2.0.1",
                "language": language, "license": license_name, "source": source, "sampleText": sample_text, "minRamMb": min_ram_mb,
                "rightsConfirmed": True, "sha256": hashes}
    output = pathlib.Path(output)
    if output.resolve() in [path.resolve() for path in paths.values()]:
        raise ValueError("La salida no puede sobrescribir un archivo de entrada.")
    if output.exists():
        raise ValueError("La salida ya existe; elige otro nombre para conservar tu copia.")
    try:
        with zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("voz.json", json.dumps(metadata, ensure_ascii=False, indent=2))
            for target, path in paths.items():
                archive.write(path, target)
    except Exception:
        output.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description="Prepara un paquete Piper compatible sin convertir ni ejecutar modelos. Android debe validarlo y probarlo después.")
    for key in ("model", "tokens", "lexicon", "output", "name", "language", "license", "source", "sample"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--ram-mib", type=int, default=2048)
    parser.add_argument("--confirmo-autorizacion", action="store_true")
    args = parser.parse_args()
    package_voice(args.model, args.tokens, args.lexicon, args.output, name=args.name, language=args.language, license_name=args.license,
                  source=args.source, sample_text=args.sample, min_ram_mb=args.ram_mib, rights=args.confirmo_autorizacion)
    print("Paquete creado. Falta validar el ONNX, las pronunciaciones, la inferencia y la escucha en el dispositivo. No se han exportado credenciales de la aplicación.")


if __name__ == "__main__":
    main()
