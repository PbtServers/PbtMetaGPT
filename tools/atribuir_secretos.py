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
        self.metadata_references = {}
        self.types = [self.u32(types + n * 4) for n in range(type_count)]
        for n, string_id in enumerate(self.types):
            self.record_metadata(string_id, {"uso": "descriptor_tipo", "indice_tipo": n})
        for label, header in (("nombre_campo", 80), ("nombre_metodo", 88)):
            count, offset = self.u32(header), self.u32(header + 4)
            if offset + count * 8 > len(data):
                raise ValueError("DEX_MEMBERS")
            for n in range(count):
                owner = int.from_bytes(data[offset + n * 8:offset + n * 8 + 2], "little")
                self.record_metadata(self.u32(offset + n * 8 + 4), {
                    "uso": label, "clase": self.type_label(owner), "indice_miembro": n})
        proto_count, protos = self.u32(72), self.u32(76)
        if protos + proto_count * 12 > len(data):
            raise ValueError("DEX_PROTOS")
        for n in range(proto_count):
            self.record_metadata(self.u32(protos + n * 12), {"uso": "shorty_prototipo", "indice_prototipo": n})
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
            source = self.u32(classes + n * 32 + 16)
            if source != 0xffffffff:
                self.record_metadata(source, {"uso": "archivo_fuente", "clase": safe_label(descriptor)})
            annotations = self.u32(classes + n * 32 + 20)
            if annotations:
                self.annotation_directory(annotations, descriptor)
            class_data = self.u32(classes + n * 32 + 24)
            if class_data:
                self.class_debug_info(class_data, descriptor)
            values = self.u32(classes + n * 32 + 28)
            if values:
                count, cursor = self.uleb(values)
                for _ in range(count):
                    cursor = self.encoded_value(cursor, descriptor, 0)

    def record_metadata(self, index, reference):
        if index >= len(self.strings):
            raise ValueError("DEX_METADATA_STRING")
        values = self.metadata_references.setdefault(index, [])
        if reference not in values:
            values.append(reference)

    def type_label(self, index):
        if index >= len(self.types) or self.types[index] >= len(self.strings):
            raise ValueError("DEX_METADATA_TYPE")
        start, end = self.strings[self.types[index]]
        return safe_label(self.data[start:end].decode("utf-8", errors="replace"))

    def annotation_set(self, offset, descriptor):
        if not offset:
            return
        count = self.u32(offset)
        if offset + 4 + count * 4 > len(self.data):
            raise ValueError("DEX_ANNOTATIONS")
        for n in range(count):
            cursor = self.u32(offset + 4 + n * 4) + 1  # annotation_item.visibility
            annotation_type, cursor = self.uleb(cursor)
            annotation = self.type_label(annotation_type)
            size, cursor = self.uleb(cursor)
            for _ in range(size):
                name, cursor = self.uleb(cursor)
                if name >= len(self.strings):
                    raise ValueError("DEX_ANNOTATION_NAME")
                start, end = self.strings[name]
                metadata = {"uso": "valor_anotacion", "clase": safe_label(descriptor),
                            "anotacion": annotation,
                            "elemento": safe_label(self.data[start:end].decode("utf-8", errors="replace"))}
                self.record_metadata(name, {**metadata, "uso": "nombre_elemento_anotacion"})
                cursor = self.encoded_value(cursor, descriptor, 0, metadata)

    def annotation_directory(self, offset, descriptor):
        self.annotation_set(self.u32(offset), descriptor)
        fields, methods, parameters = (self.u32(offset + n) for n in (4, 8, 12))
        cursor = offset + 16
        if cursor + (fields + methods + parameters) * 8 > len(self.data):
            raise ValueError("DEX_ANNOTATION_DIRECTORY")
        for _ in range(fields + methods):
            self.annotation_set(self.u32(cursor + 4), descriptor)
            cursor += 8
        for _ in range(parameters):
            ref_list = self.u32(cursor + 4)
            count = self.u32(ref_list)
            if ref_list + 4 + count * 4 > len(self.data):
                raise ValueError("DEX_PARAMETER_ANNOTATIONS")
            for n in range(count):
                self.annotation_set(self.u32(ref_list + 4 + n * 4), descriptor)
            cursor += 8

    def class_debug_info(self, cursor, descriptor):
        sizes = []
        for _ in range(4):
            size, cursor = self.uleb(cursor)
            sizes.append(size)
        for _ in range(sizes[0] + sizes[1]):
            _, cursor = self.uleb(cursor)  # field_idx_diff
            _, cursor = self.uleb(cursor)  # access_flags
        for size in sizes[2:]:
            method = 0
            for _ in range(size):
                delta, cursor = self.uleb(cursor)
                method += delta
                _, cursor = self.uleb(cursor)  # access_flags
                code, cursor = self.uleb(cursor)
                if method >= self.u32(88):
                    raise ValueError("DEX_DEBUG_METHOD")
                if code:
                    debug = self.u32(code + 8)
                    if debug:
                        self.debug_info(debug, descriptor, method)

    def debug_info(self, cursor, descriptor, method):
        _, cursor = self.uleb(cursor)  # line_start
        count, cursor = self.uleb(cursor)

        def name(position, role):
            index, position = self.uleb(position)  # uleb128p1: cero significa NO_INDEX.
            if index:
                self.record_metadata(index - 1, {"uso": role, "clase": safe_label(descriptor),
                                                "indice_metodo": method})
            return position

        for _ in range(count):
            cursor = name(cursor, "nombre_parametro_debug")
        while cursor < len(self.data):
            opcode = self.data[cursor]; cursor += 1
            if opcode == 0:  # DBG_END_SEQUENCE
                return
            if opcode in (1, 2, 5, 6):
                _, cursor = self.uleb(cursor)  # advance PC/line, end/restart local
            elif opcode in (3, 4):
                _, cursor = self.uleb(cursor)  # register_num
                cursor = name(cursor, "nombre_variable_debug")
                _, cursor = self.uleb(cursor)  # type_idx, ya recogido en type_ids
                if opcode == 4:
                    cursor = name(cursor, "firma_variable_debug")
            elif opcode == 9:
                cursor = name(cursor, "archivo_fuente_debug")
            # DBG_SET_PROLOGUE_END, EPILOGUE_BEGIN y opcodes especiales no llevan operandos.
        raise ValueError("DEX_DEBUG_END")

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

    def encoded_value(self, cursor, descriptor, depth, metadata=None):
        if cursor >= len(self.data) or depth > 32:
            raise ValueError("DEX_VALUE")
        header = self.data[cursor]; cursor += 1
        kind, size = header & 31, (header >> 5) + 1
        if kind == 0x1c:  # VALUE_ARRAY
            count, cursor = self.uleb(cursor)
            for _ in range(count):
                cursor = self.encoded_value(cursor, descriptor, depth + 1, metadata)
        elif kind == 0x1d:  # VALUE_ANNOTATION
            _, cursor = self.uleb(cursor)
            count, cursor = self.uleb(cursor)
            for _ in range(count):
                _, cursor = self.uleb(cursor)
                cursor = self.encoded_value(cursor, descriptor, depth + 1, metadata)
        elif kind not in (0x1e, 0x1f):  # NULL y BOOLEAN no llevan bytes adicionales.
            if cursor + size > len(self.data):
                raise ValueError("DEX_VALUE")
            if kind == 0x17:  # VALUE_STRING
                index = int.from_bytes(self.data[cursor:cursor + size], "little")
                if index >= len(self.strings):
                    raise ValueError("DEX_VALUE_STRING")
                if metadata is None:
                    self.static_references.setdefault(index, set()).add(safe_label(descriptor))
                else:
                    self.record_metadata(index, metadata)
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
        finding["metadatos_dex"] = []
        for n in finding["indices_string_dex"]:
            start, end = index.strings[n]
            finding["metadatos_dex"].append({
                "indice_string": n, "longitud_contenedor": end - start,
                "offset_en_contenedor": finding["offset"] - start,
                "sha256_contenedor": hashlib.sha256(data[start:end]).hexdigest(),
                "referencias": index.metadata_references.get(n, [])})
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
