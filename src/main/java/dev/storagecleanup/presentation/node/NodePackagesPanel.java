package dev.storagecleanup.presentation.node;

import dev.storagecleanup.domain.NodePackage;
import dev.storagecleanup.infrastructure.FileTreeSizer;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

public final class NodePackagesPanel extends JPanel {
  private final JFrame frame;
  private final JButton refreshButton = new JButton("Refresh packages");
  private final JButton uninstallButton = new JButton("Uninstall selected");
  private final JLabel status = new JLabel("Refresh to list globally installed npm packages.");
  private final NodePackageTableModel model = new NodePackageTableModel();
  private final JTable table = new JTable(model);
  private Path npmExecutable;

  public NodePackagesPanel(JFrame frame) {
    super(new BorderLayout(10, 10));
    this.frame = frame;
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(refreshButton);
    controls.add(uninstallButton);
    JPanel north = new JPanel(new BorderLayout());
    JLabel note =
        new JLabel(
            "Lists packages installed in npm's global prefix; other Node version managers may have separate lists.");
    note.setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
    north.add(note, BorderLayout.NORTH);
    north.add(controls, BorderLayout.CENTER);
    add(north, BorderLayout.NORTH);
    table.setFillsViewportHeight(true);
    table.setRowHeight(26);
    table.setAutoCreateRowSorter(true);
    table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.DESCENDING)));
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.getColumnModel().getColumn(0).setPreferredWidth(190);
    table.getColumnModel().getColumn(1).setPreferredWidth(120);
    table.getColumnModel().getColumn(2).setPreferredWidth(420);
    table.getColumnModel().getColumn(3).setPreferredWidth(100);
    table.setDefaultRenderer(
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
    add(new JScrollPane(table), BorderLayout.CENTER);
    status.setBorder(BorderFactory.createEmptyBorder(5, 12, 10, 12));
    add(status, BorderLayout.SOUTH);
    refreshButton.addActionListener(e -> refreshPackages());
    uninstallButton.addActionListener(e -> uninstallSelected());
    uninstallButton.setEnabled(false);
  }

  private void refreshPackages() {
    refreshButton.setEnabled(false);
    uninstallButton.setEnabled(false);
    status.setText("Reading globally installed npm packages…");
    new SwingWorker<List<NodePackage>, Void>() {
      @Override
      protected List<NodePackage> doInBackground() throws Exception {
        npmExecutable = findNpm();
        Path root = Path.of(runNpm("root", "--global").trim());
        List<NodePackage> result = new ArrayList<>();
        for (String line :
            runNpm("ls", "--global", "--depth=0", "--parseable").lines().skip(1).toList()) {
          if (line.isBlank()) continue;
          Path location = Path.of(line.trim());
          String name = readJsonString(location.resolve("package.json"), "name");
          String version = readJsonString(location.resolve("package.json"), "version");
          if (name.isBlank()) name = packageName(root, location);
          result.add(
              new NodePackage(name, version, location, FileTreeSizer.directorySize(location)));
        }
        result.sort(Comparator.comparing(NodePackage::name, String.CASE_INSENSITIVE_ORDER));
        return result;
      }

      @Override
      protected void done() {
        refreshButton.setEnabled(true);
        try {
          List<NodePackage> packages = get();
          model.setPackages(packages);
          uninstallButton.setEnabled(!packages.isEmpty());
          status.setText("Found " + packages.size() + " globally installed npm packages.");
        } catch (Exception ex) {
          status.setText(
              "Could not list global npm packages: " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
          JOptionPane.showMessageDialog(
              frame,
              dev.storagecleanup.StorageCleanup.rootMessage(ex),
              "npm unavailable",
              JOptionPane.WARNING_MESSAGE);
        }
      }
    }.execute();
  }

  private void uninstallSelected() {
    int[] rows = table.getSelectedRows();
    if (rows.length == 0) {
      JOptionPane.showMessageDialog(
          frame,
          "Select one or more packages first.",
          "Nothing selected",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    List<NodePackage> selected =
        Arrays.stream(rows)
            .map(table::convertRowIndexToModel)
            .mapToObj(model.packages::get)
            .toList();
    String names =
        selected.stream().map(NodePackage::name).reduce((a, b) -> a + "\n" + b).orElse("");
    if (JOptionPane.showConfirmDialog(
            frame,
            "Uninstall these global npm packages?\n\n" + names,
            "Confirm npm uninstall",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE)
        != JOptionPane.OK_OPTION) return;
    refreshButton.setEnabled(false);
    uninstallButton.setEnabled(false);
    status.setText("Uninstalling selected packages…");
    new SwingWorker<List<String>, Void>() {
      @Override
      protected List<String> doInBackground() {
        List<String> failures = new ArrayList<>();
        for (NodePackage item : selected) {
          try {
            runNpm("uninstall", "--global", item.name());
          } catch (Exception ex) {
            failures.add(item.name() + ": " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
          }
        }
        return failures;
      }

      @Override
      protected void done() {
        try {
          List<String> failures = get();
          status.setText(
              failures.isEmpty()
                  ? "Uninstall complete. Refreshing package list…"
                  : "Some uninstalls failed: " + String.join("; ", failures));
          refreshPackages();
        } catch (Exception ex) {
          refreshButton.setEnabled(true);
          status.setText("Uninstall failed: " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
        }
      }
    }.execute();
  }

  private static String packageName(Path root, Path location) {
    Path relative = root.relativize(location);
    if (relative.getNameCount() >= 2 && relative.getName(0).toString().startsWith("@"))
      return relative.getName(0) + "/" + relative.getName(1);
    return relative.toString();
  }

  private static String readJsonString(Path file, String key) {
    try {
      String json = Files.readString(file, StandardCharsets.UTF_8);
      Matcher matcher =
          Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
              .matcher(json);
      return matcher.find() ? matcher.group(1) : "";
    } catch (IOException ignored) {
      return "";
    }
  }

  private static Path findNpm() throws IOException {
    String pathEnv = System.getenv("PATH");
    if (pathEnv != null)
      for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
        Path candidate = Path.of(dir, "npm");
        if (Files.isExecutable(candidate)) return candidate;
      }
    throw new IOException(
        "npm was not found on PATH. Launch the app from an environment where Node.js is available.");
  }

  private String runNpm(String... args) throws IOException {
    List<String> command = new ArrayList<>();
    command.add(npmExecutable.toString());
    command.addAll(List.of(args));
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      byte[] output = process.getInputStream().readAllBytes();
      if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroyForcibly();
        throw new IOException("npm command timed out.");
      }
      String text = new String(output, StandardCharsets.UTF_8).trim();
      if (process.exitValue() != 0)
        throw new IOException(
            text.isBlank() ? "npm exited with status " + process.exitValue() : text);
      return text;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("npm command was interrupted.", ex);
    }
  }
}
