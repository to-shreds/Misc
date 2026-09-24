"""Build the offline single-file app from the canonical hosted source. No dependencies."""
from pathlib import Path
ROOT = Path(__file__).resolve().parent
text = (ROOT / "index.html").read_text(encoding="utf-8")
text = text.replace('<link rel="stylesheet" href="./style.css">',
                    "<style>" + (ROOT / "style.css").read_text(encoding="utf-8") + "</style>")
for filename in ("core.js", "zip.js", "app.js"):
    text = text.replace(f'<script src="./{filename}"></script>',
                        "<script>" + (ROOT / filename).read_text(encoding="utf-8") + "</script>")
assert "\u2014" not in text, "Unexpected em dash"
(ROOT / "standalone.html").write_text(text, encoding="utf-8")
print(f"Built standalone.html ({len(text.encode('utf-8')):,} bytes)")
