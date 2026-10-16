import { defineConfig } from "vite";

// Relative base is required for Tauri's custom-protocol asset loader
// (`asset://` / `https://tauri.localhost`). Absolute `/assets/...` paths
// fail silently and produce a blank white WebView.
export default defineConfig({
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
    rollupOptions: {
      output: {
        manualChunks: undefined,
      },
    },
  },
});
