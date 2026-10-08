import contextlib
import io
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from scripts import collect_physical_validation as collector


class FakeCommands:
    def __init__(self):
        self.calls = []
        self.attached = "List of devices attached\nphone device\nwatch device\nother device\n"
        self.wrong_role = False
        self.emulator = False
        self.app_missing = False
        self.splits = False
        self.digest = "a" * 64
        self.fail = None
        self.paths = None

    def __call__(self, command):
        self.calls.append(command)
        if command[0] == "git":
            return "checkpoint\n" if command[-1] == "HEAD" else ""
        if command[1:] == ["devices", "-l"]:
            return self.attached
        serial = command[2]
        if serial not in {"phone", "watch"}:
            raise AssertionError("Unselected device was probed")
        operation = command[3:]
        if operation == self.fail:
            raise subprocess.TimeoutExpired(command, 30)
        if operation[:2] == ["shell", "getprop"]:
            return {
                "ro.product.model": "Watch7" if serial == "watch" else "S25",
                "ro.product.manufacturer": "samsung", "ro.hardware": "ranchu" if self.emulator else "exynos",
                "ro.build.characteristics": "watch" if serial == "watch" and not self.wrong_role else "phone",
                "ro.build.version.release": "16", "ro.build.version.sdk": "36",
                "ro.kernel.qemu": "0", "ro.boot.qemu": "0",
            }[operation[2]] + "\n"
        if operation == ["shell", "dumpsys", "battery"]:
            return "level: 80\nscale: 100\nAC powered: false\n"
        if operation == ["shell", "dumpsys", "package", collector.APP]:
            return "Unable to find package" if self.app_missing else "versionCode=1\nversionName=1.0\n"
        if operation == ["shell", "pm", "path", collector.APP]:
            return self.paths or ("package:/data/app/example/base.apk\n" +
                                 ("package:/data/app/example/split.apk\n" if self.splits else ""))
        if operation == ["shell", "sha256sum", "/data/app/example/base.apk"]:
            return self.digest + "  /data/app/example/base.apk\n"
        raise AssertionError(f"Unexpected or mutating operation: {operation}")


