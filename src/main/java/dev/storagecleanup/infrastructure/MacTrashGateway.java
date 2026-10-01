package dev.storagecleanup.infrastructure;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public final class MacTrashGateway {
        public void move(Path path) throws Exception {
            Path normalized = path.toAbsolutePath().normalize();
            Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
            boolean hiddenHomeItem = normalized.startsWith(home) && home.relativize(normalized).getNameCount() > 1
                    && home.relativize(normalized).getName(0).toString().startsWith(".")
                    && !home.relativize(normalized).getName(0).toString().equals(".Trash");
            boolean allowed = normalized.startsWith(Path.of("/Applications")) || normalized.startsWith(home.resolve("Applications"))
                    || normalized.startsWith(home.resolve("Library/Caches")) || normalized.startsWith(home.resolve("Downloads"))
                    || normalized.startsWith(Path.of("/opt/homebrew/Cellar")) || normalized.startsWith(Path.of("/opt/homebrew/Caskroom"))
                    || normalized.startsWith(Path.of("/usr/local/Cellar")) || normalized.startsWith(Path.of("/usr/local/Caskroom"))
                    || (normalized.startsWith(home.resolve("Library/Application Support"))
                        && normalized.getParent() != null && normalized.getParent().equals(home.resolve("Library/Application Support")))
                    || hiddenHomeItem;
            if (!allowed || normalized.getParent() == null || Files.isSymbolicLink(normalized))
                throw new IOException("Path is outside the supported cleanup folders");
            if (!Files.exists(normalized, NOFOLLOW_LINKS)) throw new IOException("Item no longer exists");
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"))
                throw new IOException("Moving items to Trash is supported on macOS only");
            String appleScriptPath = normalized.toString().replace("\\", "\\\\").replace("\"", "\\\"");
            Process process = new ProcessBuilder("/usr/bin/osascript", "-e",
                    "tell application \"Finder\" to delete POSIX file \"" + appleScriptPath + "\"").start();
            String error = new String(process.getErrorStream().readAllBytes());
            int code = process.waitFor();
            if (code != 0) throw new IOException(error.isBlank() ? "Finder could not move the item" : error.trim());
        }
    }
