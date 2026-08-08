#!/usr/bin/env bash
# 树莓派同步部署脚本：将硬件脚本与 AI 引擎同步到树莓派5 并远程重启服务
# 用法：bash scripts/rpi-deploy.sh
# 依赖：通过环境变量配置，或修改下方默认值
#   RPI_HOST  树莓派地址（默认 redtour.local）
#   RPI_USER  登录用户（默认 pi）
#   RPI_PATH  远程部署目录（默认 /home/pi/red-tour）
set -e

RPI_HOST="${RPI_HOST:-redtour.local}"
RPI_USER="${RPI_USER:-pi}"
RPI_PATH="${RPI_PATH:-/home/pi/red-tour}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==== 树莓派部署：${RPI_USER}@${RPI_HOST}:${RPI_PATH} ===="

# 1. 同步硬件脚本（排除虚拟环境 / 缓存 / .env）
echo "---- 同步 hardware-rpi ----"
rsync -avz --delete \
  --exclude 'venv/' --exclude '__pycache__/' --exclude '.env' \
  "$ROOT_DIR/packages/hardware-rpi/" "${RPI_USER}@${RPI_HOST}:${RPI_PATH}/packages/hardware-rpi/"

# 2. 同步 AI 引擎（额外排除权重 / 模型 / Wiki 编译产物）
echo "---- 同步 ai-engine（排除权重与编译产物）----"
rsync -avz --delete \
  --exclude 'venv/' --exclude '__pycache__/' --exclude '.env' \
  --exclude 'weights/' --exclude 'models/' --exclude 'wiki_build/' \
  "$ROOT_DIR/packages/ai-engine/" "${RPI_USER}@${RPI_HOST}:${RPI_PATH}/packages/ai-engine/"

# 3. 远程重启 systemd 服务（服务名按实际配置调整，不存在则跳过）
echo "---- 远程重启服务 ----"
ssh "${RPI_USER}@${RPI_HOST}" "sudo systemctl restart red-tour-hardware.service || true"
ssh "${RPI_USER}@${RPI_HOST}" "sudo systemctl restart red-tour-engine.service || true"

echo "==== 部署完成 ===="
