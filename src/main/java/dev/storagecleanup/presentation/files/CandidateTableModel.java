package dev.storagecleanup.presentation.files;

import javax.swing.table.AbstractTableModel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import dev.storagecleanup.domain.Candidate;

public final class CandidateTableModel extends AbstractTableModel {
    private final String[] columns = {"Select", "Name", "Size", "Category", "Last used / accessed", "Location"};
    public List<Candidate> candidates = new ArrayList<>();
    private final Set<Path> selected = new HashSet<>();
    @Override public int getRowCount() { return candidates.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public String getColumnName(int column) { return columns[column]; }
    @Override public Class<?> getColumnClass(int column) {
        if (column == 0) return Boolean.class;
        if (column == 2) return Long.class;
        if (column == 4) return Instant.class;
        return String.class;
    }
    @Override public boolean isCellEditable(int row, int column) { return column == 0; }
    @Override public Object getValueAt(int row, int column) {
        Candidate c = candidates.get(row);
        return switch (column) {
            case 0 -> selected.contains(c.path());
            case 1 -> c.name();
            case 2 -> c.size();
            case 3 -> c.category();
            case 4 -> c.lastActivity();
            default -> c.path().toString();
        };
    }
    @Override public void setValueAt(Object value, int row, int column) {
        Path path = candidates.get(row).path();
        if (Boolean.TRUE.equals(value)) selected.add(path); else selected.remove(path);
        fireTableCellUpdated(row, column);
    }
    public void setCandidates(List<Candidate> items) { candidates = new ArrayList<>(items); selected.clear(); fireTableDataChanged(); }
    public List<Candidate> selectedCandidates() { return candidates.stream().filter(c -> selected.contains(c.path())).toList(); }
    public void remove(List<Candidate> items) {
        Set<Path> paths = new HashSet<>(); items.forEach(c -> paths.add(c.path()));
        candidates.removeIf(c -> paths.contains(c.path())); selected.removeAll(paths); fireTableDataChanged();
    }
}
