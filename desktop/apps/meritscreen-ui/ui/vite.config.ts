import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// Relative base is required for Tauri's custom-protocol asset loader
// (`asset://` / `https://tauri.localhost`). Absolute `/assets/...` paths
// fail silently and produce a blank white WebView.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  clearScreen: false,
  base: "./",
  server: {
    port: 1420,
    strictPort: true,
  },
  build: {
    target: "es2022",
    outDir: "dist",
    emptyOutDir: true,
    minify: "esbuild",
    cssMinify: true,
    sourcemap: false,
    chunkSizeWarningLimit: 800,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/react/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
