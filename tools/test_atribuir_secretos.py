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
