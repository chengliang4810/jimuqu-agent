import { useState } from "react";
import { cn } from "@/lib/utils";

export function Tabs({
  defaultValue,
  children,
  className,
}: {
  defaultValue: string;
  children: (active: string, setActive: (v: string) => void) => React.ReactNode;
  className?: string;
}) {
  const [active, setActive] = useState(defaultValue);
  return <div className={cn("flex flex-col gap-4", className)}>{children(active, setActive)}</div>;
}

export function TabsList({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "inline-flex w-fit items-center justify-start rounded-2xl border border-white/65 bg-white/58 p-1 text-muted-foreground backdrop-blur-sm",
        className,
      )}
      {...props}
    />
  );
}

export function TabsTrigger({
  active,
  value,
  onClick,
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { active: boolean; value: string }) {
  return (
    <button
      type="button"
      className={cn(
        "relative inline-flex items-center justify-center whitespace-nowrap rounded-xl px-3 py-1.5 text-sm font-medium transition-all cursor-pointer",
        "focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring",
        active
          ? "bg-white text-foreground shadow-[0_12px_20px_-16px_rgba(15,23,42,0.36)]"
          : "hover:bg-white/60 hover:text-foreground",
        className,
      )}
      onClick={onClick}
      {...props}
    />
  );
}
