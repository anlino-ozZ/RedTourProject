#!/usr/bin/env bash
# ============================================================================
# W-T-09 树莓派触摸屏 kiosk 一键初始化（在树莓派上以桌面用户执行，需 sudo 权限）
#
# 完成内容：
#   1. 安装 unclutter（自动隐藏鼠标）/ x11-xserver-utils（xset）
#   2. 关闭屏幕保护、DPMS 休眠、显示器熄屏
#   3. LXDE 桌面自启：xset 保活 + unclutter 隐藏光标 + kiosk 守护脚本
#   4. openbox 屏蔽 Alt+F4 / Alt+Tab / Ctrl+Alt+T 等系统快捷键
#      保留管理员组合键 Ctrl+Alt+X：一键杀掉 kiosk 与 Chromium 回到桌面
#      （另有 Ctrl+Alt+F3 切 TTY 文本控制台，始终可用）
#   5. 设置桌面自动登录（raspi-config B4）
#
# 幂等：可重复执行，重复执行只覆盖配置不产生重复条目
# 环境变量：
#   KIOSK_URL  触摸屏地址（默认 http://127.0.0.1:5175）
# ============================================================================
set -euo pipefail

KIOSK_URL="${KIOSK_URL:-http://127.0.0.1:5175}"
KIOSK_HOME="${HOME}/kiosk"
LX_AUTOSTART="${HOME}/.config/lxsession/LXDE-pi/autostart"
OPENBOX_RC="${HOME}/.config/openbox/lxde-pi-rc.xml"

log() { echo -e "\033[1;32m[kiosk-setup]\033[0m $*"; }
warn() { echo -e "\033[1;33m[kiosk-setup]\033[0m $*" >&2; }

# ---- 0. 环境校验 -----------------------------------------------------------
if [[ "$(uname -s)" != "Linux" ]] || ! grep -qi 'raspberry\|debian' /etc/os-release 2>/dev/null; then
  warn "本脚本需在树莓派 Raspberry Pi OS 上执行（当前主机不是）"
  exit 1
fi
if [[ "${XDG_SESSION_TYPE:-x11}" == "wayland" ]]; then
  warn "当前为 Wayland 会话；本脚本会自动切到 X11（见步骤 5），需重启后配置才生效"
fi
if ! sudo -n true 2>/dev/null; then
  warn "提示：部分步骤需要 sudo，如遇密码提示请输入当前用户密码"
fi

# ---- 1. 依赖安装 -----------------------------------------------------------
log "安装/更新依赖：unclutter（隐藏光标）、x11-xserver-utils（xset）"
sudo apt-get update -qq
sudo apt-get install -y -qq unclutter x11-xserver-utils curl >/dev/null

# ---- 2. kiosk 守护脚本就位 --------------------------------------------------
mkdir -p "${KIOSK_HOME}"
INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ -f "${INSTALL_DIR}/rpi-kiosk.sh" ]]; then
  cp -f "${INSTALL_DIR}/rpi-kiosk.sh" "${KIOSK_HOME}/rpi-kiosk.sh"
fi
chmod +x "${KIOSK_HOME}/rpi-kiosk.sh"
# KIOSK_URL 写入环境文件，守护脚本启动时读取
cat > "${KIOSK_HOME}/kiosk.env" <<EOF
# 由 rpi-kiosk-setup.sh 生成
KIOSK_URL=${KIOSK_URL}
EOF
log "kiosk 守护脚本已安装到 ${KIOSK_HOME}（KIOSK_URL=${KIOSK_URL}）"

