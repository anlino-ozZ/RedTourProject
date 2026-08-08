# -*- coding: utf-8 -*-
"""
AI 模型本地同步脚本
将本地下载的 AI 大模型权重 / Ollama 模型 / YOLOv8-pose 权重同步到指定目录，
便于树莓派离线环境使用。权重文件不入库（已被 .gitignore 忽略）。

用法：
    python scripts/model-sync.py --src <本地模型目录> --dst <目标目录>
示例：
    python scripts/model-sync.py --src D:/models --dst packages/ai-engine/weights
"""
import argparse
import shutil
from pathlib import Path

# 受支持的模型文件后缀（与 .gitignore 保持一致）
MODEL_EXTS = {'.pt', '.pth', '.onnx', '.bin', '.safetensors', '.gguf'}


def sync_models(src: Path, dst: Path) -> None:
    """遍历源目录，复制所有模型权重文件到目标目录"""
    if not src.exists():
        raise FileNotFoundError(f'源目录不存在: {src}')
    dst.mkdir(parents=True, exist_ok=True)

    count = 0
    total = 0
    for file in src.rglob('*'):
        if file.is_file() and file.suffix.lower() in MODEL_EXTS:
            size = file.stat().st_size
            total += size
            target = dst / file.name
            print(f'[同步] {file.name} -> {target} ({size // 1024} KB)')
            shutil.copy2(file, target)
            count += 1

    if count == 0:
        print('未发现可同步的模型权重文件（.pt/.pth/.onnx/.bin/.safetensors/.gguf）')
        return

    print(f'\n同步完成：{count} 个文件，合计 {total // (1024 * 1024)} MB')
    print('提示：权重文件已被 .gitignore 忽略，请勿提交到 Git 仓库。')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='AI 模型权重本地同步脚本')
    parser.add_argument('--src', required=True, help='本地模型源目录')
    parser.add_argument(
        '--dst',
        required=True,
        help='目标目录（如 packages/ai-engine/weights）',
    )
    args = parser.parse_args()
    sync_models(Path(args.src), Path(args.dst))
