# 把 classes.dex 插入 aapt2 生成的 APK（保持 resources.arsc 不压缩，符合 API 30+ 要求）
import sys
import zipfile

src, dex_path, dex_name, dst = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

with open(dex_path, 'rb') as f:
    dex_data = f.read()

with zipfile.ZipFile(src, 'r') as zin, \
     zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        comp = zipfile.ZIP_STORED if item.filename == 'resources.arsc' or item.compress_type == zipfile.ZIP_STORED else zipfile.ZIP_DEFLATED
        zi = zipfile.ZipInfo(item.filename)
        zi.compress_type = comp
        zi.external_attr = item.external_attr
        zout.writestr(zi, data)
    zi = zipfile.ZipInfo(dex_name)
    zi.compress_type = zipfile.ZIP_DEFLATED
    zout.writestr(zi, dex_data)

print('dex inserted ->', dst)
