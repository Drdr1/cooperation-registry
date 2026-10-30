#!/usr/bin/env python3
"""Turn Maven/Surefire failures into GitHub annotations so they show on the run summary."""
import glob
import re
import sys
import xml.etree.ElementTree as ET

MAX_ANNOTATIONS = 9
LINES_PER_ANNOTATION = 45


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def build_log_errors(path: str) -> list[str]:
    try:
        lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
    except FileNotFoundError:
        return []
    seen, out = set(), []
    for line in lines:
        if line.startswith("[ERROR]") and line not in seen:
            seen.add(line)
            out.append(re.sub(r"/home/runner/work/[^/]+/[^/]+/", "", line))
    return out


def surefire_failures() -> list[str]:
    out = []
    for report in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is None:
                    continue
                out.append(f"== {case.get('classname', '').split('.')[-1]}.{case.get('name')} ({kind})")
                out.append((node.get("message") or "").strip()[:600])
                trace = (node.text or "").splitlines()
                causes = [t.strip() for t in trace if t.strip().startswith("Caused by:")]
                frames = [t.strip() for t in trace if "dev.derops" in t][:4]
                out.extend(c[:400] for c in causes[-3:])
                out.extend(frames)
    return out


def main() -> None:
    lines = surefire_failures() + build_log_errors(sys.argv[1] if len(sys.argv) > 1 else "build.log")
    if not lines:
        print("::error title=Build failed::No details found in build.log or surefire reports")
        return
    chunks = [lines[i:i + LINES_PER_ANNOTATION] for i in range(0, len(lines), LINES_PER_ANNOTATION)]
    for n, chunk in enumerate(chunks[:MAX_ANNOTATIONS], start=1):
        print(f"::error title=Build failure part {n}/{len(chunks)}::{escape(chr(10).join(chunk))}")


if __name__ == "__main__":
    main()
