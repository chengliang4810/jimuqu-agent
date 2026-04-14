package com.jimuqu.claw.agent.store.file;

import com.jimuqu.claw.support.FileStoreSupport;
import com.jimuqu.claw.support.JsonSupport;
import org.noear.solon.Utils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class JsonFileStoreSupport {
    private JsonFileStoreSupport() {
    }

    static <T> T read(Path path, Class<T> type) {
        String json = FileStoreSupport.readUtf8(path);
        if (Utils.isBlank(json)) {
            return null;
        }

        return JsonSupport.fromJson(json, type);
    }

    static void write(Path path, Object value) {
        FileStoreSupport.writeUtf8Atomic(path, JsonSupport.toJson(value));
    }

    static List<Path> listJsonFiles(Path directory) {
        File dir = directory.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<Path> results = new ArrayList<Path>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                results.add(file.toPath());
            }
        }

        return results;
    }
}
