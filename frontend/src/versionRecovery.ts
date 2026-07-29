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
