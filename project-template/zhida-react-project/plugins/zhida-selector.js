;(function () {
  if (window.__ZHIDA_SELECTOR__) {
    return
  }

  window.__ZHIDA_SELECTOR__ = true

  var editMode = false
  var overlay = null
  var activeElement = null

  // 懒创建高亮框；父页面未显式开启编辑模式时，预览页不会产生可见或行为成本。
  function getOverlay() {
    if (overlay) {
      return overlay
    }

    overlay = document.createElement('div')
    overlay.setAttribute('data-zhida-selector-overlay', 'true')
    overlay.style.position = 'fixed'
    overlay.style.zIndex = '2147483647'
    overlay.style.pointerEvents = 'none'
    overlay.style.boxSizing = 'border-box'
    overlay.style.border = '2px solid #2563eb'
    overlay.style.background = 'rgba(37, 99, 235, 0.08)'
    overlay.style.borderRadius = '4px'
    overlay.style.display = 'none'
    document.documentElement.appendChild(overlay)

    return overlay
  }

  function hideOverlay() {
    if (overlay) {
      overlay.style.display = 'none'
    }

    activeElement = null
  }

  function removeOverlay() {
    if (overlay && overlay.parentNode) {
      overlay.parentNode.removeChild(overlay)
    }

    overlay = null
    activeElement = null
  }

  function updateOverlay(element) {
    if (!element) {
      hideOverlay()
      return
    }

    activeElement = element

    var rect = element.getBoundingClientRect()
    var box = getOverlay()
    box.style.display = 'block'
    box.style.top = rect.top + 'px'
    box.style.left = rect.left + 'px'
    box.style.width = rect.width + 'px'
    box.style.height = rect.height + 'px'
  }

  function findInspectableElement(target) {
    if (!target || target.nodeType !== 1) {
      return null
    }

    // 优先选择最近的已插桩祖先，因为用户可能点到没有源码元信息的文本或图标节点。
    return target.closest('[data-zhida-source]')
  }

  function truncateText(value) {
    var text = value == null ? '' : String(value)
    return text.length > 1000 ? text.slice(0, 1000) : text
  }

  function buildElementInfo(element) {
    // 保留源码定位和父页面展示需要的信息，避免传递运行时 DOM 细节造成噪音。
    return {
      id: element.getAttribute('data-zhida-id') || '',
      source: element.getAttribute('data-zhida-source') || '',
      tag: element.getAttribute('data-zhida-tag') || element.tagName.toLowerCase(),
      text: truncateText(element.innerText || element.textContent || ''),
    }
  }

  function postSelectedElement(element) {
    if (!window.parent || window.parent === window) {
      return
    }

    // 通过父页面消息通信，让预览应用不依赖具体编辑器 UI 或后端传输实现。
    window.parent.postMessage(
      {
        type: 'ZHIDA_ELEMENT_SELECTED',
        payload: buildElementInfo(element),
      },
      '*'
    )
  }

  function handleMouseMove(event) {
    if (!editMode) {
      return
    }

    updateOverlay(findInspectableElement(event.target))
  }

  function handleMouseOut(event) {
    if (!editMode || !activeElement) {
      return
    }

    var relatedTarget = event.relatedTarget
    if (!relatedTarget || !activeElement.contains(relatedTarget)) {
      hideOverlay()
    }
  }

  function handleClick(event) {
    if (!editMode) {
      return
    }

    // 在捕获阶段拦截点击，避免取元素时触发链接、表单或业务点击处理。
    event.preventDefault()
    event.stopPropagation()

    if (typeof event.stopImmediatePropagation === 'function') {
      event.stopImmediatePropagation()
    }

    var element = findInspectableElement(event.target)
    if (!element) {
      return
    }

    updateOverlay(element)
    postSelectedElement(element)
  }

  function refreshOverlay() {
    if (editMode && activeElement) {
      updateOverlay(activeElement)
    }
  }

  function addListeners() {
    document.addEventListener('mousemove', handleMouseMove, true)
    document.addEventListener('mouseout', handleMouseOut, true)
    document.addEventListener('click', handleClick, true)
    window.addEventListener('scroll', refreshOverlay, true)
    window.addEventListener('resize', refreshOverlay, true)
  }

  function removeListeners() {
    document.removeEventListener('mousemove', handleMouseMove, true)
    document.removeEventListener('mouseout', handleMouseOut, true)
    document.removeEventListener('click', handleClick, true)
    window.removeEventListener('scroll', refreshOverlay, true)
    window.removeEventListener('resize', refreshOverlay, true)
  }

  function enableEditMode() {
    if (editMode) {
      return
    }

    editMode = true
    addListeners()
  }

  function disableEditMode() {
    if (!editMode) {
      return
    }

    editMode = false
    removeListeners()
    removeOverlay()
  }

  window.addEventListener('message', function (event) {
    var data = event.data || {}

    if (data.type === 'ZHIDA_ENABLE_EDIT_MODE') {
      enableEditMode()
    }

    if (data.type === 'ZHIDA_DISABLE_EDIT_MODE') {
      disableEditMode()
    }
  })
})()
