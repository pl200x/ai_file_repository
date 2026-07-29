import assert from "node:assert/strict";
import test from "node:test";
import {
  draftFromVersion,
  isEmptyDraft,
  latestFileVersion,
} from "./versionRecovery.ts";
import type { FileVersion } from "./types.ts";

function version(
  versionNo: number,
  title: string,
  content?: string,
): FileVersion {
  return {
    id: versionNo,
    fileId: null,
    versionNo,
    title,
    content,
    editorId: 1,
    defaultTitle: "draft-chain",
  };
}

test("the latest version is selected by versionNo, not array order", () => {
  assert.equal(
    latestFileVersion([
      version(2, "v2"),
      version(5, "v5"),
      version(3, "v3"),
    ])?.versionNo,
    5,
  );
  assert.equal(latestFileVersion([]), null);
});

test("a stored version restores its title and content", () => {
  assert.deepEqual(
    draftFromVersion(version(3, "Recovered", "Latest content")),
    {
      title: "Recovered",
      content: "Latest content",
    },
  );
});

test("only drafts without meaningful local title or content are empty", () => {
  assert.equal(isEmptyDraft({ title: "", content: "\n " }), true);
  assert.equal(isEmptyDraft({ title: "", content: "text" }), false);
  assert.equal(isEmptyDraft({ title: "title", content: "" }), false);
});
