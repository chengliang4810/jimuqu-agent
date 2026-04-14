package com.jimuqu.claw.agent.tool;

import cn.hutool.core.thread.ThreadUtil;
import com.jimuqu.claw.agent.runtime.model.ManagedProcessRecord;
import com.jimuqu.claw.agent.runtime.model.ProcessStatus;
import com.jimuqu.claw.agent.store.ProcessStore;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.support.Ids;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class TerminalProcessManager {
    private static final int MAX_CAPTURE_CHARS = 200000;

    private final ProcessStore processStore;
    private final ClawProperties properties;
    private final Charset charset = Charset.defaultCharset();
    private final Map<String, ActiveProcess> activeProcesses = new ConcurrentHashMap<String, ActiveProcess>();

    TerminalProcessManager(ProcessStore processStore, ClawProperties properties) {
        this.processStore = processStore;
        this.properties = properties;
    }

    Map<String, Object> runForeground(String command, Path workdir, long timeoutMs) {
        Instant startedAt = Instant.now();
        try {
            Process process = createProcess(command, workdir);
            StreamAccumulator stdout = new StreamAccumulator();
            StreamAccumulator stderr = new StreamAccumulator();
            Thread[] readers = startReaders(process, stdout, stderr);

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
                waitForReaders(readers, 200L);
                return foregroundResult(command, workdir, startedAt, process, stdout, stderr, true);
            }

            waitForReaders(readers, 200L);
            return foregroundResult(command, workdir, startedAt, process, stdout, stderr, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Terminal execution was interrupted.");
        } catch (IOException e) {
            return error("Failed to start terminal command: " + e.getMessage());
        }
    }

    Map<String, Object> startBackground(String command, Path workdir) {
        int maxConcurrent = properties.getTerminal().getMaxConcurrentProcesses() == null
                ? 4
                : properties.getTerminal().getMaxConcurrentProcesses().intValue();
        if (maxConcurrent > 0 && activeProcesses.size() >= maxConcurrent) {
            return error("Too many background processes are already running.");
        }

        try {
            Process process = createProcess(command, workdir);
            ManagedProcessRecord record = ManagedProcessRecord.builder()
                    .processId(Ids.processId())
                    .pid(resolvePid(process))
                    .command(command)
                    .arguments(new ArrayList<String>())
                    .workingDirectory(workdir.toString())
                    .status(ProcessStatus.RUNNING)
                    .startedAt(Instant.now())
                    .build();

            ActiveProcess active = new ActiveProcess(record, process);
            activeProcesses.put(record.getProcessId(), active);
            processStore.save(record);
            Thread[] readers = startReaders(process, active.stdout, active.stderr);
            active.stdoutReader = readers[0];
            active.stderrReader = readers[1];
            ThreadUtil.execAsync(new Runnable() {
                @Override
                public void run() {
                    awaitBackgroundCompletion(active);
                }
            }, true);

            Map<String, Object> result = success();
            result.put("background", Boolean.TRUE);
            result.put("message", "Background process started.");
            result.put("process", formatProcess(refreshRecord(record.getProcessId())));
            return result;
        } catch (IOException e) {
            return error("Failed to start background command: " + e.getMessage());
        }
    }

    Map<String, Object> listProcesses() {
        List<ManagedProcessRecord> records = processStore.list();
        List<Map<String, Object>> processes = new ArrayList<Map<String, Object>>(records.size());
        for (ManagedProcessRecord record : records) {
            processes.add(formatProcess(refreshRecord(record.getProcessId())));
        }
        Collections.sort(processes, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                Instant leftStarted = (Instant) left.get("started_at");
                Instant rightStarted = (Instant) right.get("started_at");
                if (leftStarted == null && rightStarted == null) {
                    return 0;
                }
                if (leftStarted == null) {
                    return 1;
                }
                if (rightStarted == null) {
                    return -1;
                }
                return rightStarted.compareTo(leftStarted);
            }
        });

        Map<String, Object> result = success();
        result.put("count", Integer.valueOf(processes.size()));
        result.put("processes", processes);
        return result;
    }

    Map<String, Object> poll(String processId) {
        ManagedProcessRecord record = refreshRecord(processId);
        if (record == null) {
            return error("Process session not found: " + processId);
        }

        ActiveProcess active = activeProcesses.get(processId);
        Map<String, Object> result = success();
        result.put("process", formatProcess(record));
        result.put("running", Boolean.valueOf(active != null && active.process.isAlive()));
        result.put("stdout_delta", active == null ? "" : active.stdout.delta());
        result.put("stderr_delta", active == null ? "" : active.stderr.delta());
        return result;
    }

    Map<String, Object> readLog(String processId, int offset, int limit) {
        ManagedProcessRecord record = refreshRecord(processId);
        if (record == null) {
            return error("Process session not found: " + processId);
        }

        Map<String, Object> result = success();
        result.put("process", formatProcess(record));
        result.put("stdout", sliceLines(record.getStdout(), offset, limit));
        result.put("stderr", sliceLines(record.getStderr(), offset, limit));
        return result;
    }

    Map<String, Object> waitFor(String processId, long timeoutMs) {
        ManagedProcessRecord current = refreshRecord(processId);
        if (current == null) {
            return error("Process session not found: " + processId);
        }

        ActiveProcess active = activeProcesses.get(processId);
        if (active == null) {
            Map<String, Object> result = success();
            result.put("timed_out", Boolean.FALSE);
            result.put("process", formatProcess(current));
            result.put("stdout", current.getStdout());
            result.put("stderr", current.getStderr());
            return result;
        }

        try {
            boolean finished = active.process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            ManagedProcessRecord record = finished ? finalizeProcess(active, null) : refreshRecord(processId);
            Map<String, Object> result = success();
            result.put("timed_out", Boolean.valueOf(!finished));
            result.put("process", formatProcess(record));
            result.put("stdout", record == null ? null : record.getStdout());
            result.put("stderr", record == null ? null : record.getStderr());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Waiting for process was interrupted.");
        }
    }

    Map<String, Object> kill(String processId) {
        ManagedProcessRecord record = refreshRecord(processId);
        if (record == null) {
            return error("Process session not found: " + processId);
        }

        ActiveProcess active = activeProcesses.get(processId);
        if (active == null) {
            return error("Process is not attached or is already complete.");
        }

        active.process.destroy();
        try {
            if (!active.process.waitFor(2, TimeUnit.SECONDS)) {
                active.process.destroyForcibly();
                active.process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Killing the process was interrupted.");
        }

        ManagedProcessRecord finalized = finalizeProcess(active, ProcessStatus.STOPPED);
        Map<String, Object> result = success();
        result.put("message", "Process stopped.");
        result.put("process", formatProcess(finalized));
        return result;
    }

    private Process createProcess(String command, Path workdir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
        builder.directory(workdir.toFile());
        return builder.start();
    }

    private List<String> shellCommand(String command) {
        if (isWindows()) {
            return Arrays.asList("powershell.exe", "-NoProfile", "-Command", command);
        }
        return Arrays.asList("sh", "-lc", command);
    }

    private Thread[] startReaders(Process process, StreamAccumulator stdout, StreamAccumulator stderr) {
        Thread stdoutReader = new Thread(new Runnable() {
            @Override
            public void run() {
                drain(process.getInputStream(), stdout);
            }
        }, "claw-terminal-stdout");
        stdoutReader.setDaemon(true);
        stdoutReader.start();

        Thread stderrReader = new Thread(new Runnable() {
            @Override
            public void run() {
                drain(process.getErrorStream(), stderr);
            }
        }, "claw-terminal-stderr");
        stderrReader.setDaemon(true);
        stderrReader.start();

        return new Thread[]{stdoutReader, stderrReader};
    }

    private void drain(InputStream inputStream, StreamAccumulator target) {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(inputStream, charset);
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) >= 0) {
                if (len > 0) {
                    target.append(new String(buffer, 0, len));
                }
            }
        } catch (IOException ignored) {
            // Stream readers are best-effort snapshots for terminal output.
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // no-op
                }
            }
        }
    }

    private void awaitBackgroundCompletion(ActiveProcess active) {
        try {
            active.process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            finalizeProcess(active, null);
        }
    }

    private ManagedProcessRecord refreshRecord(String processId) {
        ActiveProcess active = activeProcesses.get(processId);
        if (active != null) {
            if (!active.process.isAlive()) {
                return finalizeProcess(active, null);
            }

            synchronized (active.record) {
                active.record.setStdout(active.stdout.snapshot());
                active.record.setStderr(active.stderr.snapshot());
                processStore.save(active.record);
                return active.record;
            }
        }

        ManagedProcessRecord record = processStore.get(processId);
        if (record == null) {
            return null;
        }

        if (record.getStatus() == ProcessStatus.RUNNING && record.getCompletedAt() == null) {
            record.setStatus(ProcessStatus.LOST);
            record.setCompletedAt(Instant.now());
            processStore.save(record);
        }

        return record;
    }

    private ManagedProcessRecord finalizeProcess(ActiveProcess active, ProcessStatus forcedStatus) {
        if (!active.finalized.compareAndSet(false, true)) {
            return awaitFinalizedRecord(active);
        }

        try {
            synchronized (active.record) {
                waitForReaders(active.stdoutReader, active.stderrReader);
                active.record.setStdout(active.stdout.snapshot());
                active.record.setStderr(active.stderr.snapshot());
                Integer exitCode = null;
                try {
                    exitCode = Integer.valueOf(active.process.exitValue());
                } catch (IllegalThreadStateException ignored) {
                    // keep null when the process refuses to exit cleanly
                }
                active.record.setExitCode(exitCode);
                active.record.setCompletedAt(Instant.now());
                active.record.setStatus(resolveStatus(forcedStatus, exitCode));
                processStore.save(active.record);
                activeProcesses.remove(active.record.getProcessId());
                return active.record;
            }
        } finally {
            active.finalizedSignal.countDown();
        }
    }

    private void waitForReaders(Thread... readers) {
        waitForReaders(readers, 200L);
    }

    private void waitForReaders(Thread[] readers, long timeoutMs) {
        if (readers == null) {
            return;
        }
        for (Thread reader : readers) {
            waitForReader(reader, timeoutMs);
        }
    }

    private void waitForReaders(Thread first, Thread second) {
        waitForReader(first, 0L);
        waitForReader(second, 0L);
    }

    private void waitForReader(Thread reader, long timeoutMs) {
        if (reader == null) {
            return;
        }
        try {
            if (timeoutMs <= 0L) {
                reader.join();
            } else {
                reader.join(timeoutMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ManagedProcessRecord awaitFinalizedRecord(ActiveProcess active) {
        try {
            active.finalizedSignal.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (active.record) {
            if (active.record.getCompletedAt() != null || active.record.getStatus() != ProcessStatus.RUNNING) {
                return active.record;
            }
        }

        ManagedProcessRecord stored = processStore.get(active.record.getProcessId());
        return stored == null ? active.record : stored;
    }

    private ProcessStatus resolveStatus(ProcessStatus forcedStatus, Integer exitCode) {
        if (forcedStatus != null) {
            return forcedStatus;
        }
        if (exitCode == null) {
            return ProcessStatus.LOST;
        }
        return exitCode.intValue() == 0 ? ProcessStatus.FINISHED : ProcessStatus.FAILED;
    }

    private Map<String, Object> foregroundResult(
            String command,
            Path workdir,
            Instant startedAt,
            Process process,
            StreamAccumulator stdout,
            StreamAccumulator stderr,
            boolean timedOut) {
        Map<String, Object> result = timedOut ? error("Terminal command timed out.") : success();
        Integer exitCode = null;
        try {
            exitCode = Integer.valueOf(process.exitValue());
        } catch (IllegalThreadStateException ignored) {
            // still null
        }
        result.put("background", Boolean.FALSE);
        result.put("command", command);
        result.put("working_directory", workdir.toString());
        result.put("started_at", startedAt);
        result.put("completed_at", Instant.now());
        result.put("timed_out", Boolean.valueOf(timedOut));
        result.put("exit_code", exitCode);
        result.put("success", Boolean.valueOf(!timedOut && exitCode != null && exitCode.intValue() == 0));
        result.put("stdout", stdout.snapshot());
        result.put("stderr", stderr.snapshot());
        return result;
    }

    private Map<String, Object> formatProcess(ManagedProcessRecord record) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("session_id", record.getProcessId());
        result.put("process_id", record.getProcessId());
        result.put("pid", record.getPid());
        result.put("command", record.getCommand());
        result.put("arguments", record.getArguments());
        result.put("working_directory", record.getWorkingDirectory());
        result.put("status", record.getStatus() == null ? null : record.getStatus().name().toLowerCase());
        result.put("exit_code", record.getExitCode());
        result.put("started_at", record.getStartedAt());
        result.put("completed_at", record.getCompletedAt());
        return result;
    }

    private Map<String, Object> sliceLines(String text, int offset, int limit) {
        String safeText = text == null ? "" : text;
        String[] lines = safeText.split("\\r?\\n", -1);
        int effectiveLimit = Math.max(1, limit);
        int start = offset >= 0 ? Math.min(offset, lines.length) : Math.max(0, lines.length - effectiveLimit);
        int end = Math.min(lines.length, start + effectiveLimit);
        List<String> page = new ArrayList<String>(Math.max(0, end - start));
        for (int i = start; i < end; i++) {
            page.add((i + 1) + "|" + lines[i]);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("offset", Integer.valueOf(start));
        result.put("limit", Integer.valueOf(effectiveLimit));
        result.put("total_lines", Integer.valueOf(lines.length));
        result.put("lines", page);
        result.put("text", page.isEmpty() ? "" : joinLines(page));
        return result;
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private Long resolvePid(Process process) {
        try {
            return Long.valueOf(String.valueOf(process.getClass().getMethod("pid").invoke(process)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private Map<String, Object> success() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.TRUE);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.FALSE);
        result.put("error", message);
        return result;
    }

    private static final class ActiveProcess {
        private final ManagedProcessRecord record;
        private final Process process;
        private final StreamAccumulator stdout = new StreamAccumulator();
        private final StreamAccumulator stderr = new StreamAccumulator();
        private final AtomicBoolean finalized = new AtomicBoolean(false);
        private final CountDownLatch finalizedSignal = new CountDownLatch(1);
        private Thread stdoutReader;
        private Thread stderrReader;

        private ActiveProcess(ManagedProcessRecord record, Process process) {
            this.record = record;
            this.process = process;
        }
    }

    private static final class StreamAccumulator {
        private final StringBuffer buffer = new StringBuffer();
        private int deltaOffset;

        private synchronized void append(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            buffer.append(text);
            if (buffer.length() > MAX_CAPTURE_CHARS) {
                int trim = buffer.length() - MAX_CAPTURE_CHARS;
                buffer.delete(0, trim);
                deltaOffset = Math.max(0, deltaOffset - trim);
            }
        }

        private synchronized String snapshot() {
            return buffer.toString();
        }

        private synchronized String delta() {
            if (deltaOffset > buffer.length()) {
                deltaOffset = buffer.length();
            }
            String delta = buffer.substring(deltaOffset);
            deltaOffset = buffer.length();
            return delta;
        }
    }
}
