"""Contratos de recursos verificables sin SDK; no sustituyen Android Lint ni un launcher."""
import math
import pathlib
import re
import unittest
import struct
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
A = "{http://schemas.android.com/apk/res/android}"


class AndroidResourcesTest(unittest.TestCase):
    def test_icon_identity_and_launcher_backups(self):
        foreground = ET.parse(RES / "drawable/ic_launcher_foreground.xml").getroot()
        self.assertEqual({"#FFFFFF", "#388BFF"}, {p.attrib[A + "fillColor"] for p in foreground.findall(".//path")})
        colors = ET.parse(RES / "values/colors.xml").getroot()
        self.assertEqual("#000000", next(c.text for c in colors if c.attrib["name"] == "ic_launcher_background"))
        for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            for filename in ("ic_launcher.png", "ic_launcher_round.png"):
                data = (RES / f"mipmap-{density}" / filename).read_bytes()
                self.assertEqual(b"\x89PNG\r\n\x1a\n", data[:8])
                self.assertEqual((size, size), struct.unpack(">II", data[16:24]))

    def test_webrtc_network_monitor_permission_declared(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = {p.attrib[A + "name"] for p in manifest.findall("uses-permission")}
        self.assertIn("android.permission.ACCESS_NETWORK_STATE", permissions)
        self.assertIn("android.permission.INTERNET", permissions)

    def test_manifest_resolves_both_adaptive_icons_on_api31_and33(self):
        app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
        colors = {c.attrib["name"] for c in ET.parse(RES / "values/colors.xml").getroot()}
        for attribute in ("icon", "roundIcon"):
            reference = app.attrib[A + attribute]
            self.assertTrue(reference.startswith("@mipmap/"))
            for qualifier in ("mipmap-anydpi-v26", "mipmap-anydpi-v33"):
                icon = ET.parse(RES / qualifier / (reference.split("/")[1] + ".xml")).getroot()
                self.assertEqual("adaptive-icon", icon.tag)
                self.assertIn(icon.find("background").attrib[A + "drawable"].split("/")[1], colors)
                foreground = icon.find("foreground").attrib[A + "drawable"]
                self.assertTrue((RES / "drawable" / (foreground.split("/")[1] + ".xml")).is_file())
                if qualifier.endswith("v33"):
                    mono = icon.find("monochrome").attrib[A + "drawable"]
                    self.assertTrue((RES / "drawable" / (mono.split("/")[1] + ".xml")).is_file())

    def test_icon_paths_stay_inside_adaptive_safe_circle(self):
        for name in ("ic_launcher_foreground", "ic_launcher_monochrome"):
            vector = ET.parse(RES / "drawable" / (name + ".xml")).getroot()
            self.assertEqual(108, float(vector.attrib[A + "viewportWidth"]))
            self.assertEqual(108, float(vector.attrib[A + "viewportHeight"]))
            group = vector.find("group")
            scale_x = float(group.attrib[A + "scaleX"])
            scale_y = float(group.attrib[A + "scaleY"])
            # Paths consist of M/L/Q/Z commands: endpoints and control points
            # inside a convex circle imply the entire Bézier lies inside it.
            for path in group.findall("path"):
                data = path.attrib[A + "pathData"]
                self.assertFalse(set(re.findall("[A-Za-z]", data)) - set("MLQZ"))
                points = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", data)]
                self.assertEqual(0, len(points) % 2)
                for x, y in zip(points[::2], points[1::2]):
                    self.assertLessEqual(math.hypot((x - 54) * scale_x, (y - 54) * scale_y), 33)

    def test_monochrome_is_a_transparent_single_color_symbol(self):
        vector = ET.parse(RES / "drawable/ic_launcher_monochrome.xml").getroot()
        paths = vector.findall(".//path")
        self.assertTrue(paths)
        self.assertTrue(all(p.attrib[A + "fillColor"] == "#FFFFFF" for p in paths))
        self.assertIsNone(vector.find("background"))

    def test_custom_theme_foregrounds_meet_text_aa(self):
        source = (ROOT / "app/src/main/java/com/pbtservers/pbtmetagpt/PbtTheme.kt").read_text(encoding="utf-8")

        def luminance(color):
            rgb = [(color >> shift & 255) / 255 for shift in (16, 8, 0)]
            linear = [n / 12.92 if n <= 0.04045 else ((n + 0.055) / 1.055) ** 2.4 for n in rgb]
            return sum(n * weight for n, weight in zip(linear, (0.2126, 0.7152, 0.0722)))

        palettes = re.findall(r"val Pbt(?:Dark|Light)Colors = \w+\((.*?)\n\)", source, re.S)
        self.assertEqual(2, len(palettes))
        for palette in palettes:
            palette = palette.replace("Color.White", "Color(0xFFFFFFFF)")
            colors = {key: int(value, 16) for key, value in re.findall(r"(\w+) = Color\(0x([A-Fa-f0-9]+)\)", palette)}
            for text, background in (("onBackground", "background"), ("onSurface", "surface"),
                                     ("onSurfaceVariant", "surfaceVariant"), ("primary", "background"),
                                     ("secondary", "surface"), ("onPrimary", "primary"), ("onSecondary", "secondary")):
                a, b = luminance(colors[text]), luminance(colors[background])
                self.assertGreaterEqual((max(a, b) + 0.05) / (min(a, b) + 0.05), 4.5, (text, background))


if __name__ == "__main__":
    unittest.main()
