import unittest
from resumir_latencias_escucha import summarize


class LatencySummaryTest(unittest.TestCase):
    def test_percentiles_use_only_valid_actual_samples(self):
        samples = [{"STOP_DETECTION": n} for n in range(1, 21)]
        samples += [{"STOP_DETECTION": -1}, {"STOP_DETECTION": float("nan")}, {"STOP_DETECTION": True}]
        self.assertEqual({"n": 20, "p50_ms": 10, "p95_ms": 19}, summarize(samples)["STOP_DETECTION"])

    def test_missing_measurements_are_not_reported_as_zero(self):
        self.assertEqual({}, summarize([{}, {"TOTAL": 123}]))
