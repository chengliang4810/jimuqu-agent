package com.jimuqu.claw.agent.job;

import cn.hutool.core.util.StrUtil;

import java.time.Instant;

public final class JobScheduleSupport {
    private JobScheduleSupport() {
    }

    public static Instant computeNextRunAt(String schedule, Instant base) {
        if (StrUtil.isBlank(schedule) || base == null) {
            return null;
        }

        String normalized = schedule.trim().toLowerCase();
        try {
            if (normalized.endsWith("ms")) {
                return plusMillis(base, normalized.substring(0, normalized.length() - 2));
            }
            if (normalized.endsWith("s") && normalized.indexOf(' ') < 0) {
                return plusMillis(base, String.valueOf(Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1000L));
            }
            if (normalized.endsWith("m") && normalized.indexOf(' ') < 0) {
                return plusMillis(base, String.valueOf(Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 60000L));
            }
            if (normalized.endsWith("h") && normalized.indexOf(' ') < 0) {
                return plusMillis(base, String.valueOf(Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 3600000L));
            }
            if (normalized.endsWith("d") && normalized.indexOf(' ') < 0) {
                return plusMillis(base, String.valueOf(Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 86400000L));
            }
        } catch (RuntimeException ignored) {
            return null;
        }

        return null;
    }

    private static Instant plusMillis(Instant base, String value) {
        long millis = Long.parseLong(value);
        if (millis <= 0) {
            return null;
        }
        return base.plusMillis(millis);
    }
}
