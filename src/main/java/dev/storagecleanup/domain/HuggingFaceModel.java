package dev.storagecleanup.domain;

import java.nio.file.Path;

public record HuggingFaceModel(String name, Long size, Path path) {}
