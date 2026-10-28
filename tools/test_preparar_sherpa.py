"""Contratos del empaquetado independiente de las dos ABI ONNX Android."""

import tempfile
import unittest
import zipfile
from pathlib import Path

from preparar_sherpa import MARKER, OLD_ORT, SHERPA_ORT, prepared


class SherpaRuntimeSplitTest(unittest.TestCase):
    def archive(self, files):
        directory = tempfile.TemporaryDirectory()
        path = Path(directory.name) / "test.aar"
        with zipfile.ZipFile(path, "w") as output:
            for filename in files:
                output.writestr(filename, b"x")
        return directory, path

    def test_rechaza_aar_sin_su_runtime_privado(self):
        entries = [MARKER, "jni/x86_64/libsherpa-onnx-jni.so"]
        folder, path = self.archive(entries)
        with folder:
            self.assertFalse(prepared(path))

    def test_rechaza_runtime_duplicado_sin_separacion(self):
        entries = [MARKER, "jni/x86_64/libsherpa-onnx-jni.so",
                   "jni/x86_64/" + SHERPA_ORT, "jni/arm64-v8a/" + SHERPA_ORT,
                   "jni/x86_64/" + OLD_ORT]
        folder, path = self.archive(entries)
        with folder:
            self.assertFalse(prepared(path))

    def test_distingue_aar_nuevo_de_una_version_anterior(self):
        entries = [MARKER, "jni/x86_64/libsherpa-onnx-jni.so",
                   "jni/x86_64/" + SHERPA_ORT, "jni/arm64-v8a/" + SHERPA_ORT]
        folder, path = self.archive(entries)
        with folder:
            self.assertTrue(prepared(path))


if __name__ == "__main__":
    unittest.main()
