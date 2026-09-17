import type { AppRoute } from "./types";

export function parseRoute(pathname: string): AppRoute {
  const normalized = pathname.replace(/\/+$/, "") || "/";
  if (normalized === "/trash") {
    return { mode: "trash", repoId: null, fileId: null };
  }
  if (normalized === "/notifications") {
    return { mode: "notifications", repoId: null, fileId: null };
  }
  if (normalized === "/search") {
    return { mode: "search", repoId: null, fileId: null };
  }
  if (normalized === "/assistant") {
    return { mode: "assistant", repoId: null, fileId: null };
  }

  const filePermissionsMatch = normalized.match(
    /^\/repo\/(\d+)\/file\/(\d+)\/permissions$/,
  );
  if (filePermissionsMatch?.[1] && filePermissionsMatch[2]) {
    const repoId = Number(filePermissionsMatch[1]);
    const fileId = Number(filePermissionsMatch[2]);
    return {
      mode: "permissions",
      repoId,
      fileId,
      targetType: "FILE",
      targetId: fileId,
    };
  }

  const repositoryPermissionsMatch = normalized.match(
    /^\/repo\/(\d+)\/permissions$/,
  );
  if (repositoryPermissionsMatch?.[1]) {
    const repoId = Number(repositoryPermissionsMatch[1]);
    return {
      mode: "permissions",
      repoId,
      fileId: null,
      targetType: "KNOWLEDGE_REPOSITORY",
      targetId: repoId,
    };
  }

  const newMatch = normalized.match(
    /^\/repo\/(\d+)\/new\/([^/]+)\/(\d+)$/,
  );
  if (newMatch?.[1] && newMatch[2] && newMatch[3]) {
    try {
      const defaultTitle = decodeURIComponent(newMatch[2]).trim();
      const versionNo = Number(newMatch[3]);
      if (
        defaultTitle &&
        Number.isSafeInteger(versionNo) &&
        versionNo > 0
      ) {
        return {
          mode: "new",
          repoId: Number(newMatch[1]),
          fileId: null,
          defaultTitle,
          versionNo,
        };
      }
    } catch {
      // Invalid encoded identifiers do not represent an editable draft.
    }
  }

  const fileMatch = normalized.match(/^\/repo\/(\d+)\/file\/(\d+)$/);
  if (fileMatch?.[1] && fileMatch[2]) {
    return {
      mode: "edit",
      repoId: Number(fileMatch[1]),
      fileId: Number(fileMatch[2]),
    };
  }

  const repositoryMatch = normalized.match(/^\/repo\/(\d+)$/);
  if (repositoryMatch?.[1]) {
    return {
      mode: "repository",
      repoId: Number(repositoryMatch[1]),
      fileId: null,
    };
  }

  return { mode: "home", repoId: null, fileId: null };
}

export const routes = {
  trash: "/trash",
  notifications: "/notifications",
  search: "/search",
  assistant: "/assistant",
  repository: (repositoryId: number) => `/repo/${repositoryId}`,
  repositoryPermissions: (repositoryId: number) =>
    `/repo/${repositoryId}/permissions`,
  newDocument: (
    repositoryId: number,
    defaultTitle: string,
    versionNo: number,
  ) =>
    `/repo/${repositoryId}/new/${encodeURIComponent(defaultTitle)}/${versionNo}`,
  document: (repositoryId: number, fileId: number) =>
    `/repo/${repositoryId}/file/${fileId}`,
  filePermissions: (repositoryId: number, fileId: number) =>
    `/repo/${repositoryId}/file/${fileId}/permissions`,
};
