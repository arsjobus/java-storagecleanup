package dev.storagecleanup.presentation.support;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import dev.storagecleanup.domain.SupportFolder;
import dev.storagecleanup.infrastructure.FileTreeSizer;
import dev.storagecleanup.infrastructure.MacTrashGateway;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

public final class SupportPanel extends JPanel {
  private final JFrame frame;
  private final JButton refreshSupportButton = new JButton("Refresh folders");
  private final JButton trashSupportButton = new JButton("Move selected to Trash");
  private final JLabel supportStatus =
      new JLabel("Refresh to measure folders in ~/Library/Application Support.");
  private final SupportTableModel supportModel = new SupportTableModel();
  private final JTable supportTable = new JTable(supportModel);

  public SupportPanel(JFrame frame) {
    super(new BorderLayout());
    this.frame = frame;
    add(createSupportPanel(), BorderLayout.CENTER);
  }

  private JPanel createSupportPanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    JLabel note =
        new JLabel(
            "Folder sizes for items directly inside ~/Library/Application Support. Selected folders go to the macOS Trash.");
    note.setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(refreshSupportButton);
    controls.add(trashSupportButton);
    JPanel north = new JPanel(new BorderLayout());
    north.add(note, BorderLayout.NORTH);
    north.add(controls, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    supportTable.setFillsViewportHeight(true);
    supportTable.setRowHeight(26);
    supportTable.setAutoCreateRowSorter(true);
    supportTable
        .getRowSorter()
        .setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.DESCENDING)));
    supportTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    supportTable.getColumnModel().getColumn(0).setPreferredWidth(300);
    supportTable.getColumnModel().getColumn(1).setPreferredWidth(520);
    supportTable.getColumnModel().getColumn(2).setPreferredWidth(100);
    supportTable.setDefaultRenderer(
        Long.class,
        new DefaultTableCellRenderer() {
          @Override
          protected void setValue(Object value) {
            setText(
                value instanceof Long bytes
                    ? dev.storagecleanup.StorageCleanup.formatSize(bytes)
                    : "Unavailable");
            setHorizontalAlignment(SwingConstants.RIGHT);
          }
        });
    panel.add(new JScrollPane(supportTable), BorderLayout.CENTER);
    supportStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 10, 12));
    panel.add(supportStatus, BorderLayout.SOUTH);
    refreshSupportButton.addActionListener(e -> refreshSupportFolders());
    trashSupportButton.addActionListener(e -> trashSupportFolders());
    trashSupportButton.setEnabled(false);
    return panel;
  }

  private void refreshSupportFolders() {
    refreshSupportButton.setEnabled(false);
    trashSupportButton.setEnabled(false);
    supportStatus.setText("Measuring Application Support folders…");
    new SwingWorker<List<SupportFolder>, Void>() {
      @Override
      protected List<SupportFolder> doInBackground() throws Exception {
        Path root = Path.of(System.getProperty("user.home"), "Library", "Application Support");
        List<SupportFolder> folders = new ArrayList<>();
        if (!Files.isDirectory(root)) return folders;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
          for (Path entry : entries) {
            if (Files.isDirectory(entry, NOFOLLOW_LINKS) && !Files.isSymbolicLink(entry))
              folders.add(
                  new SupportFolder(
                      entry.getFileName().toString(), FileTreeSizer.directorySize(entry), entry));
          }
        }
        folders.sort(Comparator.comparing(SupportFolder::name, String.CASE_INSENSITIVE_ORDER));
        return folders;
      }

      @Override
      protected void done() {
        refreshSupportButton.setEnabled(true);
        try {
          List<SupportFolder> folders = get();
          supportModel.setFolders(folders);
          trashSupportButton.setEnabled(!folders.isEmpty());
          supportStatus.setText(
              "Found "
                  + folders.size()
                  + " folders. Sizes include readable files; inaccessible files may be omitted.");
        } catch (Exception ex) {
          supportStatus.setText(
              "Could not read Application Support: " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
        }
      }
    }.execute();
  }

  private void trashSupportFolders() {
    int[] rows = supportTable.getSelectedRows();
    if (rows.length == 0) {
      JOptionPane.showMessageDialog(
          frame,
          "Select one or more folders first.",
          "Nothing selected",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    List<SupportFolder> selected =
        Arrays.stream(rows)
            .map(supportTable::convertRowIndexToModel)
            .mapToObj(supportModel.folders::get)
            .toList();
    String names =
        selected.stream()
            .map(
                folder ->
                    folder.name()
                        + " ("
                        + dev.storagecleanup.StorageCleanup.formatSize(
                            folder.size() == null ? 0 : folder.size())
                        + ")")
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    if (JOptionPane.showConfirmDialog(
            frame,
            "Move these Application Support folders to the macOS Trash?\n\n" + names,
            "Confirm moving to Trash",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE)
        != JOptionPane.OK_OPTION) return;
    refreshSupportButton.setEnabled(false);
    trashSupportButton.setEnabled(false);
    supportStatus.setText("Moving selected folders to Trash…");
    new SwingWorker<List<String>, Void>() {
      @Override
      protected List<String> doInBackground() {
        List<String> failures = new ArrayList<>();
        for (SupportFolder folder : selected) {
          try {
            new MacTrashGateway().move(folder.path());
          } catch (Exception ex) {
            failures.add(folder.name() + ": " + ex.getMessage());
          }
        }
        return failures;
      }

      @Override
      protected void done() {
        try {
          List<String> failures = get();
          supportStatus.setText(
              failures.isEmpty()
                  ? "Moved selected folders to Trash."
                  : "Some folders could not be moved: " + String.join("; ", failures));
          refreshSupportFolders();
        } catch (Exception ex) {
          refreshSupportButton.setEnabled(true);
          supportStatus.setText(
              "Could not move folders: " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
        }
      }
    }.execute();
  }
}
