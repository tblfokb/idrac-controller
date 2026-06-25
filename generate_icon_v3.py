#!/usr/bin/env python3
"""
生成 iDRAC 控制器 APK 图标 v3 - 高级科技感
设计：深蓝黑渐变圆形 + 青色发光 D + 多层光晕
用法：python generate_icon_v3.py
"""

from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
import math
import os

def generate_icon_v3(output_base="app/src/main/res"):
    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }
    for density, size in densities.items():
        img = create_icon(size)
        out = os.path.join(output_base, density)
        os.makedirs(out, exist_ok=True)
        img.save(os.path.join(out, 'ic_launcher.png'))
        img.save(os.path.join(out, 'ic_launcher_round.png'))
        print(f"  ✓ {density}/ic_launcher.png ({size}x{size})")

    # Play Store 图标
    img_play = create_icon(512)
    out = os.path.join(output_base, 'mipmap-xxxhdpi')
    os.makedirs(out, exist_ok=True)
    img_play.save(os.path.join(out, 'ic_launcher_playstore.png'))
    print(f"  ✓ Play Store 图标 (512x512)")

def create_icon(size):
    """生成单张图标"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = size // 2
    cy = size // 2
    r = int(size * 0.45)

    # 1. 绘制深色圆形背景（渐变效果：中心亮、边缘暗）
    for radius in range(r, max(r - int(size * 0.38), 8), -1):
        ratio = 1.0 - (r - radius) / max(r, 1)
        # 从 #0D1B30 到 #050A10
        red = int(13 + (5 - 13) * ratio)
        green = int(27 + (10 - 27) * ratio)
        blue = int(48 + (16 - 48) * ratio)
        alpha = int(255 * (0.95 + 0.05 * ratio))
        color = (max(0, min(255, red)), max(0, min(255, green)), max(0, min(255, blue)), alpha)
        draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)

    # 2. 外圈发光环（3层，由外到内逐渐变亮）
    for i in range(4):
        alpha = int(50 + i * 25)
        width = max(1, int(size * 0.012))
        rr = r - i * int(size * 0.008)
        if rr < 1:
            break
        draw.ellipse([cx - rr, cy - rr, cx + rr, cy + rr],
                     outline=(0, 229, 255, alpha), width=width)

    # 3. 绘制字母 D（简化：竖线 + 半圆）
    letter_color = (0, 229, 255, 240)
    shadow_color = (0, 229, 255, 50)
    bw = int(size * 0.13)
    bh = int(size * 0.48)
    lx = cx - int(size * 0.22)
    ly = cy - bh // 2

    # 阴影（偏移）
    for offset in [(3, 3), (2, 2), (1, 1)]:
        draw.rectangle([lx + offset[0], ly + offset[1], lx + bw + offset[0], ly + bh + offset[1]],
                      fill=shadow_color)

    # 主体竖线
    draw.rectangle([lx, ly, lx + bw, ly + bh], fill=letter_color)

    # 半圆弧（右半部分）
    arc_r = bh // 2
    arc_cx = lx + bw
    arc_cy = cy
    # 用椭圆弧模拟 D 的弧形（左侧被竖线遮挡）
    for w in range(max(1, int(size * 0.06)), 0, -1):
        ratio = w / max(int(size * 0.06), 1)
        alpha = int(240 * (1.0 - ratio * 0.3))
        color = (0, 229, 255, alpha)
        draw.ellipse([arc_cx - arc_r, arc_cy - arc_r, arc_cx + arc_r, arc_cy + arc_r],
                     outline=color, width=max(1, int(w)))

    # 4. 光晕层（单独渲染后合成）
    glow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    glow_r = r + int(size * 0.08)
    for radius in range(glow_r, r - int(size * 0.05), -1):
        if radius <= 0:
            break
        ratio = (glow_r - radius) / max(glow_r - r + int(size * 0.05), 1)
        alpha = int(90 * (1.0 - ratio))
        color = (0, 229, 255, max(0, alpha))
        gd.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                    fill=color)
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(3, int(size * 0.04))))
    img = Image.alpha_composite(img, glow)

    # 5. 顶部高光（玻璃质感）
    highlight = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    hd = ImageDraw.Draw(highlight)
    hl_w = int(r * 1.6)
    hl_h = int(r * 0.7)
    hd.ellipse([cx - hl_w // 2, cy - r + int(size * 0.02), cx + hl_w // 2, cy + hl_h // 2],
                fill=(255, 255, 255, 20))
    # 高斯模糊让高光更自然
    highlight = highlight.filter(ImageFilter.GaussianBlur(radius=max(3, int(size * 0.06))))
    img = Image.alpha_composite(img, highlight)

    # 6. 内发光（中心光斑）
    inner_glow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ig = ImageDraw.Draw(inner_glow)
    for radius in range(int(r * 0.4), 0, -1):
        ratio = radius / max(int(r * 0.4), 1)
        alpha = int(40 * (1.0 - ratio))
        color = (0, 229, 255, max(0, alpha))
        ig.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                    fill=color)
    img = Image.alpha_composite(img, inner_glow)

    return img

if __name__ == "__main__":
    print("🎨 生成 iDRAC 控制器图标 v3（高级科技感）...")
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, "app", "src", "main", "res")
    generate_icon_v3(output_dir)
    print("\n✅ 图标生成完成！")
