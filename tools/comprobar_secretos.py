"""Comprueba patrones de credenciales sin imprimir nunca la coincidencia."""
import pathlib
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile
from atribuir_secretos import attribute_dex, safe_label

PATTERNS = [re.compile(rb"sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{24,}"),
            # Los parsers PEM (p. ej., mbedTLS de Meta) incluyen las etiquetas literales.
            # Exigir un bloque con carga evita confundir el parser con una clave incorporada.
            re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----\r?\n[A-Za-z0-9+/=\r\n]{64,}-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
            re.compile(rb"gsk_[A-Za-z0-9]{20,}"), re.compile(rb"hf_[A-Za-z0-9]{20,}")]


def sensitive(data):
    return any(pattern.search(data) for pattern in PATTERNS)


def describe_matches(data):
    """Metadatos exclusivamente: no incluir el candidato, contexto ni texto DEX."""
    names = ("OPENAI_API_KEY", "PEM_PRIVATE_KEY", "GROQ_API_KEY", "HF_API_TOKEN")
    return [{"patron": names[number], "offset": match.start(), "longitud": len(match.group()),
             "sha256": hashlib.sha256(match.group()).hexdigest()}
            for number, pattern in enumerate(PATTERNS) for match in pattern.finditer(data)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--informe", type=pathlib.Path, help="JSON de metadatos sin candidatos ni contexto")
    parser.add_argument("--dependencias-cache", type=pathlib.Path, help="Caché Gradle para atribución exacta por huella")
    parser.add_argument("--dexdump", type=pathlib.Path, help="dexdump oficial del SDK Android")
    parser.add_argument("apk", nargs="*")
    args = parser.parse_args()
    findings = []
    classified = []
    for name in subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0"):
        if name and pathlib.Path(name).is_file():
            for match in describe_matches(pathlib.Path(name).read_bytes()):
                findings.append({"archivo": safe_label(name), **match})
    for apk in args.apk:
        with zipfile.ZipFile(apk) as archive:
            for entry in archive.infolist():
                data = archive.read(entry)
                matches = describe_matches(data)
                metadata = None
                if matches and entry.filename.endswith(".dex"):
                    cache = args.dependencias_cache
                    if cache is None and os.environ.get("GRADLE_USER_HOME"):
                        cache = pathlib.Path(os.environ["GRADLE_USER_HOME"]) / "caches/modules-2/files-2.1"
                    metadata = attribute_dex(data, matches, PATTERNS, cache, args.dexdump)
                for match in matches:
                    if match.get("clasificacion") == "IDENTIFICADOR_METODO_NO_EJECUTABLE":
                        classified.append({"archivo": safe_label(f"{apk}:{entry.filename}"), **match})
                        continue
                    findings.append({"archivo": safe_label(f"{apk}:{entry.filename}"), **match,
                                     **({"analisis_dex": metadata} if metadata else {})})
                if entry.filename.endswith("modelo-prueba.zip"):
                    findings.append({"archivo": safe_label(f"{apk}:{entry.filename}"), "patron": "FIXTURE_NO_DISTRIBUIBLE"})
    report = {"version": 2, "resultado": "RECHAZADO" if findings else "SIN_COINCIDENCIAS",
              "hallazgos": findings, "identificadores_metodo_verificados": classified,
              "nota": "Solo los identificadores de metodo DEX sin referencias const-string verificadas quedan clasificados; los restantes hallazgos bloquean."}
    if args.informe:
        args.informe.parent.mkdir(parents=True, exist_ok=True)
        args.informe.write_text(json.dumps(report, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    if findings:
        print("Comprobación rechazada. Revisar estos archivos (contenido oculto):")
        for finding in findings:
            print(json.dumps(finding, ensure_ascii=True))
        return 1
    print(f"Escaneo aceptado: {len(classified)} coincidencias clasificadas exclusivamente como identificadores de metodo DEX.")
    print("Sin patrones de claves privadas ejecutables detectados. No es una garantía contra todos los formatos de secreto.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
