package dev.storagecleanup.presentation.support;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import dev.storagecleanup.domain.SupportFolder;

final class SupportTableModel extends AbstractTableModel {
    private final String[] columns = {"Folder", "Location", "Size"};
    List<SupportFolder> folders = new ArrayList<>();
    @Override public int getRowCount() { return folders.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public Object getValueAt(int row, int column) {
        SupportFolder folder = folders.get(row);
        return switch (column) { case 0 -> folder.name(); case 1 -> folder.path().toString(); default -> folder.size(); };
    }
    @Override public Class<?> getColumnClass(int column) { return column == 2 ? Long.class : String.class; }
    @Override public String getColumnName(int column) { return columns[column]; }
    void setFolders(List<SupportFolder> values) { folders = new ArrayList<>(values); fireTableDataChanged(); }
}
