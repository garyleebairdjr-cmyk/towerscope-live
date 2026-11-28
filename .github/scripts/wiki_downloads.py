#!/usr/bin/env python3
"""Generate the wiki's Download.md from the GitHub releases API.

Usage: gh api "repos/OWNER/REPO/releases?per_page=100" | python3 wiki_downloads.py OWNER/REPO > Download.md
"""
import json
import sys
from datetime import datetime

repo = sys.argv[1]
releases = [r for r in json.load(sys.stdin) if not r.get("draft")]
base = f"https://github.com/{repo}"


def apk(r):
    """The versioned APK asset of a release (not the fixed-name copy)."""
    assets = [a for a in r.get("assets", []) if a["name"].endswith(".apk")]
    versioned = [a for a in assets if a["name"] != "TowerScope-Live.apk"]
    return (versioned or assets or [None])[0]


def size(n):
    return f"{n / 1_048_576:.1f} MB"


def date(s):
    return datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").strftime("%b %-d, %Y")


def channel(r):
    name = (r.get("name") or "").lower()
    if "pre-beta" in name:
        return "🧪 Pre-beta"
    if "beta" in name:
        return "🧪 Beta"
    return "✅ Stable"


out = ["# Download", ""]
if not releases:
    out += [
        "No builds have been published yet. The first one will appear here automatically.",
        "",
        f"Follow progress on the [Releases page]({base}/releases).",
    ]
else:
    newest = releases[0]
    a = apk(newest)
    version = newest["tag_name"].lstrip("v")
    out += [
        f"## Latest: {newest.get('name') or newest['tag_name']}",
        "",
        f"Released {date(newest['published_at'])} · {channel(newest)}" + (f" · {size(a['size'])}" if a else ""),
        "",
    ]
    if a:
        out += [
            f'<a href="{a["browser_download_url"]}"><img src="https://img.shields.io/badge/Download-v{version}-22D3EE?style=for-the-badge&logo=android&logoColor=white" alt="Download v{version}"></a>',
            "",
            "Open this page **on your phone** and tap the button. New here? Read "
            "[Installing on Android](Installing-on-Android) first; it takes about a minute.",
            "",
            "These downloads are the free **open-source edition** for Android. The Google Play and iPhone "
            "editions are described on [Editions](Editions).",
            "",
        ]
    if "pre-beta" in (newest.get("name") or "").lower():
        out += [
            "> **This is a pre-beta build.** Everything works, but expect rough edges and frequent updates. "
            f"Please [report problems]({base}/issues) with your phone model and carrier.",
            "",
        ]
    out += [
        "Permanent link to the newest build (handy for sharing): ",
        f"`{base}/releases/latest/download/TowerScope-Live.apk`",
        "",
        "## All versions",
        "",
        "Every build is signed with the same key, so you can install any of them over another, "
        "except going *down* a version, which needs an uninstall first.",
        "",
        "| Version | Released | Channel | Download | Changes |",
        "|---|---|---|---|---|",
    ]
    for r in releases:
        a = apk(r)
        link = f"[{a['name']}]({a['browser_download_url']}) ({size(a['size'])})" if a else "—"
        body = (r.get("body") or "")
        change = ""
        for line in body.splitlines():
            if line.startswith("Changes:"):
                change = line[len("Changes:"):].strip()
                break
        change = change.replace("|", "\\|")[:120]
        out.append(
            f"| [{r['tag_name']}]({r['html_url']}) | {date(r['published_at'])} | {channel(r)} | {link} | {change} |"
        )
    out += ["", f"_This page is updated automatically after each release. Full details on the [Releases page]({base}/releases)._"]

print("\n".join(out))
