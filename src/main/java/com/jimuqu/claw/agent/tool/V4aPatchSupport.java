package com.jimuqu.claw.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.agent.workspace.WorkspacePathGuard;
import com.jimuqu.claw.support.FileStoreSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class V4aPatchSupport {
    private V4aPatchSupport() {
    }

    static LinkedHashMap<String, Object> applyPatch(String patchContent, WorkspaceLayout workspaceLayout, WorkspacePathGuard workspacePathGuard) {
        try {
            List<PatchOperation> operations = parsePatch(patchContent);
            if (operations.isEmpty()) {
                return error("patch did not contain any operations");
            }

            List<String> touchedFiles = new ArrayList<String>();
            for (PatchOperation operation : operations) {
                applyOperation(operation, workspaceLayout, workspacePathGuard, touchedFiles);
            }

            LinkedHashMap<String, Object> result = success();
            result.put("operations", Integer.valueOf(operations.size()));
            result.put("files", touchedFiles);
            result.put("message", "Patch applied");
            return result;
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    private static void applyOperation(
            PatchOperation operation,
            WorkspaceLayout workspaceLayout,
            WorkspacePathGuard workspacePathGuard,
            List<String> touchedFiles) {
        if (operation.type == OperationType.ADD) {
            Path path = workspacePathGuard.resolveWorkspacePath(operation.path);
            if (path.toFile().exists()) {
                throw new IllegalArgumentException("File already exists: " + operation.path);
            }

            FileStoreSupport.writeUtf8Atomic(path, buildAddedFileContent(operation.hunks));
            touchedFiles.add(relativePath(workspaceLayout, path));
            return;
        }

        if (operation.type == OperationType.DELETE) {
            Path path = workspacePathGuard.resolveWorkspacePath(operation.path);
            if (!path.toFile().exists()) {
                throw new IllegalArgumentException("File not found: " + operation.path);
            }

            FileStoreSupport.deleteIfExists(path);
            touchedFiles.add(relativePath(workspaceLayout, path));
            return;
        }

        Path source = workspacePathGuard.resolveWorkspacePath(operation.path);
        String current = FileStoreSupport.readUtf8(source);
        if (current == null) {
            throw new IllegalArgumentException("File not found: " + operation.path);
        }

        String updated = applyHunks(operation.path, current, operation.hunks);
        Path target = source;
        if (StrUtil.isNotBlank(operation.moveTo)) {
            target = workspacePathGuard.resolveWorkspacePath(operation.moveTo);
            if (!source.equals(target) && target.toFile().exists()) {
                throw new IllegalArgumentException("Move target already exists: " + operation.moveTo);
            }
        }

        FileStoreSupport.writeUtf8Atomic(target, updated);
        if (!source.equals(target)) {
            FileStoreSupport.deleteIfExists(source);
            touchedFiles.add(relativePath(workspaceLayout, source));
        }
        touchedFiles.add(relativePath(workspaceLayout, target));
    }

    private static String applyHunks(String path, String current, List<PatchHunk> hunks) {
        boolean useCrlf = current.contains("\r\n");
        String working = normalizeNewlines(current);
        if (hunks == null || hunks.isEmpty()) {
            return restoreNewlines(working, useCrlf);
        }

        for (PatchHunk hunk : hunks) {
            working = applyHunk(path, working, hunk);
        }

        return restoreNewlines(working, useCrlf);
    }

    private static String applyHunk(String path, String content, PatchHunk hunk) {
        List<String> searchLines = new ArrayList<String>();
        List<String> replacementLines = new ArrayList<String>();
        for (HunkLine line : hunk.lines) {
            if (line.prefix == ' ' || line.prefix == '-') {
                searchLines.add(line.content);
            }
            if (line.prefix == ' ' || line.prefix == '+') {
                replacementLines.add(line.content);
            }
        }

        if (!searchLines.isEmpty()) {
            String searchText = StrUtil.join("\n", searchLines);
            String replacementText = StrUtil.join("\n", replacementLines);
            int first = content.indexOf(searchText);
            if (first < 0) {
                throw new IllegalArgumentException("Could not apply hunk to " + path + ": match not found");
            }
            if (content.indexOf(searchText, first + 1) >= 0) {
                throw new IllegalArgumentException("Could not apply hunk to " + path + ": match is ambiguous");
            }
            return content.substring(0, first) + replacementText + content.substring(first + searchText.length());
        }

        if (StrUtil.isBlank(hunk.contextHint)) {
            throw new IllegalArgumentException("Addition-only hunk in " + path + " requires a context hint");
        }

        String hint = normalizeHint(hunk.contextHint);
        int first = content.indexOf(hint);
        if (first < 0) {
            throw new IllegalArgumentException("Could not apply hunk to " + path + ": context hint not found");
        }
        if (content.indexOf(hint, first + 1) >= 0) {
            throw new IllegalArgumentException("Could not apply hunk to " + path + ": context hint is ambiguous");
        }

        String insertion = StrUtil.join("\n", replacementLines);
        int insertAt = first + hint.length();
        String prefix = insertion.isEmpty() ? "" : "\n" + insertion;
        return content.substring(0, insertAt) + prefix + content.substring(insertAt);
    }

    private static String buildAddedFileContent(List<PatchHunk> hunks) {
        List<String> lines = new ArrayList<String>();
        for (PatchHunk hunk : hunks) {
            for (HunkLine line : hunk.lines) {
                if (line.prefix == '+' || line.prefix == ' ') {
                    lines.add(line.content);
                }
            }
        }
        return StrUtil.join("\n", lines);
    }

    private static List<PatchOperation> parsePatch(String patchContent) {
        List<PatchOperation> operations = new ArrayList<PatchOperation>();
        String[] lines = normalizeNewlines(StrUtil.nullToDefault(patchContent, "")).split("\n", -1);

        PatchOperation currentOperation = null;
        PatchHunk currentHunk = null;
        boolean inPatch = false;
        for (String line : lines) {
            if (line.startsWith("*** Begin Patch")) {
                inPatch = true;
                continue;
            }
            if (line.startsWith("*** End Patch")) {
                break;
            }
            if (!inPatch && line.startsWith("***")) {
                inPatch = true;
            }
            if (!inPatch) {
                continue;
            }

            if (line.startsWith("*** Update File:")) {
                currentOperation = flushOperation(operations, currentOperation, currentHunk);
                currentHunk = null;
                currentOperation = new PatchOperation(OperationType.UPDATE, extractValue(line, "*** Update File:"));
                continue;
            }
            if (line.startsWith("*** Add File:")) {
                currentOperation = flushOperation(operations, currentOperation, currentHunk);
                currentHunk = new PatchHunk(null);
                currentOperation = new PatchOperation(OperationType.ADD, extractValue(line, "*** Add File:"));
                continue;
            }
            if (line.startsWith("*** Delete File:")) {
                currentOperation = flushOperation(operations, currentOperation, currentHunk);
                currentHunk = null;
                operations.add(new PatchOperation(OperationType.DELETE, extractValue(line, "*** Delete File:")));
                currentOperation = null;
                continue;
            }
            if (line.startsWith("*** Move File:")) {
                currentOperation = flushOperation(operations, currentOperation, currentHunk);
                currentHunk = null;
                String body = extractValue(line, "*** Move File:");
                int arrow = body.indexOf("->");
                if (arrow < 0) {
                    throw new IllegalArgumentException("Invalid move operation: " + line);
                }
                PatchOperation move = new PatchOperation(OperationType.UPDATE, body.substring(0, arrow).trim());
                move.moveTo = body.substring(arrow + 2).trim();
                operations.add(move);
                currentOperation = null;
                continue;
            }
            if (line.startsWith("*** Move to:")) {
                if (currentOperation == null) {
                    throw new IllegalArgumentException("Move target specified without an active file operation");
                }
                currentOperation.moveTo = extractValue(line, "*** Move to:");
                continue;
            }
            if (line.startsWith("@@")) {
                if (currentOperation == null) {
                    throw new IllegalArgumentException("Hunk declared without an active file operation");
                }
                if (currentHunk != null && !currentHunk.lines.isEmpty()) {
                    currentOperation.hunks.add(currentHunk);
                }
                currentHunk = new PatchHunk(extractHint(line));
                continue;
            }

            if (currentOperation == null) {
                continue;
            }

            if (currentHunk == null) {
                currentHunk = new PatchHunk(null);
            }

            if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ")) {
                currentHunk.lines.add(new HunkLine(line.charAt(0), line.substring(1)));
            } else if (line.startsWith("\\")) {
                continue;
            } else {
                currentHunk.lines.add(new HunkLine(' ', line));
            }
        }

        flushOperation(operations, currentOperation, currentHunk);

        for (PatchOperation operation : operations) {
            if (StrUtil.isBlank(operation.path)) {
                throw new IllegalArgumentException("Patch operation is missing a file path");
            }
            if (operation.type == OperationType.UPDATE && operation.hunks.isEmpty() && StrUtil.isBlank(operation.moveTo)) {
                throw new IllegalArgumentException("Update operation has no hunks: " + operation.path);
            }
        }

        return operations;
    }

    private static PatchOperation flushOperation(List<PatchOperation> operations, PatchOperation operation, PatchHunk hunk) {
        if (operation == null) {
            return null;
        }
        if (hunk != null && !hunk.lines.isEmpty()) {
            operation.hunks.add(hunk);
        }
        operations.add(operation);
        return null;
    }

    private static String extractValue(String line, String marker) {
        return line.substring(marker.length()).trim();
    }

    private static String extractHint(String line) {
        String hint = line.substring(2).trim();
        if (hint.endsWith("@@")) {
            hint = hint.substring(0, hint.length() - 2).trim();
        }
        return hint;
    }

    private static String normalizeHint(String hint) {
        return normalizeNewlines(hint.trim());
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n");
    }

    private static String restoreNewlines(String value, boolean useCrlf) {
        return useCrlf ? value.replace("\n", "\r\n") : value;
    }

    private static String relativePath(WorkspaceLayout workspaceLayout, Path path) {
        return workspaceLayout.getRoot().relativize(path).toString().replace('\\', '/');
    }

    private static LinkedHashMap<String, Object> success() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.TRUE);
        return result;
    }

    private static LinkedHashMap<String, Object> error(String message) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.FALSE);
        result.put("error", message);
        return result;
    }

    private enum OperationType {
        ADD,
        UPDATE,
        DELETE
    }

    private static class PatchOperation {
        private final OperationType type;
        private final String path;
        private String moveTo;
        private final List<PatchHunk> hunks = new ArrayList<PatchHunk>();

        private PatchOperation(OperationType type, String path) {
            this.type = type;
            this.path = path;
        }
    }

    private static class PatchHunk {
        private final String contextHint;
        private final List<HunkLine> lines = new ArrayList<HunkLine>();

        private PatchHunk(String contextHint) {
            this.contextHint = contextHint;
        }
    }

    private static class HunkLine {
        private final char prefix;
        private final String content;

        private HunkLine(char prefix, String content) {
            this.prefix = prefix;
            this.content = content;
        }
    }
}
