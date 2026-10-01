package dev.storagecleanup.presentation.node;

import dev.storagecleanup.domain.NodePackage;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class NodePackageTableModel extends AbstractTableModel {
    private final String[] columns = {"Package", "Version", "Location", "Size"};
    List<NodePackage> packages = new ArrayList<>();
    @Override public int getRowCount() { return packages.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public String getColumnName(int column) { return columns[column]; }
    @Override public Class<?> getColumnClass(int column) { return column == 3 ? Long.class : String.class; }
    @Override public Object getValueAt(int row, int column) {
        NodePackage item = packages.get(row);
        return switch (column) {
            case 0 -> item.name();
            case 1 -> item.version();
            case 2 -> item.location().toString();
            default -> item.size();
        };
    }
    void setPackages(List<NodePackage> values) { packages = new ArrayList<>(values); fireTableDataChanged(); }
}
