"""Pruebas REALES optativas, sin mocks, secretos en argumentos ni contenido en informes.

No establece cuota agotada mediante abuso. Un error/ausencia de credencial no es un éxito.
"""
import argparse
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse
import wave

QWEN = "https://qwen-qwen3-tts.hf.space"


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(url, data=None, key=None, limit=1048576):
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if key:
        headers["Authorization"] = "Bearer " + key
    req = urllib.request.Request(url, data=None if data is None else json.dumps(data).encode(), headers=headers)
    with urllib.request.build_opener(NoRedirects()).open(req, timeout=150) as response:
        body = response.read(limit + 1)
        if len(body) > limit:
            raise ValueError("Respuesta excesiva")
        return body


def measure(results, name, action):
    started = time.monotonic()
    try:
        metadata = action()
        results.append({"prueba": name, "resultado": "CORRECTO_REAL", **metadata,
                        "ms": round((time.monotonic() - started) * 1000)})
    except urllib.error.HTTPError as error:
        results.append({"prueba": name, "resultado": "FALLO_REAL", "http": error.code,
                        "retry_after_segundos": error.headers.get("Retry-After", "") if error.headers.get("Retry-After", "").isdigit() else None})
    except Exception as error:
        results.append({"prueba": name, "resultado": "BLOQUEADO_O_FALLO", "tipo": type(error).__name__})


def groq(results, args):
    key = os.environ.get("GROQ_API_KEY", "")
    if not key:
        results.append({"prueba": "groq", "resultado": "NO_EJECUTADO", "motivo": "Falta GROQ_API_KEY; no compartirla ni versionarla"})
        return
    if not args.confirmar_plan_free:
        results.append({"prueba": "groq", "resultado": "NO_EJECUTADO", "motivo": "Falta confirmar cuenta Free; una clave no certifica el plan"})
        return
    def models():
        data = json.loads(request("https://api.groq.com/openai/v1/models", key=key))
        ids = [row["id"] for row in data["data"]]
        return {"modelos": len(ids), "modelo_elegido_disponible": args.modelo in ids}
    measure(results, "groq_conexion_modelos", models)
    if args.generar_groq:
        def answer():
            response = json.loads(request("https://api.groq.com/openai/v1/chat/completions", {
                "model": args.modelo, "stream": False, "max_completion_tokens": 1024,
                **({"reasoning_effort": "low"} if args.modelo.startswith("openai/gpt-oss-") else {}),
                "messages": [{"role": "system", "content": "Responde brevemente."},
                             {"role": "user", "content": "Saluda en español y en catalán."}]}, key))
            text = response["choices"][0]["message"]["content"].strip()
            if not text:
                raise ValueError("Respuesta vacía")
            return {"caracteres": len(text), "modelo": response.get("model"), "tokens": response.get("usage", {}).get("total_tokens")}
        measure(results, "groq_generacion_es_ca", answer)


def qwen(results):
    def contract():
        info = json.loads(request(QWEN + "/gradio_api/info"))
        endpoint = info["named_endpoints"]["/generate_custom_voice"]
        names = [item["parameter_name"] for item in endpoint["parameters"]]
        if names != ["text", "language", "speaker", "instruct", "model_size"]:
            raise ValueError("Contrato incompatible")
        return {"parametros": len(names)}
    measure(results, "qwen_contrato", contract)
    if results[-1]["resultado"] != "CORRECTO_REAL":
        results.append({"prueba": "qwen_audio", "resultado": "NO_EJECUTADO", "motivo": "Contrato remoto sin validar"})
        return
    def synthesis():
        base = QWEN + "/gradio_api/call/generate_custom_voice"
        event = json.loads(request(base, {"data": ["Hola, esta es una prueba de voz.", "Spanish", "Ryan", "", "0.6B"]}))["event_id"]
        if not re.fullmatch(r"[a-fA-F0-9-]{16,64}", event):
            raise ValueError("Evento incompatible")
        events = request(base + "/" + event).decode().replace("\r\n", "\n").split("\n\n")
        complete = next(block for block in events if "event: complete" in block.splitlines())
        data = json.loads("\n".join(line[6:] for line in complete.splitlines() if line.startswith("data: ")))
        url = data[0]["url"]
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.netloc != "qwen-qwen3-tts.hf.space" or not parsed.path.startswith("/gradio_api/file=") or parsed.query or parsed.fragment:
            raise ValueError("Origen de audio no autorizado")
        audio = request(url, limit=16777216)
        with wave.open(io.BytesIO(audio)) as wav:
            if wav.getnchannels() != 1 or wav.getsampwidth() != 2 or wav.getnframes() == 0:
                raise ValueError("PCM incompatible")
            return {"bytes": len(audio), "sample_rate": wav.getframerate(), "frames": wav.getnframes()}
    measure(results, "qwen_audio_real", synthesis)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qwen", action="store_true", help="Autoriza una muestra sintética en el Space público, sin token")
    parser.add_argument("--confirmar-plan-free", action="store_true")
    parser.add_argument("--generar-groq", action="store_true", help="Autoriza una petición sintética a Groq; consume cuota")
    parser.add_argument("--modelo", default="openai/gpt-oss-20b")
    parser.add_argument("--informe", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9./_-]{0,119}", args.modelo):
        parser.error("ID de modelo no válido")
    results = []
    groq(results, args)
    if args.qwen:
        qwen(results)
    report = {"fecha_utc": datetime.now(timezone.utc).isoformat(), "tipo": "RED_REAL_SIN_MOCKS", "pruebas": results,
              "limite": "No acredita calidad lingüística, audición, Android ni gafas; cuotas solo si el servidor realmente las devuelve"}
    args.informe.parent.mkdir(parents=True, exist_ok=True)
    args.informe.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for result in results:
        print(result["prueba"] + ": " + result["resultado"])
    return 0 if results and all(result["resultado"] == "CORRECTO_REAL" for result in results) else 2


if __name__ == "__main__":
    raise SystemExit(main())
