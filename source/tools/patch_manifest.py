# 为手工构建链（build.cmd）生成带 package 属性的临时 Manifest。
# AS/Gradle 路径使用 namespace（见 app/build.gradle），源 Manifest 保持无 package 属性（AGP 8 要求）。
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
text = open(src, encoding="utf-8").read()
if "package=" not in text:
    text = text.replace("<manifest ", '<manifest package="com.jhf.jiehuafa" ', 1)
open(dst, "w", encoding="utf-8").write(text)
print("manifest patched ->", dst)
