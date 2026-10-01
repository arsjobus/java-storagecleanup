package dev.storagecleanup.infrastructure;

import dev.storagecleanup.domain.Candidate;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public final class StorageScanner {
        private final Path home;
        private final List<Path> appDirs;
        private final Path cacheDir;
        private final Path downloads;
        private final List<Path> brewDirs;

        public StorageScanner() {
            this(Path.of(System.getProperty("user.home")),
                    List.of(Path.of("/Applications"), Path.of(System.getProperty("user.home"), "Applications")),
                    Path.of(System.getProperty("user.home"), "Library/Caches"),
                    Path.of(System.getProperty("user.home"), "Downloads"),
                    List.of(Path.of("/opt/homebrew/Cellar"), Path.of("/opt/homebrew/Caskroom"),
                            Path.of("/usr/local/Cellar"), Path.of("/usr/local/Caskroom")));
        }

        StorageScanner(Path home, List<Path> appDirs, Path cacheDir, Path downloads, List<Path> brewDirs) {
            this.home = home.toAbsolutePath().normalize();
            this.appDirs = List.copyOf(appDirs);
            this.cacheDir = cacheDir;
            this.downloads = downloads;
            this.brewDirs = List.copyOf(brewDirs);
        }

        public List<Candidate> scan(long downloadThreshold, boolean includeHidden) {
            List<Candidate> result = new ArrayList<>();
            for (Path root : appDirs) addChildren(result, root, "Application", p -> p.getFileName().toString().endsWith(".app"), 0);
            addChildren(result, cacheDir, "User cache", p -> true, 0);
            addChildren(result, downloads, "Large download", Files::isRegularFile, downloadThreshold);
            if (includeHidden) {
                for (Path root : brewDirs)
                    addChildren(result, root, "Homebrew", p -> true, downloadThreshold);
                addHiddenFiles(result, home, downloadThreshold);
            }
            result.sort(Comparator.comparingLong((Candidate c) -> c.size()).reversed());
            return result;
        }

        private static void addHiddenFiles(List<Candidate> out, Path home, long minimumSize) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(home, path -> path.getFileName().toString().startsWith("."))) {
                for (Path hiddenRoot : entries) {
                    if (hiddenRoot.getFileName().toString().equals(".Trash") || Files.isSymbolicLink(hiddenRoot)) continue;
                    try {
                        Files.walkFileTree(hiddenRoot, new SimpleFileVisitor<>() {
                            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (attrs.isRegularFile() && attrs.size() >= minimumSize) {
                                    Instant accessed = attrs.lastAccessTime().toMillis() > 0 ? attrs.lastAccessTime().toInstant() : null;
                                    out.add(new Candidate("Hidden home file", file.getFileName().toString(),
                                            file.toAbsolutePath().normalize(), attrs.size(), accessed));
                                }
                                return FileVisitResult.CONTINUE;
                            }
                            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) { return FileVisitResult.CONTINUE; }
                        });
                    } catch (IOException | SecurityException ignored) { }
                }
            } catch (IOException | SecurityException ignored) { }
        }

        private static void addChildren(List<Candidate> out, Path root, String category,
                                       java.util.function.Predicate<Path> filter, long minimumSize) {
            if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                for (Path entry : entries) {
                    try {
                        if (Files.isSymbolicLink(entry) || !filter.test(entry)) continue;
                        Instant activity = category.equals("Application") ? appLastUsed(entry) : lastAccessed(entry);
                        long size = sizeOf(entry);
                        if (size >= minimumSize) {
                            out.add(new Candidate(category, entry.getFileName().toString(), entry.toAbsolutePath().normalize(), size, activity));
                        }
                    } catch (IOException | SecurityException ignored) { /* inaccessible item; continue scanning */ }
                }
            } catch (IOException | SecurityException ignored) { /* folder unavailable or access denied */ }
        }

        private static long sizeOf(Path path) throws IOException {
            if (!Files.isDirectory(path, NOFOLLOW_LINKS)) return Files.size(path);
            final long[] total = {0};
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) total[0] = saturatingAdd(total[0], attrs.size());
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException exc) { return FileVisitResult.CONTINUE; }
            });
            return total[0];
        }

        private static Instant lastAccessed(Path path) {
            try {
                var time = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS).lastAccessTime();
                return time.toMillis() > 0 ? time.toInstant() : null;
            } catch (IOException | SecurityException ignored) { return null; }
        }

        private static Instant appLastUsed(Path app) {
            Process process = null;
            try {
                process = new ProcessBuilder("/usr/bin/mdls", "-name", "kMDItemLastUsedDate", "-raw", app.toString())
                        .redirectErrorStream(true).start();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return null;
                }
                if (process.exitValue() != 0) return null;
                String value = new String(process.getInputStream().readAllBytes()).trim();
                if (value.isEmpty() || value.equals("(null)")) return null;
                return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.US)).toInstant();
            } catch (Exception ignored) {
                if (process != null) process.destroyForcibly();
                return null;
            }
        }
        private static long saturatingAdd(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
    }
