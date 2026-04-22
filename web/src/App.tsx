import { memo, useEffect, useMemo, useState, type MouseEvent } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileText,
  KeyRound,
  LayoutDashboard,
  MessageSquare,
  Package,
  Settings,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { api, type StatusResponse } from "@/lib/api";
import { cn } from "@/lib/utils";
import AnalyticsPage from "@/pages/AnalyticsPage";
import ConfigPage from "@/pages/ConfigPage";
import CronPage from "@/pages/CronPage";
import EnvPage from "@/pages/EnvPage";
import LogsPage from "@/pages/LogsPage";
import SessionsPage from "@/pages/SessionsPage";
import SkillsPage from "@/pages/SkillsPage";
import StatusPage from "@/pages/StatusPage";
import WorkspacePage from "@/pages/WorkspacePage";

const NAV_ITEMS = [
  { label: "状态", href: "/", icon: LayoutDashboard },
  { label: "会话", href: "/sessions", icon: MessageSquare },
  { label: "分析", href: "/analytics", icon: BarChart3 },
  { label: "日志", href: "/logs", icon: FileText },
  { label: "工作区", href: "/workspace", icon: FileText },
  { label: "定时任务", href: "/cron", icon: Clock3 },
  { label: "技能", href: "/skills", icon: Package },
  { label: "配置", href: "/config", icon: Settings },
  { label: "密钥", href: "/env", icon: KeyRound },
];

const PAGE_TITLES: Record<string, string> = {
  "/": "状态",
  "/sessions": "会话",
  "/analytics": "分析",
  "/logs": "日志",
  "/workspace": "工作区",
  "/cron": "定时任务",
  "/skills": "技能",
  "/config": "配置",
  "/env": "密钥",
};

const PAGE_DESCRIPTIONS: Record<string, string> = {
  "/": "查看代理、网关与渠道连接状态。",
  "/sessions": "浏览会话记录、消息详情与搜索结果。",
  "/analytics": "查看用量趋势、模型分布与统计汇总。",
  "/logs": "按文件、级别和组件筛选运行日志。",
  "/workspace": "直接编辑 AGENTS、SOUL、IDENTITY、USER 四个工作区文件。",
  "/cron": "管理自动任务、调度表达式与投递目标。",
  "/skills": "启用技能并检查工具集可用状态。",
  "/config": "编辑运行配置和原始 YAML。",
  "/env": "管理模型与渠道所需密钥。",
};

const NavItem = memo(({
  item,
  isActive,
  isSidebarOpen,
  onNavigate,
}: {
  item: typeof NAV_ITEMS[0];
  isActive: boolean;
  isSidebarOpen: boolean;
  onNavigate: (href: string, event: MouseEvent<HTMLAnchorElement>) => void;
}) => (
  <a
    href={item.href}
    onClick={(event) => onNavigate(item.href, event)}
    aria-current={isActive ? "page" : undefined}
    className={cn(
      "flex items-center gap-3 rounded-lg px-3 py-2 transition-all duration-200 hover:bg-accent hover:text-accent-foreground",
      isActive ? "bg-accent text-accent-foreground" : "text-muted-foreground",
    )}
  >
    <item.icon className="h-4 w-4 shrink-0" />
    {isSidebarOpen && <span className="text-sm truncate">{item.label}</span>}
  </a>
));

NavItem.displayName = "NavItem";

