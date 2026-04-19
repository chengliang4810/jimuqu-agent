import { useEffect, useState } from "react";
import { createPortal } from "react-dom";

export function Toast({ toast }: { toast: { message: string; type: "success" | "error" } | null }) {
  const [visible, setVisible] = useState(false);
  const [current, setCurrent] = useState(toast);

  useEffect(() => {
    if (toast) {
      setCurrent(toast);
      setVisible(true);
    } else {
      setVisible(false);
      const timer = setTimeout(() => setCurrent(null), 200);
      return () => clearTimeout(timer);
    }
  }, [toast]);

  if (!current) return null;

  // Portal to document.body so the toast escapes any ancestor stacking context
  // (e.g. <main> has `relative z-2`, which would trap z-50 below the header's z-40).
  return createPortal(
    <div
      role="status"
      aria-live="polite"
      className={`glass-card fixed top-5 right-5 z-50 rounded-2xl border px-4 py-3 text-sm font-medium shadow-[0_18px_36px_-24px_rgba(15,23,42,0.28)] ${
        current.type === "success"
          ? "text-emerald-700"
          : "text-destructive"
      }`}
      style={{
        animation: visible ? "toast-in 200ms ease-out forwards" : "toast-out 200ms ease-in forwards",
      }}
    >
      {current.message}
    </div>,
    document.body,
  );
}
