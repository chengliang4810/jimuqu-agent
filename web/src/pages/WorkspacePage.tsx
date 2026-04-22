import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Bot, FileText, NotebookPen, RotateCcw, Save, Shield, User } from "lucide-react";
import { api, type WorkspaceFile } from "@/lib/api";
import { useToast } from "@/hooks/useToast";
import { Toast } from "@/components/Toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TabsList, TabsTrigger } from "@/components/ui/tabs";

const FILE_ORDER = [
  {
    key: "agents",
    name: "AGENTS.md",
    description: "工作区规则与记忆使用方式",
    icon: FileText,
  },
  {
    key: "soul",
    name: "SOUL.md",
    description: "身份、边界、风格、对外操作原则",
    icon: Shield,
  },
  {
    key: "identity",
    name: "IDENTITY.md",
    description: "名称、emoji、avatar、vibe、creature",
    icon: Bot,
  },
  {
    key: "user",
    name: "USER.md",
    description: "用户偏好、称呼、时区、长期背景",
    icon: User,
  },
  {
    key: "tools",
    name: "TOOLS.md",
    description: "本地工具与环境相关的说明文件",
    icon: NotebookPen,
  },
  {
    key: "heartbeat",
    name: "HEARTBEAT.md",
    description: "定期轮询时需要检查的事项清单",
    icon: NotebookPen,
  },
  {
    key: "memory",
    name: "MEMORY.md",
    description: "长期记忆，记录稳定、长期有价值的信息",
    icon: NotebookPen,
  },
  {
    key: "memory_today",
    name: "memory/YYYY-MM-DD.md",
    description: "当天运行记忆，记录今日上下文与进展",
    icon: NotebookPen,
  },
] as const;

type WorkspaceKey = typeof FILE_ORDER[number]["key"];

function toFileMap(files: WorkspaceFile[]): Record<string, WorkspaceFile> {
  return files.reduce<Record<string, WorkspaceFile>>((acc, file) => {
    acc[file.key] = file;
    return acc;
  }, {});
}

