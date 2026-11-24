import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The build lands in target/classes/static/status, so Spring Boot serves it at
// /status/ from the packaged jar. `npm run dev` proxies the API to a locally
// running app on :8080.
export default defineConfig({
  plugins: [react()],
  base: '/status/',
  build: {
    outDir: '../target/classes/static/status',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
