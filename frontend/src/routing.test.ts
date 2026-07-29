import assert from "node:assert/strict";
import test from "node:test";
import { parseRoute, routes } from "./routing.ts";

test("new-document routes preserve the initialized draft identifier", () => {
  const defaultTitle = "8de32697-dfa8-4a19-b1b3-1639562827a3";
  const path = routes.newDocument(12, defaultTitle, 1);

  assert.equal(
    path,
    `/repo/12/new/${defaultTitle}/1`,
  );
  assert.deepEqual(parseRoute(path), {
    mode: "new",
    repoId: 12,
    fileId: null,
    defaultTitle,
    versionNo: 1,
  });
});

test("new-document identifiers are safely encoded in routes", () => {
  const defaultTitle = "draft/key with spaces";
  const path = routes.newDocument(7, defaultTitle, 3);

  assert.deepEqual(parseRoute(path), {
    mode: "new",
    repoId: 7,
    fileId: null,
    defaultTitle,
    versionNo: 3,
  });
});

test("a new-document route without an initialized identifier is rejected", () => {
  assert.deepEqual(parseRoute("/repo/7/new"), {
    mode: "home",
    repoId: null,
    fileId: null,
  });
});

test("a new-document route with an invalid version is rejected", () => {
  assert.deepEqual(parseRoute("/repo/7/new/draft-id/0"), {
    mode: "home",
    repoId: null,
    fileId: null,
  });
});

test("trash routes open the recycle bin without repository context", () => {
  assert.equal(routes.trash, "/trash");
  assert.deepEqual(parseRoute("/trash/"), {
    mode: "trash",
    repoId: null,
    fileId: null,
  });
});

test("repository permission routes preserve repository target context", () => {
  const path = routes.repositoryPermissions(12);

  assert.equal(path, "/repo/12/permissions");
  assert.deepEqual(parseRoute(path), {
    mode: "permissions",
    repoId: 12,
    fileId: null,
    targetType: "KNOWLEDGE_REPOSITORY",
    targetId: 12,
  });
});

test("file permission routes preserve both repository and file context", () => {
  const path = routes.filePermissions(12, 42);

  assert.equal(path, "/repo/12/file/42/permissions");
  assert.deepEqual(parseRoute(path), {
    mode: "permissions",
    repoId: 12,
    fileId: 42,
    targetType: "FILE",
    targetId: 42,
  });
});
