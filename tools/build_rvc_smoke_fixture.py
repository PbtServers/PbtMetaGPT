"""Genera modelos ONNX sintéticos diminutos para probar JNI RVC en Android.

Requiere pip install onnx en un entorno de desarrollo, NO en GitHub Actions.
No contienen voces, datos personales ni modelos de terceros; NO prueban calidad.
"""
import json
from pathlib import Path

import onnx
from onnx import TensorProto as T, helper as H

ROOT = Path(__file__).resolve().parents[1] / "app/src/androidTest/assets/rvc_smoke"
ROOT.mkdir(parents=True, exist_ok=True)


def vi(name, typ, shape):
    return H.make_tensor_value_info(name, typ, shape)


def save(name, inputs, outputs, nodes, constants=None, metadata=None):
    graph = H.make_graph(nodes, name, inputs, outputs, initializer=constants or [])
    model = H.make_model(graph, producer_name="PbtMetaGPT synthetic smoke tests", opset_imports=[H.make_operatorsetid("", 13)])
    model.ir_version = 8
    if metadata:
        info = model.metadata_props.add()
        info.key = "metadata"
        info.value = json.dumps(metadata, separators=(",", ":"))
    # El importador de pesos reales rechaza ficheros demasiado pequeños.
    # El relleno de metadatos mantiene la misma política en la prueba.
    padding = model.metadata_props.add()
    padding.key = "synthetic_fixture_padding"
    padding.value = "solo-pruebas-" * 120
    onnx.checker.check_model(model)
    onnx.save(model, ROOT / (name + ".onnx"))


def const(name, datatype, dims, values):
    return H.make_tensor(name, datatype, dims, values)


save(
    "hubert",
    [vi("audio", T.FLOAT, [1, "N"])],
    [vi("unit12", T.FLOAT, [1, 8, 768])],
    [
        H.make_node("ReduceMean", ["audio"], ["mean"], axes=[1], keepdims=1),
        H.make_node("Unsqueeze", ["mean", "axes"], ["expanded"]),
        H.make_node("Expand", ["expanded", "shape"], ["unit12"]),
    ],
    [const("axes", T.INT64, [1], [2]), const("shape", T.INT64, [3], [1, 8, 768])],
)

save(
    "rmvpe",
    [vi("waveform", T.FLOAT, [1, "N"]), vi("threshold", T.FLOAT, [1])],
    [vi("f0", T.FLOAT, [1, 20])],
    [
        H.make_node("ReduceMean", ["waveform"], ["mean"], axes=[1], keepdims=1),
        H.make_node("Add", ["mean", "threshold"], ["sum"]),
        H.make_node("Add", ["sum", "hz"], ["shift"]),
        H.make_node("Expand", ["shift", "shape"], ["f0"]),
    ],
    [const("hz", T.FLOAT, [1], [120.0]), const("shape", T.INT64, [2], [1, 20])],
)

save(
    "synth",
    [
        vi("feats", T.FLOAT, [1, "T", 768]),
        vi("p_len", T.INT64, [1]),
        vi("sid", T.INT64, [1]),
        vi("pitch", T.INT64, [1, "T"]),
        vi("pitchf", T.FLOAT, [1, "T"]),
    ],
    [vi("audio", T.FLOAT, [1, "T"])],
    [
        H.make_node("ReduceMean", ["feats"], ["mean"], axes=[2], keepdims=0),
        H.make_node("Cast", ["p_len"], ["l_float"], to=T.FLOAT),
        H.make_node("Cast", ["sid"], ["s_float"], to=T.FLOAT),
        H.make_node("Cast", ["pitch"], ["p_float"], to=T.FLOAT),
        H.make_node("Mul", ["l_float", "zero"], ["l_zero"]),
        H.make_node("Mul", ["s_float", "zero"], ["s_zero"]),
        H.make_node("Mul", ["p_float", "zero"], ["p_zero"]),
        H.make_node("Mul", ["pitchf", "zero"], ["pf_zero"]),
        H.make_node("Add", ["mean", "l_zero"], ["a"]),
        H.make_node("Add", ["a", "s_zero"], ["b"]),
        H.make_node("Add", ["b", "p_zero"], ["c"]),
        H.make_node("Add", ["c", "pf_zero"], ["audio"]),
    ],
    [const("zero", T.FLOAT, [], [0.0])],
    metadata={
        "samplingRate": 40000,
        "f0": True,
        "embChannels": 768,
        "embedder": "hubert",
        "embOutputLayer": 12,
        "useFinalProj": False,
    },
)

print("Modelos ONNX sintéticos generados en", ROOT)
for file in ROOT.glob("*.onnx"):
    print(file.name, file.stat().st_size)
