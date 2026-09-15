(() => {
  // Only extract rendered result cards; never read credentials, cookies or sign requests.
  const unwrap = v => v?.value ?? v;
  const visible = e => !!e && e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0;
  const feeds = unwrap(window.__INITIAL_STATE__?.search?.feeds);
  const searchIds = new Set(Array.isArray(feeds) ? feeds.map(f => f.id || f.noteId || f.note_id).filter(Boolean) : []);
  const items = [...document.querySelectorAll('section.note-item, .note-item')].filter(visible).map(card => {
    const link = card.querySelector('a.cover[href]') || card.querySelector('a.title[href]');
    const id = link?.pathname.match(/\/(?:explore|search_result)\/([a-f0-9]{24})\/?$/i)?.[1];
    if (!searchIds.has(id)) return null;
    return {
      url: link?.href || '',
      title: (card.querySelector('.title')?.innerText || '').trim(),
      author: (card.querySelector('.author .name, .author-wrapper .name, .name')?.innerText || '').trim(),
      metric: (card.querySelector('.like-wrapper .count, .count')?.innerText || '').trim(),
      thumbnail: card.querySelector('img')?.currentSrc || ''
    };
  }).filter(e => e && e.url && e.title);
  const text = document.body?.innerText || '';
  const hasMore = unwrap(window.__INITIAL_STATE__?.search?.hasMore);
  return JSON.stringify({items, hasMore: typeof hasMore === 'boolean' ? hasMore : true,
    loginVisible: [...document.querySelectorAll('input[placeholder*=手机号]')].some(visible),
    challenge: /访问频繁|安全验证|请完成验证|滑动滑块/.test(text),
    empty: /没有找到相关|暂无搜索结果|没有搜索到/.test(text)});
})()
