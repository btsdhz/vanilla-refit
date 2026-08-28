import os
import shutil

# 路径
MODELS_DIR = "src/main/resources/assets/btsdhz_original/models/block"

# 需要保留的文件（手动创建的）
KEEP_FILES = [
    "vertical_slab_ns.json",
    "vertical_slab_ew.json",
    "vertical_double_ns.json",
    "vertical_double_ew.json",
    "vertical_slab_ns_smooth_stone.json",
    "vertical_slab_ew_smooth_stone.json",
    "vertical_double_ns_smooth_stone.json",
    "vertical_double_ew_smooth_stone.json",
]

# 需要删除的文件（旧命名 + 数据生成器生成的，让 runData 重新生成）
DELETE_PATTERNS = [
    "smooth_vertical_",           # 旧平滑石命名
    "template_slab",              # 模板文件
    "vertical_slab_north",
    "vertical_slab_south",
    "vertical_slab_east",
    "vertical_slab_west",
    "vertical_slab_ns_",          # 数据生成器生成的
    "vertical_slab_ew_",
    "vertical_double_ns_",
    "vertical_double_ew_",
]

def main():
    if not os.path.exists(MODELS_DIR):
        print(f"目录不存在: {MODELS_DIR}")
        return

    for filename in os.listdir(MODELS_DIR):
        filepath = os.path.join(MODELS_DIR, filename)
        if not os.path.isfile(filepath):
            continue

        # 1. 保留文件
        if filename in KEEP_FILES:
            print(f"[保留] {filename}")
            continue

        # 2. 检查是否匹配删除模式
        should_delete = False
        for pattern in DELETE_PATTERNS:
            if filename.startswith(pattern) or pattern in filename:
                should_delete = True
                break

        if should_delete:
            os.remove(filepath)
            print(f"[删除] {filename}")
        else:
            print(f"[未知] {filename} - 请手动检查")

    print("\n完成！请运行 .\\gradlew runData 重新生成文件。")

if __name__ == "__main__":
    main()