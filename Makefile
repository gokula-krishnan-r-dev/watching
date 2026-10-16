# ==============================================================================
# MeritScreen — Run, Test, and Try Guide (Desktop, Android, iOS)
# ==============================================================================
#
# QUICK START / TRY & DEMO:
#   Desktop (macOS / Windows / Linux Tauri App):
#     make desktop-ui         Launch Desktop Tauri app (Role Gate: Parent / Child)
#       - Child mode: Enter mock pairing code (e.g. 123456) -> test C01 pairing,
#                     C02 checklist, C05 launcher, C07-C11 quiz overlay, lockout,
#                     and fail-lock screens.
#       - Parent mode: Enter mock OTP (e.g. 424242) -> test dashboard, limits,
#                      PIN reset, allowlists, and family management.
#     make desktop-macos-ui   Launch Desktop with live Firebase auth/sync (macOS)
#
#   Android (Emulator):
#     make dev                Boot 1 emulator, install debug APK, and launch app
#     make up-dev             Boot 2 emulators (Parent :5554 + Child :5556)
#     make watch              Hot UI watcher: rebuilds and pushes APK on .kt save
#
#   iOS (Simulator):
#     make ios-dev            Boot 1 simulator, compile, and launch
#     make ios-up-dev         Boot 2 simulators (MeritScreen_Parent + Child)
#
# TESTING & VERIFICATION:
#   Desktop:
#     make desktop-test       Run all 106 Rust unit & integration tests (11 crates)
#     make desktop-conformance Run golden vector, resilience & updater tests
#     make desktop-clippy     Run cargo clippy with zero warnings
#     make desktop-fmt        Check code formatting
#     make desktop-a11y       Run UI accessibility smoke tests (ARIA, keyboard navigation)
#     make desktop-perf       Run Guardian memory / RSS performance smoke test
#     make desktop-all        Run complete desktop test, lint, a11y & perf suite
#
#   Android:
#     make doctor             Verify Android SDK, ADB, and AVD environment
#     make logs               Stream live Android logcat
#
#   iOS:
#     make ios-doctor         Verify Xcode, tools, and iOS Simulator runtimes
#     make ios-devices        List configured iOS simulators
#
# TEARDOWN:
#     make down               Stop all Android emulators
#     make ios-down           Stop MeritScreen iOS simulators
# ==============================================================================

SHELL := /bin/bash
.ONESHELL:
.DEFAULT_GOAL := help

ROOT    := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
SCRIPTS := $(ROOT)/scripts
ENV     := source "$(SCRIPTS)/android-env.sh"

export QUIET ?= 1

.PHONY: help dev up-dev watch reload logs down devices doctor \
	ios-dev ios-up-dev ios-reload ios-down ios-devices ios-doctor \
	desktop-check desktop-test desktop-ui desktop-dev desktop-ui-build \
	desktop-a11y desktop-conformance desktop-clippy desktop-fmt desktop-perf \
	desktop-all desktop-deb desktop-pkg \
	desktop-macos desktop-macos-ui desktop-macos-doctor

help:
	@printf '%s\n' \
		'====================================================================' \
		'MeritScreen Local Demo, Runner & Test Matrix' \
		'====================================================================' \
		'' \
		'Desktop (Tauri + Pure Rust Guardian)' \
		'  make desktop-ui           Launch Desktop App in Dev Mode (Role Gate: Parent / Child)' \
		'  make desktop-dev          Alias for desktop-ui' \
		'  make desktop-test         Run all 106 tests across 11 crates' \
		'  make desktop-check        Run cargo check across workspace' \
		'  make desktop-clippy       Run cargo clippy with zero warnings' \
		'  make desktop-fmt          Verify rustfmt formatting' \
		'  make desktop-conformance  Run golden vectors, resilience & updater tests' \
		'  make desktop-a11y         Run UI accessibility smoke tests (ARIA + keyboard nav)' \
		'  make desktop-perf         Verify Guardian memory footprint (RSS <= 120MB budget)' \
		'  make desktop-all          Run complete desktop test, lint, a11y & perf suite' \
		'  make desktop-ui-build     Build production Vite/React UI bundle' \
		'  make desktop-deb          Build Linux Debian package (.deb)' \
		'  make desktop-pkg          Build macOS installer package (.pkg)' \
		'' \
		'Desktop macOS + Firebase (E2E)' \
		'  make desktop-macos-doctor Check Firebase CLI login + API key probe' \
		'  make desktop-macos        Full build + conformance + Guardian Firebase smoke' \
		'  make desktop-macos-ui     Run Desktop UI connected to live Firebase' \
		'  (requires: firebase login -> project managing-screen-time)' \
		'' \
		'Android (Emulator)' \
		'  make dev                  Boot 1 emulator, install debug APK, and launch' \
		'  make up-dev               Boot 2 emulators (Parent + Child), install & launch' \
		'  make watch                Auto rebuild+relaunch when you save .kt files' \
		'  make reload               One-shot rebuild + reinstall (emulator stays up)' \
		'  make logs                 Stream MeritScreen logcat (Ctrl+C to stop)' \
		'  make down                 Stop all emulators' \
		'  make devices              Show adb devices' \
		'  make doctor               Verify SDK + AVDs' \
		'' \
		'iOS Simulator' \
		'  make ios-dev              Boot 1 simulator, build, launch' \
		'  make ios-up-dev           Boot 2 simulators (parent + child), build, launch' \
		'  make ios-reload           Rebuild + reinstall (simulators stay up)' \
		'  make ios-down             Shut down MeritScreen_Parent / MeritScreen_Child' \
		'  make ios-devices          List available simulators' \
		'  make ios-doctor           Verify Xcode + iOS runtimes' \
		'' \
		'How to Try & Test:' \
		'  Desktop:  Run "make desktop-ui" -> Select Child to try quiz overlay & limits,' \
		'            or select Parent to manage family settings (mock OTP: 424242).' \
		'  Android:  Run "make dev", then "make watch" in a second terminal.' \
		'  iOS:      Run "make ios-up-dev" to simulate dual-device Parent <-> Child sync.'

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
	if [[ -f "$(ROOT)/scripts/desktop-macos-env.sh" && "$$(uname -s)" == "Darwin" ]]; then \
		source "$(ROOT)/scripts/desktop-macos-env.sh"; \
	fi; \
	cd "$(ROOT)/desktop"; \
	cargo run -p meritscreen-ui

desktop-dev: desktop-ui

desktop-clippy:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	cargo clippy --workspace --all-targets -- -D warnings

desktop-fmt:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	cargo fmt --all -- --check

desktop-perf:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	cargo run -p meritscreen-guardian -- --perf-smoke

desktop-deb:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	BUILD_CONFIG=debug ./packaging/linux/build-deb.sh

desktop-pkg:
	@set -euo pipefail; \
	cd "$(ROOT)/desktop"; \
	BUILD_CONFIG=debug ./packaging/macos/build-pkg.sh

desktop-all: desktop-check desktop-test desktop-conformance desktop-clippy desktop-fmt desktop-a11y desktop-perf

desktop-macos-doctor:
	@set -euo pipefail; \
	"$(SCRIPTS)/desktop-macos-e2e.sh" doctor

desktop-macos:
	@set -euo pipefail; \
	"$(SCRIPTS)/desktop-macos-e2e.sh" smoke

desktop-macos-ui:
	@set -euo pipefail; \
	"$(SCRIPTS)/desktop-macos-e2e.sh" ui

