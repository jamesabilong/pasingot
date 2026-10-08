#!/usr/bin/env python3
"""Read-only physical inventory. Exit 2: not ready; 1: collection failed.

Requires explicit serials, preserves existing evidence, and never starts a
workout, installs an APK, changes settings or infers paired acceptance.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = "app.personal.workouttracker"
PROPERTIES = {
    "model": "ro.product.model", "manufacturer": "ro.product.manufacturer",
    "hardware": "ro.hardware", "characteristics": "ro.build.characteristics",
    "osRelease": "ro.build.version.release", "apiLevel": "ro.build.version.sdk",
    "qemuKernel": "ro.kernel.qemu", "qemuBoot": "ro.boot.qemu",
}


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "Command failed")
    return result.stdout


def devices(text):
    return dict(re.findall(r"^(\S+)\s+(device|offline|unauthorized|no permissions)\b", text, re.M))


def physical_role(serial, properties):
    if (serial.startswith("emulator-") or properties["qemuKernel"] == "1"
            or properties["qemuBoot"] == "1" or properties["hardware"] in {"ranchu", "goldfish"}):
        raise ValueError("Physical collector refuses emulators")
    traits = set(properties["characteristics"].split(","))
    if traits & {"tv", "automotive"} or not properties["model"] or not properties["apiLevel"].isdigit():
        raise ValueError("Unsupported or incomplete device identity")
    return "watch" if "watch" in traits else "phone"


def collect(adb, serial, role, output, include_hash):
    def read(*args):
        return run([adb, "-s", serial, *args])

    props = {key: read("shell", "getprop", value).strip() for key, value in PROPERTIES.items()}
    if physical_role(serial, props) != role:
        raise ValueError(f"Selected {role} serial has the wrong device role")
    result = {"serial": serial, "role": role, **props}
    for name, args in {
        "battery": ("shell", "dumpsys", "battery"),
        "package": ("shell", "dumpsys", "package", APP),
        "apk-paths": ("shell", "pm", "path", APP),
    }.items():
        value = read(*args)
        (output / f"{role}-{name}.txt").write_text(value)
        result[name] = value
    result["appInstalled"] = bool(re.search(r"\bversionCode=\d+", result["package"]))
    if include_hash and result["appInstalled"]:
        paths = result["apk-paths"].splitlines()
        bases = [p.removeprefix("package:") for p in paths
                 if re.fullmatch(r"package:/[A-Za-z0-9_./=~+@-]+/base\.apk", p)]
        if len(bases) != 1:
            raise ValueError("Cannot identify exactly one installed base APK")
        digest = read("shell", "sha256sum", bases[0]).split()
        if not digest or not re.fullmatch(r"[0-9a-fA-F]{64}", digest[0]):
            raise ValueError("Invalid installed base APK hash")
        result["installedBaseApkSha256"] = digest[0].lower()
        result["hasSplits"] = len(paths) != 1
        module = "app" if role == "phone" else "wear"
        local = ROOT / f"android/{module}/build/outputs/apk/debug/{module}-debug.apk"
        result["localDebugApkSha256"] = hashlib.sha256(local.read_bytes()).hexdigest() if local.is_file() else None
        # A base-only comparison never asserts equality of an entire split install.
        result["installedApkMatches"] = (not result["hasSplits"] and
            result["installedBaseApkSha256"] == result["localDebugApkSha256"])
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--phone-serial", required=True)
    parser.add_argument("--watch-serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--include-installed-hash", action="store_true")
    args = parser.parse_args(argv)
    report = {"schemaVersion": 1, "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
              "readiness": "collection_failed", "acceptance": "not_evaluated",
              "transportReady": None, "devices": [], "errors": []}
    created = False
    try:
        for serial in [args.phone_serial, args.watch_serial]:
            if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}", serial):
                raise ValueError("Invalid serial")
        if args.phone_serial == args.watch_serial:
            raise ValueError("Phone and watch serials must differ")
        args.output.mkdir(parents=True, exist_ok=False)
        created = True
        report["gitHead"] = run(["git", "-C", str(ROOT), "rev-parse", "HEAD"]).strip()
        report["workingTreeChanges"] = run(["git", "-C", str(ROOT), "status", "--porcelain"]).splitlines()
        inventory = run([args.adb, "devices", "-l"])
        (args.output / "adb-devices.txt").write_text(inventory)
        attached = devices(inventory)
        missing = [s for s in [args.phone_serial, args.watch_serial] if attached.get(s) != "device"]
        if missing:
            report["readiness"] = "selected_devices_unavailable"
            report["errors"] = [f"{s}: {attached.get(s, 'missing')}" for s in missing]
            return 2
        # Capture only the selected devices; never probe unrelated connected devices.
        for role, serial in [("phone", args.phone_serial), ("watch", args.watch_serial)]:
            report["devices"].append(collect(args.adb, serial, role, args.output, args.include_installed_hash))
        report["readiness"] = ("physical_inventory_ready" if all(d["appInstalled"] for d in report["devices"])
                               else "app_missing")
        return 0 if report["readiness"] == "physical_inventory_ready" else 2
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        report["errors"].append(str(error))
        print(f"Collection failed: {error}", file=sys.stderr)
        return 1
    finally:
        if created:
            (args.output / "inventory.json").write_text(json.dumps(report, indent=2) + "\n")
            print(f"Readiness: {report['readiness']}\nEvidence: {args.output / 'inventory.json'}")


if __name__ == "__main__":
    sys.exit(main())
