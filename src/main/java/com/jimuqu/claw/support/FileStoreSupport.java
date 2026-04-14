package com.jimuqu.claw.support;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

public final class FileStoreSupport {
    private FileStoreSupport() {
    }

    public static String readUtf8(Path path) {
        File file = path.toFile();
        if (!file.exists()) {
            return null;
        }
        return FileUtil.readString(file, CharsetUtil.CHARSET_UTF_8);
    }

    public static void writeUtf8Atomic(Path path, String content) {
        File target = path.toFile();
        FileUtil.mkdir(target.getParentFile());
        File temp = new File(target.getParentFile(), "." + target.getName() + "." + UUID.randomUUID().toString() + ".tmp");
        FileUtil.writeString(content, temp, CharsetUtil.CHARSET_UTF_8);
        moveWithRetry(temp, target);
    }

    public static void deleteIfExists(Path path) {
        FileUtil.del(path.toFile());
    }

    private static void moveWithRetry(File temp, File target) {
        RuntimeException lastError = null;
        for (int i = 0; i < 5; i++) {
            try {
                FileUtil.move(temp, target, true);
                return;
            } catch (RuntimeException e) {
                if (moveSettledOrWorkspaceGone(temp, target)) {
                    return;
                }
                lastError = e;
                try {
                    Thread.sleep(20L * (i + 1));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        if (moveSettledOrWorkspaceGone(temp, target)) {
            return;
        }

        if (lastError != null) {
            throw lastError;
        }
    }

    private static boolean moveSettledOrWorkspaceGone(File temp, File target) {
        if (target.exists()) {
            return true;
        }

        if (temp.exists()) {
            return false;
        }

        File parent = target.getParentFile();
        return parent == null || !parent.exists();
    }
}
