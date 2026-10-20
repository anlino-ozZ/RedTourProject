#!/usr/bin/env bash
# 树莓派同步部署脚本：将硬件脚本与 AI 引擎同步到树莓派5 并远程重启服务
#
# 用法：
#   bash scripts/rpi-deploy.sh              # 同步 hardware-rpi / ai-engine 并重启服务
#   bash scripts/rpi-deploy.sh kiosk [URL]  # W-T-09：安装/更新 kiosk（Chromium 全屏自启）
#                                            URL 为触摸屏地址，默认 http://127.0.0.1:5175
#   bash scripts/rpi-deploy.sh kiosk-stop   # 远程停止 kiosk 与 Chromium（回到桌面）
#
# 依赖：通过环境变量配置，或修改下方默认值
#   RPI_HOST  树莓派地址（默认 redtour.local）
#   RPI_USER  登录用户（默认 pi）
#   RPI_PATH  远程部署目录（默认 /home/pi/RedTourProject）
set -euo pipefail

RPI_HOST="${RPI_HOST:-redtour.local}"
RPI_USER="${RPI_USER:-pi}"
RPI_PATH="${RPI_PATH:-/home/pi/RedTourProject}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SSH_TARGET="${RPI_USER}@${RPI_HOST}"

# ============================================================================
# kiosk 相关（W-T-09）
# ============================================================================
kiosk_install() {
  local kiosk_url="${1:-http://127.0.0.1:5175}"

  echo "==== kiosk 安装：${SSH_TARGET}（URL=${kiosk_url}） ===="

  # 1. 推送 kiosk 脚本到 ~/kiosk/
  echo "---- 同步 scripts/kiosk ----"
  ssh "${SSH_TARGET}" "mkdir -p ~/kiosk"
  rsync -avz --chmod=D755,F755 \
    "${ROOT_DIR}/scripts/kiosk/rpi-kiosk.sh" \
    "${ROOT_DIR}/scripts/kiosk/rpi-kiosk-setup.sh" \
    "${SSH_TARGET}:~/kiosk/"

  # 2. 远程执行幂等初始化（依赖安装/自启/键位/自动登录）
  echo "---- 远程执行 kiosk 初始化 ----"
  ssh -t "${SSH_TARGET}" "KIOSK_URL='${kiosk_url}' bash ~/kiosk/rpi-kiosk-setup.sh"

  echo "==== kiosk 安装完成，重启树莓派后自动进入全屏（或远程执行 bash ~/kiosk/rpi-kiosk.sh 立即生效） ===="
}

kiosk_stop() {
  echo "==== 远程停止 kiosk：${SSH_TARGET} ===="
  ssh "${SSH_TARGET}" "pkill -f rpi-kiosk.sh; pkill -f chromium; echo done"
}

# ============================================================================
# 默认：业务代码部署
# ============================================================================
code_deploy() {
  echo "==== 树莓派部署：${SSH_TARGET}:${RPI_PATH} ===="

  # 1. 同步硬件脚本（排除虚拟环境 / 缓存 / .env）
  echo "---- 同步 hardware-rpi ----"
  rsync -avz --delete \
    --exclude 'venv/' --exclude '__pycache__/' --exclude '.env' \
    "${ROOT_DIR}/packages/hardware-rpi/" "${SSH_TARGET}:${RPI_PATH}/packages/hardware-rpi/"

  # 2. 同步 AI 引擎（额外排除权重 / 模型 / Wiki 编译产物）
  echo "---- 同步 ai-engine（排除权重与编译产物）----"
  rsync -avz --delete \
    --exclude 'venv/' --exclude '__pycache__/' --exclude '.env' \
    --exclude 'weights/' --exclude 'models/' --exclude 'wiki_build/' \
    "${ROOT_DIR}/packages/ai-engine/" "${SSH_TARGET}:${RPI_PATH}/packages/ai-engine/"

  # 3. 远程重启 systemd 服务（服务名按实际配置调整，不存在则跳过）
  echo "---- 远程重启服务 ----"
  ssh "${SSH_TARGET}" "sudo systemctl restart red-tour-project-hardware.service || true"
  ssh "${SSH_TARGET}" "sudo systemctl restart red-tour-project-engine.service || true"

  echo "==== 部署完成 ===="
}

case "${1:-deploy}" in
  deploy)
    code_deploy
    ;;
  kiosk)
    kiosk_install "${2:-${KIOSK_URL:-http://127.0.0.1:5175}}"
    ;;
  kiosk-stop)
    kiosk_stop
    ;;
  -h|--help|help)
    grep '^#' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    echo "未知子命令：$1" >&2
    echo "用法：bash scripts/rpi-deploy.sh [deploy|kiosk [URL]|kiosk-stop]" >&2
    exit 1
    ;;
esac
