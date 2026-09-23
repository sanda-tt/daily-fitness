# -*- coding: utf-8 -*-
"""生成带重量历史与完成状态的 gym_prefs.xml，供 run-as 写入模拟器。"""
import json
import time
from xml.sax.saxutils import escape

ID = [1]

def ex(name, sets=0, detail="", weight=None):
    e = {"id": ID[0], "name": name, "sets": sets, "detail": detail, "weight": weight}
    ID[0] += 1
    return e

week = {
    1: [
        ex("上斜哑铃卧推", 4, "6～10"),
        ex("器械卧推", 3, "8～12"),
        ex("夹胸", 3, "10～15"),
        ex("肩推", 3, "8～12"),
        ex("侧平举", 4, "12～20"),
        ex("三头下压", 3, "10～15"),
        ex("坡走", 0, "30分钟"),
    ],
    2: [
        ex("高位下拉", 4, "8～12"),
        ex("坐姿划船", 4, "8～12"),
        ex("单臂下拉/单臂划船", 3, "10～12"),
        ex("反向飞鸟", 3, "12～20"),
        ex("二头弯举", 3, "8～12"),
        ex("锤式弯举", 2, "10～15"),
        ex("坡走", 0, "25～30分钟"),
    ],
    3: [
        ex("深蹲/哈克深蹲", 4, "6～10"),
        ex("腿举", 3, "8～12"),
        ex("腿弯举", 3, "10～15"),
        ex("腿屈伸", 3, "10～15"),
        ex("提踵", 4, "10～15"),
        ex("卷腹", 3, "12～20"),
        ex("悬垂举腿", 3, "8～15"),
        ex("有氧", 0, "10～20分钟"),
    ],
    4: [
        ex("走路", 0, "12000～15000步"),
        ex("坡走", 0, "40～50分钟"),
    ],
    5: [
        ex("上斜卧推", 3, "8～12"),
        ex("高位下拉", 3, "8～12"),
        ex("坐姿划船", 3, "8～12"),
        ex("侧平举", 5, "12～20"),
        ex("反向飞鸟", 3, "12～20"),
        ex("夹胸", 3, "12～15"),
        ex("二头", 2),
        ex("三头", 2),
        ex("坡走", 0, "30分钟"),
    ],
    6: [
        ex("罗马尼亚硬拉", 3, "6～10"),
        ex("腿举", 3, "10～15"),
        ex("腿弯举", 3, "10～15"),
        ex("侧平举", 4, "12～20"),
        ex("卷腹", 3),
        ex("举腿", 3),
        ex("有氧", 0, "20～30分钟"),
    ],
    7: [
        ex("散步"),
    ],
}

now = int(time.time() * 1000)
DAY = 86400000

# name -> (weights, offsets_days)
hist = {
    "罗马尼亚硬拉": ([60, 70, 80, 90, 100], [28, 21, 14, 7, 0]),
    "高位下拉": ([45, 50, 55, 60, 65], [28, 21, 14, 7, 0]),
    "上斜哑铃卧推": ([20, 22.5, 25, 27.5], [28, 14, 7, 0]),
    "坐姿划船": ([40, 45, 50, 55], [21, 14, 7, 0]),
    "深蹲/哈克深蹲": ([60, 70, 80, 90], [28, 14, 7, 0]),
}

# 把当前重量写进模板同名项目
current = {name: vals[-1] for name, (vals, _) in hist.items()}
for d, items in week.items():
    for e in items:
        if e["name"] in current:
            e["weight"] = current[e["name"]]

weights = {}
for name, (vals, offsets) in hist.items():
    weights[name] = [
        {"w": v, "t": now - off * DAY - 3600000 * (i + 1)}
        for i, (v, off) in enumerate(zip(vals, offsets))
    ]

data = {
    "nextId": ID[0],
    "templates": {str(d): items for d, items in week.items()},
    "completion": {
        # 今天（周二）前 3 项已完成：高位下拉8、坐姿划船9、单臂10
        "2026-09-22": [8, 9, 10],
        # 昨天周一全部完成
        "2026-09-21": [1, 2, 3, 4, 5, 6, 7],
    },
    "weights": weights,
}

raw = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"data\">" + escape(raw) + "</string>\n</map>\n"

with open(r"D:\developer\androidstudio\.design\gym\推文素材\gym_prefs.xml", "w", encoding="utf-8", newline="\n") as f:
    f.write(xml)
print("ok, exercises:", ID[0] - 1)
