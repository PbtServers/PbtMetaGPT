import contextlib
import hashlib
import io
import json
import pathlib
import struct
import tempfile
import unittest
import zipfile
from unittest.mock import patch

from atribuir_secretos import DexIndex, attribute_dex, dexdump_references, safe_label
from comprobar_secretos import PATTERNS, describe_matches, main, sensitive


def dex_fixture(candidate, static=False):
    """Tablas de atribución; no simula una APK ejecutable ni una dependencia real."""
    strings = [b"Lpruebas/Origen;", candidate]
    data = bytearray(156)
    data[:8] = b"dex\n039\0"
    for offset, value in [(36, 112), (40, 0x12345678), (56, 2), (60, 112), (64, 1), (68, 120), (96, 1), (100, 124)]:
        struct.pack_into("<I", data, offset, value)
    for n, value in enumerate(strings):
        struct.pack_into("<I", data, 112 + n * 4, len(data))
        data += bytes([len(value)]) + value + b"\0"
    if static:
        struct.pack_into("<I", data, 124 + 28, len(data))
        data += b"\x01\x17\x01"  # Un valor estático, VALUE_STRING, string_id=1.
    struct.pack_into("<I", data, 32, len(data))
    return bytes(data)


def metadata_fixture(candidate, role):
    """Metadatos estructurales sintéticos; nunca constituyen evidencia del APK real."""
    strings = [b"Lpruebas/Origen;", candidate, b"Lpruebas/Anotacion;", b"valor"]
    data = bytearray(180)
    data[:8] = b"dex\n039\0"
    for offset, value in [(36, 112), (40, 0x12345678), (56, 4), (60, 112),
                          (64, 2), (68, 128), (96, 1), (100, 148)]:
        struct.pack_into("<I", data, offset, value)
    struct.pack_into("<II", data, 128, 0, 2)
    struct.pack_into("<I", data, 148 + 16, 0xffffffff)
    for n, value in enumerate(strings):
        struct.pack_into("<I", data, 112 + n * 4, len(data))
        # Todas las cadenas de estas fixtures tienen menos de 128 bytes.
        data += bytes([len(value)]) + value + b"\0"
    if role in ("nombre_metodo", "nombre_campo"):
        header = 88 if role == "nombre_metodo" else 80
        struct.pack_into("<II", data, header, 1, 136)
        struct.pack_into("<HHI", data, 136, 0, 0, 1)
    elif role == "archivo_fuente":
        struct.pack_into("<I", data, 148 + 16, 1)
    elif role == "descriptor_tipo":
        struct.pack_into("<I", data, 132, 1)
    elif role == "valor_anotacion":
        while len(data) % 4:
            data += b"\0"
        directory = len(data)
        struct.pack_into("<I", data, 148 + 20, directory)
        data += struct.pack("<IIII", directory + 16, 0, 0, 0)
        data += struct.pack("<II", 1, directory + 24)
        data += b"\x01\x01\x01\x03\x1c\x01\x17\x01"
        # visibility=runtime, annotation type=1, element name=3,
        # VALUE_ARRAY de un VALUE_STRING que apunta a candidate.
    elif role.endswith("_debug"):
        struct.pack_into("<II", data, 88, 1, 136)
        struct.pack_into("<HHI", data, 136, 0, 0, 3)
        while len(data) % 4:
            data += b"\0"
        code = len(data)
        data += struct.pack("<HHHHII", 1, 1, 0, 0, code + 16, 0)
        data += b"\x01"  # line_start
        if role == "nombre_parametro_debug":
            data += b"\x01\x02\x00"  # Un parámetro, string_id+1=2, END_SEQUENCE.
        elif role == "nombre_variable_debug":
            data += b"\x00\x03\x00\x02\x00\x00"
        elif role == "firma_variable_debug":
            data += b"\x00\x04\x00\x00\x00\x02\x00"
        elif role == "archivo_fuente_debug":
            data += b"\x00\x09\x02\x00"
        else:
            raise AssertionError(role)
        struct.pack_into("<I", data, 148 + 24, len(data))
        data += b"\x00\x00\x01\x00\x00\x00"  # counts, method diff, flags
        while code >= 128:
            data += bytes([(code & 127) | 128])
            code >>= 7
        data += bytes([code])
    else:
        raise AssertionError(role)
    struct.pack_into("<I", data, 32, len(data))
    return bytes(data)


