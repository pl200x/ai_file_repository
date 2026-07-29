import { useCallback, useEffect, useRef, useState } from "react";
import type { ToastState } from "../types";

export function useToast() {
  const [toast, setToast] = useState<ToastState | null>(null);
  const timerRef = useRef<number | null>(null);

  const showToast = useCallback((message: string, success = false) => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
    }
    setToast({ message, success });
    timerRef.current = window.setTimeout(() => {
      setToast(null);
      timerRef.current = null;
    }, 2_600);
  }, []);

  useEffect(
    () => () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
      }
    },
    [],
  );

  return { toast, showToast };
}
