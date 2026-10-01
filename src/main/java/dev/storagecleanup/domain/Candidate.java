package dev.storagecleanup.domain;

import java.nio.file.Path;
import java.time.Instant;

public record Candidate(String category, String name, Path path, long size, Instant lastActivity) {}
