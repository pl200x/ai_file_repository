import assert from "node:assert/strict";
import test from "node:test";
import {
  clearDraft,
  createDraftIdentifier,
  loadDraft,
  saveDraft,
} from "./draftStorage.ts";

class MemoryStorage implements Storage {
  readonly values = new Map<string, string>();

  get length() {
    return this.values.size;
  }

  clear() {
    this.values.clear();
  }

  getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  key(index: number) {
    return [...this.values.keys()][index] ?? null;
  }

  removeItem(key: string) {
    this.values.delete(key);
  }

  setItem(key: string, value: string) {
    this.values.set(key, value);
  }
}

function installStorage() {
  const storage = new MemoryStorage();
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: storage,
  });
  return storage;
}

test("draft identifiers are random UUIDs", () => {
  const first = createDraftIdentifier();
  const second = createDraftIdentifier();
  const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  assert.match(first, uuidPattern);
  assert.match(second, uuidPattern);
  assert.notEqual(first, second);
});

test("drafts in one repository are isolated by defaultTitle", () => {
  installStorage();
  saveDraft(1, 2, 3, {
    title: "First",
    content: "First body",
    defaultTitle: "draft-one",
  });
  saveDraft(1, 2, 3, {
    title: "Second",
    content: "Second body",
    defaultTitle: "draft-two",
  });

  assert.equal(
    loadDraft(1, 2, 3, "draft-one")?.content,
    "First body",
  );
  assert.equal(
    loadDraft(1, 2, 3, "draft-two")?.content,
    "Second body",
  );

  clearDraft(1, 2, 3, "draft-one");
  assert.equal(loadDraft(1, 2, 3, "draft-one"), null);
  assert.equal(
    loadDraft(1, 2, 3, "draft-two")?.content,
    "Second body",
  );
});

test("matching shared-key drafts are migrated to the concrete key", () => {
  const storage = installStorage();
  const legacyKey = "draft:1:2:3";
  storage.setItem(
    legacyKey,
    JSON.stringify({
      title: "Legacy",
      content: "Recovered",
      defaultTitle: "legacy-chain",
    }),
  );

  assert.equal(
    loadDraft(1, 2, 3, "legacy-chain")?.content,
    "Recovered",
  );
  assert.equal(storage.getItem(legacyKey), null);
  assert.equal(
    loadDraft(1, 2, 3, "legacy-chain")?.title,
    "Legacy",
  );
});

test("legacy drafts for another chain are not consumed or cleared", () => {
  const storage = installStorage();
  const legacyKey = "draft:1:2:3";
  storage.setItem(
    legacyKey,
    JSON.stringify({
      title: "Other",
      content: "Keep me",
      defaultTitle: "other-chain",
    }),
  );

  assert.equal(loadDraft(1, 2, 3, "requested-chain"), null);
  clearDraft(1, 2, 3, "requested-chain");
  assert.notEqual(storage.getItem(legacyKey), null);
});
