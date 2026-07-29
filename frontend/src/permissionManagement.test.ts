import assert from "node:assert/strict";
import test from "node:test";
import {
  EXPIRATION_OPTIONS,
  permissionLevelLabel,
  permissionStatusLabel,
} from "./permissionManagement.ts";

test("permission durations match the backend-supported millisecond values", () => {
  assert.deepEqual(
    EXPIRATION_OPTIONS.map((option) => option.value),
    [86_400_000, 259_200_000, 2_592_000_000, 31_536_000_000],
  );
});

test("permission codes have user-facing labels", () => {
  assert.equal(permissionLevelLabel("READABLE"), "可阅读");
  assert.equal(permissionLevelLabel("MANAGEABLE"), "可管理");
  assert.equal(permissionStatusLabel("PENDING"), "待审批");
  assert.equal(permissionStatusLabel("REVOKED"), "已撤销");
});
