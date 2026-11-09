#!/usr/bin/env python3
"""Run release-equivalent benchmarks on an unlocked, idle physical device.
The .benchmark package is separate from the user's installed application.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TARGET = "dev.faizeenhoque.nosquran.benchmark"
RUNNER = "dev.faizeenhoque.nosquran.benchmarktests/androidx.test.runner.AndroidJUnitRunner"
REMOTE = "/sdcard/Android/media/dev.faizeenhoque.nosquran.benchmarktests/"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default=os.path.expanduser("~/Android/Sdk/platform-tools/adb"))
    parser.add_argument("--output", type=Path, default=ROOT / "build/performance/run")
    parser.add_argument("--generate-profile", action="store_true")
    parser.add_argument("--allow-low-battery", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    adb = [args.adb, "-s", args.serial]
    def run(command):
        subprocess.run(command, cwd=ROOT, check=True)
    def shell(*command):
        return subprocess.check_output(adb + ["shell", *command], text=True)
    battery = shell("dumpsys", "battery")
    (args.output / "battery.txt").write_text(battery)
    level = re.search(r"level:\s*(\d+)", battery)
    if level and int(level[1]) < 30 and not args.allow_low_battery:
        raise SystemExit("Charge the phone above 30%, or explicitly use --allow-low-battery for exploratory data.")
    def install():
        for apk in ["app/build/outputs/apk/benchmark/app-benchmark.apk", "benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk"]:
            run(adb + ["install", "-r", str(ROOT / apk)])
    def instrument(name, log):
        command = ["am", "instrument", "-w", "-e", "class", name]
        if args.allow_low_battery:
            command += ["-e", "androidx.benchmark.suppressErrors", "LOW-BATTERY"]
        result = shell(*command, RUNNER)
        (args.output / log).write_text(result)
        if "OK (" not in result or "FAILURES!!!" in result:
            raise SystemExit(f"Benchmark failed: inspect {args.output / log}")
    if args.generate_profile:
        run(["./gradlew", ":app:assembleBenchmark", ":benchmark:assembleBenchmark", "-Pnos.profileGeneration=true"])
        install()
        instrument("dev.faizeenhoque.nosquran.benchmark.GenerateBaselineProfile", "profile.log")
        raw = args.output / "observed-profile.txt"
        run(adb + ["pull", REMOTE + "GenerateBaselineProfile_generate-startup-prof.txt", str(raw)])
        profiles = ROOT / "app/src/main/baselineProfiles"
        profiles.mkdir(exist_ok=True)
        lines = raw.read_text().splitlines()
        (profiles / "baseline-prof.txt").write_text("\n".join(lines) + "\n")
        (profiles / "startup-prof.txt").write_text("\n".join(x for x in lines if "Ldev/faizeenhoque/nosquran/" in x) + "\n")
    run(["./gradlew", ":app:assembleRelease", ":app:assembleBenchmark", ":benchmark:assembleBenchmark"])
    install()
    (args.output / "thermal-before.txt").write_text(shell("dumpsys", "thermalservice"))
    instrument("dev.faizeenhoque.nosquran.benchmark.QuranBenchmarks", "benchmarks.log")
    run(adb + ["pull", REMOTE + "dev.faizeenhoque.nosquran.benchmarktests-benchmarkData.json", str(args.output / "metrics.json")])
    (args.output / "thermal-after.txt").write_text(shell("dumpsys", "thermalservice"))
    (args.output / "memory.txt").write_text(shell("dumpsys", "meminfo", TARGET))
    results = json.loads((args.output / "metrics.json").read_text())
    print("Completed:", ", ".join(b["name"] for b in results["benchmarks"]))
    print("Metrics and Perfetto file references:", args.output)

if __name__ == "__main__":
    main()
