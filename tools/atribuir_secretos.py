"""Atribución orientativa sin escribir DEX, volcados, candidatos ni claves en informes."""
import hashlib
import io
import os
import pathlib
import re
import struct
import subprocess
import tempfile
import zipfile


def safe_label(value):
    # Ningún nombre procedente del binario puede convertirse en una vía de filtración.
    if (len(value) > 240 or not re.fullmatch(r"[A-Za-z0-9_.$/;:<>@+\\ -]+", value)
            or "sk-" in value.lower() or "://" in value or re.search(r"(?i)bearer\s", value)):
        return "oculto-sha256-" + hashlib.sha256(value.encode()).hexdigest()
    return value


class DexIndex:
    """Tablas estándar DEX 035–040. Un fallo de interpretación nunca elimina un hallazgo."""
    def __init__(self, data):
        if len(data) < 112 or data[:4] != b"dex\n" or data[4:8] not in (b"035\0", b"037\0", b"038\0", b"039\0", b"040\0"):
            raise ValueError("DEX_UNSUPPORTED")
        self.data = data
        if self.u32(32) != len(data) or self.u32(36) != 112 or self.u32(40) != 0x12345678:
            raise ValueError("DEX_HEADER")
        count, offset = self.u32(56), self.u32(60)
        if count > len(data) // 4 or offset + count * 4 > len(data):
            raise ValueError("DEX_STRINGS")
        self.strings = []
        for number in range(count):
            cursor = self.u32(offset + number * 4)
            for _ in range(5):
                if cursor >= len(data):
                    raise ValueError("DEX_ULEB")
                last = data[cursor]; cursor += 1
                if last < 128:
                    break
            else:
                raise ValueError("DEX_ULEB")
            end = data.find(b"\0", cursor)
            if end < 0:
                raise ValueError("DEX_STRING_END")
            self.strings.append((cursor, end))
        type_count, types = self.u32(64), self.u32(68)
        class_count, classes = self.u32(96), self.u32(100)
        if types + type_count * 4 > len(data) or classes + class_count * 32 > len(data):
            raise ValueError("DEX_CLASSES")
        self.classes = set()
        self.static_references = {}
        for n in range(class_count):
            type_id = self.u32(classes + n * 32)
            if type_id >= type_count:
                raise ValueError("DEX_CLASS_TYPE")
            string_id = self.u32(types + type_id * 4)
            if string_id >= len(self.strings):
                raise ValueError("DEX_CLASS_STRING")
            start, end = self.strings[string_id]
            descriptor = data[start:end].decode("utf-8", errors="replace")
            self.classes.add(descriptor)
            values = self.u32(classes + n * 32 + 28)
            if values:
                count, cursor = self.uleb(values)
                for _ in range(count):
                    cursor = self.encoded_value(cursor, descriptor, 0)

    def u32(self, offset):
        if offset < 0 or offset + 4 > len(self.data):
            raise ValueError("DEX_BOUNDS")
        return struct.unpack_from("<I", self.data, offset)[0]

    def locate(self, start, end):
        return [n for n, (left, right) in enumerate(self.strings) if left <= start and end <= right]

    def uleb(self, cursor):
        value = 0
        for shift in range(0, 35, 7):
            if cursor >= len(self.data):
                raise ValueError("DEX_ULEB")
            byte = self.data[cursor]; cursor += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, cursor
        raise ValueError("DEX_ULEB")

    def encoded_value(self, cursor, descriptor, depth):
        if cursor >= len(self.data) or depth > 32:
            raise ValueError("DEX_VALUE")
        header = self.data[cursor]; cursor += 1
        kind, size = header & 31, (header >> 5) + 1
        if kind == 0x1c:  # VALUE_ARRAY
            count, cursor = self.uleb(cursor)
            for _ in range(count):
                cursor = self.encoded_value(cursor, descriptor, depth + 1)
        elif kind == 0x1d:  # VALUE_ANNOTATION
            _, cursor = self.uleb(cursor)
            count, cursor = self.uleb(cursor)
            for _ in range(count):
                _, cursor = self.uleb(cursor)
                cursor = self.encoded_value(cursor, descriptor, depth + 1)
        elif kind not in (0x1e, 0x1f):  # NULL y BOOLEAN no llevan bytes adicionales.
            if cursor + size > len(self.data):
                raise ValueError("DEX_VALUE")
            if kind == 0x17:  # VALUE_STRING
                index = int.from_bytes(self.data[cursor:cursor + size], "little")
                if index >= len(self.strings):
                    raise ValueError("DEX_VALUE_STRING")
                self.static_references.setdefault(index, set()).add(safe_label(descriptor))
            cursor += size
        return cursor


