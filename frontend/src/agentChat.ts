export const AGENT_INPUT_MAX_LENGTH = 1_000;

export type AgentMessageRole = "user" | "assistant";

export interface AgentChatMessage {
  id: string;
  role: AgentMessageRole;
  content: string;
  error?: boolean;
  localized?: boolean;
}

export function normalizeAgentInput(value: string) {
  return value.trim();
}

export function isAgentInputValid(value: string) {
  const normalized = normalizeAgentInput(value);
  return (
    normalized.length > 0 &&
    normalized.length <= AGENT_INPUT_MAX_LENGTH
  );
}

export function createAgentSessionId(
  userId: number,
  entropy?: string,
) {
  const fallbackEntropy = `${Date.now()}-${Math.random()
    .toString(36)
    .slice(2)}`;
  const generatedEntropy =
    entropy ?? globalThis.crypto?.randomUUID?.() ?? fallbackEntropy;
  return `web-${userId}-${generatedEntropy}`.slice(0, 128);
}

export function buildAgentChatBody(
  sessionId: string,
  userInput: string,
  userId: number,
) {
  return new URLSearchParams({
    sessionId,
    userInput: normalizeAgentInput(userInput),
    userId: String(userId),
  });
}
