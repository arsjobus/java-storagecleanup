package dev.storagecleanup.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTreeSizerTest {
  @TempDir Path temp;

  @Test
  void sumsRegularFilesRecursively() throws IOException {
    Files.write(temp.resolve("a"), new byte[3]);
    Files.createDirectories(temp.resolve("nested/deeper"));
    Files.write(temp.resolve("nested/deeper/b"), new byte[8]);
    assertEquals(11L, FileTreeSizer.directorySize(temp));
  }

  @Test
  void returnsZeroForAnEmptyDirectory() throws IOException {
    Path empty = Files.createDirectory(temp.resolve("empty"));
    assertEquals(0L, FileTreeSizer.directorySize(empty));
  }

  @Test
  void returnsNullForMissingPathAndRegularFile() throws IOException {
    Path file = Files.write(temp.resolve("file"), new byte[] {1});
    assertNull(FileTreeSizer.directorySize(temp.resolve("missing")));
    assertNull(FileTreeSizer.directorySize(file));
  }

  @Test
  void doesNotFollowDirectorySymlinks() throws IOException {
    Path external = Files.createDirectory(temp.resolve("external"));
    Files.write(external.resolve("large"), new byte[100]);
    Path root = Files.createDirectory(temp.resolve("root"));
    try {
      Files.createSymbolicLink(root.resolve("linked"), external);
    } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
      return;
    }
    assertEquals(0L, FileTreeSizer.directorySize(root));
  }
}
