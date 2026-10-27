import unittest
from comprobar_secretos import sensitive


class SecretosTest(unittest.TestCase):
    def test_detecta_groq_y_huggingface_sin_revelarlos(self):
        from comprobar_secretos import describe_matches
        for prefix, label in ((b"gsk_", "GROQ_API_KEY"), (b"hf_", "HF_API_TOKEN")):
            candidate = prefix + b"x" * 40
            self.assertTrue(sensitive(candidate))
            self.assertEqual(label, describe_matches(candidate)[0]["patron"])
            self.assertNotIn(candidate.decode(), str(describe_matches(candidate)))

    def test_detecta_claves_openai_en_texto_y_binario(self):
        for prefix in (b"sk-", b"sk-proj-", b"sk-svcacct-"):
            self.assertTrue(sensitive(b"\x00" + prefix + b"x" * 40 + b"\x00"))

    def test_distingue_etiqueta_de_parser_y_bloque_privado(self):
        start = b"-----BEGIN " + b"PRIVATE KEY-----"
        end = b"-----END " + b"PRIVATE KEY-----"
        self.assertFalse(sensitive(b"\x00" + start + b"\x00" + end))
        self.assertTrue(sensitive(start + b"\n" + b"A" * 80 + b"\n" + end))

    def test_no_rechaza_textos_de_configuracion(self):
        self.assertFalse(sensitive(b"OPENAI_API_KEY_FILE=/run/secrets/openai_api_key"))


if __name__ == "__main__":
    unittest.main()
