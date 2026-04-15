package com.jimuqu.agent.support;

import java.util.Calendar;

/**
 * 轻量级 cron 计算辅助类，仅覆盖当前项目需要的 5 段 cron 语义。
 */
public final class CronSupport {
    private CronSupport() {
    }

    /**
     * 计算下一次执行时间。
     *
     * @param cronExpr       5 段 cron 表达式
     * @param fromEpochMillis 起算时间
     * @return 下一次执行时间戳
     */
    public static long nextRunAt(String cronExpr, long fromEpochMillis) {
        if (cronExpr == null || cronExpr.trim().isEmpty()) {
            return fromEpochMillis + 60000L;
        }

        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length != 5) {
            return fromEpochMillis + 60000L;
        }

        Calendar candidate = Calendar.getInstance();
        candidate.setTimeInMillis(fromEpochMillis + 60000L);
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);

        long max = fromEpochMillis + 366L * 24L * 60L * 60L * 1000L;
        while (candidate.getTimeInMillis() <= max) {
            if (matches(parts[0], candidate.get(Calendar.MINUTE))
                    && matches(parts[1], candidate.get(Calendar.HOUR_OF_DAY))
                    && matches(parts[2], candidate.get(Calendar.DAY_OF_MONTH))
                    && matches(parts[3], candidate.get(Calendar.MONTH) + 1)
                    && matchesDayOfWeek(parts[4], candidate.get(Calendar.DAY_OF_WEEK))) {
                return candidate.getTimeInMillis();
            }

            candidate.add(Calendar.MINUTE, 1);
        }

        return fromEpochMillis + 60000L;
    }

    /**
     * 将 Java 星期映射为 cron 星期值后做匹配。
     */
    private static boolean matchesDayOfWeek(String expr, int dayOfWeek) {
        int normalized = dayOfWeek - 1;
        if (normalized < 0) {
            normalized = 0;
        }
        return matches(expr, normalized);
    }

    /**
     * 匹配单个 cron 字段。
     */
    private static boolean matches(String expr, int value) {
        if ("*".equals(expr)) {
            return true;
        }

        if (expr.startsWith("*/")) {
            int step = Integer.parseInt(expr.substring(2));
            return step > 0 && value % step == 0;
        }

        String[] items = expr.split(",");
        for (String item : items) {
            String trimmed = item.trim();
            if (trimmed.length() == 0) {
                continue;
            }

            if (trimmed.indexOf('-') > 0) {
                String[] range = trimmed.split("-", 2);
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                if (value >= start && value <= end) {
                    return true;
                }
            } else if (Integer.parseInt(trimmed) == value) {
                return true;
            }
        }

        return false;
    }
}
