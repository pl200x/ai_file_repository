import assert from "node:assert/strict";
import test from "node:test";
import {
  ALLOWED_IMAGE_TYPES,
  convertImageFileToDataUrl,
  MAX_IMAGE_SIZE_BYTES,
  type ImageFileInput,
} from "./imageUpload.ts";

function makeImageFile(
  type: string,
  bytes: Uint8Array,
  options: {
    size?: number;
    onRead?: () => void;
    readError?: Error;
  } = {},
): ImageFileInput {
  return {
    type,
    size: options.size ?? bytes.byteLength,
    async arrayBuffer() {
      options.onRead?.();
      if (options.readError) throw options.readError;
      return bytes.buffer.slice(
        bytes.byteOffset,
        bytes.byteOffset + bytes.byteLength,
      ) as ArrayBuffer;
    },
  };
}

function imageBytes(type: string) {
  switch (type) {
    case "image/png":
      return new Uint8Array([
        0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
      ]);
    case "image/jpeg":
    case "image/jpg":
      return new Uint8Array([0xff, 0xd8, 0xff, 0xdb]);
    case "image/gif":
      return new Uint8Array([
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
      ]);
    case "image/webp":
      return new Uint8Array([
        0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
        0x57, 0x45, 0x42, 0x50,
      ]);
    default:
      return new Uint8Array([1]);
  }
}

test("common image MIME types are accepted", async (context) => {
  for (const type of ALLOWED_IMAGE_TYPES) {
    await context.test(type, async () => {
      const bytes = imageBytes(type);
      const result = await convertImageFileToDataUrl(
        makeImageFile(type, bytes),
      );
      const expectedType =
        type === "image/jpg" ? "image/jpeg" : type;
      assert.equal(
        result,
        `data:${expectedType};base64,${Buffer.from(bytes).toString("base64")}`,
      );
    });
  }
});

test("unsupported types are rejected before reading", async () => {
  let readCount = 0;
  const file = makeImageFile(
    "image/svg+xml",
    new Uint8Array([1]),
    { onRead: () => readCount++ },
  );

  await assert.rejects(
    convertImageFileToDataUrl(file),
    /不支持该图片格式/,
  );
  assert.equal(readCount, 0);
});

test("oversized images are rejected before reading", async () => {
  let readCount = 0;
  const file = makeImageFile(
    "image/png",
    new Uint8Array([1]),
    {
      size: MAX_IMAGE_SIZE_BYTES + 1,
      onRead: () => readCount++,
    },
  );

  await assert.rejects(
    convertImageFileToDataUrl(file),
    /不能超过 5 MiB/,
  );
  assert.equal(readCount, 0);
});

test("file read failures return a clear error", async () => {
  const file = makeImageFile(
    "image/webp",
    new Uint8Array([1]),
    { readError: new Error("device failure") },
  );

  await assert.rejects(
    convertImageFileToDataUrl(file),
    /读取图片失败/,
  );
});

test("a renamed non-image file is rejected by its signature", async () => {
  const file = makeImageFile(
    "image/png",
    new TextEncoder().encode("<svg></svg>"),
  );

  await assert.rejects(
    convertImageFileToDataUrl(file),
    /图片内容与文件格式不匹配/,
  );
});

test("empty images are rejected with a clear error", async () => {
  const file = makeImageFile("image/gif", new Uint8Array());

  await assert.rejects(
    convertImageFileToDataUrl(file),
    /图片文件为空或大小无效/,
  );
});