# ---- 3. LXDE 自启项（完整覆盖，幂等）---------------------------------------
log "写入 LXDE 自启配置：${LX_AUTOSTART}"
mkdir -p "$(dirname "${LX_AUTOSTART}")"
cat > "${LX_AUTOSTART}" <<EOF
# ===== 由 W-T-09 rpi-kiosk-setup.sh 生成，勿手改 =====
# 关闭屏幕保护 / 熄屏 / DPMS 节能
@xset s off
@xset s noblank
@xset -dpms
# 鼠标静止 0.1s 即隐藏光标（纯触摸屏一体机）
@unclutter -idle 0.1 -root
# kiosk 守护（Chromium 崩溃自动重启）
@bash ${KIOSK_HOME}/rpi-kiosk.sh
EOF

# ---- 4. openbox：屏蔽系统级快捷键 ------------------------------------------
log "写入 openbox 键位配置：${OPENBOX_RC}"
mkdir -p "$(dirname "${OPENBOX_RC}")"
cat > "${OPENBOX_RC}" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!-- 由 W-T-09 rpi-kiosk-setup.sh 生成：kiosk 专用键位，屏蔽游客可退出的快捷键 -->
<openbox_config xmlns="http://openbox.org/3.4/rc">
  <focus>
    <focusNew>yes</focusNew>
    <followMouse>no</followMouse>
  </focus>
  <keyboard>
    <!-- 管理员组合键：杀掉 kiosk 与 Chromium，回到桌面 -->
    <keybind key="C-A-x">
      <action name="Execute">
        <command>bash -c 'pkill -f rpi-kiosk.sh; pkill -f chromium'</command>
      </action>
    </keybind>
    <!-- 以下为屏蔽项（绑到空命令 true，覆盖 openbox 内置动作）：
         Alt+F4 关窗 / Alt+Tab 切换 / Alt+F2 运行 / Ctrl+Alt+T 终端 /
         Ctrl+Alt+Del / Super 菜单 / Alt+Esc / Ctrl+Esc -->
    <keybind key="A-F4"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="A-Tab"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="A-S-Tab"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="A-F2"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="A-Escape"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="C-Escape"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="C-A-t"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="C-A-Delete"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="Super_L"><action name="Execute"><command>true</command></action></keybind>
    <keybind key="Super_R"><action name="Execute"><command>true</command></action></keybind>
  </keyboard>
  <desktop>
    <desktopnames><name>1</name></desktopnames>
  </desktop>
</openbox_config>
EOF

# ---- 5. 桌面自动登录（B4 = Desktop GUI, logged in as user）------------------
if command -v raspi-config >/dev/null 2>&1; then
  log "设置开机进入桌面并自动登录"
  sudo raspi-config nonint do_boot_behaviour B4 >/dev/null 2>&1 || \
    warn "自动登录设置失败，可手动执行：sudo raspi-config → 1 System Options → Boot / Auto Login → B4"
  # Bookworm 默认 Wayland，kiosk 配置基于 X11/openbox；W1=X11，重启后生效
  sudo raspi-config nonint do_wayland W1 >/dev/null 2>&1 || \
    warn "Wayland→X11 自动切换失败，可手动执行：sudo raspi-config → 6 Advanced Options → Wayland → X11（重启生效）"
fi

# ---- 6. 立即生效（当前 X 会话）---------------------------------------------
if [[ -n "${DISPLAY:-}" ]]; then
  xset s off 2>/dev/null || true
  xset s noblank 2>/dev/null || true
  xset -dpms 2>/dev/null || true
  pgrep -x unclutter >/dev/null || (unclutter -idle 0.1 -root &) 2>/dev/null || true
  openbox --reconfigure 2>/dev/null || true
fi

cat <<TIP

============================================================
 kiosk 初始化完成
 ------------------------------------------------------------
  触摸屏地址 : ${KIOSK_URL}（修改：${KIOSK_HOME}/kiosk.env）
  立即启动   : bash ${KIOSK_HOME}/rpi-kiosk.sh
  重启生效   : sudo reboot

  管理员退出 : Ctrl+Alt+X（回到桌面）
               Ctrl+Alt+F3（切文本控制台，始终保留）
============================================================
TIP
