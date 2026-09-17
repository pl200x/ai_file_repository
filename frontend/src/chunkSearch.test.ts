import assert from "node:assert/strict";
import { test } from "node:test";
import {
  chunkPositionLabel,
  documentTitleOf,
  groupHitsByFile,
  hiddenHitCount,
  isTruncated,
  normalizeQuery,
  searchSummary,
  snippet,
} from "./chunkSearch.ts";
import type { ChunkHit } from "./types.ts";

function hit(overrides: Partial<ChunkHit> = {}): ChunkHit {
  return {
    id: 1,
    fileId: 7,
    fileName: "季度规划",
    chunkId: "chunk-a",
    chunkContent: "片段正文",
    ownerId: 10,
    repositoryId: 3,
    chunkIndex: 0,
    visible: true,
    ...overrides,
  };
}

test("hits of one document are grouped without reordering the ranking", () => {
  const groups = groupHitsByFile([
    hit({ chunkId: "a", fileId: 7 }),
    hit({ chunkId: "b", fileId: 9, fileName: "架构评审" }),
    hit({ chunkId: "c", fileId: 7, chunkIndex: 4 }),
  ]);

  assert.deepEqual(
    groups.map((group) => group.fileId),
    [7, 9],
  );
  assert.deepEqual(
    groups[0]?.hits.map((each) => each.chunkId),
    ["a", "c"],
  );
});

test("a document the user cannot open keeps its hits but has no title", () => {
  const groups = groupHitsByFile([
    hit({ fileId: 9, fileName: null, visible: false }),
  ]);

  assert.equal(groups[0]?.visible, false);
  assert.equal(documentTitleOf(groups[0]!), "文档 #9");
});

test("summary reports the hits the permission check held back", () => {
  const hits = [hit(), hit({ visible: false }), hit({ visible: false })];

  assert.equal(hiddenHitCount(hits), 2);
  assert.equal(searchSummary(hits), "3 条相关片段 · 2 条无权限查看");
  assert.equal(searchSummary([hit()]), "1 条相关片段");
  assert.equal(searchSummary([]), "没有找到相关内容");
});

test("snippet trims by code point so surrogate pairs stay intact", () => {
  assert.equal(snippet("abcdef", 3), "abc…");
  assert.equal(snippet("🙂🙂🙂🙂", 2), "🙂🙂…");
  assert.equal(snippet("短文本", 10), "短文本");
  assert.equal(snippet(null), "");
  assert.equal(isTruncated("abcdef", 3), true);
  assert.equal(isTruncated("ab", 3), false);
  assert.equal(isTruncated(null), false);
});

test("chunk position is shown one-based", () => {
  assert.equal(chunkPositionLabel(hit({ chunkIndex: 0 })), "第 1 段");
  assert.equal(chunkPositionLabel(hit({ chunkIndex: 9 })), "第 10 段");
});

test("query keeps its inner spacing but loses the outer padding", () => {
  assert.equal(normalizeQuery("  权限 设计  "), "权限 设计");
  assert.equal(normalizeQuery("   "), "");
});
