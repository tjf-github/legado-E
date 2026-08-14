# -*- coding: utf-8 -*-
"""Generate local txt test books:
1. test_book.txt - 5 Arabic-numbered chapters, each longer than 1000 chars
   so the app's TOC rule scoring accepts it (Phase 1/2 manual test);
2. test_book_split.txt - a "glued chapter" book for the Phase 3 split feature:
   outer chapters use 第N章 (matched by default TOC rules), inner headings use
   第N回 which the default enabled rules do NOT match, so they stay glued inside
   the first chapter and can be split in-app."""

heads = [
    ("第1章 初入江湖", "江湖路远，少年背着一把旧剑踏上了旅途。清晨的薄雾尚未散去，远处的山道上已经响起了脚步声。他在镇口的茶棚歇脚，听人说这十里外的青峰山近来不太平，夜里有怪声传出。"),
    ("第2章 山中怪影", "那人影一闪而逝，快得像是错觉。少年握紧剑柄，放轻脚步继续前行。转过一处弯道，他终于看清了那是什么——一个穿着破旧道袍的老者，正蹲在一棵老树根下刨着什么。"),
    ("第3章 夜宿山庙", "天黑之前，少年在一座废弃的山庙里落脚。庙里供着的神像早已面目模糊，香案上积着厚厚的灰。他生了堆火，烤着随身带的干粮，忽然听见庙外传来一阵断断续续的哭声。"),
    ("第4章 破晓疑云", "天蒙蒙亮时，哭声终于停了。少年收拾好行囊，走出山庙。庙前的石阶上，赫然印着一串脚印，从林子里来，又消失在庙门内。可他昨晚明明把门闩上了。"),
    ("第5章 尾声", "回到镇上时，已是日上三竿。少年在茶棚里坐下，叫了一碗面。老板问他昨夜去了哪里，他只是笑了笑，说山里的雾太大，什么也没看清。"),
]

filler = "山风穿过林梢，带起一片细碎的响动。少年停下脚步，侧耳听了片刻，又继续向前走去。路边的野草沾着露水，打湿了他的衣摆，他却不以为意，只是把肩上的旧剑换了个更顺手的姿势。"


def write_book(path, chapters):
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n")  # 让第一行标题前存在空白，满足“目录(去空白)”规则的匹配条件
        for head, body in chapters:
            f.write(head + "\n")
            f.write(body + "\n")
            total = len(body)
            while total < 1200:
                f.write(filler + "\n")
                total += len(filler) + 1


write_book(r"D:\vsproject\legado-E\test_book.txt", heads)

# 粘连章节测试书：第2回/第3回 不会被默认目录规则识别为独立章节，
# 而是粘在第1章正文里；目录页对该章执行“拆分此章节”可拆出 第2回/第3回。
split_heads = [
    ("第1章 初入江湖", "江湖路远，少年背着一把旧剑踏上了旅途。清晨的薄雾尚未散去，远处的山道上已经响起了脚步声。他在镇口的茶棚歇脚，听人说这十里外的青峰山近来不太平，夜里有怪声传出。"),
    ("第2回 山中怪影", "那人影一闪而逝，快得像是错觉。少年握紧剑柄，放轻脚步继续前行。转过一处弯道，他终于看清了那是什么——一个穿着破旧道袍的老者，正蹲在一棵老树根下刨着什么。"),
    ("第3回 夜宿山庙", "天黑之前，少年在一座废弃的山庙里落脚。庙里供着的神像早已面目模糊，香案上积着厚厚的灰。他生了堆火，烤着随身带的干粮，忽然听见庙外传来一阵断断续续的哭声。"),
    ("第4章 尾声", "天蒙蒙亮时，哭声终于停了。少年收拾好行囊，走出山庙。庙前的石阶上，赫然印着一串脚印，从林子里来，又消失在庙门内。可他昨晚明明把门闩上了。"),
    ("第5章 后会有期", "回到镇上时，已是日上三竿。少年在茶棚里坐下，叫了一碗面。老板问他昨夜去了哪里，他只是笑了笑，说山里的雾太大，什么也没看清。"),
]
write_book(r"D:\vsproject\legado-E\test_book_split.txt", split_heads)

for path in (
    r"D:\vsproject\legado-E\test_book.txt",
    r"D:\vsproject\legado-E\test_book_split.txt",
):
    data = open(path, encoding="utf-8").read()
    print(path)
    print("size:", len(data.encode("utf-8")))
    print("headings:", [ln for ln in data.splitlines() if ln.startswith("第")])
