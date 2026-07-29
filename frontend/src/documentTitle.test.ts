import assert from "node:assert/strict";
import test from "node:test";
import {
  AUTO_TITLE_MAX_LENGTH,
  resolveSubmitTitle,
  UNTITLED_DOCUMENT_TITLE,
} from "./documentTitle.ts";

const IMAGE_MARKER =
  "[[IMAGE:data:image/png;base64,iVBORw0KGgo=]]";

test("a user-entered title wins and is not marked as automatic", () => {
  assert.deepEqual(
    resolveSubmitTitle("  Project notes  ", "First paragraph"),
    {
      title: "Project notes",
      autoTitle: false,
    },
  );
});

test("an empty title uses the first non-empty text line", () => {
  assert.deepEqual(
    resolveSubmitTitle(
      "   ",
      `\n${IMAGE_MARKER}\n\n  First   useful\tline  \nSecond line`,
    ),
    {
      title: "First useful line",
      autoTitle: true,
    },
  );
});

test("image-only and empty documents use the untitled base name", () => {
  assert.deepEqual(resolveSubmitTitle("", IMAGE_MARKER), {
    title: UNTITLED_DOCUMENT_TITLE,
    autoTitle: true,
  });
  assert.deepEqual(resolveSubmitTitle("", "\n \n"), {
    title: UNTITLED_DOCUMENT_TITLE,
    autoTitle: true,
  });
});

test("automatic titles are capped without splitting Unicode code points", () => {
  const content = "文".repeat(AUTO_TITLE_MAX_LENGTH + 10);
  const result = resolveSubmitTitle("", content);

  assert.equal(
    Array.from(result.title).length,
    AUTO_TITLE_MAX_LENGTH,
  );
  assert.equal(result.autoTitle, true);
});
