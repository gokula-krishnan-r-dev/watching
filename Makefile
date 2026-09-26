# MeritScreen — local Android demo
#
# Android does NOT hot-reload Text/Compose edits by itself.
# Saving a .kt file only updates the emulator after a rebuild+reinstall.
#
#   make dev       Boot 1 emulator + install + launch (once)
#   make up-dev    Boot 2 emulators (parent + child)
#   make watch     Auto-reload on save (lite + fast) ← use while editing UI
#   make reload    One-shot rebuild + reinstall (no watcher)
#   make logs      Stream app logcat (Ctrl+C to stop)
#   make down      Stop emulators
#
# AVDs: MeritScreen_API34_Lite → :5554 | MeritScreen_API34_Child → :5556

SHELL := /bin/bash
.ONESHELL:
.DEFAULT_GOAL := help

ROOT    := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
SCRIPTS := $(ROOT)/scripts
ENV     := source "$(SCRIPTS)/android-env.sh"

export QUIET ?= 1

.PHONY: help dev up-dev watch reload logs down devices doctor

help:
	@printf '%s\n' \
		'MeritScreen local demo' \
		'' \
		'  make dev       Boot 1 emulator, install, launch' \
		'  make up-dev    Boot 2 emulators, install + launch both' \
		'  make watch     Auto rebuild+relaunch when you save .kt files' \
		'  make reload    One-shot rebuild + reinstall (emulator stays up)' \
		'  make logs      Stream MeritScreen logcat (Ctrl+C to stop)' \
		'  make down      Stop all emulators' \
		'  make devices   Show adb devices' \
		'  make doctor    Verify SDK + AVDs' \
		'' \
		'Why edits do not appear instantly:' \
		'  The emulator runs a built APK, not your source files.' \
		'  Change Text → save → watcher (or reload) rebuilds → then you see it.' \
		'' \
		'Fast UI loop:' \
		'  make dev' \
		'  make watch          # leave this running in a terminal' \
		'  edit + save         # APK pushes automatically' \
		'' \
		'Instant Compose Live Edit (optional): open the project in Android Studio' \
		'and enable Live Edit — that pushes UI tweaks without a full APK install.'

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
