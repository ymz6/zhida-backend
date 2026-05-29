import fs from 'node:fs'
import { fileURLToPath } from 'node:url'

// selector 由插件托管，并且只在预览构建时 emit；不放 public/，避免正式构建复制它。
const selectorFile = fileURLToPath(new URL('./zhida-selector.js', import.meta.url))

// 错误桥脚本内联在应用模块前，确保启动和运行时错误都能回传给 iframe 父页面。
const errorBridgeScript = `
;(function () {
  if (window.__ZHIDA_ERROR_BRIDGE__) {
    return
  }

  window.__ZHIDA_ERROR_BRIDGE__ = true

  function serializeError(error) {
    if (!error) {
      return ''
    }

    if (error instanceof Error) {
      return {
        name: error.name,
        message: error.message,
        stack: error.stack,
      }
    }

    try {
      return JSON.stringify(error)
    } catch (_) {
      return String(error)
    }
  }

  function post(type, payload) {
    if (window.parent && window.parent !== window) {
      window.parent.postMessage({ type: type, payload: payload }, '*')
    }
  }

  window.addEventListener('error', function (event) {
    post('ZHIDA_RUNTIME_ERROR', {
      message: event.message,
      source: event.filename,
      lineno: event.lineno,
      colno: event.colno,
      error: serializeError(event.error),
    })
  })

  var originalConsoleError = console.error
  console.error = function () {
    var message = Array.prototype.slice.call(arguments).map(function (item) {
      if (item instanceof Error) {
        return item.stack || item.message
      }

      if (typeof item === 'string') {
        return item
      }

      try {
        return JSON.stringify(item)
      } catch (_) {
        return String(item)
      }
    }).join(' ')

    post('ZHIDA_CONSOLE_ERROR', { message: message })
    originalConsoleError.apply(console, arguments)
  }
})()
`

export default function zhidaHtmlTransformer() {
  return {
    name: 'zhida-html-transformer',

    transformIndexHtml(html, ctx) {
      if (ctx?.filename && !ctx.filename.endsWith('index.html')) {
        return html
      }

      // 该插件只会在 preview mode 注册，因此这里的注入天然只属于预览构建。
      return [
        {
          tag: 'script',
          attrs: {
            // 使用相对当前 HTML 的路径，避免父站代理预览时请求到站点根目录。
            src: './zhida-selector.js',
          },
          injectTo: 'head',
        },
        {
          tag: 'script',
          children: errorBridgeScript,
          injectTo: 'head',
        },
      ]
    },

    generateBundle() {
      // 输出到 index.html 同级目录，配合 ./zhida-selector.js 在代理路径下加载。
      this.emitFile({
        type: 'asset',
        fileName: 'zhida-selector.js',
        source: fs.readFileSync(selectorFile, 'utf8'),
      })
    },
  }
}
