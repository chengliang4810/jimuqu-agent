package com.jimuqu.claw.agent.tool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.job.JobExecutionService;
import com.jimuqu.claw.agent.job.JobScheduleSupport;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.JobRecord;
import com.jimuqu.claw.agent.runtime.model.JobStatus;
import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.agent.store.JobStore;
import com.jimuqu.claw.agent.store.ProcessStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.agent.workspace.WorkspacePathGuard;
import com.jimuqu.claw.channel.ChannelAdapter;
import com.jimuqu.claw.channel.model.ChannelOutboundMessage;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.skill.SkillCatalog;
import com.jimuqu.claw.skill.SkillManagerService;
import com.jimuqu.claw.support.FileStoreSupport;
import com.jimuqu.claw.support.Ids;
import com.jimuqu.claw.support.JsonSupport;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.ChatSession;
import org.noear.solon.ai.chat.tool.AbsTool;
import org.noear.solon.ai.chat.tool.FunctionTool;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultToolRegistry implements ToolRegistry {
    private final WorkspaceLayout workspaceLayout;
    private final WorkspacePathGuard workspacePathGuard;
    private final SkillCatalog skillCatalog;
    private final SkillManagerService skillManagerService;
    private final JobStore jobStore;
    private final ClawProperties properties;
    private final TerminalProcessManager terminalProcessManager;
    private final List<ChannelAdapter> channelAdapters;
    private final JobExecutionService jobExecutionService;
    private final RuntimeServiceResolver runtimeServiceResolver;
    private final Map<String, FunctionTool> tools = new LinkedHashMap<String, FunctionTool>();

    public DefaultToolRegistry(
            WorkspaceLayout workspaceLayout,
            WorkspacePathGuard workspacePathGuard,
            SkillCatalog skillCatalog,
            SkillManagerService skillManagerService,
            JobStore jobStore,
            ProcessStore processStore,
            ClawProperties properties,
            List<ChannelAdapter> channelAdapters) {
        this(
                workspaceLayout,
                workspacePathGuard,
                skillCatalog,
                skillManagerService,
                jobStore,
                processStore,
                properties,
                channelAdapters,
                null,
                null);
    }

    public DefaultToolRegistry(
            WorkspaceLayout workspaceLayout,
            WorkspacePathGuard workspacePathGuard,
            SkillCatalog skillCatalog,
            SkillManagerService skillManagerService,
            JobStore jobStore,
            ProcessStore processStore,
            ClawProperties properties,
            List<ChannelAdapter> channelAdapters,
            RuntimeServiceResolver runtimeServiceResolver) {
        this(
                workspaceLayout,
                workspacePathGuard,
                skillCatalog,
                skillManagerService,
                jobStore,
                processStore,
                properties,
                channelAdapters,
                null,
                runtimeServiceResolver);
    }

    public DefaultToolRegistry(
            WorkspaceLayout workspaceLayout,
            WorkspacePathGuard workspacePathGuard,
            SkillCatalog skillCatalog,
            SkillManagerService skillManagerService,
            JobStore jobStore,
            ProcessStore processStore,
            ClawProperties properties,
            List<ChannelAdapter> channelAdapters,
            JobExecutionService jobExecutionService,
            RuntimeServiceResolver runtimeServiceResolver) {
        this.workspaceLayout = workspaceLayout;
        this.workspacePathGuard = workspacePathGuard;
        this.skillCatalog = skillCatalog;
        this.skillManagerService = skillManagerService;
        this.jobStore = jobStore;
        this.properties = properties;
        this.terminalProcessManager = new TerminalProcessManager(processStore, properties);
        this.channelAdapters = channelAdapters == null ? new ArrayList<ChannelAdapter>() : channelAdapters;
        this.jobExecutionService = jobExecutionService;
        this.runtimeServiceResolver = runtimeServiceResolver;
        registerBuiltins();
    }

    @Override
    public Collection<FunctionTool> allTools() {
        return tools.values();
    }

    @Override
    public FunctionTool get(String name) {
        return tools.get(name);
    }

    private void registerBuiltins() {
        register(readFileTool());
        register(writeFileTool());
        register(patchTool());
        register(searchFilesTool());
        register(todoTool());
        register(memoryTool());
        register(delegateTaskTool());
        register(cronjobTool());
        register(terminalTool());
        register(processTool());
        register(sendMessageTool());
        register(skillsListTool());
        register(skillViewTool());
        register(skillManageTool());
    }

    private void register(FunctionTool tool) {
        tools.put(tool.name(), tool);
    }

    private FunctionTool readFileTool() {
        return new RawTool(
                "read_file",
                "Read a workspace text file with line numbers and pagination.",
                schemaReadFile(),
                args -> handleReadFile(args));
    }

    private FunctionTool writeFileTool() {
        return new RawTool(
                "write_file",
                "Write a workspace text file, replacing the full content if it already exists.",
                schemaWriteFile(),
                args -> handleWriteFile(args));
    }

    private FunctionTool patchTool() {
        return new RawTool(
                "patch",
                "Patch workspace files in replace mode or Hermes-style V4A patch mode.",
                schemaPatch(),
                args -> handlePatch(args));
    }

    private FunctionTool searchFilesTool() {
        return new RawTool(
                "search_files",
                "Search workspace files by content or by filename.",
                schemaSearchFiles(),
                args -> handleSearchFiles(args));
    }

    private FunctionTool todoTool() {
        return new RawTool(
                "todo",
                "Manage the todo list for the current session. Omit todos to read, provide todos to write.",
                schemaTodo(),
                args -> handleTodo(args));
    }

    private FunctionTool memoryTool() {
        return new RawTool(
                "memory",
                "Manage durable workspace memory in MEMORY.md or USER.md.",
                schemaMemory(),
                args -> handleMemory(args));
    }

    private FunctionTool delegateTaskTool() {
        return new RawTool(
                "delegate_task",
                "Create a child task within the unified runtime.",
                schemaDelegateTask(),
                args -> handleDelegateTask(args));
    }

    private FunctionTool cronjobTool() {
        return new RawTool(
                "cronjob",
                "Manage scheduled jobs with a single Hermes-style action tool.",
                schemaCronjob(),
                args -> handleCronjob(args));
    }

    private FunctionTool terminalTool() {
        return new RawTool(
                "terminal",
                "Execute a controlled terminal command in the workspace.",
                schemaTerminal(),
                args -> handleTerminal(args));
    }

    private FunctionTool processTool() {
        return new RawTool(
                "process",
                "Manage background processes started by terminal(background=true).",
                schemaProcess(),
                args -> handleProcess(args));
    }

    private FunctionTool sendMessageTool() {
        return new RawTool(
                "send_message",
                "Send a message to a configured channel adapter, or list available targets.",
                schemaSendMessage(),
                args -> handleSendMessage(args));
    }

    private FunctionTool skillsListTool() {
        return new RawTool(
                "skills_list",
                "List available skills with lightweight metadata.",
                schemaSkillsList(),
                args -> handleSkillsList(args));
    }

    private FunctionTool skillViewTool() {
        return new RawTool(
                "skill_view",
                "View a skill or one linked file under that skill.",
                schemaSkillView(),
                args -> handleSkillView(args));
    }

    private FunctionTool skillManageTool() {
        return new RawTool(
                "skill_manage",
                "Create, update, or remove a skill and its supporting files.",
                schemaSkillManage(),
                args -> handleSkillManage(args));
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

    private Map<String, Object> placeholder(String message) {
        Map<String, Object> result = error(message);
        result.put("implemented", Boolean.FALSE);
        return result;
    }

    private String stringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private String stringArg(Map<String, Object> args, String name, String defaultValue) {
        String value = stringArg(args, name);
        return value == null ? defaultValue : value;
    }

    private boolean boolArg(Map<String, Object> args, String name, boolean defaultValue) {
        Object value = args.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intArg(Map<String, Object> args, String name, int defaultValue) {
        Object value = args.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private List<Map<String, Object>> mapListArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (!(value instanceof List)) {
            return null;
        }

        List<?> rawList = (List<?>) value;
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (Object item : rawList) {
            if (item instanceof Map) {
                results.add((Map<String, Object>) item);
            }
        }
        return results;
    }

    private List<String> stringListArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (!(value instanceof List)) {
            return null;
        }

        List<?> rawList = (List<?>) value;
        List<String> results = new ArrayList<String>();
        for (Object item : rawList) {
            if (item != null) {
                results.add(String.valueOf(item));
            }
        }
        return results;
    }

    private String schemaReadFile() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty("Path to the file to read."));
        properties.put("offset", integerProperty("1-based line offset to start reading from."));
        properties.put("limit", integerProperty("Maximum number of lines to return."));
        return objectSchema(properties, listOf("path"));
    }

    private String schemaWriteFile() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty("Path to the file to write."));
        properties.put("content", stringProperty("Complete file content to persist."));
        return objectSchema(properties, listOf("path", "content"));
    }

    private String schemaPatch() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("mode", enumProperty("replace", "patch"));
        properties.put("path", stringProperty("Workspace file to edit in replace mode."));
        properties.put("old_string", stringProperty("Existing text to locate in replace mode."));
        properties.put("new_string", stringProperty("Replacement text in replace mode."));
        properties.put("replace_all", booleanProperty("Replace all matches instead of requiring a unique match."));
        properties.put("patch", stringProperty("Hermes patch payload in patch mode."));
        return objectSchema(properties, Collections.<String>emptyList());
    }

    private String schemaSearchFiles() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("pattern", stringProperty("Regex for content search or glob for file search."));
        properties.put("target", enumProperty("content", "files"));
        properties.put("path", stringProperty("Directory or file path to search under."));
        properties.put("file_glob", stringProperty("Optional file glob filter for content search."));
        properties.put("limit", integerProperty("Maximum number of results to return."));
        properties.put("offset", integerProperty("Number of results to skip."));
        properties.put("output_mode", enumProperty("content", "files_only", "count"));
        properties.put("context", integerProperty("Context lines around matches in content mode."));
        return objectSchema(properties, listOf("pattern"));
    }

    private String schemaTodo() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        Map<String, Object> todoItem = new LinkedHashMap<String, Object>();
        Map<String, Object> todoProps = new LinkedHashMap<String, Object>();
        todoProps.put("id", stringProperty("Stable item identifier."));
        todoProps.put("content", stringProperty("Task description."));
        todoProps.put("status", enumProperty("pending", "in_progress", "completed", "cancelled"));
        todoItem.put("type", "object");
        todoItem.put("properties", todoProps);
        properties.put("todos", arrayProperty(todoItem, "Task items to write. Omit to read the current list."));
        properties.put("merge", booleanProperty("When true, update by id instead of replacing the whole list."));
        return objectSchema(properties, Collections.<String>emptyList());
    }

    private String schemaMemory() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("action", enumProperty("add", "replace", "remove", "read"));
        properties.put("target", enumProperty("memory", "user"));
        properties.put("content", stringProperty("Entry content for add or replace."));
        properties.put("old_text", stringProperty("Unique substring used to identify an existing entry."));
        return objectSchema(properties, Collections.<String>emptyList());
    }

    private String schemaDelegateTask() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("task", stringProperty("Task description for the child run."));
        properties.put("context", stringProperty("Optional context passed into the child run."));
        return objectSchema(properties, listOf("task"));
    }

    private String schemaCronjob() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("action", stringProperty("One of create, list, update, pause, resume, remove, run."));
        properties.put("job_id", stringProperty("Required for update, pause, resume, remove, and run."));
        properties.put("prompt", stringProperty("Self-contained prompt for the job."));
        properties.put("schedule", stringProperty("Cron or interval expression."));
        properties.put("name", stringProperty("Human friendly job name."));
        properties.put("repeat", integerProperty("Reserved repeat count."));
        properties.put("deliver", stringProperty("Delivery target using Hermes route semantics."));
        properties.put("include_disabled", booleanProperty("Include paused jobs when listing."));
        properties.put("skills", arrayProperty(stringProperty("Skill name"), "Optional ordered list of attached skill names."));
        properties.put("model", stringProperty("Optional model alias override."));
        properties.put("reason", stringProperty("Pause reason."));
        properties.put("script", stringProperty("Reserved script path."));
        return objectSchema(properties, listOf("action"));
    }

    private String schemaTerminal() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("command", stringProperty("Terminal command line to run."));
        properties.put("background", booleanProperty("Run in the background and manage it later through process."));
        properties.put("workdir", stringProperty("Optional workspace-relative working directory."));
        properties.put("timeout", integerProperty("Optional timeout in seconds for foreground execution."));
        properties.put("timeout_ms", integerProperty("Optional timeout in milliseconds for foreground execution."));
        return objectSchema(properties, listOf("command"));
    }

    private String schemaProcess() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("action", stringProperty("One of list, poll, log, wait, kill. Aliases view->poll and stop->kill are also accepted."));
        properties.put("session_id", stringProperty("Background process session identifier."));
        properties.put("process_id", stringProperty("Alias of session_id."));
        properties.put("timeout", integerProperty("Optional timeout in seconds for wait."));
        properties.put("timeout_ms", integerProperty("Optional timeout in milliseconds for wait."));
        properties.put("offset", integerProperty("Optional 0-based line offset for log."));
        properties.put("limit", integerProperty("Optional page size for log."));
        return objectSchema(properties, listOf("action"));
    }

    private String schemaSendMessage() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("action", enumProperty("send", "list"));
        properties.put("target", stringProperty("Target route in platform[:chat_id[:thread_id]] format."));
        properties.put("message", stringProperty("Message body to send."));
        return objectSchema(properties, Collections.<String>emptyList());
    }

    private String schemaSkillsList() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("category", stringProperty("Optional category filter."));
        return objectSchema(properties, Collections.<String>emptyList());
    }

    private String schemaSkillView() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("name", stringProperty("Skill name."));
        properties.put("file_path", stringProperty("Optional linked file under the skill directory."));
        return objectSchema(properties, listOf("name"));
    }

    private String schemaSkillManage() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("action", stringProperty("create, edit, patch, delete, write_file, or remove_file"));
        properties.put("name", stringProperty("Skill name."));
        properties.put("content", stringProperty("Full SKILL.md content for create or edit."));
        properties.put("category", stringProperty("Optional category for create."));
        properties.put("file_path", stringProperty("Optional linked file path."));
        properties.put("file_content", stringProperty("Content for write_file."));
        properties.put("old_string", stringProperty("Target text for patch."));
        properties.put("new_string", stringProperty("Replacement text for patch."));
        properties.put("replace_all", booleanProperty("Replace all matches in patch mode."));
        return objectSchema(properties, listOf("action", "name"));
    }

    private Map<String, Object> handleReadFile(Map<String, Object> args) {
        String pathValue = stringArg(args, "path");
        if (StrUtil.isBlank(pathValue)) {
            return error("path is required");
        }

        int offset = Math.max(1, intArg(args, "offset", 1));
        int limit = Math.max(1, Math.min(intArg(args, "limit", 500), 2000));
        Path path = workspacePathGuard.resolveWorkspacePath(pathValue);
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            return error("File not found: " + pathValue);
        }

        String content = FileUtil.readString(file, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n", -1);
        int startIndex = Math.min(lines.length, offset - 1);
        int endIndex = Math.min(lines.length, startIndex + limit);
        StringBuilder buffer = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(i + 1).append('|').append(lines[i]);
        }

        Map<String, Object> result = success();
        result.put("path", workspaceLayout.getRoot().relativize(path).toString().replace('\\', '/'));
        result.put("offset", Integer.valueOf(offset));
        result.put("limit", Integer.valueOf(limit));
        result.put("total_lines", Integer.valueOf(lines.length));
        result.put("truncated", Boolean.valueOf(endIndex < lines.length));
        result.put("content", buffer.toString());
        return result;
    }

    private Map<String, Object> handleWriteFile(Map<String, Object> args) {
        String pathValue = stringArg(args, "path");
        String content = stringArg(args, "content", "");
        if (StrUtil.isBlank(pathValue)) {
            return error("path is required");
        }

        Path path = workspacePathGuard.resolveWorkspacePath(pathValue);
        FileStoreSupport.writeUtf8Atomic(path, content);

        Map<String, Object> result = success();
        result.put("path", workspaceLayout.getRoot().relativize(path).toString().replace('\\', '/'));
        result.put("bytes", Integer.valueOf(content.getBytes(StandardCharsets.UTF_8).length));
        result.put("message", "File written");
        return result;
    }

    private Map<String, Object> handlePatch(Map<String, Object> args) {
        String mode = stringArg(args, "mode", "replace");
        if ("patch".equalsIgnoreCase(mode)) {
            String patchContent = stringArg(args, "patch");
            if (StrUtil.isBlank(patchContent)) {
                return error("patch is required for patch mode");
            }
            return V4aPatchSupport.applyPatch(patchContent, workspaceLayout, workspacePathGuard);
        }

        String pathValue = stringArg(args, "path");
        String oldString = stringArg(args, "old_string");
        String newString = stringArg(args, "new_string");
        boolean replaceAll = boolArg(args, "replace_all", false);
        if (StrUtil.isBlank(pathValue)) {
            return error("path is required for replace mode");
        }
        if (oldString == null) {
            return error("old_string is required for replace mode");
        }
        if (newString == null) {
            return error("new_string is required for replace mode");
        }

        Path path = workspacePathGuard.resolveWorkspacePath(pathValue);
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            return error("File not found: " + pathValue);
        }

        String current = FileUtil.readString(file, StandardCharsets.UTF_8);
        int occurrences = StrUtil.count(current, oldString);
        if (occurrences == 0) {
            return error("old_string not found");
        }
        if (!replaceAll && occurrences > 1) {
            return error("old_string is not unique; use replace_all=true");
        }

        String updated = replaceAll ? current.replace(oldString, newString) : StrUtil.replace(current, oldString, newString, false);
        FileStoreSupport.writeUtf8Atomic(path, updated);

        Map<String, Object> result = success();
        result.put("path", workspaceLayout.getRoot().relativize(path).toString().replace('\\', '/'));
        result.put("replacements", Integer.valueOf(replaceAll ? occurrences : 1));
        result.put("message", "File patched");
        return result;
    }

    private Map<String, Object> handleSearchFiles(Map<String, Object> args) {
        String patternValue = stringArg(args, "pattern");
        if (StrUtil.isBlank(patternValue)) {
            return error("pattern is required");
        }

        String target = stringArg(args, "target", "content");
        String pathValue = StrUtil.blankToDefault(stringArg(args, "path"), ".");
        int limit = Math.max(1, Math.min(intArg(args, "limit", 50), 200));
        int offset = Math.max(0, intArg(args, "offset", 0));
        String fileGlob = stringArg(args, "file_glob");
        String outputMode = stringArg(args, "output_mode", "content");
        int context = Math.max(0, Math.min(intArg(args, "context", 0), 5));

        Path searchRoot = workspacePathGuard.resolveWorkspacePath(pathValue);
        if (!Files.exists(searchRoot)) {
            return error("Search path not found: " + pathValue);
        }

        if ("files".equalsIgnoreCase(target)) {
            return searchByFileName(searchRoot, patternValue, limit, offset);
        }

        return searchByContent(searchRoot, patternValue, fileGlob, limit, offset, outputMode, context);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> booleanProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "boolean");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> enumProperty(String... values) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("enum", values);
        return property;
    }

    private Map<String, Object> arrayProperty(Object items, String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "array");
        property.put("description", description);
        property.put("items", items);
        return property;
    }

    private String objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return JsonSupport.toJson(schema);
    }

    private List<String> listOf(String... values) {
        List<String> result = new ArrayList<String>();
        Collections.addAll(result, values);
        return result;
    }

    private Map<String, Object> searchByFileName(Path searchRoot, String patternValue, int limit, int offset) {
        String glob = patternValue.contains("*") || patternValue.contains("?") ? patternValue : "*" + patternValue + "*";
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
        List<File> files = FileUtil.loopFiles(searchRoot.toFile());
        Collections.sort(files);
        for (File file : files) {
            String relativePath = searchRoot.relativize(file.toPath()).toString().replace('\\', '/');
            if (matchesGlob(glob, relativePath) || matchesGlob(glob, file.getName())) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("path", relativePath);
                item.put("size", Long.valueOf(file.length()));
                matches.add(item);
            }
        }
        return paginateMatches(matches, limit, offset);
    }

    private Map<String, Object> searchByContent(
            Path searchRoot,
            String patternValue,
            String fileGlob,
            int limit,
            int offset,
            String outputMode,
            int context) {
        Pattern pattern = Pattern.compile(patternValue, Pattern.MULTILINE);
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
        List<File> files = FileUtil.loopFiles(searchRoot.toFile());
        Collections.sort(files);
        for (File file : files) {
            String relativePath = searchRoot.relativize(file.toPath()).toString().replace('\\', '/');
            if (StrUtil.isNotBlank(fileGlob) && !matchesGlob(fileGlob, relativePath) && !matchesGlob(fileGlob, file.getName())) {
                continue;
            }

            String content = FileUtil.readString(file, StandardCharsets.UTF_8);
            String[] lines = content.split("\\r?\\n", -1);
            int fileMatches = 0;
            for (int i = 0; i < lines.length; i++) {
                Matcher matcher = pattern.matcher(lines[i]);
                if (!matcher.find()) {
                    continue;
                }

                fileMatches++;
                if ("count".equalsIgnoreCase(outputMode)) {
                    continue;
                }

                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("path", relativePath);
                item.put("line", Integer.valueOf(i + 1));
                if ("files_only".equalsIgnoreCase(outputMode)) {
                    matches.add(item);
                    break;
                }

                item.put("content", lines[i]);
                if (context > 0) {
                    item.put("before", surroundingLines(lines, Math.max(0, i - context), i));
                    item.put("after", surroundingLines(lines, i + 1, Math.min(lines.length, i + context + 1)));
                }
                matches.add(item);
            }

            if ("count".equalsIgnoreCase(outputMode) && fileMatches > 0) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("path", relativePath);
                item.put("count", Integer.valueOf(fileMatches));
                matches.add(item);
            }
        }
        return paginateMatches(matches, limit, offset);
    }

    private List<String> surroundingLines(String[] lines, int start, int end) {
        List<String> result = new ArrayList<String>();
        for (int i = start; i < end; i++) {
            result.add((i + 1) + "|" + lines[i]);
        }
        return result;
    }

    private Map<String, Object> paginateMatches(List<Map<String, Object>> matches, int limit, int offset) {
        int safeOffset = Math.min(offset, matches.size());
        int endIndex = Math.min(matches.size(), safeOffset + limit);
        List<Map<String, Object>> page = new ArrayList<Map<String, Object>>(matches.subList(safeOffset, endIndex));
        Map<String, Object> result = success();
        result.put("count", Integer.valueOf(page.size()));
        result.put("total_count", Integer.valueOf(matches.size()));
        result.put("offset", Integer.valueOf(offset));
        result.put("limit", Integer.valueOf(limit));
        result.put("truncated", Boolean.valueOf(endIndex < matches.size()));
        result.put("matches", page);
        return result;
    }

    private boolean matchesGlob(String glob, String value) {
        return Paths.get(value).getFileSystem().getPathMatcher("glob:" + glob).matches(Paths.get(value));
    }

    private Map<String, Object> handleTodo(Map<String, Object> args) {
        String sessionId = stringArg(args, ChatSession.ATTR_SESSIONID);
        if (StrUtil.isBlank(sessionId)) {
            return error("Missing session context for todo");
        }

        TodoState state = loadTodoState(sessionId);
        List<Map<String, Object>> todosArg = mapListArg(args, "todos");
        boolean merge = boolArg(args, "merge", false);
        if (todosArg != null) {
            state.todos = merge ? mergeTodos(state.todos, todosArg) : normalizeTodos(todosArg);
            int inProgressCount = 0;
            for (TodoItem item : state.todos) {
                if ("in_progress".equals(item.status)) {
                    inProgressCount++;
                }
            }
            if (inProgressCount > 1) {
                return error("Only one todo item may be in_progress");
            }
            saveTodoState(sessionId, state);
        }

        Map<String, Object> result = success();
        result.put("todos", state.toMapList());
        result.put("summary", state.summary());
        return result;
    }

    private TodoState loadTodoState(String sessionId) {
        String json = FileStoreSupport.readUtf8(workspaceLayout.sessionTodoFile(sessionId));
        if (StrUtil.isBlank(json)) {
            return new TodoState();
        }

        return ONode.deserialize(json, TodoState.class);
    }

    private void saveTodoState(String sessionId, TodoState state) {
        FileStoreSupport.writeUtf8Atomic(workspaceLayout.sessionTodoFile(sessionId), JsonSupport.toJson(state));
    }

    private List<TodoItem> normalizeTodos(List<Map<String, Object>> todosArg) {
        List<TodoItem> items = new ArrayList<TodoItem>();
        for (Map<String, Object> raw : todosArg) {
            items.add(TodoItem.of(raw));
        }
        return items;
    }

    private List<TodoItem> mergeTodos(List<TodoItem> existing, List<Map<String, Object>> updates) {
        Map<String, TodoItem> merged = new LinkedHashMap<String, TodoItem>();
        for (TodoItem item : existing) {
            merged.put(item.id, item.copy());
        }
        for (Map<String, Object> raw : updates) {
            TodoItem incoming = TodoItem.of(raw);
            TodoItem current = merged.get(incoming.id);
            if (current == null) {
                merged.put(incoming.id, incoming);
            } else {
                current.content = incoming.content;
                current.status = incoming.status;
            }
        }
        return new ArrayList<TodoItem>(merged.values());
    }

    private Map<String, Object> handleMemory(Map<String, Object> args) {
        String action = StrUtil.blankToDefault(stringArg(args, "action"), "read");
        String target = StrUtil.blankToDefault(stringArg(args, "target"), "memory");
        Path file = "user".equalsIgnoreCase(target) ? workspaceLayout.userFile() : workspaceLayout.memoryFile();
        MemoryState state = loadMemoryState(file);

        if ("read".equalsIgnoreCase(action)) {
            return state.response(target);
        }

        String content = stringArg(args, "content");
        String oldText = stringArg(args, "old_text");
        if ("add".equalsIgnoreCase(action)) {
            if (StrUtil.isBlank(content)) {
                return error("content is required for add");
            }
            return mutateMemory(file, state, target, state.add(content, target));
        }
        if ("replace".equalsIgnoreCase(action)) {
            if (StrUtil.isBlank(content) || StrUtil.isBlank(oldText)) {
                return error("content and old_text are required for replace");
            }
            return mutateMemory(file, state, target, state.replace(oldText, content, target));
        }
        if ("remove".equalsIgnoreCase(action)) {
            if (StrUtil.isBlank(oldText)) {
                return error("old_text is required for remove");
            }
            return mutateMemory(file, state, target, state.remove(oldText));
        }

        return error("Unknown memory action: " + action);
    }

    private Map<String, Object> mutateMemory(Path file, MemoryState state, String target, MemoryMutation mutation) {
        if (!mutation.success) {
            return error(mutation.message);
        }

        saveMemoryState(file, state);
        Map<String, Object> response = state.response(target);
        response.put("message", mutation.message);
        return response;
    }

    private MemoryState loadMemoryState(Path file) {
        String raw = FileStoreSupport.readUtf8(file);
        return MemoryState.parse(raw);
    }

    private void saveMemoryState(Path file, MemoryState state) {
        FileStoreSupport.writeUtf8Atomic(file, state.render());
    }

    private Map<String, Object> handleDelegateTask(Map<String, Object> args) {
        String task = stringArg(args, "task");
        if (StrUtil.isBlank(task)) {
            return error("task is required");
        }

        String parentRunId = stringArg(args, "__runId");
        String parentSessionId = stringArg(args, ChatSession.ATTR_SESSIONID);
        if (StrUtil.isBlank(parentRunId) || StrUtil.isBlank(parentSessionId)) {
            return error("delegate_task requires active runtime context");
        }

        Integer depthLimit = properties.getRuntime().getDelegateDepthLimit();
        int currentDepth = intArg(args, "__delegateDepth", 0);
        if (depthLimit != null && depthLimit.intValue() >= 0 && currentDepth >= depthLimit.intValue()) {
            return error("Delegate depth limit reached.");
        }

        RuntimeService runtimeService = runtimeServiceResolver == null ? null : runtimeServiceResolver.resolve();
        if (runtimeService == null) {
            return error("Runtime service is unavailable");
        }

        int nextDepth = currentDepth + 1;
        String childRunId = Ids.childRunId();
        RunRequest request = RunRequest.builder()
                .runId(childRunId)
                .parentRunId(parentRunId)
                .sessionContext(buildChildSessionContext(args, parentSessionId, childRunId, nextDepth))
                .userMessage(buildDelegatedPrompt(task, stringArg(args, "context")))
                .source("delegate_task")
                .createdAt(Instant.now())
                .build();

        try {
            RunRecord childRun = runtimeService.handleRequest(request);
            return formatDelegateResult(childRun, nextDepth);
        } catch (Throwable e) {
            return error("delegate_task failed: " + e.getMessage());
        }
    }

    private SessionContext buildChildSessionContext(Map<String, Object> args, String parentSessionId, String childRunId, int nextDepth) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("delegateDepth", Integer.valueOf(nextDepth));
        metadata.put("originSessionId", parentSessionId);
        metadata.put("parentRunId", stringArg(args, "__runId"));

        return SessionContext.builder()
                .sessionId(buildChildSessionId(parentSessionId, childRunId))
                .platform(stringArg(args, "__platform"))
                .chatId(stringArg(args, "__chatId"))
                .threadId(stringArg(args, "__threadId"))
                .userId(stringArg(args, "__userId"))
                .workspaceRoot(StrUtil.blankToDefault(stringArg(args, "__workspaceRoot"), workspaceLayout.getRoot().toString()))
                .metadata(metadata)
                .build();
    }

    private String buildChildSessionId(String parentSessionId, String childRunId) {
        return "sess_" + Ids.hashKey(StrUtil.blankToDefault(parentSessionId, "-") + "|delegate|" + childRunId);
    }

    private String buildDelegatedPrompt(String task, String context) {
        String trimmedTask = task.trim();
        if (StrUtil.isBlank(context)) {
            return trimmedTask;
        }

        return "Context:\n" + context.trim() + "\n\nTask:\n" + trimmedTask;
    }

    private Map<String, Object> formatDelegateResult(RunRecord childRun, int nextDepth) {
        if (childRun == null) {
            return error("Child run returned no result.");
        }

        Map<String, Object> result = childRun.getStatus() == RunStatus.SUCCEEDED
                ? success()
                : error(StrUtil.blankToDefault(childRun.getErrorMessage(), "Child run finished with status " + statusValue(childRun.getStatus())));

        Map<String, Object> run = new LinkedHashMap<String, Object>();
        run.put("run_id", childRun.getRunId());
        run.put("parent_run_id", childRun.getParentRunId());
        run.put("session_id", childRun.getSessionId());
        run.put("status", statusValue(childRun.getStatus()));
        run.put("created_at", childRun.getCreatedAt());
        run.put("started_at", childRun.getStartedAt());
        run.put("completed_at", childRun.getCompletedAt());

        result.put("delegate_depth", Integer.valueOf(nextDepth));
        result.put("run", run);
        result.put("response", childRun.getResponseText());
        return result;
    }

    private String statusValue(RunStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    private Map<String, Object> handleCronjob(Map<String, Object> args) {
        String action = StrUtil.blankToDefault(stringArg(args, "action"), "list").toLowerCase();
        if ("create".equals(action)) {
            String schedule = stringArg(args, "schedule");
            if (StrUtil.isBlank(schedule)) {
                return error("schedule is required for create");
            }
            List<String> skills = stringListArg(args, "skills");
            String prompt = stringArg(args, "prompt");
            if (StrUtil.isBlank(prompt) && (skills == null || skills.isEmpty())) {
                return error("create requires prompt or skills");
            }

            Instant now = Instant.now();
            JobRecord record = JobRecord.builder()
                    .jobId(Ids.jobId())
                    .name(StrUtil.blankToDefault(stringArg(args, "name"), "job-" + System.currentTimeMillis()))
                    .prompt(StrUtil.nullToDefault(prompt, ""))
                    .schedule(schedule)
                    .workspaceRoot(workspaceLayout.getRoot().toString())
                    .modelAlias(stringArg(args, "model"))
                    .deliverTarget(stringArg(args, "deliver"))
                    .status(JobStatus.ACTIVE)
                    .skillNames(skills == null ? new ArrayList<String>() : skills)
                    .createdAt(now)
                    .updatedAt(now)
                    .nextRunAt(JobScheduleSupport.computeNextRunAt(schedule, now))
                    .build();
            jobStore.save(record);
            return jobResponse("Cron job created.", record);
        }

        if ("list".equals(action)) {
            boolean includeDisabled = boolArg(args, "include_disabled", true);
            List<Map<String, Object>> jobs = new ArrayList<Map<String, Object>>();
            for (JobRecord record : jobStore.list()) {
                if (!includeDisabled && record.getStatus() == JobStatus.PAUSED) {
                    continue;
                }
                jobs.add(formatJob(record));
            }
            Map<String, Object> result = success();
            result.put("count", Integer.valueOf(jobs.size()));
            result.put("jobs", jobs);
            return result;
        }

        String jobId = stringArg(args, "job_id");
        if (StrUtil.isBlank(jobId)) {
            return error("job_id is required for action '" + action + "'");
        }

        JobRecord record = jobStore.get(jobId);
        if (record == null) {
            return error("Job not found: " + jobId);
        }

        if ("remove".equals(action)) {
            jobStore.remove(jobId);
            Map<String, Object> result = success();
            result.put("message", "Cron job removed.");
            result.put("removed_job", formatJob(record));
            return result;
        }
        if ("pause".equals(action)) {
            Instant now = Instant.now();
            record.setStatus(JobStatus.PAUSED);
            record.setUpdatedAt(now);
            record.setLastResultSummary(stringArg(args, "reason"));
            record.setNextRunAt(null);
            jobStore.save(record);
            return jobResponse("Cron job paused.", record);
        }
        if ("resume".equals(action)) {
            Instant now = Instant.now();
            record.setStatus(JobStatus.ACTIVE);
            record.setUpdatedAt(now);
            record.setNextRunAt(JobScheduleSupport.computeNextRunAt(record.getSchedule(), now));
            jobStore.save(record);
            return jobResponse("Cron job resumed.", record);
        }
        if ("run".equals(action) || "run_now".equals(action) || "trigger".equals(action)) {
            if (jobExecutionService == null) {
                return error("Cron job execution service is unavailable");
            }

            try {
                RunRecord runRecord = jobExecutionService.triggerNow(record, "Triggered manually.");
                JobRecord refreshed = jobStore.get(jobId);
                Map<String, Object> result = runRecord != null && runRecord.getStatus() == RunStatus.SUCCEEDED
                        ? success()
                        : error(runRecord == null
                        ? "Cron job produced no run."
                        : StrUtil.blankToDefault(runRecord.getErrorMessage(), "Cron job finished with status " + statusValue(runRecord.getStatus())));
                result.put("message", "Cron job triggered.");
                result.put("job", formatJob(refreshed == null ? record : refreshed));
                result.put("run", formatRun(runRecord));
                result.put("response", runRecord == null ? null : runRecord.getResponseText());
                return result;
            } catch (RuntimeException e) {
                return error("Cron job trigger failed: " + e.getMessage());
            }
        }
        if ("update".equals(action)) {
            Instant now = Instant.now();
            if (args.containsKey("name")) {
                record.setName(stringArg(args, "name"));
            }
            if (args.containsKey("prompt")) {
                record.setPrompt(StrUtil.nullToDefault(stringArg(args, "prompt"), ""));
            }
            if (args.containsKey("schedule")) {
                record.setSchedule(stringArg(args, "schedule"));
            }
            if (args.containsKey("deliver")) {
                record.setDeliverTarget(stringArg(args, "deliver"));
            }
            if (args.containsKey("model")) {
                record.setModelAlias(stringArg(args, "model"));
            }
            if (args.containsKey("skills")) {
                List<String> skills = stringListArg(args, "skills");
                record.setSkillNames(skills == null ? new ArrayList<String>() : skills);
            }
            record.setUpdatedAt(now);
            record.setNextRunAt(record.getStatus() == JobStatus.PAUSED ? null : JobScheduleSupport.computeNextRunAt(record.getSchedule(), now));
            jobStore.save(record);
            return jobResponse("Cron job updated.", record);
        }

        return error("Unknown cron action: " + action);
    }

    private Map<String, Object> handleTerminal(Map<String, Object> args) {
        if (Boolean.TRUE.equals(properties.getSecurity().getRequireTerminalApproval())) {
            Map<String, Object> result = error("Terminal approval is required by security policy.");
            result.put("approval_required", Boolean.TRUE);
            return result;
        }

        String command = stringArg(args, "command");
        if (StrUtil.isBlank(command)) {
            return error("command is required");
        }
        if (!isAllowedCommand(command)) {
            return error("Command is blocked by terminal.allowedCommands policy.");
        }

        String workdirArg = StrUtil.blankToDefault(stringArg(args, "workdir"), ".");
        Path workdir = workspacePathGuard.resolveWorkspacePath(workdirArg);
        boolean background = boolArg(args, "background", false);
        long timeoutMs = resolveTerminalTimeoutMs(args);

        if (background) {
            return terminalProcessManager.startBackground(command, workdir);
        }

        return terminalProcessManager.runForeground(command, workdir, timeoutMs);
    }

    private Map<String, Object> handleProcess(Map<String, Object> args) {
        String action = StrUtil.blankToDefault(stringArg(args, "action"), "list").toLowerCase();
        if ("list".equals(action)) {
            return terminalProcessManager.listProcesses();
        }

        String sessionId = StrUtil.blankToDefault(stringArg(args, "session_id"), stringArg(args, "process_id"));
        if (StrUtil.isBlank(sessionId)) {
            return error("session_id is required for action '" + action + "'");
        }

        if ("poll".equals(action) || "view".equals(action)) {
            return terminalProcessManager.poll(sessionId);
        }
        if ("log".equals(action)) {
            int offset = intArg(args, "offset", -1);
            int limit = Math.max(1, Math.min(intArg(args, "limit", 200), 1000));
            return terminalProcessManager.readLog(sessionId, offset, limit);
        }
        if ("wait".equals(action)) {
            return terminalProcessManager.waitFor(sessionId, resolveTerminalTimeoutMs(args));
        }
        if ("kill".equals(action) || "stop".equals(action)) {
            return terminalProcessManager.kill(sessionId);
        }

        return error("Unknown process action: " + action);
    }

    private boolean isAllowedCommand(String command) {
        List<String> allowedCommands = properties.getTerminal().getAllowedCommands();
        if (allowedCommands == null || allowedCommands.isEmpty()) {
            return true;
        }

        String head = commandHead(command);
        if (StrUtil.isBlank(head)) {
            return false;
        }

        String normalized = Paths.get(head).getFileName().toString().toLowerCase();
        for (String allowed : allowedCommands) {
            if (normalized.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }

    private String commandHead(String command) {
        String trimmed = StrUtil.trim(command);
        if (StrUtil.isBlank(trimmed)) {
            return "";
        }

        char first = trimmed.charAt(0);
        if (first == '"' || first == '\'') {
            int end = trimmed.indexOf(first, 1);
            return end > 1 ? trimmed.substring(1, end) : trimmed.substring(1);
        }

        int whitespace = trimmed.indexOf(' ');
        return whitespace > 0 ? trimmed.substring(0, whitespace) : trimmed;
    }

    private long resolveTerminalTimeoutMs(Map<String, Object> args) {
        Object timeoutMsValue = args.get("timeout_ms");
        if (timeoutMsValue != null) {
            return Math.max(1L, toLong(timeoutMsValue));
        }

        Object timeoutSecondsValue = args.get("timeout");
        if (timeoutSecondsValue != null) {
            return Math.max(1L, toLong(timeoutSecondsValue) * 1000L);
        }

        Long configured = properties.getTerminal().getDefaultTimeoutMs();
        return configured == null ? 120000L : Math.max(1L, configured.longValue());
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Map<String, Object> jobResponse(String message, JobRecord record) {
        Map<String, Object> result = success();
        result.put("message", message);
        result.put("job", formatJob(record));
        return result;
    }

    private Map<String, Object> formatRun(RunRecord runRecord) {
        if (runRecord == null) {
            return null;
        }

        Map<String, Object> run = new LinkedHashMap<String, Object>();
        run.put("run_id", runRecord.getRunId());
        run.put("parent_run_id", runRecord.getParentRunId());
        run.put("session_id", runRecord.getSessionId());
        run.put("status", statusValue(runRecord.getStatus()));
        run.put("response_text", runRecord.getResponseText());
        run.put("error_message", runRecord.getErrorMessage());
        run.put("created_at", runRecord.getCreatedAt());
        run.put("started_at", runRecord.getStartedAt());
        run.put("completed_at", runRecord.getCompletedAt());
        return run;
    }

    private Map<String, Object> formatJob(JobRecord record) {
        Map<String, Object> job = new LinkedHashMap<String, Object>();
        job.put("job_id", record.getJobId());
        job.put("name", record.getName());
        job.put("prompt", record.getPrompt());
        job.put("schedule", record.getSchedule());
        job.put("workspace_root", record.getWorkspaceRoot());
        job.put("model", record.getModelAlias());
        job.put("deliver", record.getDeliverTarget());
        job.put("status", record.getStatus() == null ? null : record.getStatus().name().toLowerCase());
        job.put("skills", record.getSkillNames());
        job.put("last_result_summary", record.getLastResultSummary());
        job.put("created_at", record.getCreatedAt());
        job.put("updated_at", record.getUpdatedAt());
        job.put("last_run_at", record.getLastRunAt());
        job.put("next_run_at", record.getNextRunAt());
        return job;
    }

    private Map<String, Object> handleSendMessage(Map<String, Object> args) {
        String action = StrUtil.blankToDefault(stringArg(args, "action"), "send");
        if ("list".equalsIgnoreCase(action)) {
            return listMessageTargets();
        }

        String target = stringArg(args, "target");
        String message = stringArg(args, "message");
        if (StrUtil.isBlank(target) || StrUtil.isBlank(message)) {
            return error("target and message are required when action='send'");
        }

        ParsedTarget parsedTarget = parseTarget(target);
        if (parsedTarget.errorMessage != null) {
            return error(parsedTarget.errorMessage);
        }
        if (parsedTarget.explicitTarget && !Boolean.TRUE.equals(properties.getSecurity().getAllowExplicitSendTargets())) {
            return error("Explicit send targets are disabled by security policy");
        }

        ReplyRoute route = resolveSendRoute(parsedTarget);
        if (route == null) {
            return error("No route available for target: " + target);
        }

        ChannelAdapter adapter = findAdapter(route.getPlatform());
        if (adapter == null) {
            return error("No enabled channel adapter for platform: " + route.getPlatform());
        }

        Object adapterResult = adapter.sendMessage(route, ChannelOutboundMessage.builder().text(message).build());
        Map<String, Object> result = success();
        result.put("target", formatRoute(route));
        result.put("result", adapterResult);
        return result;
    }

    private Map<String, Object> listMessageTargets() {
        List<Map<String, Object>> targets = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, ClawProperties.ChannelProperties> entry : properties.getChannels().entrySet()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("platform", entry.getKey());
            item.put("enabled", entry.getValue().getEnabled());
            item.put("home_target", StrUtil.isBlank(entry.getValue().getHomeChatId()) ? entry.getKey() : entry.getKey() + ":" + entry.getValue().getHomeChatId());
            targets.add(item);
        }

        Map<String, Object> result = success();
        result.put("targets", targets);
        return result;
    }

    private ReplyRoute resolveSendRoute(ParsedTarget parsedTarget) {
        if (!parsedTarget.explicitTarget) {
            ClawProperties.ChannelProperties channel = properties.getChannels().get(parsedTarget.platform);
            if (channel == null || StrUtil.isBlank(channel.getHomeChatId())) {
                return null;
            }
            return ReplyRoute.builder()
                    .platform(parsedTarget.platform)
                    .chatId(channel.getHomeChatId())
                    .threadId(null)
                    .build();
        }

        return ReplyRoute.builder()
                .platform(parsedTarget.platform)
                .chatId(parsedTarget.chatId)
                .threadId(parsedTarget.threadId)
                .build();
    }

    private ChannelAdapter findAdapter(String platform) {
        for (ChannelAdapter adapter : channelAdapters) {
            if (adapter.enabled() && platform.equals(adapter.platform())) {
                return adapter;
            }
        }
        return null;
    }

    private ParsedTarget parseTarget(String target) {
        String[] parts = target.split(":", 3);
        if (parts.length == 0 || StrUtil.isBlank(parts[0])) {
            return ParsedTarget.error("Invalid target: " + target);
        }

        ParsedTarget parsedTarget = new ParsedTarget();
        parsedTarget.platform = parts[0].trim().toLowerCase();
        if (parts.length > 1) {
            parsedTarget.chatId = StrUtil.blankToDefault(parts[1].trim(), null);
            parsedTarget.threadId = parts.length > 2 ? StrUtil.blankToDefault(parts[2].trim(), null) : null;
            parsedTarget.explicitTarget = StrUtil.isNotBlank(parsedTarget.chatId);
        }
        return parsedTarget;
    }

    private String formatRoute(ReplyRoute route) {
        if (route == null) {
            return null;
        }
        if (StrUtil.isBlank(route.getChatId())) {
            return route.getPlatform();
        }
        if (StrUtil.isBlank(route.getThreadId())) {
            return route.getPlatform() + ":" + route.getChatId();
        }
        return route.getPlatform() + ":" + route.getChatId() + ":" + route.getThreadId();
    }

    private Map<String, Object> handleSkillsList(Map<String, Object> args) {
        String category = stringArg(args, "category");
        List<Map<String, Object>> all = skillCatalog.list();
        List<Map<String, Object>> filtered = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : all) {
            if (StrUtil.isBlank(category) || StrUtil.equals(category, item.get("category") == null ? null : String.valueOf(item.get("category")))) {
                filtered.add(item);
            }
        }

        Map<String, Object> result = success();
        result.put("count", Integer.valueOf(filtered.size()));
        result.put("skills", filtered);
        return result;
    }

    private Map<String, Object> handleSkillView(Map<String, Object> args) {
        String name = stringArg(args, "name");
        if (StrUtil.isBlank(name)) {
            return error("name is required");
        }
        return skillCatalog.view(name, stringArg(args, "file_path"));
    }

    private Map<String, Object> handleSkillManage(Map<String, Object> args) {
        String name = stringArg(args, "name");
        if (StrUtil.isBlank(name)) {
            return error("name is required");
        }

        return skillManagerService.manage(
                stringArg(args, "action"),
                name,
                stringArg(args, "content"),
                stringArg(args, "category"),
                stringArg(args, "file_path"),
                stringArg(args, "file_content"),
                stringArg(args, "old_string"),
                stringArg(args, "new_string"),
                boolArg(args, "replace_all", false));
    }

    private static class ParsedTarget {
        private String platform;
        private String chatId;
        private String threadId;
        private boolean explicitTarget;
        private String errorMessage;

        private static ParsedTarget error(String errorMessage) {
            ParsedTarget target = new ParsedTarget();
            target.errorMessage = errorMessage;
            return target;
        }
    }

    public static class TodoState {
        public List<TodoItem> todos = new ArrayList<TodoItem>();

        public List<Map<String, Object>> toMapList() {
            List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
            for (TodoItem todo : todos) {
                results.add(todo.toMap());
            }
            return results;
        }

        public Map<String, Object> summary() {
            int pending = 0;
            int inProgress = 0;
            int completed = 0;
            int cancelled = 0;
            for (TodoItem todo : todos) {
                if ("completed".equals(todo.status)) {
                    completed++;
                } else if ("in_progress".equals(todo.status)) {
                    inProgress++;
                } else if ("cancelled".equals(todo.status)) {
                    cancelled++;
                } else {
                    pending++;
                }
            }

            Map<String, Object> summary = new LinkedHashMap<String, Object>();
            summary.put("total", Integer.valueOf(todos.size()));
            summary.put("pending", Integer.valueOf(pending));
            summary.put("in_progress", Integer.valueOf(inProgress));
            summary.put("completed", Integer.valueOf(completed));
            summary.put("cancelled", Integer.valueOf(cancelled));
            return summary;
        }
    }

    public static class TodoItem {
        public String id;
        public String content;
        public String status;

        public static TodoItem of(Map<String, Object> raw) {
            TodoItem item = new TodoItem();
            item.id = normalizeString(raw.get("id"), "?");
            item.content = normalizeString(raw.get("content"), "(no description)");
            item.status = normalizeStatus(raw.get("status"));
            return item;
        }

        public TodoItem copy() {
            TodoItem copy = new TodoItem();
            copy.id = id;
            copy.content = content;
            copy.status = status;
            return copy;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("id", id);
            result.put("content", content);
            result.put("status", status);
            return result;
        }

        private static String normalizeString(Object value, String defaultValue) {
            String text = value == null ? null : String.valueOf(value).trim();
            return StrUtil.isBlank(text) ? defaultValue : text;
        }

        private static String normalizeStatus(Object value) {
            String status = value == null ? "pending" : String.valueOf(value).trim().toLowerCase();
            if (!"pending".equals(status) && !"in_progress".equals(status) && !"completed".equals(status) && !"cancelled".equals(status)) {
                return "pending";
            }
            return status;
        }
    }

    private static class MemoryMutation {
        private final boolean success;
        private final String message;

        private MemoryMutation(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private static class MemoryState {
        private static final String DELIMITER = "\n§\n";
        private static final int MEMORY_CHAR_LIMIT = 2200;
        private static final int USER_CHAR_LIMIT = 1375;

        private final List<String> entries = new ArrayList<String>();

        private static MemoryState parse(String raw) {
            MemoryState state = new MemoryState();
            if (StrUtil.isBlank(raw)) {
                return state;
            }
            String[] parts = raw.split("\\n§\\n");
            for (String part : parts) {
                String entry = part.trim();
                if (StrUtil.isNotBlank(entry)) {
                    state.entries.add(entry);
                }
            }
            return state;
        }

        private String render() {
            return StrUtil.join(DELIMITER, entries);
        }

        private Map<String, Object> response(String target) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("success", Boolean.TRUE);
            result.put("target", target);
            result.put("entries", new ArrayList<String>(entries));
            result.put("entry_count", Integer.valueOf(entries.size()));
            result.put("usage", usage(target));
            return result;
        }

        private MemoryMutation add(String content, String target) {
            String normalized = content.trim();
            if (entries.contains(normalized)) {
                return new MemoryMutation(true, "Entry already exists.");
            }
            entries.add(normalized);
            return withinLimit("Entry added.", target);
        }

        private MemoryMutation replace(String oldText, String content, String target) {
            MemoryLookup lookup = findUnique(oldText);
            if (!lookup.found) {
                return new MemoryMutation(false, "No entry matched '" + oldText + "'.");
            }
            if (lookup.ambiguous) {
                return new MemoryMutation(false, "Multiple entries matched '" + oldText + "'.");
            }
            entries.set(lookup.index, content.trim());
            return withinLimit("Entry replaced.", target);
        }

        private MemoryMutation remove(String oldText) {
            MemoryLookup lookup = findUnique(oldText);
            if (!lookup.found) {
                return new MemoryMutation(false, "No entry matched '" + oldText + "'.");
            }
            if (lookup.ambiguous) {
                return new MemoryMutation(false, "Multiple entries matched '" + oldText + "'.");
            }
            entries.remove(lookup.index);
            return new MemoryMutation(true, "Entry removed.");
        }

        private MemoryLookup findUnique(String needle) {
            List<Integer> matches = new ArrayList<Integer>();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).contains(needle)) {
                    matches.add(Integer.valueOf(i));
                }
            }

            if (matches.isEmpty()) {
                return new MemoryLookup(false, false, -1);
            }
            if (matches.size() > 1) {
                return new MemoryLookup(true, true, -1);
            }
            return new MemoryLookup(true, false, matches.get(0).intValue());
        }

        private MemoryMutation withinLimit(String successMessage, String target) {
            int currentChars = render().length();
            int limit = "user".equalsIgnoreCase(target) ? USER_CHAR_LIMIT : MEMORY_CHAR_LIMIT;
            if (currentChars > limit) {
                return new MemoryMutation(false, "Memory content exceeds size limit for " + target + ".");
            }
            return new MemoryMutation(true, successMessage);
        }

        private String usage(String target) {
            int current = render().length();
            int limit = "user".equalsIgnoreCase(target) ? USER_CHAR_LIMIT : MEMORY_CHAR_LIMIT;
            return current + "/" + limit + " chars";
        }
    }

    private static class MemoryLookup {
        private final boolean found;
        private final boolean ambiguous;
        private final int index;

        private MemoryLookup(boolean found, boolean ambiguous, int index) {
            this.found = found;
            this.ambiguous = ambiguous;
            this.index = index;
        }
    }

    private static class RawTool extends AbsTool {
        private final String name;
        private final String description;
        private final ToolExecutor executor;

        private RawTool(String name, String description, String inputSchema, ToolExecutor executor) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.executor = executor;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String title() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public Object handle(Map<String, Object> args) throws Throwable {
            return executor.execute(args);
        }
    }

    private interface ToolExecutor {
        Object execute(Map<String, Object> args) throws Throwable;
    }

    public interface RuntimeServiceResolver {
        RuntimeService resolve();
    }
}
