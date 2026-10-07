#!/usr/bin/env python3
"""Read-only owned-emulator inventory; exit 2 means pairing prerequisites are missing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

APP = "app.personal.workouttracker"
OWNED = {"phone": "Pasingot_Pair_Phone", "wear": "Pasingot_Timed_UI"}
COMPANIONS = {"com.google.android.apps.wear.companion", "com.google.android.wearable.app"}
ROOT = Path(__file__).resolve().parents[1]


class Adb:
    def __init__(self, executable):
        self.executable = executable

    def call(self, serial, *args, timeout=30):
        command = [self.executable, "-s", serial, *args]
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(f"ADB failed for {serial}: {result.stderr.strip() or result.stdout.strip()}")
        return result.stdout


def guard_owned(adb, serial, role):
    if not re.fullmatch(r"emulator-\d+", serial):
        raise ValueError("This preflight supports owned emulators only")
    names = adb.call(serial, "emu", "avd", "name").splitlines()
    if not names:
        raise ValueError("Missing emulator profile identity")
    name = names[0].strip()
    if name != OWNED[role]:
        raise ValueError(f"Refusing {serial}: expected {OWNED[role]}, observed {name}")
    return name


def validate_inventory(value, role):
    if not isinstance(value, dict) or value.get("schemaVersion") != 1:
        raise ValueError("Unsupported native readiness schema")
    if (value.get("role"), value.get("avdName"), value.get("packageName")) != (role, OWNED[role], APP):
        raise ValueError("Native inventory role/AVD/package binding mismatch")
    signatures = value.get("signatureSha256")
    if not isinstance(signatures, list) or not signatures or any(
        not isinstance(s, str) or not re.fullmatch(r"[0-9a-f]{64}", s) for s in signatures
    ):
        raise ValueError("Invalid native signing certificate inventory")
    local = value.get("localNodeId")
    if local is not None and (not isinstance(local, str) or not local or len(local) > 256):
        raise ValueError("Invalid local node ID")
    if type(value.get("apiAvailable")) is not bool or not isinstance(value.get("errors"), list):
        raise ValueError("Missing native API availability/errors")
    peers = value.get("connectedPeers")
    if not isinstance(peers, list):
        raise ValueError("Missing native connected peers")
    for peer in peers:
        if (not isinstance(peer, dict) or not isinstance(peer.get("id"), str)
            or not peer["id"] or len(peer["id"]) > 256 or type(peer.get("nearby")) is not bool):
            raise ValueError("Invalid connected peer inventory")
    return value


def evaluate_pair(phone, wear, companion_installed):
    """Require exact reciprocal local peers, matching app signatures and current installed builds."""
    for role, value in [("phone", phone), ("wear", wear)]:
        validate_inventory(value, role)
    blockers = []
    for role, current, opposite in [("phone", phone, wear), ("wear", wear, phone)]:
        if not current["apiAvailable"] or current["errors"]:
            blockers.append(f"{role}: Wearable API unavailable; see native errors")
        if not current["localNodeId"]:
            blockers.append(f"{role}: local Wearable node missing")
        if current.get("installedApkMatches") is not True:
            blockers.append(f"{role}: installed APK does not match current local build")
        expected = opposite["localNodeId"]
        if (not expected or [p["id"] for p in current["connectedPeers"]] != [expected]
            or not all(p["nearby"] for p in current["connectedPeers"])):
            blockers.append(f"{role}: exact sole nearby counterpart is not connected")
    if sorted(phone["signatureSha256"]) != sorted(wear["signatureSha256"]):
        blockers.append("phone/watch signing certificates differ")
    if not companion_installed and blockers:
        blockers.append("phone: watch companion is not installed; complete official companion setup")
    return {"transportReady": not blockers, "acceptanceValidated": False, "blockers": blockers,
            "companionInstalled": companion_installed,
            "phoneNodeId": phone["localNodeId"], "wearNodeId": wear["localNodeId"]}


def validate_capture_time(value, before_seconds, after_seconds):
    observed = value.get("observedAtMillis")
    if type(observed) is not int or not before_seconds * 1000 <= observed <= after_seconds * 1000 + 999:
        raise ValueError("Native inventory is stale or outside this capture window")


def collect(adb, serial, role, output):
    guard_owned(adb, serial, role)
    namespace = "app.personal.workouttracker.quickstart" if role == "phone" else "app.personal.workouttracker.wear.quickstart"
    before_seconds = int(adb.call(serial, "shell", "date", "+%s").strip())
    log = adb.call(serial, "shell", "am", "instrument", "-w", "-e", "class",
                   namespace + ".PairingReadinessTest", "-e", "pairingReadinessValidation", "true",
                   APP + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=90)
    (output / f"{role}-instrumentation.log").write_text(log)
    if "OK (1 test)" not in log or "FAILURES!!!" in log or "INSTRUMENTATION_FAILED" in log:
        raise RuntimeError(f"{role} inventory did not execute successfully; inspect its log")
    raw = adb.call(serial, "shell", "cat", f"/sdcard/Android/data/{APP}/files/pairing-readiness/latest.json")
    value = validate_inventory(json.loads(raw), role)
    after_seconds = int(adb.call(serial, "shell", "date", "+%s").strip())
    validate_capture_time(value, before_seconds, after_seconds)
    paths = adb.call(serial, "shell", "pm", "path", APP).strip().splitlines()
    if len(paths) != 1 or not paths[0].startswith("package:"):
        raise ValueError("Expected one standalone installed base APK")
    path = paths[0].removeprefix("package:")
    if not re.fullmatch(r"/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk", path):
        raise ValueError("Unexpected installed APK path")
    digest_fields = adb.call(serial, "shell", "sha256sum", path).split()
    if not digest_fields or not re.fullmatch(r"[0-9a-f]{64}", digest_fields[0]):
        raise ValueError("Invalid installed APK digest")
    installed = digest_fields[0]
    local = ROOT / ("android/app/build/outputs/apk/debug/app-debug.apk" if role == "phone"
                    else "android/wear/build/outputs/apk/debug/wear-debug.apk")
    expected = hashlib.sha256(local.read_bytes()).hexdigest()
    value.update(installedApkSha256=installed, localApkSha256=expected, installedApkMatches=installed == expected)
    (output / f"{role}.json").write_text(json.dumps(value, indent=2) + "\n")
    return value


def main(argv=None):
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or Path.home() / "Library/Android/sdk")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=str(sdk / "platform-tools/adb"))
    parser.add_argument("--phone-serial", required=True)
    parser.add_argument("--wear-serial", required=True)
    parser.add_argument("--output", type=Path, required=True, help="New evidence directory; existing directories are refused")
    args = parser.parse_args(argv)
    adb = Adb(args.adb)
    try:
        if args.phone_serial == args.wear_serial:
            raise ValueError("Phone and watch must be different devices")
        for role, serial in [("phone", args.phone_serial), ("wear", args.wear_serial)]:
            guard_owned(adb, serial, role)
        output = args.output.resolve()
        output.mkdir(parents=True, exist_ok=False)
        phone = collect(adb, args.phone_serial, "phone", output)
        wear = collect(adb, args.wear_serial, "wear", output)
        packages = adb.call(args.phone_serial, "shell", "pm", "list", "packages")
        (output / "phone-packages.txt").write_text(packages)
        installed = {line.removeprefix("package:").strip() for line in packages.splitlines()}
        report = evaluate_pair(phone, wear, bool(installed & COMPANIONS))
        (output / "readiness.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2))
        return 0 if report["transportReady"] else 2
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        print(f"Preflight failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
