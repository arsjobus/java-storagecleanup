package dev.storagecleanup.presentation.models;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import dev.storagecleanup.domain.OllamaModel;

final class OllamaTableModel extends AbstractTableModel {
    private final String[] columns = {"Model", "ID", "Size"};
    List<OllamaModel> models = new ArrayList<>();
    @Override public int getRowCount() { return models.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public Object getValueAt(int row, int column) {
        OllamaModel model = models.get(row);
        return switch (column) { case 0 -> model.name(); case 1 -> model.id(); default -> model.size(); };
    }
    @Override public Class<?> getColumnClass(int column) { return String.class; }
    @Override public String getColumnName(int column) { return columns[column]; }
    void setModels(List<OllamaModel> values) { models = new ArrayList<>(values); fireTableDataChanged(); }
}
