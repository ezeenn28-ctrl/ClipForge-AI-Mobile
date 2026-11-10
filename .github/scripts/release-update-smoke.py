#!/usr/bin/env python3
"""Drive the actual signed APK on an isolated emulator using only synthetic data."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image

PACKAGE = "com.clipforge.mobile"
ACTIVITY = PACKAGE + "/.MainActivity"
OUT = Path("build/release-acceptance").resolve()
OUT.mkdir(parents=True, exist_ok=True)
CHECKS = []


def command(*args, check=True, binary=False, timeout=90):
    result = subprocess.run(list(args), capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"Command failed: {args[:3]}: " + result.stderr.decode(errors="replace")[-1500:])
    return result if binary else result.stdout.decode(errors="replace") + result.stderr.decode(errors="replace")


def adb(*args, **kwargs):
    return command("adb", *args, **kwargs)


def hierarchy():
    # A running preview need not become accessibility-idle; use bounded UI dump retries.
    for _ in range(3):
        adb("shell", "rm", "-f", "/sdcard/clipforge-window.xml", check=False)
        adb("shell", "uiautomator", "dump", "/sdcard/clipforge-window.xml", check=False, timeout=20)
        data = adb("shell", "cat", "/sdcard/clipforge-window.xml", check=False)
        try:
            root = ET.fromstring(data[data.index("<?xml"):])
            (OUT / "last-window.xml").write_text(data)
            return root
        except (ValueError, ET.ParseError):
            time.sleep(.3)
    raise AssertionError("Could not inspect app accessibility hierarchy")


def bounds(node):
    values = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    return tuple(values) if len(values) == 4 else (0, 0, 0, 0)


def find(root, text=None, desc=None, contains=False, class_name=None):
    result = []
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in root.iter("node"):
        value = node.get("text", "") if text is not None else node.get("content-desc", "")
        wanted = text if text is not None else desc
        if wanted is not None and not ((wanted.casefold() in value.casefold()) if contains else wanted == value):
            continue
        if class_name and node.get("class") != class_name:
            continue
        x1, y1, x2, y2 = bounds(node)
        if x2 > x1 and y2 > y1:
            # Compose may leave a disabled button's TextView enabled. Its
            # clickable ancestor controls whether the action is actually usable.
            ancestor = node
            while ancestor is not None:
                if ancestor.get("enabled") == "false":
                    node.set("enabled", "false")
                    break
                ancestor = parents.get(ancestor)
            result.append(node)
    return sorted(result, key=lambda n: (n.get("class") != "android.widget.Button", n.get("clickable") != "true"))


def tap_node(node):
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(.25)


def scroll(root, horizontal=False, reverse=False):
    nodes = []
    for node in root.iter("node"):
        if node.get("scrollable") != "true":
            continue
        x1, y1, x2, y2 = bounds(node)
        width, height = x2 - x1, y2 - y1
        is_horizontal = node.get("class") == "android.widget.HorizontalScrollView"
        is_vertical = node.get("class") == "android.widget.ScrollView"
        if width > 100 and height > 40 and (is_horizontal if horizontal else is_vertical):
            nodes.append(node)
    if not nodes:
        return False  # Pickers may still be loading and expose no scroll node yet.
    node = max(nodes, key=(lambda n: bounds(n)[3]) if horizontal else
        (lambda n: (bounds(n)[2] - bounds(n)[0]) * (bounds(n)[3] - bounds(n)[1])))
    x1, y1, x2, y2 = bounds(node)
    if horizontal:
        points = (x2 - 25, (y1 + y2) // 2, x1 + 25, (y1 + y2) // 2)
    else:
        # The timeline consumes center-screen vertical drags. Use the parent's
        # left gutter so the enclosing vertical ScrollView receives the swipe.
        swipe_x = x1 + min(24, (x2 - x1) // 10)
        points = (swipe_x, y1 + (y2 - y1) * 4 // 5, swipe_x, y1 + (y2 - y1) // 5)
    if reverse:
        points = points[2:] + points[:2]
    adb("shell", "input", "swipe", *(str(v) for v in points), "350")
    time.sleep(.3)
    return True


def locate(text=None, desc=None, contains=False, timeout=30, scroll_axis=None):
    deadline = time.monotonic() + (max(timeout, 90) if scroll_axis else timeout)
    previous = None
    stationary = 0
    reverse = False
    while time.monotonic() < deadline:
        root = hierarchy()
        nodes = find(root, text=text, desc=desc, contains=contains)
        if nodes:
            return nodes[0]
        if scroll_axis:
            signature = ET.tostring(root)
            stationary = stationary + 1 if signature == previous else 0
            if stationary >= 2:
                reverse = not reverse
                stationary = 0
            previous = signature
            if not scroll(root, horizontal=scroll_axis == "horizontal", reverse=reverse):
                time.sleep(.4)
        else:
            time.sleep(.4)
    raise AssertionError(f"Control unavailable: {text or desc}")


def screenshot(name):
    data = adb("exec-out", "screencap", "-p", binary=True).stdout
    (OUT / (name + ".png")).write_bytes(data)
    return Image.open(io.BytesIO(data)).convert("RGB")


def passed(name):
    CHECKS.append(name)
    print("PASS:", name, flush=True)


def launch():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "-f", "0x10008000")
    locate(text="Release upgrade check", contains=True, timeout=35)


def open_editor():
    root = hierarchy()
    if find(root, desc="Play") or find(root, desc="Pause"):
        return
    tap_node(locate(text="Continue editing"))
    locate(text="Release upgrade check", contains=True)
    locate(desc="Play")



def choose_document(filename):
    root = hierarchy()
    list_toggle = find(root, desc="List view")
    if list_toggle:
        tap_node(list_toggle[0])
    locate(text=filename, timeout=35, scroll_axis="vertical")
    root = hierarchy()
    label = find(root, text=filename)[0]
    parents = {child: parent for parent in root.iter() for child in parent}
    target = label
    current = label
    while current is not None:
        if current.get("resource-id", "").endswith("/item_root"):
            target = current
            break
        current = parents.get(current)
    # DocumentsUI grid nameplates do not dispatch the item touch listener.
    # Activate the actual document tile, away from its separate Preview badge.
    tap_node(target)
    root = hierarchy()
    if any(n.get("package") == "com.android.documentsui" for n in root.iter("node")):
        adb("shell", "input", "keyevent", "66")
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        root = hierarchy()
        if any(n.get("package") == PACKAGE for n in root.iter("node")):
            return
        time.sleep(.4)
    raise AssertionError("System picker did not return the selected document")


def exports():
    text = adb("shell", "sh", "-c", "'if [ -d /sdcard/Movies/ClipForge ]; then find /sdcard/Movies/ClipForge -name \"clipforge_*.mp4\"; fi'", check=False)
    return [line.strip() for line in text.splitlines() if line.startswith("/sdcard/") and line.endswith(".mp4")]


def await_export(timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        files = exports()
        if files:
            root = hierarchy()
            if find(root, text="Open last MP4"):
                return files[-1]
            scroll(root)  # Completion actions may be below the short export viewport.
        time.sleep(.5)
    raise AssertionError("Approved release export did not complete")


def verify_mp4(path):
    local = OUT / "approved-release-export.mp4"
    adb("pull", path, str(local))
    metadata = json.loads(command("ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(local)))
    (OUT / "export-metadata.json").write_text(json.dumps(metadata, indent=2))
    video = next(s for s in metadata["streams"] if s["codec_type"] == "video")
    assert any(s["codec_type"] == "audio" for s in metadata["streams"]), "Release export lost audio"
    width, height = video["width"], video["height"]
    rotation = next((abs(int(s.get("rotation", 0))) for s in video.get("side_data_list", []) if "rotation" in s), 0)
    if rotation in (90, 270):
        width, height = height, width
    assert (width, height) == (1080, 1920), (width, height, rotation)
    assert 4.75 <= float(metadata["format"]["duration"]) <= 5.3, "Saved speed/cuts did not produce five seconds"
    frame = OUT / "release-caption-frame.png"
    command("ffmpeg", "-v", "error", "-y", "-ss", "1", "-i", str(local), "-frames:v", "1", str(frame))
    image = Image.open(frame).convert("RGB")
    assert image.height > image.width
    pixels = list(image.crop((0, image.height * 7 // 10, image.width, image.height * 9 // 10)).getdata())
    assert sum(r > 180 and g > 180 and b > 180 for r, g, b in pixels) > 40, "Saved caption text missing"
    assert sum(r < 110 and g > 150 and b > 170 for r, g, b in pixels) > 20, "Saved cyan highlight missing"


def main(args):
    adb("wait-for-device")
    adb("logcat", "-c")
    adb("install", "-r", str(args.baseline))
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", "files/media")
    copied = subprocess.run(["adb", "shell", "-T", "run-as", PACKAGE, "sh", "-c", "'cat > files/media/upgrade.mp4'"],
        input=args.video.read_bytes(), capture_output=True, timeout=30)
    assert copied.returncode == 0, "Could not seed baseline owned video"
    for file in (args.seed / "files").rglob("*"):
        if not file.is_file():
            continue
        relative = file.relative_to(args.seed / "files").as_posix()
        target = "files/" + relative
        adb("shell", "run-as", PACKAGE, "mkdir", "-p", str(Path(target).parent))
        copied = subprocess.run(["adb", "shell", "-T", "run-as", PACKAGE, "sh", "-c", "'cat > " + target + "'"],
            input=file.read_bytes(), capture_output=True, timeout=30)
        assert copied.returncode == 0, "Could not seed baseline app data"

    adb("shell", "run-as", PACKAGE, "mkdir", "-p", "files/models")
    copied = subprocess.run(["adb", "shell", "-T", "run-as", PACKAGE, "sh", "-c", "'cat > files/models/ggml-tiny.bin'"],
        input=args.model.read_bytes(), capture_output=True, timeout=60)
    assert copied.returncode == 0, "Could not seed verified public speech model"
    if args.phase == "speech-import":
        adb("install", "-r", str(args.release))
        launch()
        exercise_speech_import(args)
        return
    launch()
    screenshot("baseline-project")
    passed("Pinned Build 94 source compatibility APK opens production-serialized synthetic project")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("install", "-r", str(args.release))
    run_as = adb("shell", "run-as", PACKAGE, "id", check=False, binary=True)
    assert run_as.returncode != 0 and b"not debuggable" in (run_as.stdout + run_as.stderr), "Installed release is debuggable"
    passed("Signed release updates Build 94 and rejects run-as debugging")
    launch()
    locate(text="Upgrade draft B", contains=True, scroll_axis="vertical")
    passed("Both named projects survive the signed update")
    launch()
    open_editor()
    preview = locate(desc="Preview of time-synchronized subtitles")
    x1, y1, x2, y2 = bounds(preview)
    crop = (x1, y1 + (y2 - y1) * 37 // 100, x2, y1 + (y2 - y1) * 63 // 100)
    tap_node(locate(desc="Play"))
    time.sleep(.4)
    first = screenshot("release-playing-first").crop(crop)
    time.sleep(.5)
    second = screenshot("release-playing-second").crop(crop)
    assert sum(sum(pixel) > 75 for pixel in first.getdata()) > first.width * first.height // 10, "Installed release preview is black"
    assert hashlib.sha256(first.tobytes()).digest() != hashlib.sha256(second.tobytes()).digest(), "Installed release video did not advance"
    root = hierarchy()
    pause = find(root, desc="Pause")
    if pause:
        tap_node(pause[0])
    passed("Actual non-debuggable release renders changing video frames")
    tap_node(locate(text="More", scroll_axis="horizontal"))
    tap_node(locate(text="Campaign tools", scroll_axis="vertical"))
    locate(text="Upgrade campaign fixture", contains=True, scroll_axis="vertical")
    passed("Campaign record survives signed update")
    adb("shell", "input", "keyevent", "4")
    open_editor()
    tap_node(locate(text="Export"))
    button = locate(text="Export approved MP4", scroll_axis="vertical")
    assert button.get("enabled") == "false", "Export was enabled without review"
    assert not exports(), "Release exported without approval"
    passed("Export remains blocked before explicit approval")
    root = hierarchy()
    boxes = [n for n in root.iter("node") if n.get("checkable") == "true" and n.get("checked") == "false"]
    if not boxes:
        locate(text="I reviewed this edit and approve its export", scroll_axis="vertical")
        root = hierarchy()
        boxes = [n for n in root.iter("node") if n.get("checkable") == "true" and n.get("checked") == "false"]
    assert boxes, "Review checkbox unavailable"
    tap_node(boxes[0])
    button = locate(text="Export approved MP4", scroll_axis="vertical")
    assert button.get("enabled") == "true"
    tap_node(button)
    verify_mp4(await_export())
    passed("Actual release approval/export preserves saved speed, captions, framing and audio in Gallery MP4")
    tap_node(locate(text="Share last MP4", scroll_axis="vertical"))
    time.sleep(.5)
    assert "ChooserActivity" in adb("shell", "dumpsys", "activity", "activities"), "Share sheet did not open"
    screenshot("release-share-sheet")
    adb("shell", "input", "keyevent", "4")
    passed("Release opens Android share sheet without sending to a recipient")
    launch()
    open_editor()
    tap_node(locate(text="Export"))
    assert locate(text="Export approved MP4", scroll_axis="vertical").get("enabled") == "false"
    passed("Process restart restores project and clears export approval")
    exercise_speech_import(args)


def exercise_speech_import(args):
    # Exercise real foreground-service decoding/inference through the signed APK.
    adb("shell", "input", "keyevent", "4")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "mkdir", "-p", "/sdcard/Download")
    adb("push", str(args.speech), "/sdcard/Download/ClipForgeSpeechFixture.mp4")
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/Download/ClipForgeSpeechFixture.mp4")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "-f", "0x10008000")
    tap_node(locate(text="New project"))
    root = hierarchy()
    if not find(root, text="ClipForgeSpeechFixture.mp4"):
        nav = [n for n in root.iter("node") if any(s in n.get("content-desc", "").lower() for s in ("roots", "navigation drawer"))]
        if nav:
            tap_node(nav[0]); tap_node(locate(text="Downloads"))
    choose_document("ClipForgeSpeechFixture.mp4")
    locate(text="ClipForgeSpeechFixture", contains=True, timeout=40)
    # New project intentionally starts with an empty, non-destructive sequence.
    tap_node(locate(text="Add source clip", scroll_axis="vertical"))
    tap_node(locate(text="Captions", scroll_axis="horizontal"))
    tap_node(locate(text="Generate captions", scroll_axis="vertical"))
    locate(text="country", contains=True, timeout=150, scroll_axis="vertical")
    screenshot("signed-release-auto-captions")
    passed("Actual signed APK decodes public English speech and produces editable captions through its packaged JNI/service")
    # Clean-install test resets only this isolated emulator's synthetic app data.
    adb("uninstall", PACKAGE)
    adb("install", str(args.release))
    adb("shell", "mkdir", "-p", "/sdcard/Download")
    adb("push", str(args.video), "/sdcard/Download/ClipForgeImportFixture.mp4")
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/Download/ClipForgeImportFixture.mp4")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "-f", "0x10008000")
    tap_node(locate(text="New project", timeout=40))
    root = hierarchy()
    if not find(root, text="ClipForgeImportFixture.mp4"):
        nav = [n for n in root.iter("node") if any(s in n.get("content-desc", "").lower() for s in ("roots", "navigation drawer"))]
        if nav:
            tap_node(nav[0])
            tap_node(locate(text="Downloads"))
    choose_document("ClipForgeImportFixture.mp4")
    locate(text="ClipForgeImportFixture", contains=True, timeout=40)
    root = hierarchy()
    assert find(root, desc="Play") or find(root, desc="Pause"), "Clean import did not open editor"
    screenshot("clean-release-import")
    time.sleep(1.5)  # Exercise the documented one-second autosave debounce.
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "-f", "0x10008000")
    locate(text="ClipForgeImportFixture", contains=True, timeout=35)
    passed("Clean signed install imports through system picker and retains its new project after restart")
    logs = adb("logcat", "-d", "-v", "brief")
    (OUT / "logcat.txt").write_text(logs)
    assert not re.search(r"FATAL EXCEPTION[\s\S]{0,1500}Process: com\.clipforge\.mobile", logs), "App crash in release log"
    assert "Cmdline: com.clipforge.mobile" not in logs, "Native ClipForge crash in release log"
    passed("No ClipForge Java/native fatal exception in real release workflow")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("baseline", "release", "seed", "video", "speech", "model"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--phase", choices=("full", "speech-import"), default="full")
    args = parser.parse_args()
    failure = None
    try:
        main(args)
    except Exception as error:
        failure = str(error)
        print("ACCEPTANCE FAILURE:", failure, flush=True)
        try:
            screenshot("failure")
            root = hierarchy()
            print("LAST UI:", [(n.get("text"), n.get("content-desc"), n.get("enabled"), n.get("bounds")) for n in root.iter("node") if n.get("text") or n.get("content-desc")], flush=True)
            print("GALLERY FILES:", exports(), flush=True)
            (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-v", "brief"))
        except Exception:
            pass
        raise
    finally:
        (OUT / "result.json").write_text(json.dumps({"checks": CHECKS, "passed": failure is None, "error": failure,
            "scope": "Actual signed APK, isolated Android emulator, synthetic fixtures only"}, indent=2))
