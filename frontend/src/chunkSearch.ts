import type { ChunkHit } from "./types";

//一次检索返回多少片段。后端上限 50，默认 4；这里取 8：
//语义检索会被无权限文档挤占名额，多召回一点才够凑出一屏有用结果
export const SEARCH_TOP_K = 8;
export const TOP_K_OPTIONS = [4, 8, 16] as const;

//片段按 800 token 切，实测平均 3400 多字符，整段铺开会把结果页撑成一篇文章
export const SNIPPET_MAX_CHARS = 200;

export interface SearchFileGroup {
  fileId: number;
  fileName: string | null;
  repositoryId: number;
  visible: boolean;
  hits: ChunkHit[];
}

//同一篇文档可能命中多段，按文档归并；分组顺序沿用该文档第一次命中的名次，
//也就是相似度顺序 —— 后端已经按相似度排好，这里不能重排
export function groupHitsByFile(hits: ChunkHit[]): SearchFileGroup[] {
  const groups: SearchFileGroup[] = [];
  const indexByFileId = new Map<number, number>();

  for (const hit of hits) {
    const existingIndex = indexByFileId.get(hit.fileId);
    if (existingIndex === undefined) {
      indexByFileId.set(hit.fileId, groups.length);
      groups.push({
        fileId: hit.fileId,
        fileName: hit.fileName,
        repositoryId: hit.repositoryId,
        visible: hit.visible,
        hits: [hit],
      });
      continue;
    }
    const group = groups[existingIndex];
    if (group) {
      group.hits.push(hit);
    }
  }

  return groups;
}

export function hiddenHitCount(hits: ChunkHit[]) {
  return hits.filter((hit) => !hit.visible).length;
}

//命中数和"看不到几条"要一起说：只报总数会让用户以为结果就这么少，
//而实际上是权限挡掉的
export function searchSummary(hits: ChunkHit[]) {
  if (hits.length === 0) return "没有找到相关内容";
  const hidden = hiddenHitCount(hits);
  const base = `${hits.length} 条相关片段`;
  return hidden > 0 ? `${base} · ${hidden} 条无权限查看` : base;
}

export function documentTitleOf(
  group: SearchFileGroup,
  fallback?: (fileId: number) => string,
) {
  //无权限时后端不下发文件名，用 id 兜底而不是显示空白
  return group.fileName?.trim() || fallback?.(group.fileId) || `文档 #${group.fileId}`;
}

export function chunkPositionLabel(hit: ChunkHit) {
  return `第 ${hit.chunkIndex + 1} 段`;
}

export function isTruncated(
  content: string | null,
  maxChars = SNIPPET_MAX_CHARS,
) {
  return (content?.length ?? 0) > maxChars;
}

export function snippet(
  content: string | null,
  maxChars = SNIPPET_MAX_CHARS,
) {
  const text = content?.trim() ?? "";
  if (!text) return "";
  //按码点截断：直接 slice 会把 emoji 之类的代理对劈成半个字符
  const codePoints = Array.from(text);
  if (codePoints.length <= maxChars) return text;
  return `${codePoints.slice(0, maxChars).join("")}…`;
}

export function normalizeQuery(input: string) {
  return input.trim();
}
