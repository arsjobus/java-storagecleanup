package dev.storagecleanup.domain;

public record BrewPackage(String type, String name, String parentPackage, String version, Long size) { }
