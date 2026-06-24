#!/usr/bin/env python3
"""
生成科技感 iDRAC 控制器 APK 图标
设计风格：深蓝黑底色 + 青色/蓝色科技感元素
"""

from PIL import Image, ImageDraw, ImageFont
import os

# 图标尺寸配置 (适配不同密度)
ICON_SIZES = {
    'mdpi': 48,      # 1x
    'hdpi': 72,      # 1.5x
    'xhdpi': 96,     # 2x
    'xxhdpi': 144,   # 3x
    'xxxhdpi': 192,  # 4x
}

def create_tech_icon(size):
    """创建科技感图标"""
    # 创建方形画布
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # 背景：深蓝黑渐变效果 (用矩形模拟)
    bg_color = (8, 12, 28)  # 深蓝黑
    draw.rectangle([0, 0, size, size], fill=bg_color)
    
    # 添加圆角效果 (模拟 Android 自适应图标)
    # 绘制四个角的圆角
    corner_radius = int(size * 0.15)
    
    # 清除四个角 (变成透明)
    # 左上角
    draw.pieslice([0, 0, corner_radius*2, corner_radius*2], 180, 270, fill=(0, 0, 0, 0))
    # 右上角
    draw.pieslice([size-corner_radius*2, 0, size, corner_radius*2], 270, 360, fill=(0, 0, 0, 0))
    # 左下角
    draw.pieslice([0, size-corner_radius*2, corner_radius*2, size], 90, 180, fill=(0, 0, 0, 0))
    # 右下角
    draw.pieslice([size-corner_radius*2, size-corner_radius*2, size, size], 0, 90, fill=(0, 0, 0, 0))
    
    # 主色调
    cyan = (0, 200, 255)        # 亮青色
    blue = (0, 120, 255)        # 蓝色
    dark_blue = (0, 60, 120)    # 深蓝色
    green = (0, 255, 150)       # 亮绿色
    
    center = size // 2
    margin = int(size * 0.15)
    
    # 绘制服务器/机柜外形 (矩形框)
    rack_left = margin
    rack_top = int(size * 0.25)
    rack_right = size - margin
    rack_bottom = int(size * 0.75)
    
    # 机柜外框 (青色)
    draw.rectangle([rack_left, rack_top, rack_right, rack_bottom], 
                   outline=cyan, width=max(1, int(size * 0.015)))
    
    # 机柜内部横线 (模拟服务器插槽)
    slot_count = 3
    slot_height = (rack_bottom - rack_top) // (slot_count + 1)
    for i in range(1, slot_count + 1):
        y = rack_top + i * slot_height
        # 指示灯 (绿色小圆点)
        dot_x = rack_left + int(size * 0.08)
        dot_y = y - int(size * 0.02)
        dot_r = max(2, int(size * 0.025))
        draw.ellipse([dot_x-dot_r, dot_y-dot_r, dot_x+dot_r, dot_y+dot_r], fill=green)
        
        # 插槽横线
        draw.line([rack_left + int(size*0.15), y, rack_right - int(size*0.05), y], 
                  fill=dark_blue, width=max(1, int(size * 0.01)))
    
    # 绘制闪电图标 (电源符号，右下角)
    bolt_size = int(size * 0.25)
    bolt_x = center + int(size * 0.15)
    bolt_y = center + int(size * 0.15)
    
    # 闪电形状 (简化三角形)
    bolt_points = [
        (bolt_x, bolt_y - bolt_size//2),
        (bolt_x - bolt_size//3, bolt_y),
        (bolt_x - bolt_size//6, bolt_y),
        (bolt_x, bolt_y + bolt_size//2),
        (bolt_x + bolt_size//6, bolt_y),
        (bolt_x + bolt_size//3, bolt_y),
    ]
    draw.polygon(bolt_points, fill=cyan)
    
    # 添加外圈光晕效果 (蓝色半透明圆圈)
    halo_radius = int(size * 0.48)
    for i in range(3):
        r = halo_radius - i * int(size * 0.03)
        alpha = max(10, 40 - i * 15)
        overlay = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        overlay_draw = ImageDraw.Draw(overlay)
        overlay_draw.ellipse([center-r, center-r, center+r, center+r], 
                                outline=(*blue, alpha), width=max(1, int(size * 0.01)))
        img = Image.alpha_composite(img, overlay)
        draw = ImageDraw.Draw(img)
    
    # 底部装饰线 (电路纹理)
    line_y = int(size * 0.88)
    draw.line([int(size*0.2), line_y, int(size*0.8), line_y], 
              fill=(*cyan, 100), width=max(1, int(size * 0.01)))
    
    # 小圆点装饰 (模拟电路节点)
    node_radius = max(2, int(size * 0.02))
    for x_pos in [int(size*0.2), int(size*0.5), int(size*0.8)]:
        draw.ellipse([x_pos-node_radius, line_y-node_radius, 
                       x_pos+node_radius, line_y+node_radius], 
                       fill=cyan)
    
    return img

def create_round_icon(base_img, size):
    """创建圆形图标"""
    # 创建圆形蒙版
    mask = Image.new('L', (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([0, 0, size, size], fill=255)
    
    # 应用蒙版
    result = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    result.paste(base_img, (0, 0), mask)
    return result

def main():
    print("正在生成科技感图标...")
    
    # 获取脚本所在目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    res_dir = os.path.join(script_dir, 'app', 'src', 'main', 'res')
    
    for density, size in ICON_SIZES.items():
        print(f"  生成 {density} ({size}x{size})...")
        
        # 生成基础图标
        icon = create_tech_icon(size)
        
        # 保存普通图标
        out_dir = os.path.join(res_dir, f'mipmap-{density}')
        os.makedirs(out_dir, exist_ok=True)
        
        icon_path = os.path.join(out_dir, 'ic_launcher.png')
        icon.save(icon_path, 'PNG')
        print(f"    ✓ {icon_path}")
        
        # 生成圆形图标
        round_icon = create_round_icon(icon, size)
        round_path = os.path.join(out_dir, 'ic_launcher_round.png')
        round_icon.save(round_path, 'PNG')
        print(f"    ✓ {round_path}")
    
    # 生成 Play Store 高清图标 (512x512)
    print("  生成 Play Store 图标 (512x512)...")
    play_icon = create_tech_icon(512)
    play_path = os.path.join(script_dir, 'ic_launcher_playstore.png')
    play_icon.save(play_path, 'PNG')
    print(f"    ✓ {play_path}")
    
    print("\n✅ 图标生成完成！")

if __name__ == '__main__':
    main()
