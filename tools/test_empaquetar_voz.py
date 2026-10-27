import hashlib
import json
import pathlib
import tempfile
import unittest
import zipfile
from empaquetar_voz import package_voice


class PaqueteTest(unittest.TestCase):
    def test_empaquetado_fijo_y_hashes_sin_acreditar_inferencia(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            files = [root / name for name in ("modelo.onnx", "simbolos.txt", "pronunciaciones.tsv")]
            for file in files:
                file.write_bytes(b"datos ficticios de prueba de empaquetado, no una voz")
            output = root / "voz.zip"
            package_voice(*files, output, name="Original", language="es-ES", license_name="Prueba", source="Suite local", sample_text="hola", min_ram_mb=2048, rights=True)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(set(archive.namelist()), {"voz.json", "model.onnx", "tokens.txt", "lexicon.tsv"})
                metadata = json.loads(archive.read("voz.json"))
                for name in ("model.onnx", "tokens.txt", "lexicon.tsv"):
                    self.assertEqual(metadata["sha256"][name], hashlib.sha256(archive.read(name)).hexdigest())
                self.assertNotIn("api_key", metadata)
            before = output.read_bytes()
            with self.assertRaises(ValueError):
                package_voice(*files, output, name="Original", language="es-ES", license_name="Prueba", source="Suite", sample_text="hola", min_ram_mb=2048, rights=True)
            self.assertEqual(before, output.read_bytes())

    def test_exige_autorizacion_y_rechaza_secretos(self):
        for rights, name in ((False, "Original"), (True, "sk-" + "x" * 40)):
            with self.assertRaises(ValueError):
                package_voice("no existe", "no existe", "no existe", "no existe", name=name, language="es-ES", license_name="Prueba", source="Suite", sample_text="hola", min_ram_mb=2048, rights=rights)


if __name__ == "__main__":
    unittest.main()
