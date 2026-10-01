package dev.storagecleanup.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StorageAllocationTest {
  @Test
  void descriptionIncludesEachAllocationAndFormatsBytes() {
    StorageAllocation allocation = new StorageAllocation(4096, 1024, 1024, 512, 256, 128);
    String description = allocation.description();
    assertTrue(description.startsWith("<html><table"));
    assertTrue(description.contains("Apps 1.0 KB"));
    assertTrue(description.contains("Caches 512 B"));
    assertTrue(description.contains("Personal 256 B"));
    assertTrue(description.contains("Other* 128 B"));
    assertTrue(description.contains("Free 1.0 KB"));
    assertTrue(description.endsWith("</html>"));
  }

  @Test
  void descriptionFormatsLargeValuesUsingBinaryUnits() {
    StorageAllocation allocation =
        new StorageAllocation(2L * 1024 * 1024, 0, 1024L * 1024, 0, 0, 0);
    assertTrue(allocation.description().contains("Apps 1.0 MB"));
  }
}
