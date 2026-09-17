const IMAGE_DATA_URL_PATTERN =
  /^data:(image\/(?:png|jpe?g|jpg|gif|webp));base64,([A-Za-z0-9+/]+={0,2})$/;

const IMAGE_MARKER_PATTERN =
  /^\[\[IMAGE:(data:image\/(?:png|jpe?g|jpg|gif|webp);base64,[A-Za-z0-9+/]+={0,2})\]\]$/;

export interface TextDocumentBlock {
  type: "text";
  content: string;
  start: number;
}

export interface ImageDocumentBlock {
  type: "image";
  dataUrl: string;
  marker: string;
  start: number;
}

export type DocumentBlock =
  | TextDocumentBlock
  | ImageDocumentBlock;

export interface ImageInsertionResult {
  content: string;
  caretOffset: number;
}

function hasValidBase64Payload(dataUrl: string) {
  const match = IMAGE_DATA_URL_PATTERN.exec(dataUrl);
  if (!match) return false;

  const payload = match[2];
  if (!payload) return false;
  return (
    payload.length > 0 &&
    payload.length % 4 === 0
  );
}

export function isSupportedImageDataUrl(
  dataUrl: string,
): boolean {
  return hasValidBase64Payload(dataUrl);
}

export function createImageMarker(dataUrl: string): string {
  if (!isSupportedImageDataUrl(dataUrl)) {
    throw new Error("图片数据格式无效，无法插入文档");
  }
  return `[[IMAGE:${dataUrl}]]`;
}

function parseImageMarker(line: string) {
  const match = IMAGE_MARKER_PATTERN.exec(line);
  if (!match) return null;

  const dataUrl = match[1];
  if (!dataUrl) return null;
  if (!isSupportedImageDataUrl(dataUrl)) return null;

  return { dataUrl, marker: line };
}

/**
 * Parses normalized, line-oriented document content into editable blocks.
 *
 * Only a complete marker occupying an entire line is interpreted as an image.
 * Anything malformed or mixed with text remains an ordinary text block.
 */
export function parseDocumentContent(
  content: string,
): DocumentBlock[] {
  const normalized = content
    .replaceAll("\r\n", "\n")
    .replaceAll("\r", "\n");
  const lines = normalized.split("\n");
  const blocks: DocumentBlock[] = [];
  let textLines: string[] = [];
  let textStart = 0;
  let offset = 0;

  const flushText = () => {
    if (textLines.length === 0) return;
    blocks.push({
      type: "text",
      content: textLines.join("\n"),
      start: textStart,
    });
    textLines = [];
  };

  lines.forEach((line, index) => {
    const image = parseImageMarker(line);
    if (image) {
      flushText();
      blocks.push({
        type: "image",
        dataUrl: image.dataUrl,
        marker: image.marker,
        start: offset,
      });
    } else {
      if (textLines.length === 0) {
        textStart = offset;
      }
      textLines.push(line);
    }

    offset += line.length;
    if (index < lines.length - 1) {
      offset += 1;
    }
  });

  flushText();
  return blocks;
}

export function serializeDocumentContent(
  blocks: readonly DocumentBlock[],
): string {
  return blocks
    .map((block) =>
      block.type === "image"
        ? createImageMarker(block.dataUrl)
        : block.content
            .replaceAll("\r\n", "\n")
            .replaceAll("\r", "\n"),
    )
    .join("\n");
}

export function normalizeDocumentContent(
  content: string,
): string {
  return serializeDocumentContent(parseDocumentContent(content));
}

/**
 * Converts the editor's internal `[[IMAGE:data:...]]` marker syntax into
 * real Markdown image syntax so `content` can be handed to a Markdown
 * renderer (react-markdown) unchanged otherwise.
 */
export function toMarkdownSource(content: string): string {
  return parseDocumentContent(content)
    .map((block) =>
      block.type === "image" ? `![](${block.dataUrl})` : block.content,
    )
    .join("\n\n");
}

/**
 * Inserts an image on its own line and returns the absolute position of the
 * empty line immediately following it.
 */
export function insertImageAtSelection(
  content: string,
  selectionStart: number,
  selectionEnd: number,
  dataUrl: string,
): ImageInsertionResult {
  const normalized = normalizeDocumentContent(content);
  const marker = createImageMarker(dataUrl);
  const boundedStart = Math.max(
    0,
    Math.min(normalized.length, selectionStart),
  );
  const boundedEnd = Math.max(
    0,
    Math.min(normalized.length, selectionEnd),
  );
  const start = Math.min(boundedStart, boundedEnd);
  const end = Math.max(boundedStart, boundedEnd);
  const prefix = normalized.slice(0, start);
  let suffix = normalized.slice(end);

  // The marker supplies the line break at the selection boundary.
  if (suffix.startsWith("\n")) {
    suffix = suffix.slice(1);
  }

  const prefixWithLineBreak =
    prefix.length > 0 && !prefix.endsWith("\n")
      ? `${prefix}\n`
      : prefix;
  const inserted = `${prefixWithLineBreak}${marker}\n`;

  return {
    content: `${inserted}${suffix}`,
    caretOffset: inserted.length,
  };
}
