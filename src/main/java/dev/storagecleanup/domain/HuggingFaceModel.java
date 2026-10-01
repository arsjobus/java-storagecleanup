package dev.storagecleanup.domain;

import java.nio.file.Path;
import java.time.Instant;

public record HuggingFaceModel(String name, Long size, Path path) { }
