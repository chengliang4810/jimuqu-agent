import { useEffect, useState } from "react";
import {
  Activity,
  AlertTriangle,
  Clock,
  Cpu,
  Database,
  Radio,
  Wifi,
  WifiOff,
} from "lucide-react";
import { api } from "@/lib/api";
import type { GatewayDoctorPlatform, GatewayDoctorResponse, PlatformStatus, SessionInfo, StatusResponse, WeixinQrTicket } from "@/lib/api";
import { timeAgo, isoTimeAgo } from "@/lib/utils";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n";
import { formatTokenCount } from "@/lib/format";

function platformLabel(name: string): string {
  if (name === "feishu") return "飞书";
  if (name === "dingtalk") return "钉钉";
  if (name === "wecom") return "企微";
  if (name === "weixin") return "微信";
  return name;
}

function sourceLabel(name?: string | null): string {
  if (!name || name === "local") return "本地";
  if (name === "feishu") return "飞书";
  if (name === "dingtalk") return "钉钉";
  if (name === "wecom") return "企微";
  if (name === "weixin") return "微信";
  return name;
}

function isQrPageUrl(url?: string | null): boolean {
  if (!url) return false;
  return url.includes("liteapp.weixin.qq.com/q/") || url.includes("/q/") || !/\.(png|jpg|jpeg|gif|webp)(\?|$)/i.test(url);
}

