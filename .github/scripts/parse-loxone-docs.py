#!/usr/bin/env python3
"""
Loxone Structure File PDF parser.

Downloads the official Loxone Structure File PDF, extracts the version from
the page footers, and parses the document into structured JSON.

The TOC is parsed first to get a definitive list of all section titles.
Those titles are then used as anchors when walking the body text.

Output JSON structure:
{
  "version": "16.0",
  "sourceUrl": "...",
  "parsedAt": "...",
  "generalSections": [
    {
      "name": "General Info",
      "description": "...",
      "fields": [{"name": "...", "description": "...", "enumValues": {...}}],
      "subsections": [...]
    },
    ...
  ],
  "controls": [
    {
      "name": "Switch",
      "description": "...",
      "coveredConfigItems": "...",
      "details": [{"name": "...", "description": "..."}],
      "states": [{"name": "...", "description": "...", "enumValues": {...}}],
      "commands": [{"name": "...", "description": "..."}],
      "subsections": [...]
    },
    ...
  ]
}

Environment variables:
    PDF_CACHE   If set, caches the downloaded PDF at this path (useful for local dev).
    DOCS_FORCE  If set to "true", re-parses and overwrites even if the version already exists.
"""

from __future__ import annotations

import io
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

import pdfplumber
import requests

LOXONE_DOCS_URL = "https://www.loxone.com/wp-content/uploads/datasheets/StructureFile.pdf"
OUTPUT_DIR = Path("loxone-docs")
DOWNLOAD_TIMEOUT = 60

# Matches footer lines like "Structure File 16.0 Page 3 of 174"
FOOTER_VERSION_RE = re.compile(r"Structure\s+File\s+(\d+\.\d+)", re.IGNORECASE)
# TOC entry: title followed by dots and a page number
TOC_ENTRY_RE = re.compile(r"^(.+?)\s*\.{3,}\s*(\d+)\s*$")
# Standalone version number line (footer artifact)
STANDALONE_VERSION_RE = re.compile(r"^\d+\.\d+\s*$")

# Internal mode headers that should NOT be treated as section boundaries
# These appear inside control sections as sub-headers
MODE_HEADERS = {"States", "@States", "Details", "Commands", "Covered Config Items"}

# Bullet chars: standard unicode + Wingdings PUA chars from pdfplumber
BULLET_CHARS = ["●", "○", "■", "•", "\uf0cf", "\uf0cb", "—‹", "—", "–", "ƒ"]


# ---------------------------------------------------------------------------
# Download
# ---------------------------------------------------------------------------

def download_pdf(url: str) -> bytes:
    print(f"Downloading PDF from {url}...")
    response = requests.get(url, timeout=DOWNLOAD_TIMEOUT)
    response.raise_for_status()
    print(f"Downloaded {len(response.content):,} bytes")
    return response.content


# ---------------------------------------------------------------------------
# PDF text extraction
# ---------------------------------------------------------------------------

def extract_pages(pdf_bytes: bytes) -> tuple:
    """Return (page_texts, detected_version)."""
    pages: list[str] = []
    detected_version = None

    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        for page in pdf.pages:
            raw = page.extract_text() or ""
            all_lines = raw.splitlines()
            clean_lines: list[str] = []

            if detected_version is None:
                for idx, line in enumerate(all_lines):
                    stripped = line.strip()
                    m = FOOTER_VERSION_RE.search(stripped)
                    if m:
                        detected_version = m.group(1)
                        print(f"Detected version from footer (combined): {detected_version}")
                        break
                    if STANDALONE_VERSION_RE.match(stripped):
                        if idx + 1 < len(all_lines):
                            next_line = all_lines[idx + 1].strip()
                            if "Structure File" in next_line and "Page" in next_line:
                                detected_version = stripped
                                print(f"Detected version from footer (standalone): {detected_version}")
                                break

            for line in all_lines:
                stripped = line.strip()
                if FOOTER_VERSION_RE.search(stripped):
                    continue
                if "Structure File" in stripped and "Page" in stripped:
                    continue
                if STANDALONE_VERSION_RE.match(stripped):
                    continue
                if stripped:
                    clean_lines.append(stripped)
            pages.append("\n".join(clean_lines))

    return pages, detected_version


# ---------------------------------------------------------------------------
# Bullet normalisation
# ---------------------------------------------------------------------------

