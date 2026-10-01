package dev.storagecleanup.domain;

import java.nio.file.Path;

public record SupportFolder(String name, Long size, Path path) { }
