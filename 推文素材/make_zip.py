# -*- coding: utf-8 -*-
import os, zipfile

root = r"D:\developer\androidstudio\.design\gym"
src = os.path.join(root, "推文素材")
zip_path = os.path.join(root, "小黑盒推文-每日健身.zip")

keep_files = {"cover.jpg", "小黑盒推文.md", "小黑盒推文预览.html",
              "gen_prefs.py", "make_memes.py", "make_cover.py", "gym_prefs.xml"}
keep_dirs = {"screenshots", "memes"}

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    for f in keep_files:
        p = os.path.join(src, f)
        if os.path.exists(p):
            z.write(p, os.path.join("推文素材", f))
    for d in keep_dirs:
        dp = os.path.join(src, d)
        for name in sorted(os.listdir(dp)):
            p = os.path.join(dp, name)
            if os.path.isfile(p):
                z.write(p, os.path.join("推文素材", d, name))

print("zip size", os.path.getsize(zip_path))
with zipfile.ZipFile(zip_path) as z:
    for n in z.namelist():
        print(n)
