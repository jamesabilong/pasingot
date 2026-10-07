import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from scripts import paired_preflight as preflight


def inventory(role, local, peer):
    return {"schemaVersion": 1, "role": role, "avdName": preflight.OWNED[role],
            "packageName": preflight.APP, "signatureSha256": ["a" * 64],
            "localNodeId": local, "apiAvailable": True, "errors": [],
            "connectedPeers": [{"id": peer, "nearby": True}], "installedApkMatches": True}


class PairDecisionTest(unittest.TestCase):
    def setUp(self):
        self.phone = inventory("phone", "phone-node", "wear-node")
        self.wear = inventory("wear", "wear-node", "phone-node")

    def decision(self):
        return preflight.evaluate_pair(self.phone, self.wear, True)

    def test_exact_pair_is_ready_but_not_acceptance(self):
        result = self.decision()
        self.assertTrue(result["transportReady"])
        self.assertFalse(result["acceptanceValidated"])

    def test_stale_or_missing_capture_rejected(self):
        for observed in [None, 999, 3000, True]:
            with self.subTest(observed=observed), self.assertRaises(ValueError):
                preflight.validate_capture_time({"observedAtMillis": observed}, 1, 1)
        preflight.validate_capture_time({"observedAtMillis": 1500}, 1, 1)

    def test_unavailable_api_blocks(self):
        self.phone["apiAvailable"] = False
        self.assertFalse(self.decision()["transportReady"])

    def test_different_signatures_block(self):
        self.wear["signatureSha256"] = ["b" * 64]
        self.assertFalse(self.decision()["transportReady"])

    def test_stale_installed_apk_blocks(self):
        self.phone["installedApkMatches"] = False
        self.assertFalse(self.decision()["transportReady"])

    def test_extra_peer_blocks(self):
        self.phone["connectedPeers"].append({"id": "other", "nearby": True})
        self.assertFalse(self.decision()["transportReady"])

    def test_cloud_only_peer_blocks(self):
        self.phone["connectedPeers"][0]["nearby"] = False
        self.assertFalse(self.decision()["transportReady"])

    def test_future_or_misbound_inventory_rejected(self):
        for field, value in [("schemaVersion", 999), ("role", "wear"),
                             ("avdName", "Pixel_8"), ("packageName", "other.app")]:
            with self.subTest(field=field):
                changed = dict(self.phone, **{field: value})
                with self.assertRaises(ValueError):
                    preflight.validate_inventory(changed, "phone")

    def test_missing_companion_diagnosed_when_disconnected(self):
        self.phone["connectedPeers"] = []
        result = preflight.evaluate_pair(self.phone, self.wear, False)
        self.assertFalse(result["transportReady"])
        self.assertTrue(any("companion" in item for item in result["blockers"]))


class FakeAdb:
    def __init__(self, original=False):
        self.original = original
        self.calls = []

    def call(self, serial, *args, **kwargs):
        self.calls.append((serial, args))
        if args != ("emu", "avd", "name"):
            raise AssertionError("Unexpected device operation")
        role = "phone" if serial == "emulator-5582" else "wear"
        return ("Pixel_8" if self.original else preflight.OWNED[role]) + "\nOK\n"


class CaptureSafetyTest(unittest.TestCase):
    def args(self, output):
        return ["--phone-serial", "emulator-5582", "--wear-serial", "emulator-5580",
                "--output", str(output)]

    def test_original_profile_rejected_before_evidence_or_probe(self):
        adb = FakeAdb(original=True)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "evidence"
            with patch.object(preflight, "Adb", return_value=adb):
                self.assertEqual(preflight.main(self.args(output)), 1)
            self.assertFalse(output.exists())
        self.assertEqual(len(adb.calls), 1)

    def test_existing_evidence_is_preserved_without_probe(self):
        adb = FakeAdb()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            marker = output / "preserve.txt"
            marker.write_text("historical evidence")
            with patch.object(preflight, "Adb", return_value=adb):
                self.assertEqual(preflight.main(self.args(output)), 1)
            self.assertEqual(marker.read_text(), "historical evidence")
        self.assertEqual(len(adb.calls), 2)


if __name__ == "__main__":
    unittest.main()
