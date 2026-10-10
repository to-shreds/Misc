"""Generate the GitHub Pages entry point from the shared control template."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check that index.html matches the template without writing.")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    source = (root / "templates" / "control.html").read_bytes()
    target = root / "index.html"
    if args.check:
        if not target.exists() or target.read_bytes() != source:
            parser.exit(1, "index.html is out of date. Run python build_public.py.\n")
        print("index.html matches templates/control.html.")
        return
    temporary = root / "index.html.tmp"
    temporary.write_bytes(source)
    temporary.replace(target)
    print("Generated index.html from templates/control.html.")


if __name__ == "__main__":
    main()
