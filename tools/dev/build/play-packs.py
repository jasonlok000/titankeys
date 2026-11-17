"""Splits a built APK into an App Bundle's modules: the base, and install-time asset packs for the big data (docs/play/
asset-packs.md). A prototype for the Play build's size: it measures, it doesn't ship. play-packs.sh runs it.

    python3 play-packs.py <proto.apk> <out folder>

<proto.apk> is the APK converted by `aapt2 convert --output-format proto`. Each pack's assets leave the base, and the pack gets
them under the same paths, so the app reads them through its AssetManager as before.
"""
import os
import sys
import zipfile

# Pack name: the asset paths it takes (a folder ends in /).
PACKS = {
    "swipe2_pack": ["assets/swipe2/"],
    # Rime's tables, both grammar models (tk_yue.gram, zh-hant-t-essay-bgw.gram) and the next-word tables.
    "rime_pack": ["assets/rime/", "assets/rime.sha256"],
    "mozc_pack": ["assets/mozc/", "assets/mozc.sha256"],
    "swipe_pack": ["assets/swipe/"],
}
# What the base module keeps where (bundletool's module layout).
SIGNATURE = (".SF", ".RSA", ".DSA", ".EC")


def pack_of(name):
    for pack, paths in PACKS.items():
        if any(name == p or (p.endswith("/") and name.startswith(p)) for p in paths):
            return pack
    return None


def base_path(name):
    if name == "AndroidManifest.xml":
        return "manifest/AndroidManifest.xml"
    if name == "resources.pb" or name.startswith(("res/", "assets/", "lib/")):
        return name
    if name.endswith(".dex") and "/" not in name:
        return "dex/" + name
    if name.startswith("META-INF/") and (name == "META-INF/MANIFEST.MF" or name.endswith(SIGNATURE)):
        return None
    if name.startswith("META-INF/com/android/"):
        return None
    return "root/" + name


def main():
    source, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    zips = {"base": zipfile.ZipFile(os.path.join(out, "base.zip"), "w", zipfile.ZIP_DEFLATED)}
    for pack in PACKS:
        zips[pack] = zipfile.ZipFile(os.path.join(out, pack + ".zip"), "w", zipfile.ZIP_DEFLATED)
    moved = {name: 0 for name in zips}
    with zipfile.ZipFile(source) as apk:
        for info in apk.infolist():
            if info.is_dir():
                continue
            pack = pack_of(info.filename)
            target = info.filename if pack else base_path(info.filename)
            if target is None:
                continue
            zip_name = pack or "base"
            # Stored stays stored: Rime and Mozc's tables are copied out, the native libraries mapped in place.
            compress = info.compress_type
            zips[zip_name].writestr(zipfile.ZipInfo(target, date_time=(1980, 1, 1, 0, 0, 0)), apk.read(info), compress_type=compress)
            moved[zip_name] += info.file_size
    for pack in PACKS:
        manifest = os.path.join(out, pack + "-manifest", "AndroidManifest.xml")
        zips[pack].write(manifest, "manifest/AndroidManifest.xml")
    for z in zips.values():
        z.close()
    for name, size in moved.items():
        print(f"{name}: {size / 1e6:.1f} MB raw")


if __name__ == "__main__":
    main()
