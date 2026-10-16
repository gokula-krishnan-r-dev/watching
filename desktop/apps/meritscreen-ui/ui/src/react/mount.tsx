import * as React from "react";
import { createRoot, type Root } from "react-dom/client";
import "./tailwind.css";

let root: Root | null = null;
let host: HTMLElement | null = null;

/** Render a React screen into the legacy `#view` container. */
export function mountScreen(container: HTMLElement, node: React.ReactNode): void {
  if (!root || host !== container || !container.querySelector(":scope > .ms-react-root")) {
    unmountScreen();
    container.innerHTML = "";
    const el = document.createElement("div");
    el.className = "ms-react-root";
    container.appendChild(el);
    root = createRoot(el);
    host = container;
  }
  root.render(<React.StrictMode>{node}</React.StrictMode>);
}

/** Called before a legacy (innerHTML) screen renders. */
export function unmountScreen(): void {
  if (root) {
    root.unmount();
    root = null;
    host = null;
  }
}
