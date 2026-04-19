import { useEffect, useMemo, useState } from "react";
import {
  Eye,
  EyeOff,
  ExternalLink,
  KeyRound,
  MessageSquare,
  Save,
  Settings,
  Trash2,
  Wrench,
  X,
  Zap,
} from "lucide-react";
import { api } from "@/lib/api";
import type { EnvVarInfo } from "@/lib/api";
import { useToast } from "@/hooks/useToast";
import { Toast } from "@/components/Toast";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useI18n } from "@/i18n";

const CATEGORY_META = {
  provider: { icon: Zap, label: "模型密钥" },
  messaging: { icon: MessageSquare, label: "消息渠道" },
  tool: { icon: Wrench, label: "工具与集成" },
  setting: { icon: Settings, label: "设置" },
} as const;

function EnvVarRow({
  varKey,
  info,
  edits,
  setEdits,
  revealed,
  saving,
  onSave,
  onClear,
  onReveal,
}: {
  varKey: string;
  info: EnvVarInfo;
  edits: Record<string, string>;
  setEdits: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  revealed: Record<string, string>;
  saving: string | null;
  onSave: (key: string) => void;
  onClear: (key: string) => void;
  onReveal: (key: string) => void;
}) {
  const { t } = useI18n();
  const isEditing = edits[varKey] !== undefined;
  const isRevealed = !!revealed[varKey];
  const displayValue = isRevealed ? revealed[varKey] : (info.redacted_value ?? "---");

  return (
    <div className="grid gap-2 rounded-[20px] border border-border/70 bg-white/50 p-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 min-w-0">
          <Label className="font-mono-ui text-[0.72rem]">{varKey}</Label>
          <Badge variant={info.is_set ? "success" : "outline"}>
            {info.is_set ? t.common.set : t.env.notSet}
          </Badge>
          {info.advanced && <Badge variant="secondary">高级</Badge>}
        </div>

        {info.url && (
          <a
            href={info.url}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-[0.7rem] text-primary hover:underline"
          >
            {t.env.getKey} <ExternalLink className="h-3 w-3" />
          </a>
        )}
      </div>

      <p className="text-xs text-muted-foreground">{info.description}</p>

      {info.tools.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {info.tools.map((tool) => (
            <Badge key={tool} variant="secondary" className="text-[0.65rem] py-0 px-1.5">
              {tool}
            </Badge>
          ))}
        </div>
      )}

      {!isEditing ? (
        <div className="flex items-center gap-2">
          <div className={`flex-1 border border-border px-3 py-2 font-mono-ui text-xs ${
            isRevealed ? "rounded-xl bg-white text-foreground select-all" : "rounded-xl bg-muted/30 text-muted-foreground"
          }`}>
            {info.is_set ? displayValue : "---"}
          </div>

          {info.is_set && (
            <Button size="sm" variant="ghost" onClick={() => onReveal(varKey)} title={isRevealed ? t.env.hideValue : t.env.showValue}>
              {isRevealed ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </Button>
          )}

          <Button size="sm" variant="outline" onClick={() => setEdits((prev) => ({ ...prev, [varKey]: "" }))}>
            <KeyRound className="h-3 w-3" />
            {info.is_set ? t.common.replace : t.common.set}
          </Button>

          {info.is_set && (
            <Button
              size="sm"
              variant="ghost"
              className="text-destructive hover:text-destructive hover:bg-destructive/10"
              onClick={() => onClear(varKey)}
              disabled={saving === varKey}
            >
              <Trash2 className="h-3 w-3" />
              {t.common.clear}
            </Button>
          )}
        </div>
      ) : (
        <div className="flex items-center gap-2">
          <Input
            autoFocus
            type={info.is_password ? "password" : "text"}
            value={edits[varKey]}
            onChange={(e) => setEdits((prev) => ({ ...prev, [varKey]: e.target.value }))}
            placeholder={info.is_set ? t.env.replaceCurrentValue.replace("{preview}", info.redacted_value ?? "---") : t.env.enterValue}
            className="flex-1 font-mono-ui text-xs"
          />
          <Button size="sm" onClick={() => onSave(varKey)} disabled={saving === varKey || !edits[varKey]}>
            <Save className="h-3 w-3" />
            {saving === varKey ? "..." : t.common.save}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={() =>
              setEdits((prev) => {
                const next = { ...prev };
                delete next[varKey];
                return next;
              })
            }
          >
            <X className="h-3 w-3" />
            {t.common.cancel}
          </Button>
        </div>
      )}
    </div>
  );
}

