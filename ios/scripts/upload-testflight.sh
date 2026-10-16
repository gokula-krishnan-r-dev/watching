#!/usr/bin/env bash
# Archive MeritScreen and upload to TestFlight (Sandeep Suntha team DUYBZ23SQ4).
#
# Prerequisites:
#   1. Xcode → Settings → Accounts: signed in as Sandeep Suntha (team DUYBZ23SQ4)
#   2. App Store Connect app exists for bundle id com.watching.sandeep (or create on first upload)
#   3. Capabilities on the team: App Groups (group.com.watching.sandeep), Push
#   4. Optional: Family Controls *distribution* approved + FAMILY_CONTROLS_DISTRIBUTION=YES
#      (otherwise Release strips Screen Time appexes so ASC does not reject missing entitlements)
#
# Usage:
#   ./ios/scripts/upload-testflight.sh
#   ./ios/scripts/upload-testflight.sh --archive-only   # skip upload
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IOS="$ROOT/ios"
PROJ="$IOS/MeritScreen.xcodeproj"
SCHEME="MeritScreen" # Xcode scheme; shipped product/display name is Watching
TEAM="DUYBZ23SQ4"
ARCHIVE_DIR="$IOS/build/archives"
EXPORT_DIR="$IOS/build/export"
ARCHIVE_PATH="$ARCHIVE_DIR/MeritScreen.xcarchive"
EXPORT_OPTIONS="$IOS/ExportOptions-TestFlight.plist"
ARCHIVE_ONLY=0

for arg in "$@"; do
  case "$arg" in
    --archive-only) ARCHIVE_ONLY=1 ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
  esac
done

mkdir -p "$ARCHIVE_DIR" "$EXPORT_DIR"

echo "==> Cleaning previous archive"
rm -rf "$ARCHIVE_PATH" "$EXPORT_DIR"/*

echo "==> Archiving (team $TEAM, generic iOS device)"
xcodebuild \
  -project "$PROJ" \
  -scheme "$SCHEME" \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  DEVELOPMENT_TEAM="$TEAM" \
  CODE_SIGN_STYLE=Automatic \
  -allowProvisioningUpdates \
  -allowProvisioningDeviceRegistration \
  clean archive | xcpretty 2>/dev/null || xcodebuild \
  -project "$PROJ" \
  -scheme "$SCHEME" \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  DEVELOPMENT_TEAM="$TEAM" \
  CODE_SIGN_STYLE=Automatic \
  -allowProvisioningUpdates \
  -allowProvisioningDeviceRegistration \
  clean archive

echo "==> Archive ready: $ARCHIVE_PATH"

if [[ "$ARCHIVE_ONLY" -eq 1 ]]; then
  echo "Skipping upload (--archive-only)."
  echo "Open Organizer in Xcode → Distribute App → App Store Connect → TestFlight."
  open "$ARCHIVE_DIR"
  exit 0
fi

echo "==> Exporting + uploading to App Store Connect / TestFlight"
xcodebuild -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$EXPORT_DIR" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -allowProvisioningUpdates

echo "==> Done. Check App Store Connect → TestFlight (processing can take a few minutes)."
