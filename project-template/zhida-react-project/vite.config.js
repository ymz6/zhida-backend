import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'
import zhidaJsxInspector from './plugins/zhida-jsx-inspector.js'
import zhidaHtmlTransformer from './plugins/zhida-html-transformer.js'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const isPreview = mode === 'preview'

  return {
    base: './',
    plugins: [
      tailwindcss(),
      isPreview ? zhidaJsxInspector() : null,
      react({
        babel: {
          plugins: [['babel-plugin-react-compiler']],
        },
      }),
      isPreview ? zhidaHtmlTransformer() : null,
    ].filter(Boolean),
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
})
