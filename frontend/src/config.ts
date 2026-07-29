import type { DemoUser } from "./types";

export const TENANT_ID = 1;
export const DEFAULT_USER_ID = 3;
export const AUTOSAVE_INTERVAL_MS = 30_000;

export const DEMO_USERS: DemoUser[] = [
  { id: 1, name: "user1" },
  { id: 2, name: "user2" },
  { id: 3, name: "user3" },
  { id: 4, name: "user4" },
  { id: 5, name: "user5" },
];
