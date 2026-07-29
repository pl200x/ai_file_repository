import assert from "node:assert/strict";
import test from "node:test";
import {
  insertImageAtSelection,
  normalizeDocumentContent,
  parseDocumentContent,
  serializeDocumentContent,
  type DocumentBlock,
} from "./documentContent.ts";

const PNG_DATA_URL = "data:image/png;base64,iVBORw0KGgo=";
const JPEG_DATA_URL = "data:image/jpeg;base64,/9j/2Q==";
const PNG_MARKER = `[[IMAGE:${PNG_DATA_URL}]]`;
const JPEG_MARKER = `[[IMAGE:${JPEG_DATA_URL}]]`;

test("text-only documents remain unchanged", () => {
  const content = "First paragraph\n\nSecond paragraph";
  const blocks = parseDocumentContent(content);

  assert.deepEqual(blocks, [
    { type: "text", content, start: 0 },
  ]);
  assert.equal(serializeDocumentContent(blocks), content);
});

test("a valid marker on its own line is parsed as one image", () => {
  const content = `Before\n${PNG_MARKER}\nAfter`;
  const blocks = parseDocumentContent(content);

  assert.deepEqual(
    blocks.map((block) => block.type),
    ["text", "image", "text"],
  );
  assert.deepEqual(blocks[1], {
    type: "image",
    dataUrl: PNG_DATA_URL,
    marker: PNG_MARKER,
    start: "Before\n".length,
  });
  assert.equal(serializeDocumentContent(blocks), content);
});

test("multiple images and text preserve their exact order", () => {
  const content =
    `Top\n\n${PNG_MARKER}\nMiddle\n${JPEG_MARKER}\n\nBottom\n`;
  const blocks = parseDocumentContent(content);

  assert.deepEqual(
    blocks.map((block) => block.type),
    ["text", "image", "text", "image", "text"],
  );
  assert.equal(
    blocks.filter((block) => block.type === "image").length,
    2,
  );
  assert.equal(serializeDocumentContent(blocks), content);
});

test("an image document can be updated and receive another image", () => {
  const original = `Before\n${PNG_MARKER}\nAfter`;
  const updatedBlocks: DocumentBlock[] =
    parseDocumentContent(original).map((block) =>
      block.type === "text" && block.content === "After"
        ? { ...block, content: "Updated after" }
        : block,
    );
  const updated = serializeDocumentContent(updatedBlocks);

  assert.equal(updated, `Before\n${PNG_MARKER}\nUpdated after`);

  const insertion = insertImageAtSelection(
    updated,
    updated.length,
    updated.length,
    JPEG_DATA_URL,
  );
  assert.equal(
    insertion.content,
    `Before\n${PNG_MARKER}\nUpdated after\n${JPEG_MARKER}\n`,
  );
  assert.equal(insertion.caretOffset, insertion.content.length);
  assert.deepEqual(
    parseDocumentContent(insertion.content).map(
      (block) => block.type,
    ),
    ["text", "image", "text", "image", "text"],
  );
});

test("insertion splits a text line and leaves the caret below the image", () => {
  const insertion = insertImageAtSelection(
    "hello world",
    5,
    5,
    PNG_DATA_URL,
  );
  const expectedPrefix = `hello\n${PNG_MARKER}\n`;

  assert.equal(insertion.content, `${expectedPrefix} world`);
  assert.equal(insertion.caretOffset, expectedPrefix.length);
  assert.equal(
    insertion.content[insertion.caretOffset - 1],
    "\n",
  );
});

test("malformed and inline markers are rendered as ordinary text", () => {
  const malformed = [
    "[[IMAGE:data:image/png;base64,not-valid!*]]",
    `prefix ${PNG_MARKER}`,
    `${PNG_MARKER} suffix`,
    `[[image:${PNG_DATA_URL}]]`,
    "[[IMAGE:data:image/svg+xml;base64,PHN2Zz4=]]",
  ].join("\n");
  const blocks = parseDocumentContent(malformed);

  assert.deepEqual(blocks, [
    { type: "text", content: malformed, start: 0 },
  ]);
  assert.equal(serializeDocumentContent(blocks), malformed);
});

test("normalization keeps content while converting line endings", () => {
  assert.equal(
    normalizeDocumentContent(`Before\r\n${PNG_MARKER}\rAfter`),
    `Before\n${PNG_MARKER}\nAfter`,
  );
});
