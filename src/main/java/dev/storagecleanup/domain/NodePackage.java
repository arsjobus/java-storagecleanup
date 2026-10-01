package dev.storagecleanup.domain;

import java.nio.file.Path;

public record NodePackage(String name, String version, Path location, Long size) { }