export default function WorkspacePage() {
  const [files, setFiles] = useState<Record<string, WorkspaceFile>>({});
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [activeKey, setActiveKey] = useState<WorkspaceKey>("agents");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [restoring, setRestoring] = useState(false);
  const [restoreConfirmKey, setRestoreConfirmKey] = useState<WorkspaceKey | null>(null);
  const { toast, showToast } = useToast();

  const loadAll = async () => {
    setLoading(true);
    try {
      const response = await api.getWorkspaceFiles();
      const mapped = toFileMap(response.files);
      setFiles(mapped);
      setDrafts(
        response.files.reduce<Record<string, string>>((acc, file) => {
          acc[file.key] = file.content ?? "";
          return acc;
        }, {}),
      );
    } catch (error) {
      showToast(`加载工作区文件失败：${error}`, "error");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll().catch(() => {});
  }, []);

  useEffect(() => {
    setRestoreConfirmKey(null);
  }, [activeKey]);

  const activeMeta = useMemo(
    () => FILE_ORDER.find((item) => item.key === activeKey) ?? FILE_ORDER[0],
    [activeKey],
  );
  const activeFile = files[activeKey];
  const activeDraft = drafts[activeKey] ?? "";
  const dirtyKeys = useMemo(
    () =>
      FILE_ORDER.filter((item) => (drafts[item.key] ?? "") !== (files[item.key]?.content ?? "")).map(
        (item) => item.key,
      ),
    [drafts, files],
  );

  const saveFile = async (key: WorkspaceKey) => {
    setSaving(true);
    try {
      const response = await api.saveWorkspaceFile(key, drafts[key] ?? "");
      setFiles((prev) => ({
        ...prev,
        [key]: response.file,
      }));
      setDrafts((prev) => ({
        ...prev,
        [key]: response.file.content ?? "",
      }));
      showToast(`已保存 ${response.file.name}`, "success");
    } catch (error) {
      showToast(`保存失败：${error}`, "error");
    } finally {
      setSaving(false);
    }
  };

  const saveAll = async () => {
    if (dirtyKeys.length === 0) {
      showToast("当前没有未保存的修改。", "success");
      return;
    }
    setSaving(true);
    try {
      for (const key of dirtyKeys) {
        const response = await api.saveWorkspaceFile(key, drafts[key] ?? "");
        setFiles((prev) => ({
          ...prev,
          [key]: response.file,
        }));
        setDrafts((prev) => ({
          ...prev,
          [key]: response.file.content ?? "",
        }));
      }
      showToast("已保存全部工作区文件。", "success");
    } catch (error) {
      showToast(`保存失败：${error}`, "error");
    } finally {
      setSaving(false);
    }
  };

  const reloadCurrent = async () => {
    setReloading(true);
    try {
      const file = await api.getWorkspaceFile(activeKey);
      setFiles((prev) => ({
        ...prev,
        [activeKey]: file,
      }));
      setDrafts((prev) => ({
        ...prev,
        [activeKey]: file.content ?? "",
      }));
      showToast(`已重新载入 ${file.name}`, "success");
    } catch (error) {
      showToast(`重新载入失败：${error}`, "error");
    } finally {
      setReloading(false);
    }
  };

  const restoreCurrent = async () => {
    setRestoring(true);
    try {
      const response = await api.restoreWorkspaceFile(activeKey);
      setFiles((prev) => ({
        ...prev,
        [activeKey]: response.file,
      }));
      setDrafts((prev) => ({
        ...prev,
        [activeKey]: response.file.content ?? "",
      }));
      setRestoreConfirmKey(null);
      showToast(`已将 ${response.file.name} 恢复为模板默认内容。`, "success");
    } catch (error) {
      showToast(`恢复失败：${error}`, "error");
    } finally {
      setRestoring(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  const ActiveIcon = activeMeta.icon;
  const isDirty = dirtyKeys.indexOf(activeKey) >= 0;

  return (
    <div className="flex flex-col gap-4">
      <Toast toast={toast} />

      <Card size="sm">
        <CardContent className="flex flex-col gap-4 pt-4">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2 text-sm font-medium">
                <FileText className="h-4 w-4 text-muted-foreground" />
                <span>人格工作区文件</span>
              </div>
              <p className="text-xs text-muted-foreground">
                这些工作区文件直接保存在 <code className="rounded bg-muted/60 px-1 py-0.5">runtime</code> 根目录及
                <code className="rounded bg-muted/60 px-1 py-0.5">runtime/memory</code> 目录下，运行时会作为 companion
                工作区上下文注入。
              </p>
            </div>

            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={reloadCurrent} disabled={reloading || saving || restoring}>
                <RotateCcw className="h-3.5 w-3.5" />
                {reloading ? "重新载入中" : "重新载入当前"}
              </Button>
              <Button variant="outline" size="sm" onClick={saveAll} disabled={saving || restoring}>
                <Save className="h-3.5 w-3.5" />
                {saving ? "保存中" : `保存全部${dirtyKeys.length > 0 ? ` (${dirtyKeys.length})` : ""}`}
              </Button>
              <Button size="sm" onClick={() => saveFile(activeKey)} disabled={saving || restoring}>
                <Save className="h-3.5 w-3.5" />
                {saving ? "保存中" : "保存当前"}
              </Button>
            </div>
          </div>

          <TabsList className="max-w-full overflow-x-auto scrollbar-none">
            {FILE_ORDER.map((item) => {
              const Icon = item.icon;
              const active = item.key === activeKey;
              const dirty = dirtyKeys.indexOf(item.key) >= 0;
              return (
                <TabsTrigger
                  key={item.key}
                  active={active}
                  value={item.key}
                  onClick={() => setActiveKey(item.key)}
                  className="gap-2"
                >
                  <Icon className="h-3.5 w-3.5 shrink-0" />
                  <span>{item.name}</span>
                  {dirty ? <span className="text-[10px] text-amber-600">*</span> : null}
                </TabsTrigger>
              );
            })}
          </TabsList>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="py-3 px-4">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex items-start gap-3">
              <div className="mt-0.5 rounded-lg border bg-muted/50 p-2">
                <ActiveIcon className="h-4 w-4 text-muted-foreground" />
              </div>
              <div className="space-y-1">
                <CardTitle className="text-sm">{activeMeta.name}</CardTitle>
                <p className="text-xs text-muted-foreground">{activeMeta.description}</p>
                <p className="text-[11px] text-muted-foreground break-all">
                  {activeFile?.path ?? "runtime/" + activeMeta.name}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Badge variant={activeFile?.exists ? "default" : "outline"}>
                {activeFile?.exists ? "已存在" : "尚未创建"}
              </Badge>
              {isDirty ? <Badge variant="secondary">未保存</Badge> : <Badge variant="outline">已同步</Badge>}
            </div>
          </div>
        </CardHeader>

        <CardContent className="pb-4">
          <div className="mb-3 flex flex-col gap-3 rounded-xl border border-border bg-muted/30 p-3">
            <div className="flex flex-col gap-2 xl:flex-row xl:items-center xl:justify-between">
              <div className="space-y-1">
                <div className="flex items-center gap-2 text-sm font-medium">
                  <AlertTriangle className="h-4 w-4 text-amber-600" />
                  <span>恢复模板默认内容</span>
                </div>
                <p className="text-xs text-muted-foreground">
                  会用项目内置模板覆盖当前文件内容。已有修改将被替换。
                </p>
              </div>

              {restoreConfirmKey === activeKey ? (
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setRestoreConfirmKey(null)}
                    disabled={restoring || saving}
                  >
                    取消
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={restoreCurrent}
                    disabled={restoring || saving}
                  >
                    <AlertTriangle className="h-3.5 w-3.5" />
                    {restoring ? "恢复中" : "确认恢复模板"}
                  </Button>
                </div>
              ) : (
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => setRestoreConfirmKey(activeKey)}
                  disabled={restoring || saving}
                >
                  <AlertTriangle className="h-3.5 w-3.5" />
                  恢复模板
                </Button>
              )}
            </div>

            {restoreConfirmKey === activeKey ? (
              <div className="rounded-lg border border-amber-300/60 bg-amber-50/70 px-3 py-2 text-xs text-amber-900">
                二次确认：点击“确认恢复模板”后，会立即将当前文件替换为内置模板内容。
              </div>
            ) : null}
          </div>

          <textarea
            className="min-h-[560px] w-full resize-y rounded-xl border border-border bg-background/60 px-4 py-3 font-mono text-sm leading-6 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20"
            value={activeDraft}
            onChange={(event) =>
              setDrafts((prev) => ({
                ...prev,
                [activeKey]: event.target.value,
              }))
            }
            spellCheck={false}
            placeholder={`在这里编辑 ${activeMeta.name} 的内容。`}
          />
        </CardContent>
      </Card>
    </div>
  );
}
