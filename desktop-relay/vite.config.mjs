import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  root: path.join(__dirname, 'src', 'renderer-app'),
  base: './',
  plugins: [preact()],
  build: {
    outDir: path.join(__dirname, 'src', 'renderer', 'dist'),
    emptyOutDir: true,
  },
});
