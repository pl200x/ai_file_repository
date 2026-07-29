import { DEMO_USERS } from "./config";

export function formatDateTime(value: string | number | null | undefined) {
  if (value == null) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const part = (number: number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())} ${part(date.getHours())}:${part(date.getMinutes())}:${part(date.getSeconds())}`;
}

export function formatClock(date: Date) {
  const part = (number: number) => String(number).padStart(2, "0");
  return `${part(date.getHours())}:${part(date.getMinutes())}:${part(date.getSeconds())}`;
}

export function userName(userId: number) {
  return DEMO_USERS.find((user) => user.id === userId)?.name ?? `user${userId}`;
}

export function errorMessage(error: unknown) {
  if (error instanceof DOMException && error.name === "AbortError") {
    return "";
  }
  return error instanceof Error ? error.message : "发生未知错误";
}
