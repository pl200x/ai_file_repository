export const MAX_MARKDOWN_SIZE_BYTES = 5 * 1024 * 1024;

const ALLOWED_EXTENSIONS = [".md", ".markdown"];

export interface MarkdownFileInput {
  readonly name: string;
  readonly size: number;
}

// content-type 对 .md 文件不可靠（不同浏览器/系统会报 text/markdown、
// text/plain 甚至空字符串），改按文件名后缀判断，和后端 MarkdownExtractionService 一致
export function validateMarkdownFile(file: MarkdownFileInput): void {
  const lowerName = file.name.trim().toLowerCase();
  if (!ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext))) {
    throw new Error("只支持上传 .md / .markdown 文件");
  }
  if (!Number.isFinite(file.size) || file.size <= 0) {
    throw new Error("Markdown 文件为空或大小无效");
  }
  if (file.size > MAX_MARKDOWN_SIZE_BYTES) {
    throw new Error(
      `Markdown 文件过大，单个文件不能超过 ${MAX_MARKDOWN_SIZE_BYTES / 1024 / 1024} MB`,
    );
  }
}
