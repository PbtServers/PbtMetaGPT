"""Resume muestras técnicas exportadas; no confunde parada solicitada con parada audible."""
import argparse
import json
import math
from pathlib import Path

STAGES = ("STOP_DETECTION", "STOP_AUDIO_REQUEST", "FOLLOW_UP_READY")


def summarize(samples):
    result = {}
    for stage in STAGES:
        values = sorted(sample[stage] for sample in samples if isinstance(sample, dict)
                        and isinstance(sample.get(stage), (int, float))
                        and not isinstance(sample[stage], bool) and math.isfinite(sample[stage]) and sample[stage] >= 0)
        if values:
            result[stage] = {"n": len(values), "p50_ms": values[math.ceil(len(values) * .50) - 1],
                             "p95_ms": values[math.ceil(len(values) * .95) - 1]}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archivo", type=Path, help="Array JSON de LatencyStore, sin conversaciones")
    parser.add_argument("--dispositivo", required=True, help="Modelo y versión de Android")
    parser.add_argument("--ruta", required=True, help="Entrada/salida comprobadas; indicar emulador cuando corresponda")
    args = parser.parse_args()
    if args.archivo.stat().st_size > 1024 * 1024:
        parser.error("El archivo excede 1 MiB.")
    samples = json.loads(args.archivo.read_text(encoding="utf-8"))
    if not isinstance(samples, list):
        parser.error("Se requiere un array de muestras técnicas.")
    print(json.dumps({"dispositivo": args.dispositivo, "ruta": args.ruta, "metodo": "rango más próximo",
                      "metricas": summarize(samples),
                      "limite": "STOP_AUDIO_REQUEST no mide parada audible; PCM/emulador no acredita acústica ni gafas."},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
