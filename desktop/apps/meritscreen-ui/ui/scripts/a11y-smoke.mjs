#!/usr/bin/env node
/**
 * a11y smoke (d10): launcher + quiz + fail lock required string keys + CSS focus rules.
 * Run: npm run a11y-smoke
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const en = JSON.parse(readFileSync(join(root, "src/i18n/en.json"), "utf8"));
const css = readFileSync(join(root, "src/styles.css"), "utf8");
// UI lives in legacy main.ts plus the React layer (src/react); scan both.
import { readdirSync, statSync } from "node:fs";
const collect = (dir) =>
  readdirSync(dir).flatMap((f) => {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) return f === "test" ? [] : collect(p);
    return /\.(ts|tsx)$/.test(f) ? [readFileSync(p, "utf8")] : [];
  });
const main = [readFileSync(join(root, "src/main.ts"), "utf8"), ...collect(join(root, "src/react"))].join("\n");

const required = [
  "a11y_quiz_dialog",
  "a11y_quiz_choices",
  "a11y_fail_lock_dialog",
  "a11y_launcher_apps",
  "a11y_cooldown_timer",
  "child_launcher_title",
  "child_quiz_progress",
  "child_fail_lock_title",
  "child_fail_lock_body",
];

const missing = required.filter((k) => !en[k]);
if (missing.length) {
  console.error("a11y-smoke: missing locale keys:", missing.join(", "));
  process.exit(1);
}

if (!css.includes(":focus-visible")) {
  console.error("a11y-smoke: styles.css missing :focus-visible");
  process.exit(1);
}

for (const needle of [
  'role="dialog"',
  "a11y_cooldown_timer",
  "aria-keyshortcuts",
  "aria-modal",
  "a11y_quiz_choices",
  "a11y_fail_lock_dialog",
  "a11y_launcher_apps",
  "keydown",
]) {
  if (!main.includes(needle)) {
    console.error(`a11y-smoke: UI missing ${needle}`);
    process.exit(1);
  }
}

console.log("a11y-smoke OK (launcher + quiz + fail lock keys, focus, ARIA)");
