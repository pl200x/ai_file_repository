import assert from "node:assert/strict";
import test from "node:test";
import {
  buildFilePermissionRequest,
  isFileAccessDenied,
} from "./fileAccess.ts";
import type { PermissionLevel } from "./types.ts";

test("recognizes the backend permission-denied business code", () => {
  assert.equal(isFileAccessDenied({ code: 501 }), true);
  assert.equal(isFileAccessDenied({ code: 404 }), false);
  assert.equal(isFileAccessDenied(new Error("offline")), false);
});

for (const permissionType of [
  "READABLE",
  "WRITABLE",
  "MANAGEABLE",
] satisfies PermissionLevel[]) {
  test(`builds a self-service ${permissionType} file request`, () => {
    assert.deepEqual(
      buildFilePermissionRequest(
        6,
        42,
        permissionType,
        2_592_000_000,
      ),
      {
        requestUserId: 6,
        targetUserId: 6,
        targetType: "FILE",
        targetId: 42,
        permissionType,
        expirationDate: 2_592_000_000,
      },
    );
  });
}
