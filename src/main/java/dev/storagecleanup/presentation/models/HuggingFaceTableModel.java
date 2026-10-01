package dev.storagecleanup.presentation.models;

import dev.storagecleanup.domain.HuggingFaceModel;
import java.util.*;
import javax.swing.table.AbstractTableModel;

final class HuggingFaceTableModel extends AbstractTableModel {
  private final String[] columns = {"Model repository", "Cache location", "Size"};
  private List<HuggingFaceModel> models = new ArrayList<>();

  @Override
  public int getRowCount() {
    return models.size();
  }

  @Override
  public int getColumnCount() {
    return columns.length;
  }

  @Override
  public Object getValueAt(int row, int column) {
    HuggingFaceModel model = models.get(row);
    return switch (column) {
      case 0 -> model.name();
      case 1 -> model.path().toString();
      default -> model.size();
    };
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return column == 2 ? Long.class : String.class;
  }

  @Override
  public String getColumnName(int column) {
    return columns[column];
  }

  void setModels(List<HuggingFaceModel> values) {
    models = new ArrayList<>(values);
    fireTableDataChanged();
  }
}
