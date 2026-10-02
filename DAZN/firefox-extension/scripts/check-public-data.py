#!/usr/bin/env python3
"""Check actual public schemas with the extension's JS sanitizer, without retaining raw data.

Requires Python 3 and Node.js. Performs exactly one official NHL season request and
one neutral DAZN search request. Stdout contains aggregate counts only. This is not
a Firefox, authenticated-session, entitlement, DRM, or playback acceptance test.
"""
import argparse
import datetime
import gzip
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import urllib.request

MAX_BYTES = 2 * 1024 * 1024
ROOT = Path(__file__).resolve().parent.parent
SEARCH_URL = "https://search.discovery.indazn.com/v1/search?searchTerm=Buffalo&country=gb&brand=dazn"
COUNT_KEYS = ("neutralGames", "matchedGames", "totalCompatibleVariants")
JS_CHECK = r"""
'use strict';
try {
  const fs = require('node:fs');
  const path = require('node:path');
  const directory = process.argv[1];
  const input = JSON.parse(fs.readFileSync(0, 'utf8'));
  const schedule = require(path.join(directory, 'src/content/schedule.js'));
  require(path.join(directory, 'src/content/route-resolver.js'));
  const catalogue = require(path.join(directory, 'src/content/catalogue.js'));
  if (!Array.isArray(input.schedule.games)) throw new Error();
  const games = schedule.sanitize(input.schedule);
  const results = games.map(game => catalogue.match(input.catalogue, game));
  if (results.some(result => result.status === 'unsupported')) throw new Error();
  const matched = results.filter(result => result.status === 'matched');
  process.stdout.write(JSON.stringify({
    neutralGames: games.length,
    matchedGames: matched.length,
    totalCompatibleVariants: matched.reduce((total, result) => total + result.variants.length, 0)
  }));
} catch (_) { process.exitCode = 1; }
"""


def read_public_json(url):
    request = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0",
        "Accept": "application/json",
        "Referer": "https://www.dazn.com/",
    })
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 200 or response.geturl() != url:
            raise ValueError()
        payload = response.read(MAX_BYTES + 1)
    if len(payload) > MAX_BYTES:
        raise ValueError()
    if payload[:2] == b"\x1f\x8b":
        with gzip.GzipFile(fileobj=io.BytesIO(payload)) as compressed:
            payload = compressed.read(MAX_BYTES + 1)
    if len(payload) > MAX_BYTES:
        raise ValueError()
    return json.loads(payload)


def main():
    now = datetime.datetime.now(datetime.timezone.utc)
    first_year = now.year - (now.month < 7)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--season", default=f"{first_year}{first_year + 1}",
                        help="NHL season, e.g. 20262027; defaults to the current season")
    args = parser.parse_args()
    if not re.fullmatch(r"20\d{2}20\d{2}", args.season) or int(args.season[4:]) != int(args.season[:4]) + 1:
        print("public-data-invalid-season", file=sys.stderr)
        return 1
    try:
        schedule = read_public_json("https://api-web.nhle.com/v1/club-schedule-season/BUF/" + args.season)
        catalogue = read_public_json(SEARCH_URL)
        result = subprocess.run(["node", "-e", JS_CHECK, str(ROOT)],
                                input=json.dumps({"schedule": schedule, "catalogue": catalogue}),
                                text=True, capture_output=True, timeout=20, check=False)
        if result.returncode:
            raise ValueError()
        counts = json.loads(result.stdout)
        if set(counts) != set(COUNT_KEYS) or any(type(counts[key]) is not int or counts[key] < 0 for key in COUNT_KEYS):
            raise ValueError()
        print(json.dumps({key: counts[key] for key in COUNT_KEYS}))
        return 0
    except Exception:
        # Never print provider response bodies, URLs, exception messages or raw input.
        print("public-data-check-unavailable", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