def dexdump_references(data, indices, executable):
    """El volcado con contenido privado pasa solo por una tubería, nunca por logs/archivos."""
    references = {number: set() for number in indices}
    with tempfile.TemporaryDirectory(prefix="pbt-dex-") as directory:
        path = pathlib.Path(directory) / "analisis.dex"
        path.write_bytes(data)
        with subprocess.Popen([str(executable), "-d", str(path)], stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL, text=True, encoding="utf-8", errors="replace") as process:
            current = None
            for line in process.stdout:
                descriptor = re.match(r"\s*Class descriptor\s*:\s*'([^']+)'", line)
                if descriptor:
                    current = safe_label(descriptor.group(1))
                # Solo referencias const-string del desensamblador, no fragmentos del valor.
                instruction = re.search(r"\|[0-9a-f]+: const-string(?:/jumbo)? .* // string@([0-9a-f]+)\s*$", line)
                if current and instruction:
                    number = int(instruction.group(1), 16)
                    if number in references:
                        references[number].add(current)
            if process.wait() != 0:
                raise ValueError("DEXDUMP_FAILED")
    return {number: sorted(classes) for number, classes in references.items()}


def library_matches(cache, classes, fingerprints, patterns):
    """Coincidencia exacta de huella en clases que el DEX realmente declara; no implica inocuidad."""
    found = {fingerprint: [] for fingerprint in fingerprints}
    if not cache or not pathlib.Path(cache).is_dir():
        return found

    def scan_jar(jar, module, container):
        for entry in jar.infolist():
            descriptor = "L" + entry.filename.removesuffix(".class") + ";"
            if not entry.filename.endswith(".class") or descriptor not in classes or entry.file_size > 32 * 1024 * 1024:
                continue
            content = jar.read(entry)
            for pattern in patterns:
                for match in pattern.finditer(content):
                    fingerprint = hashlib.sha256(match.group()).hexdigest()
                    if fingerprint in found:
                        origin = {"modulo": safe_label(module), "archivo": safe_label(container), "clase": safe_label(descriptor)}
                        if origin not in found[fingerprint]:
                            found[fingerprint].append(origin)

    for path in pathlib.Path(cache).rglob("*"):
        if path.suffix not in (".jar", ".aar") or not path.is_file():
            continue
        parts = path.relative_to(cache).parts
        module = ":".join(parts[:3])
        try:
            with zipfile.ZipFile(path) as archive:
                if path.suffix == ".jar":
                    scan_jar(archive, module, path.name)
                else:
                    for entry in archive.infolist():
                        if (entry.filename == "classes.jar" or entry.filename.startswith("libs/")) and entry.filename.endswith(".jar") and entry.file_size <= 64 * 1024 * 1024:
                            with zipfile.ZipFile(io.BytesIO(archive.read(entry))) as jar:
                                scan_jar(jar, module, path.name + ":" + entry.filename)
        except (OSError, zipfile.BadZipFile):
            # Esta ruta auxiliar no modifica el resultado del escáner.
            continue
    return found


def attribute_dex(data, findings, patterns, cache=None, dexdump=None):
    try:
        index = DexIndex(data)
    except ValueError:
        return {"estado": "DEX_NO_INTERPRETADO"}
    numbers = set()
    for finding in findings:
        finding["indices_string_dex"] = index.locate(finding["offset"], finding["offset"] + finding["longitud"])
        finding["clases_valores_estaticos"] = sorted({c for n in finding["indices_string_dex"] for c in index.static_references.get(n, [])})
        numbers.update(finding["indices_string_dex"])
    origins = library_matches(cache, index.classes, {f["sha256"] for f in findings}, patterns)
    for finding in findings:
        finding["coincidencias_en_dependencias"] = origins[finding["sha256"]]
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not dexdump and sdk:
        candidate = pathlib.Path(sdk) / "build-tools" / "36.0.0" / ("dexdump.exe" if os.name == "nt" else "dexdump")
        dexdump = candidate if candidate.is_file() else None
    if dexdump:
        try:
            references = dexdump_references(data, numbers, dexdump)
            for finding in findings:
                finding["clases_const_string"] = sorted({c for number in finding["indices_string_dex"] for c in references.get(number, [])})
        except (OSError, ValueError):
            return {"estado": "DEXDUMP_NO_DISPONIBLE", "clases_declaradas": len(index.classes)}
    return {"estado": "ATRIBUCION_PARCIAL" if not dexdump else "ANALIZADO", "clases_declaradas": len(index.classes)}
