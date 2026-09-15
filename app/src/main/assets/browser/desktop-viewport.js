// Desktop presentation only: never access form values, cookies, or native bridges.
(() => {
  if (window !== window.top || window.__collectionDesktopViewport) return;
  window.__collectionDesktopViewport = true;
  const content = 'width=1200, user-scalable=yes';
  const apply = () => {
    const head = document.head;
    if (!head) return;
    let metas = head.querySelectorAll('meta[name="viewport"]');
    if (!metas.length) {
      const meta = document.createElement('meta');
      meta.name = 'viewport';
      meta.content = content;
      head.appendChild(meta);
      return;
    }
    metas.forEach(meta => { if (meta.content !== content) meta.content = content; });
  };
  const watchHead = () => {
    if (!document.head) return false;
    new MutationObserver(apply).observe(document.head, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['content']
    });
    apply();
    return true;
  };
  if (!watchHead()) {
    const waitForHead = new MutationObserver(() => {
      if (watchHead()) waitForHead.disconnect();
    });
    waitForHead.observe(document, { childList: true, subtree: true });
  }
})();
