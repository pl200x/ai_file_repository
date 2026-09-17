import type {
  DraftContent,
  FileVersion,
} from "./types";

export function latestFileVersion(
  versions: readonly FileVersion[],
): FileVersion | null {
  let latest: FileVersion | null = null;
  for (const version of versions) {
    if (!latest || version.versionNo > latest.versionNo) {
      latest = version;
    }
  }
  return latest;
}

export function draftFromVersion(
  version: FileVersion,
): DraftContent {
  return {
    title: version.title ?? "",
    content: version.content ?? "",
  };
}

export function isEmptyDraft(draft: DraftContent) {
  return !draft.title.trim() && !draft.content.trim();
}

export function sameDraft(left: DraftContent, right: DraftContent) {
  return left.title === right.title && left.content === right.content;
}

//自动保存算个人草稿，所以链头不是自己写的就不该去取它的正文：
//有读权限的人本来就能读任意版本，取了就等于把别人未提交的内容摆出来
export function shouldLoadChainHeadContent(
  chainHead: FileVersion,
  userId: number,
) {
  return chainHead.editorId === userId;
}

//链头是自己写的时候，它可能是"我未提交的修改"，也可能就是我上次的正式提交。
//files.content 只在正式提交时更新，两者一致就说明没有待恢复的草稿
export function pendingOwnDraft(
  chainHead: FileVersion,
  chainHeadDraft: DraftContent | null,
  committed: DraftContent,
  userId: number,
): DraftContent | null {
  if (!shouldLoadChainHeadContent(chainHead, userId)) return null;
  if (!chainHeadDraft) return null;
  return sameDraft(chainHeadDraft, committed) ? null : chainHeadDraft;
}
