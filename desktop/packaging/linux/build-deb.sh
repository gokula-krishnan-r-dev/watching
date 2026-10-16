#!/usr/bin/env bash
# Build Debian .deb package for MeritScreen (d11 beta).
# Supports dpkg-deb on Linux, with portable ar/tar fallback on macOS/other hosts.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESKTOP_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CONFIG="${BUILD_CONFIG:-debug}"
VERSION="0.1.0"
ARCH="amd64"

PKG_NAME="meritscreen_${VERSION}_${ARCH}"
OUT_DIR="${DESKTOP_DIR}/target/linux-deb"
STAGE_DIR="${OUT_DIR}/${PKG_NAME}"
BIN_DIR="${DESKTOP_DIR}/target/${CONFIG}"

echo "Building Debian package (${CONFIG}) -> ${PKG_NAME}.deb"

rm -rf "${STAGE_DIR}"
mkdir -p "${STAGE_DIR}/DEBIAN"
mkdir -p "${STAGE_DIR}/usr/bin"
mkdir -p "${STAGE_DIR}/lib/systemd/system"
mkdir -p "${STAGE_DIR}/etc/xdg/autostart"
mkdir -p "${STAGE_DIR}/usr/share/applications"
mkdir -p "${STAGE_DIR}/usr/share/icons/hicolor/128x128/apps"

# Ensure binaries exist
if [ ! -f "${BIN_DIR}/meritscreen-guardian" ]; then
  echo "Compiling guardian and agent (${CONFIG})..."
  cargo build --manifest-path "${DESKTOP_DIR}/Cargo.toml" -p meritscreen-guardian -p meritscreen-agent
fi

cp "${BIN_DIR}/meritscreen-guardian" "${STAGE_DIR}/usr/bin/"
cp "${BIN_DIR}/meritscreen-agent" "${STAGE_DIR}/usr/bin/"
if [ -f "${BIN_DIR}/meritscreen-ui" ]; then
  cp "${BIN_DIR}/meritscreen-ui" "${STAGE_DIR}/usr/bin/"
else
  # Ephemeral UI placeholder if not yet built for linux
  cp "${BIN_DIR}/meritscreen-agent" "${STAGE_DIR}/usr/bin/meritscreen-ui"
fi

chmod 755 "${STAGE_DIR}/usr/bin/"*

# Systemd units & XDG autostart
cp "${SCRIPT_DIR}/meritscreen-guardian.service" "${STAGE_DIR}/lib/systemd/system/"
cp "${SCRIPT_DIR}/meritscreen-agent.desktop" "${STAGE_DIR}/etc/xdg/autostart/"

# Desktop application launcher
cat > "${STAGE_DIR}/usr/share/applications/meritscreen.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=MeritScreen
Comment=Focus and screen time platform for kids
Exec=/usr/bin/meritscreen-ui
Icon=meritscreen
Terminal=false
Categories=Education;Utility;
EOF

# App icon
if [ -f "${DESKTOP_DIR}/apps/meritscreen-ui/icons/128x128.png" ]; then
  cp "${DESKTOP_DIR}/apps/meritscreen-ui/icons/128x128.png" "${STAGE_DIR}/usr/share/icons/hicolor/128x128/apps/meritscreen.png"
fi

# Package control metadata
cat > "${STAGE_DIR}/DEBIAN/control" << EOF
Package: meritscreen
Version: ${VERSION}
Section: utils
Priority: optional
Architecture: ${ARCH}
Maintainer: MeritScreen Team <support@meritscreen.org>
Description: Native desktop child protection and learning platform
 MeritScreen pairs with parents to enforce healthy device time limits,
 mandatory educational quizzes, and fail-safe application gating.
EOF

cat > "${STAGE_DIR}/DEBIAN/postinst" << 'EOF'
#!/bin/sh
set -e
if [ -d /run/systemd/system ]; then
    systemctl daemon-reload || true
    systemctl enable --now meritscreen-guardian.service || true
fi
exit 0
EOF
chmod 755 "${STAGE_DIR}/DEBIAN/postinst"

cat > "${STAGE_DIR}/DEBIAN/prerm" << 'EOF'
#!/bin/sh
set -e
if [ -d /run/systemd/system ]; then
    systemctl stop meritscreen-guardian.service || true
    systemctl disable meritscreen-guardian.service || true
fi
exit 0
EOF
chmod 755 "${STAGE_DIR}/DEBIAN/prerm"

cat > "${STAGE_DIR}/DEBIAN/postrm" << 'EOF'
#!/bin/sh
set -e
if [ -d /run/systemd/system ]; then
    systemctl daemon-reload || true
fi
exit 0
EOF
chmod 755 "${STAGE_DIR}/DEBIAN/postrm"

TARGET_DEB="${OUT_DIR}/${PKG_NAME}.deb"
if command -v dpkg-deb >/dev/null 2>&1; then
  dpkg-deb --build --root-owner-group "${STAGE_DIR}" "${TARGET_DEB}"
else
  echo "dpkg-deb not found; using portable ar/tar packager..."
  TMP_BUILD="${OUT_DIR}/.tmp_deb"
  rm -rf "${TMP_BUILD}"
  mkdir -p "${TMP_BUILD}"
  echo "2.0" > "${TMP_BUILD}/debian-binary"

  (cd "${STAGE_DIR}/DEBIAN" && tar -czf "${TMP_BUILD}/control.tar.gz" .)
  (cd "${STAGE_DIR}" && tar --exclude='./DEBIAN' -czf "${TMP_BUILD}/data.tar.gz" .)

  (cd "${TMP_BUILD}" && { ar -S -rc "${TARGET_DEB}" debian-binary control.tar.gz data.tar.gz 2>/dev/null || ar -rc "${TARGET_DEB}" debian-binary control.tar.gz data.tar.gz; })
  rm -rf "${TMP_BUILD}"
fi

echo "Created ${TARGET_DEB} ($(du -h "${TARGET_DEB}" | cut -f1))"
