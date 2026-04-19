import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  FileText,
  RefreshCw,
  Search,
  Shield,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { api } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { useI18n } from "@/i18n";

const FILES = ["agent", "errors", "gateway"] as const;
const LEVELS = ["ALL", "DEBUG", "INFO", "WARNING", "ERROR"] as const;
const COMPONENTS = ["all", "gateway", "agent", "tools", "cli", "cron"] as const;
const LINE_COUNTS = [50, 100, 200, 500] as const;

const FILE_LABELS: Record<(typeof FILES)[number], string> = {
  agent: "代理日志",
  errors: "错误日志",
  gateway: "网关日志",
};

const LEVEL_LABELS: Record<(typeof LEVELS)[number], string> = {
  ALL: "全部",
  DEBUG: "调试",
  INFO: "信息",
  WARNING: "警告",
  ERROR: "错误",
};

const COMPONENT_LABELS: Record<(typeof COMPONENTS)[number], string> = {
  all: "全部组件",
  gateway: "网关",
  agent: "代理",
  tools: "工具",
  cli: "命令",
  cron: "定时任务",
};

type ParsedSeverity = "error" | "warning" | "info" | "debug";

interface ParsedLogLine {
  id: string;
  raw: string;
  timestamp: string;
  level: string;
  severity: ParsedSeverity;
  thread: string;
  logger: string;
  message: string;
}

function classifyLine(line: string): ParsedSeverity {
  const upper = line.toUpperCase();
  if (upper.includes("ERROR") || upper.includes("CRITICAL") || upper.includes("FATAL")) return "error";
  if (upper.includes("WARNING") || upper.includes("WARN")) return "warning";
  if (upper.includes("DEBUG")) return "debug";
  return "info";
}

function parseLogLine(line: string, index: number): ParsedLogLine {
  const match = line.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([A-Z]+)\s+\[([^\]]+)\]\s+([^\s]+)\s+-\s+(.*)$/);
  const severity = classifyLine(line);

  if (!match) {
    return {
      id: `line-${index}`,
      raw: line,
      timestamp: "--",
      level: severity.toUpperCase(),
      severity,
      thread: "--",
      logger: "--",
      message: line,
    };
  }

  return {
    id: `line-${index}`,
    raw: line,
    timestamp: match[1],
    level: match[2],
    severity,
    thread: match[3],
    logger: match[4],
    message: match[5],
  };
}

function severityBadgeVariant(severity: ParsedSeverity): "destructive" | "warning" | "secondary" | "outline" {
  if (severity === "error") return "destructive";
  if (severity === "warning") return "warning";
  if (severity === "debug") return "outline";
  return "secondary";
}

function SummaryCard({
  title,
  value,
  description,
  icon: Icon,
  toneClass,
}: {
  title: string;
  value: string;
  description: string;
  icon: LucideIcon;
  toneClass: string;
}) {
  return (
    <Card
      size="sm"
      className="glass-card border-none shadow-sm backdrop-blur-md transition-all hover:-translate-y-0.5"
    >
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-1.5">
        <CardTitle className="text-[13px] font-medium text-muted-foreground">
          {title}
        </CardTitle>
        <div
          className={cn(
            "flex h-8 w-8 items-center justify-center rounded-xl",
            toneClass,
          )}
        >
          <Icon className="h-3.5 w-3.5" />
        </div>
      </CardHeader>
      <CardContent className="space-y-0.5">
        <div className="text-[2rem] leading-none font-semibold tracking-tight">
          {value}
        </div>
        <p className="text-[11px] text-muted-foreground">{description}</p>
      </CardContent>
    </Card>
  );
}

function FilterSection({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-2">
      <div className="text-[11px] font-medium text-muted-foreground">{label}</div>
      {children}
    </div>
  );
}

