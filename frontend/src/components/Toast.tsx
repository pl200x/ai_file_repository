import type { ToastState } from "../types";

interface ToastProps {
  toast: ToastState | null;
}

export function Toast({ toast }: ToastProps) {
  if (!toast) return null;

  return (
    <div
      className={`toast ${toast.success ? "success" : ""}`}
      role="status"
      aria-live="polite"
    >
      {toast.message}
    </div>
  );
}
