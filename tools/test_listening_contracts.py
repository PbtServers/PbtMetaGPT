"""Permisos y fixture: sin SDK, audio ni credenciales; no sustituye instrumentación."""
import pathlib
import unittest
import xml.etree.ElementTree as ET
import preparar_activacion_prueba as fixture

ROOT = pathlib.Path(__file__).resolve().parents[1]
A = "{http://schemas.android.com/apk/res/android}"


class ListeningContractsTest(unittest.TestCase):
    def test_service_has_explicit_microphone_contract_without_boot_or_export(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = {item.attrib[A + "name"] for item in manifest.findall("uses-permission")}
        self.assertTrue({"android.permission.RECORD_AUDIO", "android.permission.FOREGROUND_SERVICE",
                         "android.permission.FOREGROUND_SERVICE_MICROPHONE", "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                         "android.permission.POST_NOTIFICATIONS"} <= permissions)
        self.assertNotIn("android.permission.RECEIVE_BOOT_COMPLETED", permissions)
        service = next(item for item in manifest.find("application").findall("service")
                       if item.attrib[A + "name"] == ".ContinuousWakeService")
        self.assertEqual("false", service.attrib[A + "exported"])
        self.assertEqual({"microphone", "connectedDevice"}, set(service.attrib[A + "foregroundServiceType"].split("|")))
        self.assertEqual("false", service.attrib[A + "stopWithTask"])
        packages = [item.attrib[A + "name"] for item in manifest.find("queries").findall("package")]
        self.assertEqual(1, packages.count("com.openai.chatgpt"))

    def test_fixture_is_pinned_and_excluded_from_main_apk(self):
        self.assertEqual(64, len(fixture.SHA256))
        runtime = (ROOT / "app/src/main/java/com/pbtservers/pbtmetagpt/WakeModel.kt").read_text(encoding="utf-8")
        self.assertIn(fixture.SHA256, runtime)
        self.assertIn("app/src/androidTest/assets/activacion-prueba.zip", (ROOT / ".gitignore").read_text())
        self.assertFalse((ROOT / "app/src/main/assets/activacion-prueba.zip").exists())
