export const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

export const ALLOWED_IMAGE_TYPES = [
  "image/png",
  "image/jpeg",
  "image/jpg",
  "image/gif",
  "image/webp",
] as const;

export interface ImageFileInput {
  readonly type: string;
  readonly size: number;
  arrayBuffer(): Promise<ArrayBuffer>;
}

const allowedTypes = new Set<string>(ALLOWED_IMAGE_TYPES);

function normalizedMimeType(type: string) {
  const normalized = type.trim().toLowerCase();
  return normalized === "image/jpg" ? "image/jpeg" : normalized;
}

export function validateImageFile(file: ImageFileInput): string {
  const sourceType = file.type.trim().toLowerCase();
  if (!allowedTypes.has(sourceType)) {
    throw new Error(
      "不支持该图片格式，请选择 PNG、JPEG、JPG、GIF 或 WebP 图片",
    );
  }
  if (!Number.isFinite(file.size) || file.size <= 0) {
    throw new Error("图片文件为空或大小无效");
  }
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    throw new Error("图片过大，单张图片不能超过 5 MiB");
  }
  return normalizedMimeType(sourceType);
}

function bytesToBase64(bytes: Uint8Array) {
  const chunkSize = 0x8000;
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(
      ...bytes.subarray(offset, offset + chunkSize),
    );
  }
  return btoa(binary);
}

function startsWithBytes(
  bytes: Uint8Array,
  signature: readonly number[],
) {
  return (
    bytes.length >= signature.length &&
    signature.every((value, index) => bytes[index] === value)
  );
}

function hasExpectedImageSignature(
  mimeType: string,
  bytes: Uint8Array,
) {
  switch (mimeType) {
    case "image/png":
      return startsWithBytes(bytes, [
        0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
      ]);
    case "image/jpeg":
      return startsWithBytes(bytes, [0xff, 0xd8, 0xff]);
    case "image/gif":
      return (
        startsWithBytes(bytes, [
          0x47, 0x49, 0x46, 0x38, 0x37, 0x61,
        ]) ||
        startsWithBytes(bytes, [
          0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
        ])
      );
    case "image/webp":
      return (
        startsWithBytes(bytes, [0x52, 0x49, 0x46, 0x46]) &&
        bytes[8] === 0x57 &&
        bytes[9] === 0x45 &&
        bytes[10] === 0x42 &&
        bytes[11] === 0x50
      );
    default:
      return false;
  }
}

export async function convertImageFileToDataUrl(
  file: ImageFileInput,
): Promise<string> {
  // These checks deliberately happen before reading the file.
  const mimeType = validateImageFile(file);

  let buffer: ArrayBuffer;
  try {
    buffer = await file.arrayBuffer();
  } catch {
    throw new Error("读取图片失败，请重新选择图片后再试");
  }

  if (buffer.byteLength > MAX_IMAGE_SIZE_BYTES) {
    throw new Error("图片过大，单张图片不能超过 5 MiB");
  }
  if (buffer.byteLength === 0) {
    throw new Error("读取图片失败：图片内容为空");
  }

  const bytes = new Uint8Array(buffer);
  if (!hasExpectedImageSignature(mimeType, bytes)) {
    throw new Error(
      "图片内容与文件格式不匹配，请选择有效的 PNG、JPEG、JPG、GIF 或 WebP 图片",
    );
  }

  try {
    return `data:${mimeType};base64,${bytesToBase64(bytes)}`;
  } catch {
    throw new Error("图片转换为 Base64 失败，请重新选择图片后再试");
  }
}
