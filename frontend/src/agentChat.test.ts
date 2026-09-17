import assert from "node:assert/strict";
import test from "node:test";
import {
  AGENT_INPUT_MAX_LENGTH,
  buildAgentChatBody,
  createAgentSessionId,
  isAgentInputValid,
  normalizeAgentInput,
} from "./agentChat.ts";

test("agent input is normalized and bounded like the backend contract", () => {
  assert.equal(normalizeAgentInput("  如何申请权限？  "), "如何申请权限？");
  assert.equal(isAgentInputValid("   "), false);
  assert.equal(isAgentInputValid("x".repeat(AGENT_INPUT_MAX_LENGTH)), true);
  assert.equal(
    isAgentInputValid("x".repeat(AGENT_INPUT_MAX_LENGTH + 1)),
    false,
  );
});

test("agent sessions are isolated by current user", () => {
  assert.equal(createAgentSessionId(7, "session-seed"), "web-7-session-seed");
  assert.notEqual(
    createAgentSessionId(7, "session-seed"),
    createAgentSessionId(8, "session-seed"),
  );
});

test("agent request body matches the form endpoint", () => {
  const body = buildAgentChatBody("session-1", "  文档保留多久？ ", 7);

  assert.equal(body.get("sessionId"), "session-1");
  assert.equal(body.get("userInput"), "文档保留多久？");
  assert.equal(body.get("userId"), "7");
});