export default function LogsPage() {
  const [file, setFile] = useState<(typeof FILES)[number]>("agent");
  const [level, setLevel] = useState<(typeof LEVELS)[number]>("ALL");
  const [component, setComponent] = useState<(typeof COMPONENTS)[number]>("all");
  const [lineCount, setLineCount] = useState<(typeof LINE_COUNTS)[number]>(100);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [search, setSearch] = useState("");
  const [lines, setLines] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastLoadedAt, setLastLoadedAt] = useState<number | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const { t } = useI18n();

  const fetchLogs = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .getLogs({ file, lines: lineCount, level, component })
      .then((resp) => {
        setLines(resp.lines);
        setLastLoadedAt(Date.now());
        window.setTimeout(() => {
          if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
          }
        }, 30);
      })
      .catch((err) => setError(String(err)))
      .finally(() => setLoading(false));
  }, [component, file, level, lineCount]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = window.setInterval(fetchLogs, 5000);
    return () => window.clearInterval(interval);
  }, [autoRefresh, fetchLogs]);

  const parsedLines = useMemo(
    () => lines.map((line, index) => parseLogLine(line, index)),
    [lines],
  );

  const filteredLines = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return parsedLines;
    return parsedLines.filter((line) => {
      const haystack = `${line.timestamp} ${line.level} ${line.thread} ${line.logger} ${line.message} ${line.raw}`.toLowerCase();
      return haystack.includes(keyword);
    });
  }, [parsedLines, search]);

  const errorCount = filteredLines.filter((line) => line.severity === "error").length;
  const warningCount = filteredLines.filter((line) => line.severity === "warning").length;
  const infoCount = filteredLines.filter((line) => line.severity === "info").length;
  const lastLoadedLabel = lastLoadedAt
    ? new Date(lastLoadedAt).toLocaleTimeString("zh-CN", { hour12: false })
    : "--";

  return (
    <div className="animate-[fade-in_240ms_ease-out] space-y-5">
      <TabsList className="glass-card flex h-11 w-full justify-start overflow-x-auto rounded-xl border-none p-1 no-scrollbar lg:w-fit">
        {FILES.map((item) => (
          <TabsTrigger
            key={item}
            active={file === item}
            value={item}
            onClick={() => setFile(item)}
            className="gap-2 px-5 shrink-0"
          >
            {item === "gateway" ? <Shield className="h-4 w-4" /> : <FileText className="h-4 w-4" />}
            {FILE_LABELS[item]}
          </TabsTrigger>
        ))}
      </TabsList>

      <Card className="glass-card border-none shadow-md backdrop-blur-md">
        <CardContent className="space-y-4 pt-0">
          <div className="grid gap-3 xl:grid-cols-[minmax(0,1fr)_auto_auto] xl:items-center">
            <div className="min-w-0">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder="搜索时间、线程、来源或消息..."
                  className="glass-card h-10 rounded-xl px-10"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 justify-self-start xl:justify-self-center">
              <Button
                variant="outline"
                size="sm"
                className="glass-card h-9 rounded-xl px-3.5"
                onClick={fetchLogs}
                disabled={loading}
              >
                <RefreshCw className={cn("mr-1.5 h-4 w-4", loading && "animate-spin")} />
                {t.common.refresh}
              </Button>

              <div className="flex items-center gap-2 rounded-xl border bg-card/30 px-3 py-2">
                <Switch checked={autoRefresh} onCheckedChange={setAutoRefresh} size="sm" />
                <span className="text-xs text-muted-foreground">{t.logs.autoRefresh}</span>
                <Badge variant={autoRefresh ? "success" : "outline"}>
                  {autoRefresh ? t.common.live : t.common.off}
                </Badge>
              </div>
            </div>

            <div className="justify-self-start xl:justify-self-end">
              <Badge variant="outline" className="h-8 rounded-xl px-3 text-xs">
                {FILE_LABELS[file]} · {LEVEL_LABELS[level]} · {COMPONENT_LABELS[component]}
              </Badge>
            </div>
          </div>

          <div className="grid gap-3 xl:grid-cols-3">
            <FilterSection label={t.logs.level}>
              <TabsList className="max-w-full overflow-x-auto no-scrollbar">
                {LEVELS.map((item) => (
                  <TabsTrigger
                    key={item}
                    active={level === item}
                    value={item}
                    onClick={() => setLevel(item)}
                    className="shrink-0"
                  >
                    {LEVEL_LABELS[item]}
                  </TabsTrigger>
                ))}
              </TabsList>
            </FilterSection>

            <FilterSection label={t.logs.component}>
              <TabsList className="max-w-full overflow-x-auto no-scrollbar">
                {COMPONENTS.map((item) => (
                  <TabsTrigger
                    key={item}
                    active={component === item}
                    value={item}
                    onClick={() => setComponent(item)}
                    className="shrink-0"
                  >
                    {COMPONENT_LABELS[item]}
                  </TabsTrigger>
                ))}
              </TabsList>
            </FilterSection>

            <FilterSection label={t.logs.lines}>
              <TabsList className="max-w-full overflow-x-auto no-scrollbar">
                {LINE_COUNTS.map((item) => (
                  <TabsTrigger
                    key={item}
                    active={lineCount === item}
                    value={String(item)}
                    onClick={() => setLineCount(item)}
                    className="shrink-0"
                  >
                    {item} 行
                  </TabsTrigger>
                ))}
              </TabsList>
            </FilterSection>
          </div>

          {error ? (
            <div className="rounded-xl border border-destructive/20 bg-destructive/8 px-4 py-3 text-sm text-destructive">
              {error}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <SummaryCard
          title="当前结果"
          value={`${filteredLines.length}`}
          description={`${FILE_LABELS[file]}最近 ${lineCount} 行`}
          icon={Zap}
          toneClass="bg-primary/12 text-primary"
        />
        <SummaryCard
          title="错误行数"
          value={`${errorCount}`}
          description="ERROR / FATAL / CRITICAL"
          icon={AlertTriangle}
          toneClass="bg-red-500/12 text-red-500"
        />
        <SummaryCard
          title="警告行数"
          value={`${warningCount}`}
          description={`信息行数 ${infoCount}`}
          icon={CheckCircle2}
          toneClass="bg-amber-500/12 text-amber-500"
        />
        <SummaryCard
          title="最近刷新"
          value={lastLoadedLabel}
          description={autoRefresh ? "自动刷新已开启" : "手动刷新模式"}
          icon={RefreshCw}
          toneClass="bg-sky-500/12 text-sky-500"
        />
      </div>

      <Card className="glass-card overflow-hidden border-none gap-0 py-0 shadow-xl backdrop-blur-md">
        <CardHeader className="flex min-h-1 items-center border-b border-border/40 bg-[var(--table-section-bg)] py-3">
          <div className="flex w-full flex-col gap-1 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <CardTitle className="text-[15px] font-semibold">
                日志明细
              </CardTitle>
            </div>
            <div className="text-xs text-muted-foreground">
              当前展示 {filteredLines.length} 条，来源 {FILE_LABELS[file]}
            </div>
          </div>
        </CardHeader>

        <CardContent className="px-0">
          <div ref={scrollRef} className="overflow-auto max-h-[720px]">
            <table className="min-w-[1180px] table-fixed text-sm">
              <thead>
                <tr>
                  <th className="h-12 w-[188px] px-4 text-left text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                    时间
                  </th>
                  <th className="w-[100px] px-4 text-left text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                    级别
                  </th>
                  <th className="w-[140px] px-4 text-left text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                    线程
                  </th>
                  <th className="w-[260px] px-4 text-left text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                    来源
                  </th>
                  <th className="px-4 text-left text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                    消息
                  </th>
                </tr>
              </thead>
              <tbody>
                {filteredLines.length > 0 ? (
                  filteredLines.map((line) => (
                    <tr key={line.id}>
                      <td className="px-4 py-3 align-top text-xs font-mono-ui text-muted-foreground">
                        {line.timestamp}
                      </td>
                      <td className="px-4 py-3 align-top">
                        <Badge variant={severityBadgeVariant(line.severity)}>
                          {line.level}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 align-top text-xs font-mono-ui text-muted-foreground">
                        {line.thread}
                      </td>
                      <td className="px-4 py-3 align-top">
                        <div className="truncate font-mono-ui text-[11px] text-foreground" title={line.logger}>
                          {line.logger}
                        </div>
                      </td>
                      <td className="px-4 py-3 align-top">
                        <div
                          className={cn(
                            "text-sm leading-6 text-foreground whitespace-pre-wrap break-words",
                            line.severity === "error" && "text-destructive",
                            line.severity === "warning" && "text-amber-700",
                            line.severity === "debug" && "text-muted-foreground",
                          )}
                          title={line.raw}
                        >
                          {line.message}
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td
                      colSpan={5}
                      className="px-4 py-12 text-center text-sm text-muted-foreground"
                    >
                      {search.trim() ? "当前筛选条件下没有匹配日志。" : t.logs.noLogLines}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
