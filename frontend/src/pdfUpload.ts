export const MAX_PDF_SIZE_BYTES = 20 * 1024 * 1024;

const PDF_SIGNATURE = [0x25, 0x50, 0x44, 0x46, 0x2d]; // "%PDF-"

export interface PdfFileInput {
  readonly type: string;
  readonly size: number;
  slice(start: number, end: number): { arrayBuffer(): Promise<ArrayBuffer> };
}

export function validatePdfFile(file: PdfFileInput): void {
  const sourceType = file.type.trim().toLowerCase();
  if (sourceType !== "application/pdf") {
    throw new Error("只支持上传 PDF 文件");
  }
  if (!Number.isFinite(file.size) || file.size <= 0) {
    throw new Error("PDF 文件为空或大小无效");
  }
  if (file.size > MAX_PDF_SIZE_BYTES) {
    throw new Error(
      `PDF 过大，单个文件不能超过 ${MAX_PDF_SIZE_BYTES / 1024 / 1024} MB`,
    );
  }
}

function hasPdfSignature(bytes: Uint8Array): boolean {
  return (
    bytes.length >= PDF_SIGNATURE.length &&
    PDF_SIGNATURE.every((value, index) => bytes[index] === value)
  );
}

// 只读文件头几个字节做签名校验，不必把整份 PDF 读进内存——
// 和 imageUpload.ts 里"改后缀伪装"的防护思路一致
export async function assertPdfSignature(file: PdfFileInput): Promise<void> {
  let buffer: ArrayBuffer;
  try {
    buffer = await file.slice(0, PDF_SIGNATURE.length).arrayBuffer();
  } catch {
    throw new Error("读取 PDF 失败，请重新选择文件后再试");
  }
  if (!hasPdfSignature(new Uint8Array(buffer))) {
    throw new Error("文件内容不是有效的 PDF，请检查后重新选择");
  }
}
