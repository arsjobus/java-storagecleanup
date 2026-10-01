package dev.storagecleanup.presentation.homebrew;

import dev.storagecleanup.domain.BrewPackage;
import java.util.*;
import javax.swing.table.AbstractTableModel;

final class BrewTableModel extends AbstractTableModel {
  private final String[] columns = {
    "Type", "Package", "Parent Package", "Installed version(s)", "Size"
  };
  List<BrewPackage> packages = new ArrayList<>();

  @Override
  public int getRowCount() {
    return packages.size();
  }

  @Override
  public int getColumnCount() {
    return columns.length;
  }

  @Override
  public Object getValueAt(int row, int column) {
    BrewPackage item = packages.get(row);
    return switch (column) {
      case 0 -> item.type();
      case 1 -> item.name();
      case 2 -> item.parentPackage();
      case 3 -> item.version();
      default -> item.size();
    };
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return column == 4 ? Long.class : String.class;
  }

  @Override
  public String getColumnName(int column) {
    return columns[column];
  }

  void setPackages(List<BrewPackage> values) {
    packages = new ArrayList<>(values);
    fireTableDataChanged();
  }
}
