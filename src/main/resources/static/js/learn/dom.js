const SVG_NS = 'http://www.w3.org/2000/svg';

export function h(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === null || value === undefined || value === false) {
      continue;
    }
    if (key === 'class') {
      node.className = value;
    } else if (key === 'text') {
      node.textContent = value;
    } else if (key === 'on') {
      Object.entries(value).forEach(([event, handler]) => node.addEventListener(event, handler));
    } else if (key === 'dataset') {
      Object.assign(node.dataset, value);
    } else if (key in node && typeof value !== 'string') {
      node[key] = value;
    } else {
      node.setAttribute(key, value === true ? '' : value);
    }
  }
  node.append(...children.flat().filter(child => child !== null && child !== undefined && child !== false));
  return node;
}

export function icon(...paths) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  for (const d of paths) {
    const path = document.createElementNS(SVG_NS, 'path');
    path.setAttribute('d', d);
    svg.append(path);
  }
  return svg;
}

export const ICONS = {
  back: 'M15 6l-6 6 6 6',
  next: 'M9 6l6 6-6 6',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  search: 'M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM20 20l-4.8-4.8',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  replay: 'M4 12a8 8 0 1 0 2.4-5.7M4 4v4.5h4.5'
};

export function badge(level) {
  return h('span', { class: `badge ${level}`, text: level });
}

export function jp(tag, text, props = {}) {
  return h(tag, { ...props, lang: 'ja', text });
}
