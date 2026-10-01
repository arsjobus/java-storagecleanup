package dev.storagecleanup.presentation.files;

import dev.storagecleanup.domain.Candidate;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandidateTableModelTest {
    @Test void exposesExpectedColumnsValuesAndTypes() {
        Candidate item = new Candidate("User cache", "cache", Path.of("/tmp/cache"), 42, Instant.EPOCH);
        CandidateTableModel model = new CandidateTableModel();
        model.setCandidates(List.of(item));
        assertEquals(1, model.getRowCount());
        assertEquals(6, model.getColumnCount());
        assertEquals("Name", model.getColumnName(1));
        assertEquals(Boolean.class, model.getColumnClass(0));
        assertEquals(Instant.class, model.getColumnClass(3));
        assertEquals(Long.class, model.getColumnClass(5));
        assertEquals("cache", model.getValueAt(0, 1));
        assertEquals("/tmp/cache", model.getValueAt(0, 4));
        assertEquals(42L, model.getValueAt(0, 5));
        assertFalse(model.isCellEditable(0, 1));
        assertTrue(model.isCellEditable(0, 0));
    }

    @Test void tracksSelectionByPathAndClearsItWhenRowsAreReplaced() {
        Candidate first = candidate("first");
        Candidate second = candidate("second");
        CandidateTableModel model = new CandidateTableModel();
        model.setCandidates(List.of(first, second));
        model.setValueAt(true, 1, 0);
        assertEquals(List.of(second), model.selectedCandidates());
        assertEquals(Boolean.TRUE, model.getValueAt(1, 0));
        model.setCandidates(List.of(first));
        assertTrue(model.selectedCandidates().isEmpty());
        assertEquals(Boolean.FALSE, model.getValueAt(0, 0));
    }

    @Test void removeDeletesRequestedRowsAndTheirSelection() {
        Candidate first = candidate("first");
        Candidate second = candidate("second");
        CandidateTableModel model = new CandidateTableModel();
        model.setCandidates(List.of(first, second));
        model.setValueAt(true, 0, 0);
        model.remove(List.of(first));
        assertEquals(List.of(second), model.candidates);
        assertTrue(model.selectedCandidates().isEmpty());
    }

    @Test void falseOrNonTrueValuesDeselectRow() {
        Candidate item = candidate("selected");
        CandidateTableModel model = new CandidateTableModel();
        model.setCandidates(List.of(item));
        model.setValueAt(true, 0, 0);
        model.setValueAt("true", 0, 0);
        assertTrue(model.selectedCandidates().isEmpty());
    }

    private static Candidate candidate(String name) {
        return new Candidate("test", name, Path.of("/tmp", name), name.length(), null);
    }
}
