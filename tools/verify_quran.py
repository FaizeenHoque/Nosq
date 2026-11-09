#!/usr/bin/env python3
"""Compare bundled editions and references with their published API responses.

Run with --source-dir DIRECTORY containing quran-uthmani.json, en.sahih.json,
bn.bengali.json and en.transliteration.json, or --download to retrieve them.
The script never modifies Quran assets.
"""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/quran"
EDITIONS = {"quran-uthmani": "ar.txt", "en.sahih": "en.sahih.txt",
            "bn.bengali": "bn.bengali.txt", "en.transliteration": "en.transliteration.txt"}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--source-dir", type=Path)
    group.add_argument("--download", action="store_true")
    args = parser.parse_args()
    for edition, filename in EDITIONS.items():
        if args.download:
            with urllib.request.urlopen(f"https://api.alquran.cloud/v1/quran/{edition}", timeout=60) as response:
                payload = json.load(response)
        else:
            payload = json.loads((args.source_dir / f"{edition}.json").read_text())
        surahs = payload["data"]["surahs"]
        actual = (ROOT / filename).read_text().splitlines()
        published = []
        metadata = []
        for surah in surahs:
            for ayah in surah["ayahs"]:
                text = ayah["text"]
                if edition == "quran-uthmani":
                    text = text.lstrip("\ufeff")
                    if surah["number"] not in (1, 9) and ayah["numberInSurah"] == 1:
                        # API includes an unnumbered introductory Bismillah in
                        # these first ayahs. The app renders it in the header.
                        # The API spelling has a shadda on bāʾ for Surah 95.
                        words = text.split(" ", 4)
                        assert len(words) == 5
                        assert words[1:4] == actual[0].split(" ")[1:4]
                        text = words[4]
                published.append(text)
                metadata.append((surah["number"], ayah["numberInSurah"], ayah["juz"]))
        assert len(surahs) == 114 and len(published) == 6236
        differences = [i + 1 for i, (a, b) in enumerate(zip(actual, published)) if a != b]
        assert len(actual) == len(published) and not differences, (edition, differences)
        if edition == "quran-uthmani":
            bundled = [tuple(map(int, row.split("\t")[:3])) for row in (ROOT / "meta.tsv").read_text().splitlines()]
            assert bundled == metadata
        print(f"PASS {edition}: 6,236 exact verse matches")
    for filename, expected in json.loads((ROOT / "integrity.json").read_text()).items():
        assert hashlib.sha256((ROOT / filename).read_bytes()).hexdigest() == expected, filename
    print("PASS references, Juz metadata and asset integrity")

if __name__ == "__main__":
    main()
