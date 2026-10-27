"""Comprueba patrones de credenciales sin imprimir nunca la coincidencia."""
import pathlib
import re
import subprocess
import sys
import zipfile

PATTERNS = [re.compile(rb"sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{24,}"),
            re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")]


def sensitive(data):
    return any(pattern.search(data) for pattern in PATTERNS)


def main():
    findings = []
    for name in subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0"):
        if name and pathlib.Path(name).is_file() and sensitive(pathlib.Path(name).read_bytes()):
            findings.append(name)
    for apk in sys.argv[1:]:
        with zipfile.ZipFile(apk) as archive:
            for entry in archive.infolist():
                if sensitive(archive.read(entry)):
                    findings.append(f"{apk}:{entry.filename}")
    if findings:
        print("Comprobación rechazada. Revisar estos archivos (contenido oculto):")
        print("\n".join(findings))
        return 1
    print("Sin patrones de claves privadas en archivos versionados ni APK indicadas. No es una garantía contra todos los formatos de secreto.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
