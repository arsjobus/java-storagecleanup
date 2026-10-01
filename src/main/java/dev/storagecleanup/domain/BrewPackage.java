package dev.storagecleanup.domain;

import java.nio.file.Path;
import java.time.Instant;

public record BrewPackage(String type, String name, String version, Long size) { }