export default function EnvPage() {
  const [vars, setVars] = useState<Record<string, EnvVarInfo> | null>(null);
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [revealed, setRevealed] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<string | null>(null);
  const { toast, showToast } = useToast();
  const { t } = useI18n();

  useEffect(() => {
    api.getEnvVars().then(setVars).catch(() => {});
  }, []);

  const grouped = useMemo(() => {
    if (!vars) return [];
    const groups = new Map<string, [string, EnvVarInfo][]>();
    for (const entry of Object.entries(vars)) {
      const category = entry[1].category || "setting";
      if (!groups.has(category)) groups.set(category, []);
      groups.get(category)!.push(entry);
    }
    return Array.from(groups.entries()).sort(([a], [b]) => {
      const order = ["provider", "messaging", "tool", "setting"];
      return order.indexOf(a) - order.indexOf(b);
    });
  }, [vars]);

  const handleSave = async (key: string) => {
    const value = edits[key];
    if (!value) return;
    setSaving(key);
    try {
      await api.setEnvVar(key, value);
      setVars((prev) =>
        prev
          ? {
              ...prev,
              [key]: { ...prev[key], is_set: true, redacted_value: `${value.slice(0, 4)}...${value.slice(-4)}` },
            }
          : prev,
      );
      setEdits((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
      setRevealed((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
      showToast(`${key} ${t.common.save.toLowerCase()}d`, "success");
    } catch (e) {
      showToast(`${t.config.failedToSave}: ${e}`, "error");
    } finally {
      setSaving(null);
    }
  };

  const handleClear = async (key: string) => {
    setSaving(key);
    try {
      await api.deleteEnvVar(key);
      setVars((prev) =>
        prev
          ? { ...prev, [key]: { ...prev[key], is_set: false, redacted_value: null } }
          : prev,
      );
      setRevealed((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
      showToast(`${key} ${t.common.removed}`, "success");
    } catch (e) {
      showToast(`${t.common.failedToRemove}: ${e}`, "error");
    } finally {
      setSaving(null);
    }
  };

  const handleReveal = async (key: string) => {
    if (revealed[key]) {
      setRevealed((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
      return;
    }

    try {
      const resp = await api.revealEnvVar(key);
      setRevealed((prev) => ({ ...prev, [key]: resp.value }));
    } catch (e) {
      showToast(`${t.common.failedToReveal}: ${e}`, "error");
    }
  };

  if (!vars) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <Toast toast={toast} />

      <div className="flex flex-col gap-1">
        <p className="text-sm text-muted-foreground">
          {t.env.description} <code>runtime/.env</code>
        </p>
        <p className="text-[0.72rem] text-muted-foreground/70">
          {t.env.changesNote}
        </p>
      </div>

      {grouped.map(([category, entries]) => {
        const meta = CATEGORY_META[category as keyof typeof CATEGORY_META] ?? CATEGORY_META.setting;
        const Icon = meta.icon;
        const configured = entries.filter(([, info]) => info.is_set).length;

        return (
          <Card key={category}>
            <CardHeader className="border-b border-border">
              <div className="flex items-center gap-2">
                <Icon className="h-5 w-5 text-muted-foreground" />
                <CardTitle className="text-base">
                  {category === "provider" ? t.env.llmProviders : meta.label}
                </CardTitle>
              </div>
              <CardDescription>
                {configured} {t.common.of} {entries.length} {t.common.configured}
              </CardDescription>
            </CardHeader>

            <CardContent className="grid gap-3 pt-4">
              {entries.map(([key, info]) => (
                <EnvVarRow
                  key={key}
                  varKey={key}
                  info={info}
                  edits={edits}
                  setEdits={setEdits}
                  revealed={revealed}
                  saving={saving}
                  onSave={handleSave}
                  onClear={handleClear}
                  onReveal={handleReveal}
                />
              ))}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
