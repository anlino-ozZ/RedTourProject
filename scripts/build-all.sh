#!/usr/bin/env bash
# 一键打包三套前端（H5 / Admin / Touch），产物输出到各自 dist/
# 用法：在仓库根目录执行  bash scripts/build-all.sh
set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGES=("web-h5" "web-admin" "web-touch")

echo "==== 红色文旅前端一键打包 ===="
for pkg in "${PACKAGES[@]}"; do
  dir="$ROOT_DIR/packages/$pkg"
  echo "---- 打包 $pkg ----"
  if [ ! -d "$dir" ]; then
    echo "[跳过] 目录不存在: $dir"
    continue
  fi
  cd "$dir"
  # 未安装依赖时自动安装
  if [ ! -d node_modules ]; then
    echo "[依赖] 未检测到 node_modules，执行 npm install..."
    npm install
  fi
  npm run build
  echo "[完成] $pkg 产物位于 $dir/dist"
  cd "$ROOT_DIR"
done

echo "==== 全部前端打包完成 ===="
