import assert from "node:assert/strict";
import test from "node:test";
import {
  assertPdfSignature,
  MAX_PDF_SIZE_BYTES,
  validatePdfFile,
  type PdfFileInput,
} from "./pdfUpload.ts";

function makePdfFile(
  type: string,
  bytes: Uint8Array,
  options: { size?: number } = {},
): PdfFileInput {
  return {
    type,
    size: options.size ?? bytes.byteLength,
    slice(start: number, end: number) {
      const sliced = bytes.slice(start, end);
      return {
        async arrayBuffer() {
          return sliced.buffer.slice(
            sliced.byteOffset,
            sliced.byteOffset + sliced.byteLength,
          ) as ArrayBuffer;
        },
      };
    },
  };
}

const PDF_HEADER = new TextEncoder().encode("%PDF-1.7\n%rest of file");

test("a normal PDF passes both checks", async () => {
  const file = makePdfFile("application/pdf", PDF_HEADER);
  validatePdfFile(file);
  await assertPdfSignature(file);
});

test("non-pdf content types are rejected", () => {
  const file = makePdfFile("text/plain", PDF_HEADER);
  assert.throws(() => validatePdfFile(file), /只支持上传 PDF 文件/);
});

test("empty uploads are rejected with a clear error", () => {
  const file = makePdfFile("application/pdf", new Uint8Array(), { size: 0 });
  assert.throws(() => validatePdfFile(file), /为空或大小无效/);
});

test("uploads over the size limit are rejected", () => {
  const file = makePdfFile("application/pdf", PDF_HEADER, {
    size: MAX_PDF_SIZE_BYTES + 1,
  });
  assert.throws(() => validatePdfFile(file), /不能超过 20 MB/);
});

test("a renamed non-pdf file is rejected by its signature", async () => {
  const file = makePdfFile(
    "application/pdf",
    new TextEncoder().encode("<html></html>"),
  );

  await assert.rejects(
    assertPdfSignature(file),
    /文件内容不是有效的 PDF/,
  );
});
