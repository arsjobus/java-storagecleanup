package dev.storagecleanup.domain;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public record StorageAllocation(long total, long free, long applications, long caches, long personal, long other) {
        public static StorageAllocation scan(List<Candidate> candidates) {
            Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
            long total = 0, free = 0;
            try {
                FileStore store = Files.getFileStore(home);
                total = store.getTotalSpace();
                free = store.getUsableSpace();
            } catch (IOException ignored) { }

            // Scanner has already measured these folders to populate the candidate list.
            long apps = 0, caches = 0;
            for (Candidate candidate : candidates) {
                if (candidate.category().equals("Application")) apps = safeAdd(apps, candidate.size());
                else if (candidate.category().equals("User cache")) caches = safeAdd(caches, candidate.size());
            }
            long personal = 0;
            for (String folder : List.of("Desktop", "Documents", "Downloads", "Movies", "Music", "Pictures"))
                personal = safeAdd(personal, folderSize(home.resolve(folder)));
            long used = Math.max(0, total - free);
            long other = Math.max(0, used - Math.min(used, safeAdd(safeAdd(apps, caches), personal)));
            return new StorageAllocation(total, free, apps, caches, personal, other);
        }

        public static Map<String, Long> scanOtherBreakdown(long other) {
            Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
            Map<String, Long> breakdown = new LinkedHashMap<>();
            breakdown.put("Application support", safeAdd(folderSize(home.resolve("Library/Application Support")), folderSize(Path.of("/Library/Application Support"))));
            breakdown.put("App containers", safeAdd(folderSize(home.resolve("Library/Containers")), folderSize(home.resolve("Library/Group Containers"))));
            breakdown.put("Developer data", safeAdd(folderSize(home.resolve("Library/Developer")), folderSize(Path.of("/Library/Developer"))));
            breakdown.put("Mail", folderSize(home.resolve("Library/Mail")));
            breakdown.put("Messages", folderSize(home.resolve("Library/Messages")));
            long system = 0;
            for (String path : List.of("/System", "/private", "/usr", "/opt")) system = safeAdd(system, folderSize(Path.of(path)));
            breakdown.put("System folders", system);
            long identified = 0;
            for (long size : breakdown.values()) identified = safeAdd(identified, size);
            breakdown.put("Other files and unclassified", Math.max(0, other - Math.min(other, identified)));
            return Collections.unmodifiableMap(breakdown);
        }

        public String description() {
            return "<html><table width='100%' cellpadding='1' cellspacing='0'>"
                    + "<tr><td><font color='#4876bf'>●</font> Apps " + formatSize(applications) + "</td>"
                    + "<td><font color='#a46ed2'>●</font> Caches " + formatSize(caches) + "</td></tr>"
                    + "<tr><td><font color='#d98b32'>●</font> Personal " + formatSize(personal) + "</td>"
                    + "<td><font color='#8d97a4'>●</font> Other* " + formatSize(other) + "</td></tr>"
                    + "<tr><td><font color='#aeb7c2'>●</font> Free " + formatSize(free) + "</td><td></td></tr>"
                    + "</table></html>";
        }

        private static long folderSize(Path path) {
            if (!Files.isDirectory(path, NOFOLLOW_LINKS)) return 0;
            final long[] total = {0};
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile()) total[0] = safeAdd(total[0], attrs.size());
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException exc) { return FileVisitResult.CONTINUE; }
                });
            } catch (IOException ignored) { }
            return total[0];
        }

        private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return new DecimalFormat("#,##0.0").format(value) + " " + units[unit];
}

    private static long safeAdd(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
    }
