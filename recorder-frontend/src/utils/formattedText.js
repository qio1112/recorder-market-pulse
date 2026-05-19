const HEADING_PATTERN = /^(#{1,3})\s+(.+)$/;

export function parseBoldSpans(text) {
  const value = typeof text === 'string' ? text : '';
  const spans = [];
  let cursor = 0;

  while (cursor < value.length) {
    const start = value.indexOf('**', cursor);
    if (start === -1) {
      spans.push({ text: value.slice(cursor), bold: false });
      break;
    }

    const end = value.indexOf('**', start + 2);
    if (end === -1) {
      spans.push({ text: value.slice(cursor), bold: false });
      break;
    }

    if (start > cursor) {
      spans.push({ text: value.slice(cursor, start), bold: false });
    }

    spans.push({ text: value.slice(start + 2, end), bold: true });
    cursor = end + 2;
  }

  return spans.length ? spans : [{ text: '', bold: false }];
}

export function parseFormattedText(text) {
  const value = typeof text === 'string' ? text : '';

  return value.split(/\r?\n/).map((line) => {
    const headingMatch = line.match(HEADING_PATTERN);
    const level = headingMatch ? headingMatch[1].length : 0;
    const content = headingMatch ? headingMatch[2] : line;

    return {
      level,
      spans: parseBoldSpans(content)
    };
  });
}
