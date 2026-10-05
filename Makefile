# MeritScreen — local Android + iOS demo
#
# Android does NOT hot-reload Text/Compose edits by itself.
# Saving a .kt file only updates the emulator after a rebuild+reinstall.
#
# Android:
#   make dev       Boot 1 emulator + install + launch (once)
#   make up-dev    Boot 2 emulators (parent + child)
#   make watch     Auto-reload on save (lite + fast) ← use while editing UI
#   make reload    One-shot rebuild + reinstall (no watcher)
#   make logs      Stream app logcat (Ctrl+C to stop)
#   make down      Stop emulators
#
# iOS (Simulator):
#   make ios-dev       Boot 1 simulator + build + launch
#   make ios-up-dev    Boot 2 simulators (parent + child) ← pairing / sync
#   make ios-reload    Rebuild + reinstall (simulators stay up)
#   make ios-down      Shut down MeritScreen simulators
#   make ios-devices   List simulators
#   make ios-doctor    Verify Xcode + runtimes
#
# Android AVDs: MeritScreen_API34_Lite → :5554 | MeritScreen_API34_Child → :5556
# iOS Sims:     MeritScreen_Parent | MeritScreen_Child  (auto-created)

SHELL := /bin/bash
.ONESHELL:
.DEFAULT_GOAL := help

ROOT    := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
SCRIPTS := $(ROOT)/scripts
ENV     := source "$(SCRIPTS)/android-env.sh"

export QUIET ?= 1

.PHONY: help dev up-dev watch reload logs down devices doctor \
	ios-dev ios-up-dev ios-reload ios-down ios-devices ios-doctor \
	desktop-check desktop-test desktop-ui desktop-ui-build desktop-a11y desktop-conformance \
	desktop-macos desktop-macos-ui desktop-macos-doctor

help:
	@printf '%s\n' \
		'MeritScreen local demo' \
		'' \
		'Android' \
		'  make dev       Boot 1 emulator, install, launch' \
		'  make up-dev    Boot 2 emulators, install + launch both' \
		'  make watch     Auto rebuild+relaunch when you save .kt files' \
		'  make reload    One-shot rebuild + reinstall (emulator stays up)' \
		'  make logs      Stream MeritScreen logcat (Ctrl+C to stop)' \
		'  make down      Stop all emulators' \
		'  make devices   Show adb devices' \
		'  make doctor    Verify SDK + AVDs' \
		'' \
		'iOS Simulator' \
		'  make ios-dev       Boot 1 simulator, build, launch' \
		'  make ios-up-dev    Boot 2 simulators (parent + child), build, launch' \
		'  make ios-reload    Rebuild + reinstall (simulators stay up)' \
		'  make ios-down      Shut down MeritScreen_Parent / MeritScreen_Child' \
		'  make ios-devices   List available simulators' \
		'  make ios-doctor    Verify Xcode + iOS runtimes' \
		'' \
		'Desktop (Rust)' \
		'  make desktop-check      cargo check --workspace' \
		'  make desktop-test       cargo test --workspace' \
		'  make desktop-ui-build   npm build UI assets' \
		'  make desktop-a11y       UI a11y smoke (d10)' \
		'  make desktop-conformance  vectors + resilience + updater (d12)' \
		'  make desktop-ui         launch Tauri role-gate shell (demo backends)' \
		'' \
		'Desktop macOS + Firebase (E2E)' \
		'  make desktop-macos-doctor  Firebase CLI login + API key probe' \
		'  make desktop-macos         Full build + conformance + Guardian Firebase smoke' \
		'  make desktop-macos-ui      Same as desktop-macos, then launch Tauri UI' \
		'  (requires: firebase login → project managing-screen-time)' \
		'' \
		'Why Android edits do not appear instantly:' \
		'  The emulator runs a built APK, not your source files.' \
		'  Change Text → save → watcher (or reload) rebuilds → then you see it.' \
		'' \
		'Fast Android UI loop:' \
		'  make dev' \
		'  make watch          # leave this running in a terminal' \
		'  edit + save         # APK pushes automatically' \
		'' \
		'Fast iOS dual-device (parent ↔ child):' \
		'  make ios-up-dev' \
		'  edit Swift → make ios-reload'

dev:
	@$(ENV) && "$(SCRIPTS)/run-demo.sh" --quiet

up-dev:
	@$(ENV) && "$(SCRIPTS)/run-dual-demo.sh" --quiet

# Lite auto-reload: polls sources, incremental :app:installDebug, relaunches app.
# Keep emulator running. Ctrl+C stops the watcher only.
watch:
	@$(ENV) && exec "$(SCRIPTS)/watch-reload.sh"

# One-shot rebuild for when the watcher is not running.
reload:
	@$(ENV) && \
	count=$$(adb devices 2>/dev/null | awk 'BEGIN{n=0} /^emulator-.*[[:space:]]device$$/{n++} END{print n}'); \
	if [[ "$$count" -eq 0 ]]; then \
		printf 'error: no emulator running — start with: make dev  (or make up-dev)\n' >&2; \
		exit 1; \
	fi; \
	printf 'Rebuilding APK and reinstalling on %s emulator(s)…\n' "$$count"; \
	if [[ "$$count" -ge 2 ]]; then \
		"$(SCRIPTS)/run-dual-demo.sh" --quiet --rebuild; \
	else \
		"$(SCRIPTS)/run-demo.sh" --quiet --no-avd --rebuild; \
	fi; \
	printf 'Done — new build is live.\n'

