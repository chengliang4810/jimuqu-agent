import { useEffect, useState } from "react";
import { Clock, Pause, Play, Plus, Save, Trash2, Zap } from "lucide-react";
import { api } from "@/lib/api";
import type { CronJob } from "@/lib/api";
import { useToast } from "@/hooks/useToast";
import { Toast } from "@/components/Toast";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectOption } from "@/components/ui/select";
import { useI18n } from "@/i18n";

function formatTime(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleString();
}

const STATUS_VARIANT: Record<string, "success" | "warning" | "destructive"> = {
  enabled: "success",
  scheduled: "success",
  paused: "warning",
  error: "destructive",
  completed: "destructive",
};

const STATE_LABELS: Record<string, string> = {
  scheduled: "已启用",
  paused: "已暂停",
  error: "错误",
  completed: "完成",
};

const DELIVER_LABELS: Record<string, string> = {
  local: "本地",
  feishu: "飞书",
  dingtalk: "钉钉",
  wecom: "企微",
  weixin: "微信",
};

export default function CronPage() {
  const [jobs, setJobs] = useState<CronJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const { toast, showToast } = useToast();
  const { t } = useI18n();

  // New job form state
  const [prompt, setPrompt] = useState("");
  const [schedule, setSchedule] = useState("");
  const [name, setName] = useState("");
  const [deliver, setDeliver] = useState("local");
  const [creating, setCreating] = useState(false);

  const loadJobs = () => {
    api
      .getCronJobs()
      .then(setJobs)
      .catch(() => showToast(t.common.loading, "error"))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadJobs();
  }, []);

  const handleCreate = async () => {
    if (!prompt.trim() || !schedule.trim()) {
      showToast(`${t.cron.prompt} & ${t.cron.schedule} required`, "error");
      return;
    }
    setCreating(true);
    try {
      await api.createCronJob({
        prompt: prompt.trim(),
        schedule: schedule.trim(),
        name: name.trim() || undefined,
        deliver,
      });
      showToast(t.common.create + " ✓", "success");
      setPrompt("");
      setSchedule("");
      setName("");
      setDeliver("local");
      setCreateDialogOpen(false);
      loadJobs();
    } catch (e) {
      showToast(`${t.config.failedToSave}: ${e}`, "error");
    } finally {
      setCreating(false);
    }
  };

  const handlePauseResume = async (job: CronJob) => {
    try {
      const isPaused = job.state === "paused";
      if (isPaused) {
        await api.resumeCronJob(job.id);
        showToast(`${t.cron.resume}: "${job.name || job.prompt.slice(0, 30)}"`, "success");
      } else {
        await api.pauseCronJob(job.id);
        showToast(`${t.cron.pause}: "${job.name || job.prompt.slice(0, 30)}"`, "success");
      }
      loadJobs();
    } catch (e) {
      showToast(`${t.status.error}: ${e}`, "error");
    }
  };

  const handleTrigger = async (job: CronJob) => {
    try {
      await api.triggerCronJob(job.id);
      showToast(`${t.cron.triggerNow}: "${job.name || job.prompt.slice(0, 30)}"`, "success");
      loadJobs();
    } catch (e) {
      showToast(`${t.status.error}: ${e}`, "error");
    }
  };

  const handleDelete = async (job: CronJob) => {
    try {
      await api.deleteCronJob(job.id);
      showToast(`${t.common.delete}: "${job.name || job.prompt.slice(0, 30)}"`, "success");
      loadJobs();
    } catch (e) {
      showToast(`${t.status.error}: ${e}`, "error");
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <Toast toast={toast} />

      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <Clock className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t.cron.scheduledJobs}</h2>
          <Badge variant="secondary">{jobs.length}</Badge>
        </div>
        <Button
          onClick={() => setCreateDialogOpen(true)}
          className="gap-2"
        >
          <Plus className="h-4 w-4" />
          {t.common.create}
        </Button>
      </div>

      {/* Jobs list */}
      <div className="flex flex-col gap-3">
        {jobs.length === 0 && (
          <Card>
            <CardContent className="py-8 text-center text-sm text-muted-foreground">
              {t.cron.noJobs}
            </CardContent>
          </Card>
        )}

        {jobs.map((job) => (
          <Card key={job.id}>
            <CardContent className="flex items-center gap-4 py-4">
              {/* Info */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-medium text-sm truncate">
                    {job.name || job.prompt.slice(0, 60) + (job.prompt.length > 60 ? "..." : "")}
                  </span>
                  <Badge variant={STATUS_VARIANT[job.state] ?? "secondary"}>
                    {STATE_LABELS[job.state] ?? job.state}
                  </Badge>
                  {job.deliver && job.deliver !== "local" && (
                    <Badge variant="outline">{DELIVER_LABELS[job.deliver] ?? job.deliver}</Badge>
                  )}
                </div>
                {job.name && (
                  <p className="text-xs text-muted-foreground truncate mb-1">
                    {job.prompt.slice(0, 100)}{job.prompt.length > 100 ? "..." : ""}
                  </p>
                )}
                <div className="flex items-center gap-4 text-xs text-muted-foreground">
                  <span className="font-mono">{job.schedule_display}</span>
                  <span>{t.cron.last}: {formatTime(job.last_run_at)}</span>
                  <span>{t.cron.next}: {formatTime(job.next_run_at)}</span>
                </div>
                {job.last_error && (
                  <p className="text-xs text-destructive mt-1">{job.last_error}</p>
                )}
              </div>

              {/* Actions */}
              <div className="flex items-center gap-1 shrink-0">
                <Button
                  variant="ghost"
                  size="icon"
                  title={job.state === "paused" ? t.cron.resume : t.cron.pause}
                  aria-label={job.state === "paused" ? t.cron.resume : t.cron.pause}
                  onClick={() => handlePauseResume(job)}
                >
                  {job.state === "paused" ? (
                    <Play className="h-4 w-4 text-success" />
                  ) : (
                    <Pause className="h-4 w-4 text-warning" />
                  )}
                </Button>

                <Button
                  variant="ghost"
                  size="icon"
                  title={t.cron.triggerNow}
                  aria-label={t.cron.triggerNow}
                  onClick={() => handleTrigger(job)}
                >
                  <Zap className="h-4 w-4" />
                </Button>

                <Button
                  variant="ghost"
                  size="icon"
                  title={t.common.delete}
                  aria-label={t.common.delete}
                  onClick={() => handleDelete(job)}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {createDialogOpen && (
        <div
          className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/28 px-4 backdrop-blur-[2px]"
          onClick={() => setCreateDialogOpen(false)}
        >
          <Card
            className="w-full max-w-2xl py-0"
            onClick={(event) => event.stopPropagation()}
          >
            <CardHeader className="border-b px-4 py-4">
              <CardTitle className="flex items-center gap-2 text-base">
                <Plus className="h-4 w-4" />
                {t.cron.newJob}
              </CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 px-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="cron-name">{t.cron.nameOptional}</Label>
                <Input
                  id="cron-name"
                  placeholder={t.cron.namePlaceholder}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="cron-prompt">{t.cron.prompt}</Label>
                <textarea
                  id="cron-prompt"
                  className="flex min-h-[112px] w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-sm outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                  placeholder={t.cron.promptPlaceholder}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                />
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="grid gap-2">
                  <Label htmlFor="cron-schedule">{t.cron.schedule}</Label>
                  <Input
                    id="cron-schedule"
                    placeholder={t.cron.schedulePlaceholder}
                    value={schedule}
                    onChange={(e) => setSchedule(e.target.value)}
                  />
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="cron-deliver">{t.cron.deliverTo}</Label>
                  <Select
                    id="cron-deliver"
                    value={deliver}
                    onValueChange={(v) => setDeliver(v)}
                  >
                    <SelectOption value="local">{t.cron.delivery.local}</SelectOption>
                    <SelectOption value="feishu">{t.cron.delivery.telegram}</SelectOption>
                    <SelectOption value="dingtalk">{t.cron.delivery.discord}</SelectOption>
                    <SelectOption value="wecom">{t.cron.delivery.slack}</SelectOption>
                    <SelectOption value="weixin">{t.cron.delivery.email}</SelectOption>
                  </Select>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 border-t pt-4">
                <Button
                  variant="ghost"
                  onClick={() => setCreateDialogOpen(false)}
                >
                  {t.common.cancel}
                </Button>
                <Button onClick={handleCreate} disabled={creating} className="gap-2">
                  <Save className="h-4 w-4" />
                  {creating ? t.common.saving : t.common.save}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
