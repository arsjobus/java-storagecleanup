package dev.storagecleanup.presentation;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/** A table that applies subtle striping consistently across all cell renderers. */
public class AlternatingRowTable extends JTable {
  public AlternatingRowTable(TableModel model) {
    super(model);
  }

  @Override
  public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
    Component component = super.prepareRenderer(renderer, row, column);
    if (!isCellSelected(row, column)) {
      component.setBackground(row % 2 == 0 ? getBackground() : alternateBackground());
    }
    return component;
  }

  private Color alternateBackground() {
    Color background = getBackground();
    Color alternate = UIManager.getColor("Table.alternateRowColor");
    if (alternate != null) return alternate;
    return new Color(
        (background.getRed() * 7 + 248) / 8,
        (background.getGreen() * 7 + 248) / 8,
        (background.getBlue() * 7 + 248) / 8);
  }
}
