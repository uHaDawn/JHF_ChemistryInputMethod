# 把构件插入 aapt2 生成的 APK（保持 resources.arsc 不压缩，符合 API 30+ 要求）
# 用法：zip_insert.py <src.apk> <dst.apk> <entry-name>=<file-path> [<entry-name>=<file-path> ...]
# 例：zip_insert.py unsigned.apk patched.apk classes.dex=interim/dex/classes.dex \
#        lib/arm64-v8a/librime_jni.so=out/arm64-v8a/librime_jni.so
import sys
import zipfile

src, dst = sys.argv[1], sys.argv[2]
entries = []
for arg in sys.argv[3:]:
    name, _, path = arg.partition('=')
    if not path:
        raise SystemExit('bad entry (expect name=path): ' + arg)
    entries.append((name, path))

with zipfile.ZipFile(src, 'r') as zin, \
     zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        comp = zipfile.ZIP_STORED if item.filename == 'resources.arsc' or item.compress_type == zipfile.ZIP_STORED else zipfile.ZIP_DEFLATED
        zi = zipfile.ZipInfo(item.filename)
        zi.compress_type = comp
        zi.external_attr = item.external_attr
        zout.writestr(zi, data)
    for name, path in entries:
        with open(path, 'rb') as f:
            data = f.read()
        zi = zipfile.ZipInfo(name)
        zi.compress_type = zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        zout.writestr(zi, data)

print('inserted %d entr%s -> %s' % (len(entries), 'y' if len(entries) == 1 else 'ies', dst))
