import type { StoredDraft } from "./types";

function draftKey(
  tenantId: number,
  userId: number,
  repositoryId: number,
  defaultTitle: string,
) {
  return `draft:${tenantId}:${userId}:${repositoryId}:${encodeURIComponent(defaultTitle)}`;
}

function sharedDraftKey(
  tenantId: number,
  userId: number,
  repositoryId: number,
) {
  return `draft:${tenantId}:${userId}:${repositoryId}`;
}

function oldestDraftKey(tenantId: number, repositoryId: number) {
  return `draft:${tenantId}:${repositoryId}`;
}

function parseStoredDraft(raw: string | null): StoredDraft | null {
  if (!raw) return null;

  try {
    const value = JSON.parse(raw) as Partial<StoredDraft>;
    if (
      typeof value.title !== "string" ||
      typeof value.content !== "string" ||
      typeof value.defaultTitle !== "string" ||
      !value.defaultTitle.trim()
    ) {
      return null;
    }
    return {
      title: value.title,
      content: value.content,
      defaultTitle: value.defaultTitle,
    };
  } catch {
    return null;
  }
}

export function loadDraft(
  tenantId: number,
  userId: number,
  repositoryId: number,
  defaultTitle: string,
): StoredDraft | null {
  const identifier = defaultTitle.trim();
  if (!identifier) return null;

  try {
    const currentKey = draftKey(
      tenantId,
      userId,
      repositoryId,
      identifier,
    );
    const current = parseStoredDraft(localStorage.getItem(currentKey));
    if (current?.defaultTitle === identifier) return current;

    const legacyKeys = [
      sharedDraftKey(tenantId, userId, repositoryId),
      oldestDraftKey(tenantId, repositoryId),
    ];
    for (const legacyKey of legacyKeys) {
      const legacy = parseStoredDraft(localStorage.getItem(legacyKey));
      if (legacy?.defaultTitle !== identifier) continue;

      localStorage.setItem(currentKey, JSON.stringify(legacy));
      localStorage.removeItem(legacyKey);
      return legacy;
    }
    return null;
  } catch {
    return null;
  }
}

export function saveDraft(
  tenantId: number,
  userId: number,
  repositoryId: number,
  draft: StoredDraft,
) {
  const identifier = draft.defaultTitle.trim();
  if (!identifier) return;

  try {
    localStorage.setItem(
      draftKey(tenantId, userId, repositoryId, identifier),
      JSON.stringify({ ...draft, defaultTitle: identifier }),
    );
  } catch {
    // Local storage may be unavailable or full; editing must remain usable.
  }
}

export function clearDraft(
  tenantId: number,
  userId: number,
  repositoryId: number,
  defaultTitle: string,
) {
  const identifier = defaultTitle.trim();
  if (!identifier) return;

  try {
    localStorage.removeItem(
      draftKey(tenantId, userId, repositoryId, identifier),
    );

    const legacyKeys = [
      sharedDraftKey(tenantId, userId, repositoryId),
      oldestDraftKey(tenantId, repositoryId),
    ];
    for (const legacyKey of legacyKeys) {
      const legacy = parseStoredDraft(localStorage.getItem(legacyKey));
      if (legacy?.defaultTitle === identifier) {
        localStorage.removeItem(legacyKey);
      }
    }
  } catch {
    // Removing a draft is best-effort.
  }
}

export function createDraftIdentifier() {
  return crypto.randomUUID();
}
