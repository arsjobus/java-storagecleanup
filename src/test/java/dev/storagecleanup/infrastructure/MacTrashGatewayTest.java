package dev.storagecleanup.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MacTrashGatewayTest {
  @TempDir Path temp;

  @Test
  void rejectsPathsOutsideSupportedFoldersBeforeInvokingFinder() throws IOException {
    Path item = Files.write(temp.resolve("item"), new byte[] {1});
    IOException error = assertThrows(IOException.class, () -> new MacTrashGateway().move(item));
    assertEquals("Path is outside the supported cleanup folders", error.getMessage());
    assertTrue(Files.exists(item));
  }

  @Test
  void rejectsMissingItemsInsideSupportedFolder() {
    Path missing = Path.of(System.getProperty("user.home"), "Downloads", "unit-test-missing-item");
    IOException error = assertThrows(IOException.class, () -> new MacTrashGateway().move(missing));
    assertEquals("Item no longer exists", error.getMessage());
  }

  @Test
  void rejectsTheTrashDirectoryItself() {
    Path trash = Path.of(System.getProperty("user.home"), ".Trash", "item");
    IOException error = assertThrows(IOException.class, () -> new MacTrashGateway().move(trash));
    assertEquals("Path is outside the supported cleanup folders", error.getMessage());
  }
}
