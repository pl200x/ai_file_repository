import assert from "node:assert/strict";
import test from "node:test";
import {
  draftFromVersion,
  isEmptyDraft,
  latestFileVersion,
  pendingOwnDraft,
  shouldLoadChainHeadContent,
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

function editorVersion(
  versionNo: number,
  editorId: number,
  content: string,
): FileVersion {
  return {
    id: versionNo,
    fileId: 42,
    versionNo,
    title: "design doc",
    content,
    editorId,
    defaultTitle: "draft-123",
  };
}

test("another editor's autosave is never fetched or rendered", () => {
  const head = editorVersion(9, 6, "别人还没提交的内容");

  assert.equal(shouldLoadChainHeadContent(head, 3), false);
  //即便正文被拿到了，规则本身也要拦住它
  assert.equal(
    pendingOwnDraft(
      head,
      { title: "design doc", content: "别人还没提交的内容" },
      { title: "design doc", content: "已提交内容" },
      3,
    ),
    null,
  );
});

test("my own unsaved autosave is restored", () => {
  const head = editorVersion(9, 3, "我改到一半的内容");

  assert.equal(shouldLoadChainHeadContent(head, 3), true);
  assert.deepEqual(
    pendingOwnDraft(
      head,
      { title: "design doc", content: "我改到一半的内容" },
      { title: "design doc", content: "已提交内容" },
      3,
    ),
    { title: "design doc", content: "我改到一半的内容" },
  );
});

test("my own head that equals the committed content is not a pending draft", () => {
  const head = editorVersion(9, 3, "已提交内容");

  assert.equal(
    pendingOwnDraft(
      head,
      { title: "design doc", content: "已提交内容" },
      { title: "design doc", content: "已提交内容" },
      3,
    ),
    null,
  );
});
