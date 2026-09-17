import assert from "node:assert/strict";
import { test } from "node:test";
import {
  notificationSummary,
  notificationTargetLabel,
  notificationTopicLabel,
  permissionLevelFromContent,
  totalPageCount,
  unreadBadgeText,
} from "./notifications.ts";
import type { NotificationItem } from "./types.ts";

function notification(
  overrides: Partial<NotificationItem> = {},
): NotificationItem {
  return {
    id: 1,
    topic: "APPLY_PERMISSION",
    applicantId: 6,
    applicantName: "王五",
    receiverId: 3,
    targetType: "FILE",
    targetId: 21,
    targetTitle: "季度规划",
    operationContent: "WRITABLE",
    operationTime: "2026-08-18 10:00:00",
    read: false,
    ...overrides,
  };
}

test("permission request reads as a sentence", () => {
  assert.equal(
    notificationSummary(notification()),
    "王五 申请 文档《季度规划》的可编辑权限",
  );
});

test("repository targets are labelled as repositories", () => {
  assert.equal(
    notificationTargetLabel(
      notification({
        targetType: "KNOWLEDGE_REPOSITORY",
        targetTitle: "团队知识库",
      }),
    ),
    "知识库《团队知识库》",
  );
});

test("deleted target falls back to its id", () => {
  assert.equal(
    notificationTargetLabel(notification({ targetTitle: null })),
    "文档《#21》",
  );
});

test("missing applicant name falls back to the id", () => {
  assert.equal(
    notificationSummary(
      notification({ applicantName: null, operationContent: "READABLE" }),
    ),
    "用户 ID 6 申请 文档《季度规划》的可阅读权限",
  );
});

test("unknown permission level is shown verbatim", () => {
  assert.equal(
    notificationSummary(notification({ operationContent: "OWNER" })),
    "王五 申请 文档《季度规划》的OWNER权限",
  );
});

test("blank permission level degrades gracefully", () => {
  assert.equal(
    notificationSummary(notification({ operationContent: "  " })),
    "王五 申请 文档《季度规划》的权限",
  );
});

test("non permission topics use the raw content", () => {
  assert.equal(
    notificationSummary(
      notification({ topic: "COMMENT", operationContent: "写得不错" }),
    ),
    "王五 · 文档《季度规划》 · 写得不错",
  );
});

test("unread badge caps at 99+", () => {
  assert.equal(unreadBadgeText(0), "");
  assert.equal(unreadBadgeText(-1), "");
  assert.equal(unreadBadgeText(7), "7");
  assert.equal(unreadBadgeText(99), "99");
  assert.equal(unreadBadgeText(100), "99+");
});

test("topic labels fall back to the raw code", () => {
  assert.equal(notificationTopicLabel("APPLY_PERMISSION"), "权限申请");
  assert.equal(notificationTopicLabel("SOMETHING_NEW"), "SOMETHING_NEW");
});

test("permission level is only extracted for known codes", () => {
  assert.equal(permissionLevelFromContent("WRITABLE"), "WRITABLE");
  assert.equal(permissionLevelFromContent(" READABLE "), "READABLE");
  assert.equal(permissionLevelFromContent("OWNER"), null);
  assert.equal(permissionLevelFromContent(null), null);
});

test("page count never drops below one", () => {
  assert.equal(totalPageCount(0, 20), 1);
  assert.equal(totalPageCount(20, 20), 1);
  assert.equal(totalPageCount(21, 20), 2);
  assert.equal(totalPageCount(41, 20), 3);
});
