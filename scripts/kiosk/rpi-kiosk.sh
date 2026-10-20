#!/usr/bin/env bash
# ============================================================================
# W-T-09 Chromium kiosk 守护脚本（由 LXDE autostart 调用，无需手工执行）
#
# 行为：
#   1. 读取同目录 kiosk.env 中的 KIOSK_URL
#   2. 等待触摸屏页面可达（后端/前端服务可能晚于桌面启动）
#   3. 以 --kiosk 全屏启动 Chromium，崩溃/退出后 2s 自动重启
#   4. 每次启动前清除"未正常关闭"恢复提示
#
# 管理员退出：Ctrl+Alt+X（openbox 绑定会同时杀掉本脚本与 chromium）
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# shellcheck source=kiosk.env
[[ -f "${SCRIPT_DIR}/kiosk.env" ]] && source "${SCRIPT_DIR}/kiosk.env"
KIOSK_URL="${KIOSK_URL:-http://127.0.0.1:5175}"

# ---- X11 环境（autostart 下通常已有，SSH 手工调试时兜底）----
export DISPLAY="${DISPLAY:-:0}"
export XAUTHORITY="${XAUTHORITY:-$HOME/.Xauthority}"

# Raspberry Pi OS 上浏览器可执行文件为 chromium-browser，部分版本为 chromium
CHROMIUM_BIN="$(command -v chromium-browser || command -v chromium)"
if [[ -z "${CHROMIUM_BIN}" ]]; then
  echo "[kiosk] 未找到 chromium，请先安装：sudo apt-get install -y chromium-browser" >&2
  exit 1
fi

# ---- 运行期再次确保不休眠（守护进程每 60s 刷一次，防止外部程序改回）----
(
  while true; do
    xset s off 2>/dev/null
    xset -dpms 2>/dev/null
    sleep 60
  done
) &
KEEPALIVE_PID=$!
trap 'kill ${KEEPALIVE_PID} 2>/dev/null' EXIT

# ---- 等待页面可达，最多 10 分钟（避免停在 Chromium 错误页）----
echo "[kiosk] 等待触摸屏页面就绪：${KIOSK_URL}"
WAIT_SECONDS=0
MAX_WAIT=600
until curl -sf -o /dev/null --max-time 3 "${KIOSK_URL}"; do
  WAIT_SECONDS=$((WAIT_SECONDS + 3))
  if (( WAIT_SECONDS >= MAX_WAIT )); then
    echo "[kiosk] 等待超时（${MAX_WAIT}s），仍尝试启动浏览器" >&2
    break
  fi
  sleep 3
done

# 清除 Chromium 崩溃恢复气泡所需的状态标记
clear_crash_flag() {
  local prefs
  while IFS= read -r -d '' prefs; do
    sed -i 's/"exited_cleanly":false/"exited_cleanly":true/; s/"exit_type":"Crashed"/"exit_type":"Normal"/' "${prefs}" 2>/dev/null || true
  done < <(find "${HOME}/.config/chromium" -maxdepth 2 -name Preferences -print0 2>/dev/null)
}

# ---- 主循环：崩溃自愈 ----
while true; do
  clear_crash_flag
  echo "[kiosk] 启动 Chromium kiosk：${KIOSK_URL}"

  "${CHROMIUM_BIN}" \
    --kiosk "${KIOSK_URL}" \
    --noerrdialogs \
    --disable-infobars \
    --disable-session-crashed-bubble \
    --no-first-run \
    --no-default-browser-check \
    --start-fullscreen \
    --fast \
    --fast-start \
    --disable-features=Translate,GlobalMediaControls,MediaRouter \
    --overscroll-history-navigation=0 \
    --disable-pinch \
    --force-device-scale-factor=1 \
    --autoplay-policy=no-user-gesture-required \
    --check-for-update-interval=31536000

  echo "[kiosk] Chromium 已退出，2 秒后自动重启…"
  sleep 2
done
