"""Sophisticated Storage 상자 안쪽 질감과 Mekanism 개인 상자/통 안쪽 질감을 새로 그린다 (16x16, 외부 에셋 복사 없음).
실행: python3 -I tools/gen_inner_textures.py  (core/src/main/resources/assets/insight_addons/textures/block 에 씀)"""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "core", "src", "main", "resources", "assets", "insight_addons", "textures", "block")

# 나무 종류별 바닥 판자 기본색 (바닐라 판자 평균색을 참고해 직접 정한 값)
WOODS = {
    "oak": (178, 144, 92), "spruce": (112, 82, 48), "birch": (212, 200, 140), "jungle": (158, 112, 78),
    "acacia": (166, 90, 52), "dark_oak": (70, 46, 22), "mangrove": (122, 56, 50), "cherry": (222, 176, 170),
    "bamboo": (192, 172, 82), "crimson": (104, 50, 72), "warped": (44, 108, 100),
}
# 등급별 모서리 장식 색 (Sophisticated 등급 상자의 모서리 장식 색을 참고)
TIERS = {
    "wood": None, "copper": (226, 124, 92), "iron": (214, 218, 222), "gold": (250, 214, 70),
    "diamond": (76, 232, 222), "netherite": (74, 68, 76),
}

def clamp(v):
    return max(0, min(255, int(v)))

def shade(c, f):
    return tuple(clamp(v * f) for v in c)

def planks(base, seed):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    inner = shade(base, 0.72)  # 상자 안쪽이라 바깥보다 조금 어둡게
    for row in range(4):
        shift = (row % 2) * 5
        for y in range(row * 4, row * 4 + 4):
            for x in range(16):
                f = 1.0
                if y == row * 4 + 3:
                    f = 0.78  # 판자 사이 이음선
                elif y == row * 4:
                    f = 1.08
                if (x + shift) % 16 in (0, 8) and y != row * 4 + 3:
                    f *= 0.86  # 판자 끝 이음선
                f *= 1 + (rnd.random() - 0.5) * 0.10
                px[x, y] = shade(inner, f) + (255,)
    return im

def accents(im, color):
    px = im.load()
    c = color + (255,)
    d = shade(color, 0.7) + (255,)
    for cx, cy, sx, sy in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for i in range(4):
            px[cx + sx * i, cy] = c
            px[cx, cy + sy * i] = c
        px[cx + sx, cy + sy] = d

def steel(base, seed, led=None, dark_edge=True):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            f = 1 + (rnd.random() - 0.5) * 0.06
            if x in (0, 15) or y in (0, 15):
                f *= 0.82 if dark_edge else 1.0
            if x in (1, 14) or y in (1, 14):
                f *= 1.08
            px[x, y] = shade(base, f) + (255,)
    for i in range(3, 13):
        px[i, 8] = shade(base, 0.9) + (255,)
        px[8, i] = shade(base, 0.9) + (255,)
    if led:
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            px[x, y] = led + (255,)
    return im

def main():
    d = os.path.join(OUT, "sophisticated_inner")
    os.makedirs(d, exist_ok=True)
    n = 0
    for w, base in WOODS.items():
        for t, col in TIERS.items():
            im = planks(base, hash((w, t)) & 0xFFFF)
            if col:
                accents(im, col)
            im.save(os.path.join(d, "%s_%s.png" % (w, t)))
            n += 1
    # Mekanism 개인 상자/통: 어두운 강철 대신 밝은 회색 강철 + 초록 표시등(원래 개인 저장소의 초록 표시등 색을 참고)
    steel((150, 156, 164), 7, led=(70, 210, 120)).save(os.path.join(OUT, "personal_steel_panel.png"))
    steel((138, 144, 152), 11, led=(70, 210, 120)).save(os.path.join(OUT, "personal_barrel_inner.png"))
    print("wrote", n, "sophisticated textures + 2 mekanism textures")

main()
