#!/usr/bin/env bash
# Build Red Hat / Fedora .rpm package skeleton for MeritScreen (d11 beta).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESKTOP_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CONFIG="${BUILD_CONFIG:-debug}"
VERSION="0.1.0"

OUT_DIR="${DESKTOP_DIR}/target/linux-rpm"
BIN_DIR="${DESKTOP_DIR}/target/${CONFIG}"

echo "Staging RPM package layout (${CONFIG}) in ${OUT_DIR}"
mkdir -p "${OUT_DIR}/SPECS" "${OUT_DIR}/SOURCES" "${OUT_DIR}/RPMS" "${OUT_DIR}/BUILD"

SPEC_FILE="${OUT_DIR}/SPECS/meritscreen.spec"
cat > "${SPEC_FILE}" << EOF
Name:           meritscreen
Version:        ${VERSION}
Release:        1%{?dist}
Summary:        Native desktop child protection and learning platform
License:        Proprietary
URL:            https://meritscreen.org

%description
MeritScreen pairs with parents to enforce healthy device time limits,
mandatory educational quizzes, and fail-safe application gating.

%install
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/lib/systemd/system
mkdir -p %{buildroot}/etc/xdg/autostart
mkdir -p %{buildroot}/usr/share/applications

install -m 755 ${BIN_DIR}/meritscreen-guardian %{buildroot}/usr/bin/
install -m 755 ${BIN_DIR}/meritscreen-agent %{buildroot}/usr/bin/
install -m 644 ${SCRIPT_DIR}/meritscreen-guardian.service %{buildroot}/usr/lib/systemd/system/
install -m 644 ${SCRIPT_DIR}/meritscreen-agent.desktop %{buildroot}/etc/xdg/autostart/

%post
%systemd_post meritscreen-guardian.service

%preun
%systemd_preun meritscreen-guardian.service

%postun
%systemd_postun_with_restart meritscreen-guardian.service

%files
/usr/bin/meritscreen-guardian
/usr/bin/meritscreen-agent
/usr/lib/systemd/system/meritscreen-guardian.service
/etc/xdg/autostart/meritscreen-agent.desktop
EOF

if command -v rpmbuild >/dev/null 2>&1; then
  rpmbuild --define "_topdir ${OUT_DIR}" -bb "${SPEC_FILE}"
  echo "RPM built successfully."
else
  echo "rpmbuild not installed; staged RPM spec at ${SPEC_FILE}"
fi
