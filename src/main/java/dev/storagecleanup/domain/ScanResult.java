package dev.storagecleanup.domain;

import java.util.List;

public record ScanResult(List<Candidate> candidates, StorageAllocation allocation) { }
