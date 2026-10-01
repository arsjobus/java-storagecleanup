package dev.storagecleanup.infrastructure;

import dev.storagecleanup.domain.Candidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageScannerTest {
    @TempDir Path temp;

    private StorageScanner scanner() throws IOException {
        Path home = Files.createDirectory(temp.resolve("home"));
        Path apps = Files.createDirectory(temp.resolve("apps"));
        Path caches = Files.createDirectories(home.resolve("Library/Caches"));
        Path downloads = Files.createDirectory(home.resolve("Downloads"));
        Path brew = Files.createDirectory(temp.resolve("brew"));
        return new StorageScanner(home, List.of(apps), caches, downloads, List.of(brew));
    }

    @Test void returnsCacheFoldersAndLargeDownloadsInDescendingSizeOrder() throws IOException {
        StorageScanner scanner = scanner();
        Path home = temp.resolve("home");
        Files.write(Files.createDirectories(home.resolve("Library/Caches/small")).resolve("a"), new byte[2]);
        Files.write(Files.createDirectories(home.resolve("Library/Caches/large")).resolve("b"), new byte[7]);
        Files.write(home.resolve("Downloads/under"), new byte[3]);
        Files.write(home.resolve("Downloads/over"), new byte[8]);

        List<Candidate> result = scanner.scan(5, false);
        assertEquals(List.of("over", "large", "small"), result.stream().map(Candidate::name).toList());
        assertEquals(List.of(8L, 7L, 2L), result.stream().map(Candidate::size).toList());
    }

    @Test void onlyIncludesAppBundlesAndDoesNotFollowRootSymlinks() throws IOException {
        StorageScanner scanner = scanner();
        Path apps = temp.resolve("apps");
        Files.write(Files.createDirectories(apps.resolve("Tool.app/Contents")).resolve("bin"), new byte[4]);
        Files.createDirectory(apps.resolve("not-an-app"));
        Path outside = Files.createDirectories(temp.resolve("outside.app"));
        Files.write(outside.resolve("payload"), new byte[30]);
        try { Files.createSymbolicLink(apps.resolve("Linked.app"), outside); }
        catch (UnsupportedOperationException | IOException | SecurityException unavailable) { }

        List<Candidate> result = scanner.scan(0, false);
        assertEquals(1, result.size());
        assertEquals("Tool.app", result.get(0).name());
        assertEquals(4L, result.get(0).size());
    }

    @Test void optionalHiddenScanHonorsMinimumSizeAndSkipsTrashAndSymlinkRoots() throws IOException {
        StorageScanner scanner = scanner();
        Path home = temp.resolve("home");
        Files.write(home.resolve(".small"), new byte[2]);
        Files.write(home.resolve(".large"), new byte[6]);
        Path trash = Files.createDirectory(home.resolve(".Trash"));
        Files.write(trash.resolve("kept-out"), new byte[100]);
        Path hiddenTarget = Files.createDirectory(temp.resolve("target"));
        Files.write(hiddenTarget.resolve("external"), new byte[100]);
        try { Files.createSymbolicLink(home.resolve(".linked"), hiddenTarget); }
        catch (UnsupportedOperationException | IOException | SecurityException unavailable) { }

        assertTrue(scanner.scan(5, false).isEmpty());
        List<Candidate> included = scanner.scan(5, true);
        assertEquals(List.of(".large"), included.stream().map(Candidate::name).toList());
        assertEquals("Hidden home file", included.get(0).category());
    }

    @Test void ignoresMissingRoots() {
        StorageScanner scanner = new StorageScanner(temp, List.of(temp.resolve("absent")),
                temp.resolve("no-cache"), temp.resolve("no-downloads"), List.of(temp.resolve("no-brew")));
        assertTrue(scanner.scan(0, true).isEmpty());
    }
}
