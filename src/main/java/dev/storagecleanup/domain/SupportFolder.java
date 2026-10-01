package dev.storagecleanup.domain;

import java.nio.file.Path;
import java.time.Instant;

public record SupportFolder(String name, Long size, Path path) { }