def normalize_bullets(line: str) -> str:
    for bullet in BULLET_CHARS:
        line = line.replace(bullet, "-")
    return re.sub(r" +", " ", line)


# ---------------------------------------------------------------------------
# TOC parsing
# ---------------------------------------------------------------------------

def parse_toc(pages: list[str]) -> list[tuple[str, int]]:
    """Parse the TOC and return (section_name, indent_level) tuples."""
    in_toc = False
    entries: list[tuple[str, int]] = []

    for page_text in pages:
        lines = page_text.splitlines()
        for line in lines:
            stripped = line.strip()
            if stripped == "Table of contents":
                in_toc = True
                continue
            if in_toc and stripped == "AalEmergency":
                in_toc = False
                break
            if not in_toc:
                continue

            m = TOC_ENTRY_RE.match(stripped)
            if not m:
                continue
            title = m.group(1).strip()
            if not title:
                continue

            leading = len(line) - len(line.lstrip())
            if leading == 0:
                level = 0
            elif leading <= 4:
                level = 1
            else:
                level = 2

            entries.append((title, level))

    print(f"TOC: found {len(entries)} entries")
    return entries


def classify_toc(toc_entries: list[tuple[str, int]]) -> tuple[list[str], list[str]]:
    """
    Split TOC entries into general section names and control type names.

    Everything from "Control Types" onward (excluding "Revision History" and later)
    is a control type. Sub-headers like "Commands", "States" etc. that appear in
    the TOC are excluded from section names entirely (they're mode switches).
    """
    general_names: list[str] = []
    control_names: list[str] = []
    in_controls = False
    past_controls = False

    for name, level in toc_entries:
        if name in MODE_HEADERS:
            continue
        if name == "Control Types":
            in_controls = True
            continue
        if name == "Revision History":
            past_controls = True
            continue

        if past_controls:
            continue
        elif in_controls:
            control_names.append(name)
        else:
            general_names.append(name)

    print(f"Classification: {len(general_names)} general sections, {len(control_names)} control types")
    return general_names, control_names


# ---------------------------------------------------------------------------
# Body text assembly
# ---------------------------------------------------------------------------

def get_body_lines(pages: list[str]) -> list[str]:
    """Get all body lines, skipping the TOC pages."""
    all_lines = "\n".join(pages).splitlines()
    for i, line in enumerate(all_lines):
        stripped = line.strip()
        if stripped == "General Info" and not TOC_ENTRY_RE.match(stripped):
            return [l.strip() for l in all_lines[i:] if l.strip()]
    return [l.strip() for l in all_lines if l.strip()]


# ---------------------------------------------------------------------------
# Field / command line helpers
# ---------------------------------------------------------------------------

def is_field_line(line: str) -> bool:
    """
    A field/state starts with '- lowerCamelCase' (no spaces in name part).
    Rejects enum lines, JSON sub-field artifacts (ending with ':'),
    and JSON structure chars like [, {, 82, etc.
    """
    if not line.startswith("- "):
        return False
    if is_enum_line(line):
        return False
    name = line[2:].strip()
    if not name:
        return False
    # Must start with a lowercase ASCII letter (rejects JSON artifacts like [, {, digits)
    if not name[0].isascii() or not name[0].islower():
        return False
    if name.endswith(":"):
        return False
    if " " in name or len(name) >= 60:
        return False
    return True


def is_command_line(line: str, current_cmd_name: str | None = None) -> bool:
    """
    A command starts with '- camelCase' or '- PascalCase' possibly followed by /{param}.
    E.g. '- on', '- FullUp', '- setSensor/{sensorIndex}/{targetTemp}/{sensorName}'
    Commands can start with upper or lowercase (unlike fields which are always lower).

    If current_cmd_name is provided, rejects parameter names that match /{name} in the
    current command's path template.
    """
    if not line.startswith("- "):
        return False
    if is_enum_line(line):
        return False
    name = line[2:].strip()
    if not name:
        return False
    # Must start with an ASCII letter
    if not name[0].isascii() or not name[0].isalpha():
        return False
    if name.endswith(":"):
        return False
    if " " in name or len(name) >= 80:
        return False
    # If this looks like a parameter of the current command, reject it.
    # E.g. current_cmd = "setSensor/{sensorIndex}/{targetTemp}/{sensorName}"
    # and name = "sensorIndex" → reject
    if current_cmd_name and "/" in current_cmd_name:
        param_names = re.findall(r"\{(\w+)\}", current_cmd_name)
        if name in param_names:
            return False
    return True


