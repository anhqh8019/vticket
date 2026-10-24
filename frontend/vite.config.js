import { defineConfig } from "vite";
export default defineConfig({
  server: {
    proxy: Object.fromEntries(
      ["/api", "/vietqr", "/api-vietqr-callback"].map((path) => [
        path,
        { target: "http://localhost:8080", changeOrigin: true },
      ]),
    ),
  },
});