export default function StatusPage() {
  const [status, setStatus] = useState<StatusResponse | null>(null);
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [doctor, setDoctor] = useState<GatewayDoctorResponse | null>(null);
  const [weixinQr, setWeixinQr] = useState<WeixinQrTicket | null>(null);
  const [startingQr, setStartingQr] = useState(false);
  const { t } = useI18n();

  useEffect(() => {
    const load = () => {
      api.getStatus().then(setStatus).catch(() => {});
      api.getSessions(50).then((resp) => setSessions(resp.sessions)).catch(() => {});
      api.getGatewayDoctor().then(setDoctor).catch(() => {});
    };
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (!weixinQr || (weixinQr.status !== "pending" && weixinQr.status !== "scanned" && weixinQr.status !== "initializing")) {
      return;
    }
    const interval = setInterval(() => {
      api.getWeixinQrTicket(weixinQr.ticket).then(setWeixinQr).catch(() => {});
    }, 1500);
    return () => clearInterval(interval);
  }, [weixinQr]);

  if (!status) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  const PLATFORM_STATE_BADGE: Record<string, { variant: "success" | "warning" | "destructive" | "outline"; label: string }> = {
    connected: { variant: "success", label: t.status.connected },
    disconnected: { variant: "warning", label: t.status.disconnected },
    fatal: { variant: "destructive", label: t.status.error },
    disabled: { variant: "outline", label: t.common.disabled },
  };

  const GATEWAY_STATE_DISPLAY: Record<string, { badge: "success" | "warning" | "destructive" | "outline"; label: string }> = {
    running: { badge: "success", label: t.status.running },
    starting: { badge: "warning", label: t.status.starting },
    startup_failed: { badge: "destructive", label: t.status.failed },
    stopped: { badge: "outline", label: t.status.stopped },
  };

  function gatewayValue(): string {
    if (status!.gateway_running && status!.gateway_pid) return `${t.status.pid} ${status!.gateway_pid}`;
    if (status!.gateway_running) return t.status.runningRemote;
    if (status!.gateway_state === "startup_failed") return t.status.startFailed;
    return t.status.notRunning;
  }

  function gatewayBadge() {
    const info = status!.gateway_state ? GATEWAY_STATE_DISPLAY[status!.gateway_state] : null;
    if (info) return info;
    return status!.gateway_running
      ? { badge: "success" as const, label: t.status.running }
      : { badge: "outline" as const, label: t.common.off };
  }

  const gwBadge = gatewayBadge();

  const items = [
    {
      icon: Cpu,
      label: t.status.agent,
      value: `v${status.version}`,
      badgeText: t.common.live,
      badgeVariant: "success" as const,
    },
    {
      icon: Radio,
      label: t.status.gateway,
      value: gatewayValue(),
      badgeText: gwBadge.label,
      badgeVariant: gwBadge.badge,
    },
    {
      icon: Activity,
      label: t.status.activeSessions,
      value: status.active_sessions > 0 ? `${status.active_sessions} ${t.status.running.toLowerCase()}` : t.status.noneRunning,
      badgeText: status.active_sessions > 0 ? t.common.live : t.common.off,
      badgeVariant: (status.active_sessions > 0 ? "success" : "outline") as "success" | "outline",
    },
  ];

  const platforms = Object.entries(status.gateway_platforms ?? {});
  const activeSessions = sessions.filter((s) => s.is_active);
  const recentSessions = sessions.filter((s) => !s.is_active).slice(0, 5);
  const doctorPlatforms = doctor?.platforms ?? [];

  async function handleStartWeixinQr() {
    setStartingQr(true);
    try {
      const ticket = await api.startWeixinQrLogin();
      setWeixinQr(ticket);
    } finally {
      setStartingQr(false);
    }
  }

  // Collect alerts that need attention
  const alerts: { message: string; detail?: string }[] = [];
  if (status.gateway_state === "startup_failed") {
    alerts.push({
      message: t.status.gatewayFailedToStart,
      detail: status.gateway_exit_reason ?? undefined,
    });
  }
  const failedPlatforms = platforms.filter(([, info]) => info.state === "fatal");
  for (const [name, info] of failedPlatforms) {
    const stateLabel = info.state === "fatal" ? t.status.platformError : t.status.platformDisconnected;
    alerts.push({
      message: `${platformLabel(name)} ${stateLabel}`,
      detail: info.error_message ?? undefined,
    });
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Alert banner — breaks grid monotony for critical states */}
      {alerts.length > 0 && (
        <div className="rounded-[24px] border border-destructive/20 bg-destructive/[0.06] p-4 shadow-[0_18px_36px_-28px_rgba(220,38,38,0.28)]">
          <div className="flex items-start gap-3">
            <AlertTriangle className="h-5 w-5 text-destructive shrink-0 mt-0.5" />
            <div className="flex flex-col gap-2 min-w-0">
              {alerts.map((alert, i) => (
                <div key={i}>
                  <p className="text-sm font-medium text-destructive">{alert.message}</p>
                  {alert.detail && (
                    <p className="text-xs text-destructive/70 mt-0.5">{alert.detail}</p>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        {items.map(({ icon: Icon, label, value, badgeText, badgeVariant }) => (
          <Card key={label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">{label}</CardTitle>
              <Icon className="h-4 w-4 text-muted-foreground" />
            </CardHeader>

            <CardContent>
              <div className="text-2xl font-bold font-display">{value}</div>

              {badgeText && (
                <Badge variant={badgeVariant} className="mt-2">
                  {badgeVariant === "success" && (
                    <span className="mr-1 inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
                  )}
                  {badgeText}
                </Badge>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {platforms.length > 0 && (
        <PlatformsCard platforms={platforms} platformStateBadge={PLATFORM_STATE_BADGE} />
      )}

      {doctorPlatforms.length > 0 && (
        <DoctorCard
          platforms={doctorPlatforms}
          weixinQr={weixinQr}
          startingQr={startingQr}
          onStartWeixinQr={handleStartWeixinQr}
        />
      )}

      {activeSessions.length > 0 && (
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Activity className="h-5 w-5 text-success" />
              <CardTitle className="text-base">{t.status.activeSessions}</CardTitle>
            </div>
          </CardHeader>

          <CardContent className="grid gap-3">
            {activeSessions.map((s) => (
              <div
                key={s.id}
                className="flex w-full flex-col gap-2 rounded-[20px] border border-border/70 bg-white/48 p-3 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="flex flex-col gap-1 min-w-0 w-full">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-sm truncate">{s.title ?? t.common.untitled}</span>

                    <Badge variant="success" className="text-[10px] shrink-0">
                      <span className="mr-1 inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
                      {t.common.live}
                    </Badge>
                  </div>

                  <span className="text-xs text-muted-foreground truncate">
                    <span className="font-mono-ui">{(s.model ?? t.common.unknown).split("/").pop()}</span> · {s.message_count} {t.common.msgs}{(s.total_tokens ?? 0) > 0 ? ` · ${formatTokenCount(s.total_tokens ?? 0)} Token` : ""} · {timeAgo(s.last_active)}
                  </span>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {recentSessions.length > 0 && (
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Clock className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">{t.status.recentSessions}</CardTitle>
            </div>
          </CardHeader>

          <CardContent className="grid gap-3">
            {recentSessions.map((s) => (
              <div
                key={s.id}
                className="flex w-full flex-col gap-2 rounded-[20px] border border-border/70 bg-white/48 p-3 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="flex flex-col gap-1 min-w-0 w-full">
                  <span className="font-medium text-sm truncate">{s.title ?? t.common.untitled}</span>

                  <span className="text-xs text-muted-foreground truncate">
                    <span className="font-mono-ui">{(s.model ?? t.common.unknown).split("/").pop()}</span> · {s.message_count} {t.common.msgs}{(s.total_tokens ?? 0) > 0 ? ` · ${formatTokenCount(s.total_tokens ?? 0)} Token` : ""} · {timeAgo(s.last_active)}
                  </span>

                  {s.preview && (
                    <span className="text-xs text-muted-foreground/70 truncate">
                      {s.preview}
                    </span>
                  )}
                </div>

                <Badge variant="outline" className="text-[10px] shrink-0 self-start sm:self-center">
                  <Database className="mr-1 h-3 w-3" />
                  {sourceLabel(s.source)}
                </Badge>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function PlatformsCard({ platforms, platformStateBadge }: PlatformsCardProps) {
  const { t } = useI18n();

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Radio className="h-5 w-5 text-muted-foreground" />
          <CardTitle className="text-base">{t.status.connectedPlatforms}</CardTitle>
        </div>
      </CardHeader>

      <CardContent className="grid gap-3">
        {platforms.map(([name, info]) => {
          const display = platformStateBadge[info.state] ?? {
            variant: "outline" as const,
            label: info.state,
          };
          const IconComponent = info.state === "connected" ? Wifi : info.state === "fatal" ? AlertTriangle : WifiOff;

          return (
            <div
              key={name}
              className="flex w-full flex-col gap-2 rounded-[20px] border border-border/70 bg-white/48 p-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="flex items-center gap-3 min-w-0 w-full">
                <IconComponent className={`h-4 w-4 shrink-0 ${
                  info.state === "connected"
                    ? "text-success"
                    : info.state === "fatal"
                      ? "text-destructive"
                      : "text-warning"
                }`} />

                <div className="flex flex-col gap-0.5 min-w-0">
                  <span className="text-sm font-medium truncate">{platformLabel(name)}</span>

                  {info.error_message && (
                    <span className="text-xs text-destructive">{info.error_message}</span>
                  )}

                  {info.updated_at && (
                    <span className="text-xs text-muted-foreground">
                      {t.status.lastUpdate}: {isoTimeAgo(info.updated_at)}
                    </span>
                  )}
                </div>
              </div>

              <Badge variant={display.variant} className="shrink-0 self-start sm:self-center">
                {display.variant === "success" && (
                  <span className="mr-1 inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
                )}
                {display.label}
              </Badge>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

interface PlatformsCardProps {
  platforms: [string, PlatformStatus][];
  platformStateBadge: Record<string, { variant: "success" | "warning" | "destructive" | "outline"; label: string }>;
}

function DoctorCard({
  platforms,
  weixinQr,
  startingQr,
  onStartWeixinQr,
}: {
  platforms: GatewayDoctorPlatform[];
  weixinQr: WeixinQrTicket | null;
  startingQr: boolean;
  onStartWeixinQr: () => void;
}) {
  const { t } = useI18n();

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-5 w-5 text-muted-foreground" />
          <CardTitle className="text-base">{t.status.doctorTitle}</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="grid gap-3">
        {platforms.map((item) => (
          <div
            key={item.platform}
            className="rounded-[20px] border border-border/70 bg-white/48 p-3"
          >
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">{platformLabel(item.platform)}</span>
                  <Badge variant={item.connected ? "success" : item.missing_env.length > 0 ? "warning" : "outline"}>
                    {item.connected ? t.status.setupReady : item.missing_env.length > 0 ? t.status.missingConfig : (item.setup_state ?? t.common.unknown)}
                  </Badge>
                </div>
                <div className="mt-2 grid gap-1 text-xs text-muted-foreground">
                  <span>{t.status.connectionMode}: {item.connection_mode ?? "custom"}</span>
                  {item.detail && <span>{item.detail}</span>}
                  {item.missing_env.length > 0 && (
                    <span>{t.status.missingEnv}: {item.missing_env.join(", ")}</span>
                  )}
                  {item.features.length > 0 && (
                    <span>{t.status.features}: {item.features.join(", ")}</span>
                  )}
                  {item.next_step && (
                    <span>{t.status.nextStep}: {item.next_step}</span>
                  )}
                  {item.last_error_message && (
                    <span className="text-destructive">{item.last_error_message}</span>
                  )}
                </div>
              </div>

              {item.platform === "weixin" && (
                <div className="flex flex-col items-start gap-2 sm:items-end">
                  <Button
                    size="sm"
                    onClick={onStartWeixinQr}
                    disabled={startingQr || (weixinQr?.status === "pending" || weixinQr?.status === "scanned" || weixinQr?.status === "initializing")}
                  >
                    {startingQr ? t.common.loading : t.status.weixinQrStart}
                  </Button>
                  {weixinQr && (
                    <div className="max-w-[220px] text-xs text-muted-foreground">
                      <div>
                        {weixinQr.status === "confirmed"
                          ? t.status.weixinQrConfirmed
                          : weixinQr.status === "failed"
                            ? t.status.weixinQrFailed
                            : t.status.weixinQrPending}
                      </div>
                      {weixinQr.message && <div className="mt-1">{weixinQr.message}</div>}
                      {weixinQr.qr_image_url && (weixinQr.status === "pending" || weixinQr.status === "scanned" || weixinQr.status === "initializing") && (
                        isQrPageUrl(weixinQr.qr_image_url) ? (
                          <div className="mt-2 flex max-w-[320px] flex-col gap-2">
                            <a
                              href={weixinQr.qr_image_url}
                              target="_blank"
                              rel="noreferrer"
                              className="text-primary underline underline-offset-2"
                            >
                              {t.status.weixinQrOpenPage}
                            </a>
                            <div>{t.status.weixinQrPageHint}</div>
                            <iframe
                              src={weixinQr.qr_image_url}
                              title="weixin qr page"
                              className="h-80 w-80 rounded-md border border-border bg-white"
                            />
                          </div>
                        ) : (
                          <img
                            src={weixinQr.qr_image_url}
                            alt="weixin qr"
                            className="mt-2 h-28 w-28 rounded-md border border-border bg-white p-1"
                          />
                        )
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