def is_enum_line(line: str) -> bool:
    return bool(re.match(r"^-?\s*\d+\s*=", line))


def parse_enum_line(line: str) -> tuple[int, str]:
    cleaned = line.lstrip("-").strip()
    parts = cleaned.split("=", 1)
    return int(parts[0].strip()), parts[1].strip()


# ---------------------------------------------------------------------------
# Section splitting
# ---------------------------------------------------------------------------

def split_into_sections(
    body_lines: list[str],
    section_names: set[str],
) -> list[dict]:
    """
    Split body_lines into sections using section_names as boundary anchors.
    Mode headers (States, Commands, Details, etc.) are kept INSIDE
    their parent section's lines, not treated as new sections.
    """
    flat: list[dict] = []
    current: dict | None = None

    for line in body_lines:
        norm = normalize_bullets(line)
        if norm in section_names and norm not in MODE_HEADERS:
            if current is not None:
                flat.append(current)
            current = {"name": norm, "lines": []}
        elif current is not None:
            current["lines"].append(norm)

    if current is not None:
        flat.append(current)

    return flat


# ---------------------------------------------------------------------------
# General section parser
# ---------------------------------------------------------------------------

def parse_general_section(name: str, lines: list[str]) -> dict:
    """
    Parse a general (non-control) section.

    For non-control sections, we prioritise capturing all text content.
    We try to extract fields when the format is obvious, but all remaining
    text is dumped into the description rather than being lost.
    """
    section: dict = {
        "name": name,
        "description": [],
        "fields": [],
    }

    current_field = None

    def flush_field():
        nonlocal current_field
        if current_field:
            entry: dict = {
                "name": current_field["name"],
                "description": " ".join(current_field["desc"]),
            }
            if current_field["enums"]:
                entry["enumValues"] = current_field["enums"]
            section["fields"].append(entry)
        current_field = None

    for line in lines:
        if is_field_line(line):
            flush_field()
            current_field = {"name": line[2:].strip(), "desc": [], "enums": {}}
        elif is_enum_line(line) and current_field:
            val, label = parse_enum_line(line)
            current_field["enums"][str(val)] = label
        elif current_field:
            cleaned = line.lstrip("-").strip()
            if cleaned:
                current_field["desc"].append(cleaned)
        else:
            # Dump everything into description - better to have text in wrong
            # place than to miss it entirely
            section["description"].append(line)

    flush_field()
    section["description"] = " ".join(section["description"]).strip()
    return section


# ---------------------------------------------------------------------------
# Control section parser
# ---------------------------------------------------------------------------

def parse_control_section(name: str, lines: list[str]) -> dict:
    """
    Parse a control type section with explicit mode headers.
    Uses the PDF's own section names: Details, States, Commands, Covered Config Items.
    """
    control: dict = {
        "name": name,
        "description": [],
        "details": [],
        "states": [],
        "commands": [],
    }

    mode = "TEXT"
    current_field = None
    current_cmd = None
    covered_lines: list[str] = []

    def flush_field():
        nonlocal current_field
        if current_field:
            entry: dict = {
                "name": current_field["name"],
                "description": " ".join(current_field["desc"]),
            }
            if current_field["enums"]:
                entry["enumValues"] = current_field["enums"]
            target = control["details"] if current_field["target"] == "details" else control["states"]
            target.append(entry)
        current_field = None

    def flush_cmd():
        nonlocal current_cmd
        if current_cmd:
            control["commands"].append({
                "name": current_cmd["name"],
                "description": " ".join(current_cmd["desc"]),
            })
        current_cmd = None

    for line in lines:
        if line == "Details":
            flush_field()
            flush_cmd()
            mode = "DETAILS"
            continue
        if line in ("States", "@States"):
            flush_field()
            flush_cmd()
            mode = "STATES"
            continue
        if line == "Commands":
            flush_field()
            flush_cmd()
            mode = "COMMANDS"
            continue
        if line == "Covered Config Items":
            flush_field()
            flush_cmd()
            mode = "COVERED"
            continue

        if mode == "TEXT":
            control["description"].append(line)

        elif mode == "COVERED":
            covered_lines.append(line)

        elif mode in ("DETAILS", "STATES"):
            target_key = "details" if mode == "DETAILS" else "states"
            if is_field_line(line):
                flush_field()
                current_field = {
                    "name": line[2:].strip(),
                    "desc": [],
                    "enums": {},
                    "target": target_key,
                }
            elif is_enum_line(line) and current_field:
                val, label = parse_enum_line(line)
                current_field["enums"][str(val)] = label
            elif current_field:
                cleaned = line.lstrip("-").strip()
                if cleaned:
                    current_field["desc"].append(cleaned)

        elif mode == "COMMANDS":
            cmd_name_for_check = current_cmd["name"] if current_cmd else None
            if is_command_line(line, cmd_name_for_check):
                flush_cmd()
                current_cmd = {"name": line[2:].strip(), "desc": []}
            elif current_cmd:
                cleaned = line.lstrip("-").strip()
                if cleaned:
                    current_cmd["desc"].append(cleaned)

    flush_field()
    flush_cmd()

    control["description"] = " ".join(control["description"]).strip()
    if covered_lines:
        control["coveredConfigItems"] = " ".join(covered_lines).strip()
    return control


