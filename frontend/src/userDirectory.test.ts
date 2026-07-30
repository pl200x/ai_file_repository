import assert from "node:assert/strict";
import test from "node:test";
import { userName } from "./format.ts";
import type { UserSummary } from "./types.ts";

const users: UserSummary[] = [
  {
    id: 6,
    tenantId: 1,
    groupId: 1,
    name: "user_test",
    email: "user6@example.com",
    profile: null,
  },
];

test("userName resolves names from the backend user directory", () => {
  assert.equal(userName(6, users), "user_test");
});

test("userName keeps an informative fallback for unknown users", () => {
  assert.equal(userName(99, users), "用户 ID 99");
});