class PhysicalCollectorTest(unittest.TestCase):
    def invoke(self, fake, directory, extra=()):
        output = Path(directory) / "evidence"
        with patch.object(collector, "run", side_effect=fake), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = collector.main(["--adb", "adb", "--phone-serial", "phone", "--watch-serial", "watch",
                                   "--output", str(output), *extra])
        report = json.loads((output / "inventory.json").read_text()) if (output / "inventory.json").exists() else None
        return code, report

    def test_inventory_never_claims_transport_or_acceptance(self):
        fake = FakeCommands()
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.invoke(fake, directory)
        self.assertEqual(code, 0)
        self.assertEqual(report["readiness"], "physical_inventory_ready")
        self.assertIsNone(report["transportReady"])
        self.assertEqual(report["acceptance"], "not_evaluated")
        self.assertEqual([d["serial"] for d in report["devices"]], ["phone", "watch"])
        self.assertFalse(any("sha256sum" in c for c in fake.calls))

    def test_missing_offline_and_unauthorized_never_probe_devices(self):
        for state in ["missing", "offline", "unauthorized", "no permissions"]:
            fake = FakeCommands()
            fake.attached = "List of devices attached\n" + ("" if state == "missing" else f"phone {state}\n")
            with self.subTest(state=state), tempfile.TemporaryDirectory() as directory:
                code, report = self.invoke(fake, directory)
                self.assertEqual(code, 2)
                self.assertEqual(report["readiness"], "selected_devices_unavailable")
                self.assertEqual(report["devices"], [])
                self.assertFalse(any("-s" in c for c in fake.calls))

    def test_existing_evidence_is_preserved_before_any_read(self):
        fake = FakeCommands()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "evidence"
            output.mkdir()
            (output / "inventory.json").write_text("original")
            code, _ = self.invoke_existing(fake, directory)
            self.assertEqual(code, 1)
            self.assertEqual((output / "inventory.json").read_text(), "original")
        self.assertEqual(fake.calls, [])

    def invoke_existing(self, fake, directory):
        # Existing evidence deliberately is not JSON and must not be parsed by this helper.
        with patch.object(collector, "run", side_effect=fake), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return collector.main(["--phone-serial", "phone", "--watch-serial", "watch",
                                   "--output", str(Path(directory) / "evidence")]), None

    def test_invalid_or_equal_serials_rejected_before_output(self):
        for phone, watch in [("phone", "phone"), ("-s", "watch"), ("phone;reboot", "watch"), ("phone", "watch\n")]:
            fake = FakeCommands()
            with self.subTest(phone=phone, watch=watch), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "evidence"
                with patch.object(collector, "run", side_effect=fake), contextlib.redirect_stderr(io.StringIO()):
                    code = collector.main([f"--phone-serial={phone}", f"--watch-serial={watch}", "--output", str(output)])
                self.assertEqual(code, 1)
                self.assertFalse(output.exists())
                self.assertEqual(fake.calls, [])

    def test_emulator_rejected_before_package_or_battery_reads(self):
        fake = FakeCommands()
        fake.emulator = True
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.invoke(fake, directory)
        self.assertEqual(code, 1)
        self.assertEqual(report["readiness"], "collection_failed")
        self.assertFalse(any("dumpsys" in c for c in fake.calls))

    def test_wrong_role_does_not_read_watch_package(self):
        fake = FakeCommands()
        fake.wrong_role = True
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.invoke(fake, directory)
        self.assertEqual(code, 1)
        self.assertEqual(report["devices"][0]["role"], "phone")
        self.assertFalse(any(c[2:5] == ["watch", "shell", "dumpsys"] for c in fake.calls if c[0] != "git"))

    def test_read_failure_retains_failed_report(self):
        fake = FakeCommands()
        fake.fail = ["shell", "dumpsys", "battery"]
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.invoke(fake, directory)
            self.assertTrue((Path(directory) / "evidence" / "adb-devices.txt").exists())
        self.assertEqual(code, 1)
        self.assertEqual(report["readiness"], "collection_failed")
        self.assertTrue(report["errors"])

    def test_app_missing_is_not_ready(self):
        fake = FakeCommands()
        fake.app_missing = True
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.invoke(fake, directory)
        self.assertEqual(code, 2)
        self.assertEqual(report["readiness"], "app_missing")

    def test_split_install_never_claims_whole_apk_equality(self):
        fake = FakeCommands()
        fake.splits = True
        with tempfile.TemporaryDirectory() as directory:
            _, report = self.invoke(fake, directory, ["--include-installed-hash"])
        self.assertTrue(all(d["hasSplits"] and not d["installedApkMatches"] for d in report["devices"]))

    def test_exact_and_stale_local_apks_are_distinguished(self):
        fake = FakeCommands()
        fake.digest = hashlib.sha256(b"current").hexdigest()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for module in ["app", "wear"]:
                apk = root / f"android/{module}/build/outputs/apk/debug/{module}-debug.apk"
                apk.parent.mkdir(parents=True)
                apk.write_bytes(b"current" if module == "app" else b"stale")
            with patch.object(collector, "ROOT", root):
                code, report = self.invoke(fake, directory, ["--include-installed-hash"])
        self.assertEqual(code, 0)  # Inventory readiness does not imply a build match.
        self.assertTrue(report["devices"][0]["installedApkMatches"])
        self.assertFalse(report["devices"][1]["installedApkMatches"])
        self.assertIsNone(report["transportReady"])

    def test_malformed_path_refused_before_remote_hash(self):
        fake = FakeCommands()
        fake.paths = "package:/data/app/$(reboot)/base.apk\n"
        with tempfile.TemporaryDirectory() as directory:
            code, _ = self.invoke(fake, directory, ["--include-installed-hash"])
        self.assertEqual(code, 1)
        self.assertFalse(any("sha256sum" in c for c in fake.calls))

    def test_malformed_hash_is_failure(self):
        fake = FakeCommands()
        fake.digest = "not-a-hash"
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.invoke(fake, directory, ["--include-installed-hash"])
        self.assertEqual(code, 1)
        self.assertEqual(report["readiness"], "collection_failed")

    def test_tv_and_unknown_identity_are_refused(self):
        props = {"model": "TV", "apiLevel": "36", "hardware": "real", "qemuBoot": "0",
                 "qemuKernel": "0", "characteristics": "tv"}
        for changes in [{}, {"characteristics": "automotive"},
                        {"characteristics": "phone", "apiLevel": ""},
                        {"characteristics": "watch", "apiLevel": ""},
                        {"characteristics": "watch", "model": ""}]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                collector.physical_role("device", {**props, **changes})


if __name__ == "__main__":
    unittest.main()