# ---------------------------------------------------------------------------
# Main assembly
# ---------------------------------------------------------------------------

def build_document(body_lines: list[str], toc_entries: list[tuple[str, int]]) -> dict:
    """Build the full structured document from body lines and TOC."""
    general_names, control_names = classify_toc(toc_entries)

    all_section_names = set(general_names) | set(control_names)
    raw_sections = split_into_sections(body_lines, all_section_names)
    print(f"Split into {len(raw_sections)} raw sections")

    by_name: dict[str, list[dict]] = {}
    for sec in raw_sections:
        by_name.setdefault(sec["name"], []).append(sec)

    name_usage: dict[str, int] = {}

    general_sections = []
    for name in general_names:
        idx = name_usage.get(name, 0)
        candidates = by_name.get(name, [])
        if idx < len(candidates):
            raw = candidates[idx]
            parsed = parse_general_section(raw["name"], raw["lines"])
            general_sections.append(parsed)
        else:
            general_sections.append({"name": name, "description": "", "fields": []})
        name_usage[name] = idx + 1

    controls = []
    for name in control_names:
        idx = name_usage.get(name, 0)
        candidates = by_name.get(name, [])
        if idx < len(candidates):
            raw = candidates[idx]
            parsed = parse_control_section(raw["name"], raw["lines"])
            controls.append(parsed)
        else:
            controls.append({
                "name": name, "description": "",
                "details": [], "states": [], "commands": [],
            })
        name_usage[name] = idx + 1

    general_sections = nest_general_sections(general_sections, toc_entries, general_names)
    controls = nest_controls(controls, toc_entries, control_names)

    return {"generalSections": general_sections, "controls": controls}


def nest_general_sections(
    sections: list[dict],
    toc_entries: list[tuple[str, int]],
    names: list[str],
) -> list[dict]:
    """Nest general sections according to TOC indent levels."""
    toc_level: dict[str, int] = {}
    name_set = set(names)
    for n, lvl in toc_entries:
        if n not in toc_level and n in name_set:
            toc_level[n] = lvl

    by_name: dict[str, list[dict]] = {}
    for s in sections:
        by_name.setdefault(s["name"], []).append(s)
    usage: dict[str, int] = {}

    result: list[dict] = []
    stack: list[tuple[int, dict]] = []

    for name in names:
        level = toc_level.get(name, 0)
        idx = usage.get(name, 0)
        candidates = by_name.get(name, [])
        sec = candidates[idx] if idx < len(candidates) else {
            "name": name, "description": "", "fields": [],
        }
        usage[name] = idx + 1

        if level == 0:
            stack = [(0, sec)]
            result.append(sec)
        else:
            while len(stack) > 1 and stack[-1][0] >= level:
                stack.pop()
            parent = stack[-1][1]
            parent.setdefault("subsections", []).append(sec)
            stack.append((level, sec))

    return result


