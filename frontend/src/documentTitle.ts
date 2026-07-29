import { parseDocumentContent } from "./documentContent.ts";

export const UNTITLED_DOCUMENT_TITLE = "未命名文档";
export const AUTO_TITLE_MAX_LENGTH = 50;

export interface SubmitTitle {
  title: string;
  autoTitle: boolean;
}

function truncateTitle(value: string) {
  return Array.from(value).slice(0, AUTO_TITLE_MAX_LENGTH).join("");
}

export function resolveSubmitTitle(
  enteredTitle: string,
  content: string,
): SubmitTitle {
  const userTitle = enteredTitle.trim();
  if (userTitle) {
    return { title: userTitle, autoTitle: false };
  }

  for (const block of parseDocumentContent(content)) {
    if (block.type !== "text") continue;

    for (const line of block.content.split("\n")) {
      const candidate = line.trim().replace(/\s+/gu, " ");
      if (candidate) {
        return {
          title: truncateTitle(candidate),
          autoTitle: true,
        };
      }
    }
  }

  return {
    title: UNTITLED_DOCUMENT_TITLE,
    autoTitle: true,
  };
}