class AtribucionSecretosTest(unittest.TestCase):
    def setUp(self):
        self.candidate = b"sk-" + b"x" * 40

    def test_informa_patron_posicion_longitud_y_huella_sin_valor(self):
        data = b"\0" + self.candidate + b"\0"
        finding, = describe_matches(data)
        self.assertEqual("OPENAI_API_KEY", finding["patron"])
        self.assertEqual(1, finding["offset"])
        self.assertEqual(len(self.candidate), finding["longitud"])
        self.assertEqual(hashlib.sha256(self.candidate).hexdigest(), finding["sha256"])
        self.assertNotIn(self.candidate.decode(), json.dumps(finding))

    def test_no_debilita_patrones_en_subcadenas_y_prefijos(self):
        for prefix in (b"sk-", b"sk-proj-", b"sk-svcacct-", b"prefixsk-", b"task-"):
            self.assertTrue(sensitive(prefix + b"x" * 40))
            self.assertTrue(describe_matches(prefix + b"x" * 40))

    def test_distingue_pem_de_clave_openai(self):
        data = b"-----BEGIN " + b"PRIVATE KEY-----\n" + b"A" * 80 + b"\n-----END PRIVATE KEY-----"
        finding, = describe_matches(data)
        self.assertEqual("PEM_PRIVATE_KEY", finding["patron"])
        self.assertNotIn(data.decode(), json.dumps(finding))

    def test_identifica_string_dex_y_clase_declarada(self):
        data = dex_fixture(self.candidate)
        index = DexIndex(data)
        self.assertEqual({"Lpruebas/Origen;"}, index.classes)
        finding, = describe_matches(data)
        result = attribute_dex(data, [finding], PATTERNS)
        self.assertIn(result["estado"], ("ANALIZADO", "ATRIBUCION_PARCIAL", "DEXDUMP_NO_DISPONIBLE"))
        self.assertEqual([1], finding["indices_string_dex"])
        self.assertNotIn(self.candidate.decode(), json.dumps(finding))

    def test_dex_truncado_corrupto_o_desconocido_sigue_siendo_sensible(self):
        for data in (b"dex\n041\0" + self.candidate, b"dex\n039\0" + self.candidate, b"binario" + self.candidate):
            findings = describe_matches(data)
            self.assertTrue(findings)
            self.assertEqual("DEX_NO_INTERPRETADO", attribute_dex(data, findings, PATTERNS)["estado"])

    def test_atribuye_valor_estatico_a_su_clase_sin_dexdump(self):
        data = dex_fixture(self.candidate, static=True)
        findings = describe_matches(data)
        attribute_dex(data, findings, PATTERNS)
        self.assertEqual(["Lpruebas/Origen;"], findings[0]["clases_valores_estaticos"])
        self.assertTrue(sensitive(data))

    def test_identifica_nombres_tipos_y_fuentes_sin_const_string(self):
        for role in ("nombre_metodo", "nombre_campo", "archivo_fuente", "descriptor_tipo"):
            with self.subTest(role=role):
                data = metadata_fixture(self.candidate, role)
                findings = describe_matches(data)
                attribute_dex(data, findings, PATTERNS)
                metadata, = findings[0]["metadatos_dex"]
                self.assertEqual(role, metadata["referencias"][0]["uso"])
                self.assertEqual(0, metadata["offset_en_contenedor"])
                self.assertEqual(len(self.candidate), metadata["longitud_contenedor"])
                self.assertEqual(hashlib.sha256(self.candidate).hexdigest(), metadata["sha256_contenedor"])
                self.assertNotIn(self.candidate.decode(), json.dumps(findings))
                self.assertTrue(sensitive(data))

    def test_atribuye_anotacion_y_array_sin_confundir_con_valor_estatico(self):
        data = metadata_fixture(self.candidate, "valor_anotacion")
        findings = describe_matches(data)
        attribute_dex(data, findings, PATTERNS)
        reference, = findings[0]["metadatos_dex"][0]["referencias"]
        self.assertEqual({"uso": "valor_anotacion", "clase": "Lpruebas/Origen;",
                          "anotacion": "Lpruebas/Anotacion;", "elemento": "valor"}, reference)
        self.assertEqual([], findings[0]["clases_valores_estaticos"])
        self.assertNotIn(self.candidate.decode(), json.dumps(findings))
        self.assertTrue(sensitive(data))

    def test_atribuye_parametros_variables_firmas_y_archivos_debug(self):
        for role in ("nombre_parametro_debug", "nombre_variable_debug", "firma_variable_debug", "archivo_fuente_debug"):
            with self.subTest(role=role):
                data = metadata_fixture(self.candidate, role)
                findings = describe_matches(data)
                attribute_dex(data, findings, PATTERNS)
                reference, = findings[0]["metadatos_dex"][0]["referencias"]
                self.assertEqual({"uso": role, "clase": "Lpruebas/Origen;", "indice_metodo": 0}, reference)
                self.assertNotIn(self.candidate.decode(), json.dumps(findings))
                self.assertTrue(sensitive(data))

    def test_cinco_subcadenas_identicas_no_son_una_excepcion(self):
        candidate = b"sk-" + b"y" * 27
        fingerprints, containers = set(), set()
        for n in range(5):
            data = metadata_fixture(b"prefijo" + bytes([65 + n]) + candidate + b"$sufijo", "nombre_metodo")
            findings = describe_matches(data)
            attribute_dex(data, findings, PATTERNS)
            finding, = findings
            self.assertEqual(30, finding["longitud"])
            metadata, = finding["metadatos_dex"]
            self.assertEqual(8, metadata["offset_en_contenedor"])
            self.assertEqual("Lpruebas/Origen;", metadata["referencias"][0]["clase"])
            fingerprints.add(finding["sha256"])
            containers.add(metadata["sha256_contenedor"])
            self.assertTrue(sensitive(data))
            self.assertNotIn(candidate.decode(), json.dumps(findings))
        self.assertEqual(1, len(fingerprints))
        self.assertEqual(5, len(containers))

    def test_identificador_metodo_solo_se_acepta_con_desensamblado_verificado(self):
        candidate = b"sk-" + b"y" * 27
        data = metadata_fixture(b"prefijoA" + candidate + b"$sufijo", "nombre_metodo")
        findings = describe_matches(data)
        with patch("atribuir_secretos.dexdump_references", return_value={1: []}):
            attribute_dex(data, findings, PATTERNS, dexdump="dexdump")
        self.assertEqual("IDENTIFICADOR_METODO_NO_EJECUTABLE", findings[0]["clasificacion"])
        self.assertEqual(30, findings[0]["longitud"])
        self.assertNotIn(candidate.decode(), json.dumps(findings))

    def test_const_string_y_ausencia_de_dexdump_mantienen_el_bloqueo(self):
        candidate = b"sk-" + b"y" * 27
        data = metadata_fixture(b"prefijoA" + candidate + b"$sufijo", "nombre_metodo")
        no_sdk = describe_matches(data)
        with patch.dict("os.environ", {"ANDROID_HOME": "", "ANDROID_SDK_ROOT": ""}):
            attribute_dex(data, no_sdk, PATTERNS)
        self.assertEqual("RECHAZAR", no_sdk[0]["clasificacion"])
        loaded = describe_matches(data)
        with patch("atribuir_secretos.dexdump_references", return_value={1: ["Lpruebas/Origen;"]}):
            attribute_dex(data, loaded, PATTERNS, dexdump="dexdump")
        self.assertEqual("RECHAZAR", loaded[0]["clasificacion"])

    def test_credenciales_en_valores_dex_o_metadatos_no_metodo_rechazadas(self):
        for role in ("nombre_campo", "descriptor_tipo", "archivo_fuente", "valor_anotacion"):
            with self.subTest(role=role):
                data = metadata_fixture(self.candidate, role)
                findings = describe_matches(data)
                with patch("atribuir_secretos.dexdump_references", return_value={1: []}):
                    attribute_dex(data, findings, PATTERNS, dexdump="dexdump")
                self.assertEqual("RECHAZAR", findings[0]["clasificacion"])
        data = dex_fixture(self.candidate, static=True)
        findings = describe_matches(data)
        with patch("atribuir_secretos.dexdump_references", return_value={1: []}):
            attribute_dex(data, findings, PATTERNS, dexdump="dexdump")
        self.assertEqual("RECHAZAR", findings[0]["clasificacion"])

    def test_cli_acepta_solo_metodo_confirmado_y_rechaza_const_string(self):
        candidate = b"sk-" + b"y" * 27
        data = metadata_fixture(b"prefijoA" + candidate + b"$sufijo", "nombre_metodo")
        with tempfile.TemporaryDirectory() as root:
            apk = pathlib.Path(root) / "app.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes4.dex", data)
            args = ["comprobar_secretos.py", str(apk)]
            with patch("sys.argv", args), patch("comprobar_secretos.subprocess.check_output", return_value=b""), patch("atribuir_secretos.dexdump_references", return_value={1: []}), patch.dict("os.environ", {"ANDROID_HOME": "", "ANDROID_SDK_ROOT": ""}), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, main())  # Sin verificación de desensamblado.
            with patch("sys.argv", ["comprobar_secretos.py", "--dexdump", "dexdump", str(apk)]), patch("comprobar_secretos.subprocess.check_output", return_value=b""), patch("atribuir_secretos.dexdump_references", return_value={1: []}), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, main())
            with patch("sys.argv", ["comprobar_secretos.py", "--dexdump", "dexdump", str(apk)]), patch("comprobar_secretos.subprocess.check_output", return_value=b""), patch("atribuir_secretos.dexdump_references", return_value={1: ["Lpruebas/Origen;"]}), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, main())

    def test_metadatos_corruptos_no_eliminan_el_rechazo(self):
        for role, offset in (("nombre_metodo", 136 + 4), ("valor_anotacion", 148 + 20)):
            with self.subTest(role=role):
                data = bytearray(metadata_fixture(self.candidate, role))
                struct.pack_into("<I", data, offset, 0x7fffffff)
                findings = describe_matches(data)
                self.assertEqual("DEX_NO_INTERPRETADO", attribute_dex(bytes(data), findings, PATTERNS)["estado"])
                self.assertTrue(findings)
                self.assertTrue(sensitive(data))

    def test_atribuye_huella_a_jar_y_aar_solo_si_la_clase_esta_en_dex(self):
        with tempfile.TemporaryDirectory() as root:
            cache = pathlib.Path(root)
            folder = cache / "pruebas" / "biblioteca" / "1.0" / "hash"
            folder.mkdir(parents=True)
            jar_bytes = io.BytesIO()
            with zipfile.ZipFile(jar_bytes, "w") as jar:
                jar.writestr("pruebas/Origen.class", b"constante\0" + self.candidate)
                jar.writestr("pruebas/Ausente.class", b"constante\0" + self.candidate)
            with zipfile.ZipFile(folder / "biblioteca.aar", "w") as aar:
                aar.writestr("classes.jar", jar_bytes.getvalue())
            (folder / "biblioteca.jar").write_bytes(jar_bytes.getvalue())
            data = dex_fixture(self.candidate)
            findings = describe_matches(data)
            attribute_dex(data, findings, PATTERNS, cache)
            origins = findings[0]["coincidencias_en_dependencias"]
            self.assertEqual(2, len(origins))
            self.assertTrue(all(item["clase"] == "Lpruebas/Origen;" for item in origins))
            self.assertTrue(all(item["modulo"] == "pruebas:biblioteca:1.0" for item in origins))
            self.assertNotIn(self.candidate.decode(), json.dumps(origins))
            self.assertTrue(sensitive(data))  # Una atribución jamás acepta la clave.

    def test_nombres_no_pueden_filtrar_candidatos_ni_url(self):
        for value in (self.candidate.decode(), "https://privado.invalid/?token=contenido", "https://usuario:contenido@privado.invalid", "Bearer contenido", "clase\ncontenido"):
            label = safe_label(value)
            self.assertTrue(label.startswith("oculto-sha256-"))
            self.assertNotIn(value, label)

    def test_dexdump_no_exporta_el_valor_y_mapea_referencia_const_string(self):
        process = unittest.mock.MagicMock()
        process.__enter__.return_value = process
        process.stdout = io.StringIO("Class descriptor  : 'Lpruebas/Origen;'\n000000: 1a00 0100 |0000: const-string v0, \"" + self.candidate.decode() + "\" // string@0001\n")
        process.wait.return_value = 0
        with patch("atribuir_secretos.subprocess.Popen", return_value=process):
            result = dexdump_references(dex_fixture(self.candidate), {1}, "dexdump")
        self.assertEqual({1: ["Lpruebas/Origen;"]}, result)
        self.assertNotIn(self.candidate.decode(), json.dumps(result))

    def test_cli_rechaza_classes4_y_no_filtra_credencial_al_informe_o_consola(self):
        with tempfile.TemporaryDirectory() as root:
            apk = pathlib.Path(root) / "app.apk"
            report = pathlib.Path(root) / "hallazgos.json"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes4.dex", dex_fixture(self.candidate))
            stdout = io.StringIO()
            with patch("sys.argv", ["comprobar_secretos.py", "--informe", str(report), str(apk)]), patch("comprobar_secretos.subprocess.check_output", return_value=b""), contextlib.redirect_stdout(stdout):
                self.assertEqual(1, main())
            result = json.loads(report.read_text())
            self.assertEqual("RECHAZADO", result["resultado"])
            self.assertEqual("OPENAI_API_KEY", result["hallazgos"][0]["patron"])
            self.assertNotIn(self.candidate.decode(), stdout.getvalue() + report.read_text())

    def test_cli_sigue_rechazando_fixture_incluso_sin_patron_secreto(self):
        with tempfile.TemporaryDirectory() as root:
            apk = pathlib.Path(root) / "app.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/modelo-prueba.zip", b"no se distribuye")
            with patch("sys.argv", ["comprobar_secretos.py", str(apk)]), patch("comprobar_secretos.subprocess.check_output", return_value=b""), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, main())


if __name__ == "__main__":
    unittest.main()
