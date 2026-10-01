package dev.storagecleanup.infrastructure;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileTreeSizer {
  private FileTreeSizer() {}

  public static Long directorySize(Path directory) {
    if (!Files.isDirectory(directory)) return null;
    final long[] total = {0};
    try {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (attrs.isRegularFile()) {
                try {
                  total[0] = Math.addExact(total[0], attrs.size());
                } catch (ArithmeticException ex) {
                  return FileVisitResult.TERMINATE;
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) {
              return FileVisitResult.CONTINUE;
            }
          });
      return total[0];
    } catch (IOException ex) {
      return null;
    }
  }
}
