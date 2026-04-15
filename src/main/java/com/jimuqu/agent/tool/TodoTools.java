package com.jimuqu.agent.tool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HashUtil;
import com.jimuqu.agent.config.AppConfig;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;

import java.io.File;

public class TodoTools {
    private final AppConfig appConfig;
    private final String sourceKey;

    public TodoTools(AppConfig appConfig, String sourceKey) {
        this.appConfig = appConfig;
        this.sourceKey = sourceKey;
    }

    @ToolMapping(name = "todo", description = "Manage a lightweight todo list. action can be add, list, clear, or done.")
    public String todo(String action, String value) {
        File file = todoFile();
        ONode node = file.exists() ? ONode.ofJson(FileUtil.readUtf8String(file)) : new ONode().asArray();
        if ("add".equalsIgnoreCase(action)) {
            node.add(value);
            FileUtil.writeUtf8String(node.toJson(), file);
            return "Todo added: " + value;
        }
        if ("clear".equalsIgnoreCase(action)) {
            FileUtil.writeUtf8String(new ONode().asArray().toJson(), file);
            return "Todo list cleared";
        }
        if ("done".equalsIgnoreCase(action)) {
            for (int i = 0; i < node.size(); i++) {
                if (value.equals(node.get(i).getString())) {
                    node.remove(i);
                    break;
                }
            }
            FileUtil.writeUtf8String(node.toJson(), file);
            return "Todo removed: " + value;
        }
        return node.toJson();
    }

    private File todoFile() {
        String name = "todo-" + HashUtil.apHash(sourceKey) + ".json";
        return FileUtil.file(appConfig.getRuntime().getCacheDir(), name);
    }
}
