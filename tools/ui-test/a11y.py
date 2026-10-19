#!/usr/bin/env python3
"""Accessibility check of the screen showing now, from `uiautomator dump`: like Accessibility Scanner's two most
common findings, it lists clickable views with no spoken label and touch targets under 48 dp.

    python3 tools/ui-test/a11y.py [name]     # exits 1 if anything is flagged
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PKG = "dev.vulcanlauncher"


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=True).stdout


def findings(xml, density):
    out = []
    for n in ET.fromstring(xml).iter("node"):
        if n.get("package") != PKG or n.get("clickable") != "true" and n.get("long-clickable") != "true":
            continue
        l, t, r, b = map(int, re.findall(r"\d+", n.get("bounds")))
        w, h = (r - l) / density, (b - t) / density
        # A clickable container is labelled by the text of the views inside it, as TalkBack reads them.
        label = n.get("text") or n.get("content-desc") or any(c.get("text") or c.get("content-desc") for c in n.iter("node"))
        where = f"{n.get('class').split('.')[-1]} at {n.get('bounds')}"
        if not label:
            out.append(f"no label: {where}")
        if w < 47.5 or h < 47.5:
            out.append(f"small target {w:.0f}x{h:.0f} dp: {where} {n.get('text') or n.get('content-desc') or ''}".rstrip())
    return out


if __name__ == "__main__":
    density = int(re.search(r"(\d+)\s*$", adb("shell", "wm", "density").strip()).group(1)) / 160
    adb("shell", "uiautomator", "dump", "/data/local/tmp/a11y.xml")
    found = findings(adb("exec-out", "cat", "/data/local/tmp/a11y.xml"), density)
    print(f"{sys.argv[1] if len(sys.argv) > 1 else 'screen'}: {len(found)} finding(s)")
    for f in found:
        print("  " + f)
    sys.exit(1 if found else 0)
