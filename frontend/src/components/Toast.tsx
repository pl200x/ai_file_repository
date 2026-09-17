import type { ToastState } from "../types";
import { useTranslation } from "../i18n";

interface ToastProps {
  toast: ToastState | null;
}

export function Toast({ toast }: ToastProps) {
  const { t } = useTranslation();
  if (!toast) return null;

  return (
    <div
      className={`toast ${toast.success ? "success" : ""}`}
      role="status"
      aria-live="polite"
    >
      {t(toast.message)}
    </div>
  );
}