def nest_controls(
    controls: list[dict],
    toc_entries: list[tuple[str, int]],
    names: list[str],
) -> list[dict]:
    """Nest control subsections (e.g. BMW Wallbox under CarCharger)."""
    toc_level: dict[str, int] = {}
    name_set = set(names)
    for n, lvl in toc_entries:
        if n not in toc_level and n in name_set:
            toc_level[n] = lvl

    by_name: dict[str, list[dict]] = {}
    for c in controls:
        by_name.setdefault(c["name"], []).append(c)
    usage: dict[str, int] = {}

    result: list[dict] = []
    stack: list[tuple[int, dict]] = []

    for name in names:
        level = toc_level.get(name, 0)
        idx = usage.get(name, 0)
        candidates = by_name.get(name, [])
        ctrl = candidates[idx] if idx < len(candidates) else {
            "name": name, "description": "",
            "details": [], "states": [], "commands": [],
        }
        usage[name] = idx + 1

        if level == 0:
            stack = [(0, ctrl)]
            result.append(ctrl)
        else:
            while len(stack) > 1 and stack[-1][0] >= level:
                stack.pop()
            parent = stack[-1][1]
            parent.setdefault("subsections", []).append(ctrl)
            stack.append((level, ctrl))

    return result


# ---------------------------------------------------------------------------
# Versions index
# ---------------------------------------------------------------------------

def update_versions_index(docs_dir: Path) -> None:
    versions = []
    for f in docs_dir.glob("structure-file-*.json"):
        m = re.match(r"structure-file-(\d+\.\d+)\.json", f.name)
        if m:
            versions.append(m.group(1))

    def version_key(v: str) -> tuple[int, int]:
        parts = v.split(".")
        return int(parts[0]), int(parts[1])

    versions.sort(key=version_key)
    index_file = docs_dir / "versions.json"
    index_file.write_text(json.dumps(versions, indent=2) + "\n")
    print(f"Updated {index_file} with versions: {versions}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    pdf_cache = os.environ.get("PDF_CACHE", "").strip()

    if pdf_cache and Path(pdf_cache).exists():
        print(f"Using cached PDF: {pdf_cache}")
        pdf_bytes = Path(pdf_cache).read_bytes()
    else:
        pdf_bytes = download_pdf(LOXONE_DOCS_URL)
        if pdf_cache:
            Path(pdf_cache).write_bytes(pdf_bytes)
            print(f"Saved PDF cache to {pdf_cache}")

    pages, detected_version = extract_pages(pdf_bytes)

    version = detected_version
    if not version:
        print("ERROR: Could not detect version from PDF.", file=sys.stderr)
        sys.exit(1)
    if not re.match(r"^\d+\.\d+$", version):
        print(f"ERROR: Detected version '{version}' is not in X.Y format.", file=sys.stderr)
        sys.exit(1)

    print(f"Using docs version: {version}")

    # Write version to a file so the workflow can read it even when we skip
    Path(".detected_docs_version").write_text(version)

    force = os.environ.get("DOCS_FORCE", "").strip().lower() in ("1", "true", "yes")
    output_file = OUTPUT_DIR / f"structure-file-{version}.json"
    if output_file.exists() and not force:
        print(f"Docs for version {version} already exist at {output_file} — skipping parse.")
        print("Use DOCS_FORCE=true to force re-parse.")
        sys.exit(0)

    toc_entries = parse_toc(pages)
    if not toc_entries:
        print("ERROR: Could not parse Table of Contents from PDF.", file=sys.stderr)
        sys.exit(1)

    body_lines = get_body_lines(pages)
    document = build_document(body_lines, toc_entries)

    general_count = len(document["generalSections"])
    control_count = len(document["controls"])
    print(f"Parsed {general_count} general sections, {control_count} controls")

    cmds_total = sum(len(c.get("commands", [])) for c in document["controls"])
    states_total = sum(len(c.get("states", [])) for c in document["controls"])
    details_total = sum(len(c.get("details", [])) for c in document["controls"])
    print(f"Controls total: {states_total} states, {details_total} details, {cmds_total} commands")

    bundle = {
        "version": version,
        "sourceUrl": LOXONE_DOCS_URL,
        "parsedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "generalSectionCount": general_count,
        "controlCount": control_count,
        "generalSections": document["generalSections"],
        "controls": document["controls"],
    }

    OUTPUT_DIR.mkdir(exist_ok=True)
    output_file = OUTPUT_DIR / f"structure-file-{version}.json"
    output_file.write_text(json.dumps(bundle, indent=4, ensure_ascii=False) + "\n")
    print(f"Written to {output_file}")

    update_versions_index(OUTPUT_DIR)


if __name__ == "__main__":
    main()