# App logcat only. SERIAL=emulator-5556 make logs to pick one device.
logs:
	@$(ENV) && exec "$(SCRIPTS)/app-logs.sh"

down:
	@$(ENV) && \
	serials=$$(adb devices 2>/dev/null | awk '/^emulator-/{print $$1}'); \
	if [[ -z "$$serials" ]]; then \
		printf 'No emulators running.\n'; \
		exit 0; \
	fi; \
	for s in $$serials; do \
		printf 'Stopping %s\n' "$$s"; \
		adb -s "$$s" emu kill >/dev/null 2>&1 || true; \
	done; \
	adb kill-server >/dev/null 2>&1 || true; \
	printf 'Done.\n'

devices:
	@$(ENV) && adb devices -l

doctor:
	@$(ENV) && \
	printf 'ANDROID_HOME=%s\n' "$$ANDROID_HOME"; \
	command -v adb >/dev/null && adb version | head -1; \
	command -v emulator >/dev/null && emulator -version 2>&1 | head -1; \
	printf 'AVDs:\n'; \
	emulator -list-avds | sed 's/^/  /'; \
	printf 'Devices:\n'; \
	adb devices -l | sed 's/^/  /'

# ---------------------------------------------------------------------------
# iOS Simulator
# ---------------------------------------------------------------------------

ios-dev:
	@QUIET="$(QUIET)" "$(SCRIPTS)/run-ios-demo.sh" --quiet

ios-up-dev:
	@QUIET="$(QUIET)" "$(SCRIPTS)/run-ios-dual-demo.sh" --quiet

ios-reload:
	@set -euo pipefail; \
	booted=$$(xcrun simctl list devices 2>/dev/null | grep -c '(Booted)' || true); \
	if [[ "$$booted" -eq 0 ]]; then \
		printf 'error: no simulator booted — start with: make ios-dev  (or make ios-up-dev)\n' >&2; \
		exit 1; \
	fi; \
	printf 'Rebuilding iOS app and reinstalling on %s simulator(s)…\n' "$$booted"; \
	parent=$$(xcrun simctl list devices available 2>/dev/null | grep -F 'MeritScreen_Parent' | grep -c '(Booted)' || true); \
	child=$$(xcrun simctl list devices available 2>/dev/null | grep -F 'MeritScreen_Child' | grep -c '(Booted)' || true); \
	if [[ "$$parent" -ge 1 && "$$child" -ge 1 ]]; then \
		QUIET=0 "$(SCRIPTS)/run-ios-dual-demo.sh" --rebuild; \
	else \
		QUIET=0 "$(SCRIPTS)/run-ios-demo.sh" --no-boot --rebuild; \
	fi; \
	printf 'Done — new build is live.\n'

ios-down:
	@set -euo pipefail; \
	ROOT="$(ROOT)"; \
	source "$(SCRIPTS)/ios-common.sh"; \
	shutdown_named_sims; \
	printf 'Done.\n'

ios-devices:
	@xcrun simctl list devices available

ios-doctor:
	@set -euo pipefail; \
	printf 'xcodebuild: '; xcodebuild -version | head -1; \
	printf 'Project: %s\n' "$(ROOT)/ios/MeritScreen.xcodeproj"; \
	printf 'Runtimes:\n'; \
	xcrun simctl list runtimes available | sed 's/^/  /'; \
	printf 'MeritScreen sims:\n'; \
	xcrun simctl list devices available | grep -E 'MeritScreen_(Parent|Child)' | sed 's/^/  /' || printf '  (none yet — created on first ios-dev / ios-up-dev)\n'; \
	printf 'Schemes:\n'; \
	xcodebuild -project "$(ROOT)/ios/MeritScreen.xcodeproj" -list 2>/dev/null | sed -n '/Schemes:/,$$p' | sed 's/^/  /'

desktop-check:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	cargo check --workspace

desktop-test:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	cargo test --workspace

desktop-ui-build:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop/apps/meritscreen-ui/ui"; \
	npm ci; \
	npm run build

desktop-a11y:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop/apps/meritscreen-ui/ui"; \
	npm run a11y-smoke

desktop-conformance:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	cargo test -p meritscreen-core vectors; \
	cargo test -p meritscreen-guardian --test resilience; \
	cargo test -p meritscreen-updater

desktop-ui: desktop-ui-build
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	cargo run -p meritscreen-ui

desktop-macos-doctor:
	@set -euo pipefail; \
	"$(SCRIPTS)/desktop-macos-e2e.sh" doctor

desktop-macos:
	@set -euo pipefail; \
	"$(SCRIPTS)/desktop-macos-e2e.sh" smoke

desktop-macos-ui:
	@set -euo pipefail; \
	"$(SCRIPTS)/desktop-macos-e2e.sh" ui
