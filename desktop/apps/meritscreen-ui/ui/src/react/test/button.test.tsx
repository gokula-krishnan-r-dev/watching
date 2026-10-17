import { render, screen } from "@testing-library/react";
import { Button } from "../ui/button";
// No @types/node in this project; minimal local declarations for the one fs call.
// @ts-expect-error no node type declarations available
import { readFileSync } from "node:fs";
declare const process: { cwd(): string };
const css = readFileSync(`${process.cwd()}/src/styles.css`, "utf8") as unknown as string;

function tokensByOrder(): Array<Record<string, string>> {
  // styles.css declares the button tokens three times, in order:
  // light (:root), dark ([data-theme="dark"]), dark via prefers-color-scheme.
  const themes: Array<Record<string, string>> = [{}, {}, {}];
  const seen: Record<string, number> = {};
  for (const m of css.matchAll(/--ms-((?:btn|focus)-[a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    const i = seen[m[1]] ?? 0;
    seen[m[1]] = i + 1;
    if (i < 3) themes[i][m[1]] = m[2];
  }
  return themes;
}
const [lightT, darkT, darkMediaT] = tokensByOrder();
const themes: Record<string, Record<string, string>> = { light: lightT, dark: darkT, darkMedia: darkMediaT };

function lum(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
export function contrast(a: string, b: string): number {
  const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m);
  return (x + 0.05) / (y + 0.05);
}

const PAIRS: Array<[string, string, string]> = [
  ["primary", "btn-primary-fg", "btn-primary-bg"],
  ["primary hover", "btn-primary-hover-fg", "btn-primary-hover-bg"],
  ["secondary", "btn-secondary-fg", "btn-secondary-bg"],
  ["secondary hover", "btn-secondary-hover-fg", "btn-secondary-hover-bg"],
  ["destructive", "btn-destructive-fg", "btn-destructive-bg"],
  ["destructive hover", "btn-destructive-hover-fg", "btn-destructive-hover-bg"],
  ["tab selected", "btn-tab-selected-fg", "btn-tab-selected-bg"],
  ["tab selected hover", "btn-tab-selected-hover-fg", "btn-tab-selected-hover-bg"],
  ["disabled", "btn-disabled-fg", "btn-disabled-bg"],
];

describe("button design tokens (WCAG AA >= 4.5:1)", () => {
  for (const [theme, t] of Object.entries(themes)) {
    it(`${theme}: all button fg/bg pairs pass AA`, () => {
      for (const [name, fg, bg] of PAIRS) {
        expect(t[fg], `${theme} missing ${fg}`).toBeTruthy();
        expect(t[bg], `${theme} missing ${bg}`).toBeTruthy();
        expect(contrast(t[fg], t[bg]), `${theme} ${name} ${t[fg]} on ${t[bg]}`).toBeGreaterThanOrEqual(4.5);
      }
    });
    it(`${theme}: unselected tab + outline/ghost text pass AA on app background`, () => {
      const bg = theme === "light" ? "#fcf9f4" : "#101416";
      expect(contrast(t["btn-tab-fg"], bg)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(t["btn-outline-fg"], bg)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(t["btn-ghost-fg"], bg)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(t["focus-ring"], bg)).toBeGreaterThanOrEqual(3); // non-text focus indicator
    });
  }
  it("dark media-query tokens match the explicit dark theme button tokens", () => {
    for (const [, fg, bg] of PAIRS) {
      expect(themes.darkMedia[fg]).toBe(themes.dark[fg]);
      expect(themes.darkMedia[bg]).toBe(themes.dark[bg]);
    }
  });
});

describe("Button component", () => {
  it("variants use the --ms-btn-* tokens, never pale-mint + white", () => {
    render(
      <>
        <Button>Primary</Button>
        <Button variant="destructive">Delete</Button>
        <Button variant="secondary">Secondary</Button>
      </>,
    );
    expect(screen.getByRole("button", { name: "Primary" }).className).toContain("bg-[var(--ms-btn-primary-bg)]");
    expect(screen.getByRole("button", { name: "Primary" }).className).toContain("text-[var(--ms-btn-primary-fg)]");
    expect(screen.getByRole("button", { name: "Delete" }).className).toContain("bg-[var(--ms-btn-destructive-bg)]");
    expect(screen.getByRole("button", { name: "Secondary" }).className).toContain("bg-[var(--ms-btn-secondary-bg)]");
    for (const el of screen.getAllByRole("button")) expect(el.className).not.toContain("text-white");
  });

  it("tab variant styles selected state from tokens", () => {
    render(
      <Button variant="tab" role="tab" aria-selected={true}>
        Gokul Desktop
      </Button>,
    );
    const tab = screen.getByRole("tab");
    expect(tab.className).toContain("aria-selected:bg-[var(--ms-btn-tab-selected-bg)]");
    expect(tab.className).toContain("aria-selected:text-[var(--ms-btn-tab-selected-fg)]");
  });

  it("loading shows spinner, sets aria-busy and disables; disabled uses token classes", () => {
    render(
      <>
        <Button loading>Save</Button>
        <Button disabled>Off</Button>
      </>,
    );
    const save = screen.getByRole("button", { name: /save/i });
    expect(save).toBeDisabled();
    expect(save).toHaveAttribute("aria-busy", "true");
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Off" }).className).toContain("disabled:bg-[var(--ms-btn-disabled-bg)]");
  });
});

describe("button colour cascade", () => {
  it("the form-control inherit reset lives in @layer base so text-* utilities win", () => {
    const tw = readFileSync(`${process.cwd()}/src/react/tailwind.css`, "utf8") as unknown as string;
    expect(tw.indexOf("@layer base, theme, utilities")).toBeGreaterThanOrEqual(0);
    const base = tw.slice(tw.indexOf("@layer base {"));
    expect(base).toContain(".ms-react-root button");
    expect(base).toContain("color: inherit");
  });
});