function Sidebar({
  isSidebarOpen,
  onToggleSidebar,
}: {
  isSidebarOpen: boolean;
  onToggleSidebar: () => void;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const [status, setStatus] = useState<StatusResponse | null>(null);

  useEffect(() => {
    const load = () => {
      api.getStatus().then(setStatus).catch(() => {});
    };

    load();
    const timer = window.setInterval(load, 10000);
    return () => window.clearInterval(timer);
  }, []);

  const handleNavigate = (href: string, event: MouseEvent<HTMLAnchorElement>) => {
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return;
    }

    if (href === location.pathname) {
      event.preventDefault();
      return;
    }

    event.preventDefault();
    navigate(href);
  };

  const renderedItems = useMemo(
    () =>
      NAV_ITEMS.map((item) => (
        <NavItem
          key={item.href}
          item={item}
          isActive={item.href === "/" ? location.pathname === "/" : location.pathname.startsWith(item.href)}
          isSidebarOpen={isSidebarOpen}
          onNavigate={handleNavigate}
        />
      )),
    [isSidebarOpen, location.pathname],
  );

  return (
    <div
      className={cn(
        "relative z-20 flex shrink-0 flex-col glass-sidebar transition-[width] duration-300 ease-in-out",
        isSidebarOpen ? "w-56" : "w-16",
      )}
    >
      <div className="flex h-16 items-center border-b px-4 shrink-0">
        <button
          type="button"
          onClick={() => navigate("/")}
          title="返回首页"
          aria-label="返回首页"
          className="flex w-full items-center gap-2 overflow-hidden rounded-xl px-2 py-1.5 text-left transition-colors duration-200 hover:bg-accent/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
        >
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <span className="text-sm font-bold">JA</span>
          </div>
          {isSidebarOpen && (
            <div className="flex flex-col overflow-hidden animate-[fade-in_200ms_ease-out]">
              <span className="text-sm font-bold truncate">Jimuqu Agent</span>
              <span className="text-xs text-muted-foreground truncate opacity-70">国内渠道工作台</span>
            </div>
          )}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto py-4">
        <nav className="grid gap-1 px-2">{renderedItems}</nav>
      </div>

      <div className="border-t p-2 shrink-0">
        {isSidebarOpen ? (
          <div className="mb-2 rounded-xl border bg-card/30 px-3 py-2 text-xs text-muted-foreground shadow-sm">
            <div className="flex items-center justify-between">
              <span>网关</span>
              <Badge variant={status?.gateway_running ? "default" : "outline"}>
                {status?.gateway_running ? "在线" : "离线"}
              </Badge>
            </div>
            <div className="mt-2 flex items-center justify-between">
              <span>版本</span>
              <span className="font-medium text-foreground">{status ? `v${status.version}` : "--"}</span>
            </div>
          </div>
        ) : null}

        <Button
          variant="ghost"
          size="icon"
          className="w-full justify-start gap-3 px-3 h-10"
          onClick={onToggleSidebar}
        >
          {isSidebarOpen ? (
            <>
              <ChevronLeft className="h-4 w-4 shrink-0" />
              <span className="text-sm">收起侧边栏</span>
            </>
          ) : (
            <ChevronRight className="h-4 w-4 shrink-0" />
          )}
        </Button>
      </div>
    </div>
  );
}

function Header() {
  const location = useLocation();
  const [status, setStatus] = useState<StatusResponse | null>(null);
  const [portInput] = useState("8080");

  useEffect(() => {
    const load = () => {
      api.getStatus().then(setStatus).catch(() => {});
    };

    load();
    const timer = window.setInterval(load, 10000);
    return () => window.clearInterval(timer);
  }, []);

  const path = NAV_ITEMS.find((item) =>
    item.href === "/" ? location.pathname === "/" : location.pathname.startsWith(item.href),
  )?.href ?? "/";
  const pageTitle = PAGE_TITLES[path] ?? "状态";
  const pageDescription = PAGE_DESCRIPTIONS[path] ?? "";

  return (
    <header className="sticky top-0 z-30 grid h-16 grid-cols-[minmax(0,auto)_minmax(0,1fr)_auto] items-center gap-3 glass-header px-4 xl:px-6">
      <div className="flex min-w-0 items-center gap-3 overflow-hidden">
        <h1 className="truncate text-lg font-semibold">{pageTitle}</h1>
        <Badge variant={status?.gateway_running ? "default" : "secondary"} className="h-5">
          {status?.gateway_running ? "服务已连接" : "服务未连接"}
        </Badge>
        {status?.version ? (
          <span className="text-xs text-muted-foreground">v{status.version}</span>
        ) : null}
      </div>

      <div className="hidden min-w-0 items-center justify-center px-2 lg:flex">
        <p className="truncate text-xs text-muted-foreground">{pageDescription}</p>
      </div>

      <div className="ml-auto flex shrink-0 items-center gap-2 xl:gap-3">
        <div className="flex items-center gap-2 rounded-lg border bg-card/30 px-2.5 py-1.5 shadow-sm">
          <span className="text-xs font-medium text-muted-foreground">监听端口</span>
          <Input
            className="h-7 w-16 border-none bg-transparent p-0 text-xs font-mono focus-visible:ring-0"
            value={portInput}
            readOnly
          />
          <Badge variant={status?.gateway_running ? "success" : "outline"} className="ml-1">
            {status?.gateway_running ? "在线" : "离线"}
          </Badge>
        </div>
      </div>
    </header>
  );
}

export default function App() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar
        isSidebarOpen={isSidebarOpen}
        onToggleSidebar={() => setIsSidebarOpen((value) => !value)}
      />
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <Header />
        <main className="relative min-w-0 flex-1 overflow-y-auto p-6 no-scrollbar">
          <Routes>
            <Route path="/" element={<StatusPage />} />
            <Route path="/sessions" element={<SessionsPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/logs" element={<LogsPage />} />
            <Route path="/workspace" element={<WorkspacePage />} />
            <Route path="/cron" element={<CronPage />} />
            <Route path="/skills" element={<SkillsPage />} />
            <Route path="/config" element={<ConfigPage />} />
            <Route path="/env" element={<EnvPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}
