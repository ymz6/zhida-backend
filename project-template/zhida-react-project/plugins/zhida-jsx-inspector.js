import { transformSync } from '@babel/core'
import * as t from '@babel/types'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { cwd } from 'node:process'

const SKIPPED_TAGS = new Set(['script', 'style'])
const FRAGMENT_TAGS = new Set(['Fragment', 'React.Fragment'])

function normalizePath(filePath) {
  return filePath.replace(/\\/g, '/')
}

function stripQuery(id) {
  return id.split('?')[0]
}

function makeZhidaId(source, tag) {
  const hash = createHash('sha256').update(`${source}:${tag}`).digest('hex').slice(0, 10)
  return `zda_${hash}`
}

function hasAttribute(openingElement, name) {
  return openingElement.attributes.some((attribute) => {
    return t.isJSXAttribute(attribute) && t.isJSXIdentifier(attribute.name, { name })
  })
}

function addStringAttribute(openingElement, name, value) {
  if (hasAttribute(openingElement, name)) {
    return
  }

  openingElement.attributes.push(
    t.jsxAttribute(t.jsxIdentifier(name), t.stringLiteral(value))
  )
}

function getJsxName(node) {
  if (t.isJSXIdentifier(node)) {
    return node.name
  }

  if (t.isJSXMemberExpression(node)) {
    const objectName = getJsxName(node.object)
    const propertyName = getJsxName(node.property)

    return objectName && propertyName ? `${objectName}.${propertyName}` : null
  }

  if (t.isJSXNamespacedName(node)) {
    return `${node.namespace.name}:${node.name.name}`
  }

  return null
}

function shouldInspectTag(tag) {
  if (!tag || FRAGMENT_TAGS.has(tag)) {
    return false
  }

  return !SKIPPED_TAGS.has(tag.toLowerCase())
}

function createBabelPlugin(relativePath) {
  return {
    name: 'zhida-jsx-inspector-babel-plugin',
    visitor: {
      JSXOpeningElement(babelPath) {
        const openingElement = babelPath.node
        const tag = getJsxName(openingElement.name)

        if (!shouldInspectTag(tag)) {
          return
        }

        const line = openingElement.loc?.start.line
        const column = openingElement.loc?.start.column

        if (!line || column === undefined) {
          return
        }

        const source = `${relativePath}:${line}:${column + 1}`
        const id = makeZhidaId(source, tag)

        // 这些属性只在构建阶段注入；如果源码里已经显式声明，则不重复添加或覆盖。
        // 对组件标签来说，属性能否出现在 DOM 上取决于该组件是否把 props 透传到底层元素。
        addStringAttribute(openingElement, 'data-zhida-id', id)
        addStringAttribute(openingElement, 'data-zhida-source', source)
        addStringAttribute(openingElement, 'data-zhida-tag', tag)
      },
    },
  }
}

export default function zhidaJsxInspector() {
  let root = cwd()

  return {
    name: 'zhida-jsx-inspector',
    enforce: 'pre',

    configResolved(config) {
      root = config.root
    },

    transform(code, id) {
      const filePath = stripQuery(id)
      const normalizedFilePath = normalizePath(filePath)
      const normalizedRoot = normalizePath(root)

      // 预览编辑应指向业务 JSX；shadcn 基础组件会被多处复用，指向它们会让编辑定位落到错误源码层。
      if (!normalizedFilePath.endsWith('.jsx')) {
        return null
      }

      if (!normalizedFilePath.startsWith(`${normalizedRoot}/src/`)) {
        return null
      }

      if (normalizedFilePath.includes('/src/components/ui/')) {
        return null
      }

      // 回传给 Agent 的源码路径以 src 为根，避免重复暴露已固定的编辑范围。
      const sourceRoot = path.join(root, 'src')
      const relativePath = normalizePath(path.relative(sourceRoot, filePath))

      try {
        // 在 React JSX 转换前插入属性，这样还能拿到 JSX 源码位置并尽量保留 sourcemap。
        const result = transformSync(code, {
          ast: false,
          babelrc: false,
          code: true,
          configFile: false,
          filename: filePath,
          generatorOpts: {
            jsescOption: {
              minimal: true,
            },
          },
          parserOpts: {
            plugins: ['jsx'],
            sourceType: 'module',
          },
          plugins: [createBabelPlugin(relativePath)],
          sourceFileName: relativePath,
          sourceMaps: true,
        })

        if (!result?.code) {
          return null
        }

        return {
          code: result.code,
          map: result.map ?? null,
        }
      } catch (error) {
        this.error(
          `zhida-jsx-inspector failed to transform ${relativePath}: ${error.message}`
        )
      }
    },
  }
}
