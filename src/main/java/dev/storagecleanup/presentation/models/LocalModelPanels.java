package dev.storagecleanup.presentation.models;

import dev.storagecleanup.domain.HuggingFaceModel;
import dev.storagecleanup.domain.OllamaModel;
import dev.storagecleanup.infrastructure.FileTreeSizer;
import dev.storagecleanup.presentation.AlternatingRowTable;
import java.awt.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

public final class LocalModelPanels {
  private static final Pattern OLLAMA_LIST_LINE =
      Pattern.compile(
          "^(\\S+)\\s+(\\S+)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*([KMGT]?B)\\b.*$",
          Pattern.CASE_INSENSITIVE);
  private final JFrame frame;
  private final JButton refreshOllamaButton = new JButton("Refresh models");
  private final JButton removeOllamaButton = new JButton("Remove selected");
  private final JLabel ollamaStatus =
      new JLabel("Refresh to list locally installed Ollama models.");
  private final OllamaTableModel ollamaModel = new OllamaTableModel();
  private final JTable ollamaTable = new AlternatingRowTable(ollamaModel);
  private Path ollamaExecutable;
  private final JButton refreshHuggingFaceButton = new JButton("Check cache");
  private final JLabel huggingFaceStatus =
      new JLabel("Check for models in the standard Hugging Face home cache.");
  private final HuggingFaceTableModel huggingFaceModel = new HuggingFaceTableModel();
  private final JTable huggingFaceTable = new AlternatingRowTable(huggingFaceModel);

  public LocalModelPanels(JFrame frame) {
    this.frame = frame;
  }

  public JPanel ollamaPanel() {
    return createOllamaPanel();
  }

  public JPanel huggingFacePanel() {
    return createHuggingFacePanel();
  }

  private JPanel createOllamaPanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(refreshOllamaButton);
    controls.add(removeOllamaButton);
    JLabel note = new JLabel("Size is reported by Ollama for each locally installed model.");
    note.setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
    JPanel north = new JPanel(new BorderLayout());
    north.add(note, BorderLayout.NORTH);
    north.add(controls, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    ollamaTable.setFillsViewportHeight(true);
    ollamaTable.setRowHeight(26);
    ollamaTable.setAutoCreateRowSorter(true);
    ollamaTable.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.DESCENDING)));
    ollamaTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    ollamaTable.getColumnModel().getColumn(0).setPreferredWidth(330);
    ollamaTable.getColumnModel().getColumn(1).setPreferredWidth(180);
    ollamaTable.getColumnModel().getColumn(2).setPreferredWidth(100);
    ollamaTable.setDefaultRenderer(
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
    panel.add(new JScrollPane(ollamaTable), BorderLayout.CENTER);
    ollamaStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 10, 12));
    panel.add(ollamaStatus, BorderLayout.SOUTH);
    refreshOllamaButton.addActionListener(e -> refreshOllamaModels());
    removeOllamaButton.addActionListener(e -> removeOllamaModels());
    removeOllamaButton.setEnabled(false);
    return panel;
  }

  private JPanel createHuggingFacePanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    JPanel north = new JPanel(new BorderLayout());
    JLabel note =
        new JLabel("Checks ~/.cache/huggingface/hub for locally cached model repositories.");
    note.setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
    north.add(note, BorderLayout.NORTH);
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(refreshHuggingFaceButton);
    north.add(controls, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    huggingFaceTable.setFillsViewportHeight(true);
    huggingFaceTable.setRowHeight(26);
    huggingFaceTable.setAutoCreateRowSorter(true);
    huggingFaceTable
        .getRowSorter()
        .setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.DESCENDING)));
    huggingFaceTable.getColumnModel().getColumn(0).setPreferredWidth(270);
    huggingFaceTable.getColumnModel().getColumn(1).setPreferredWidth(470);
    huggingFaceTable.getColumnModel().getColumn(2).setPreferredWidth(100);
    huggingFaceTable.setDefaultRenderer(
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
    panel.add(new JScrollPane(huggingFaceTable), BorderLayout.CENTER);
    huggingFaceStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 10, 12));
    panel.add(huggingFaceStatus, BorderLayout.SOUTH);
    refreshHuggingFaceButton.addActionListener(e -> refreshHuggingFaceCache());
    return panel;
  }

  private void refreshHuggingFaceCache() {
    refreshHuggingFaceButton.setEnabled(false);
    huggingFaceStatus.setText("Checking Hugging Face model cache…");
    new SwingWorker<List<HuggingFaceModel>, Void>() {
      @Override
      protected List<HuggingFaceModel> doInBackground() throws Exception {
        Path hub = Path.of(System.getProperty("user.home"), ".cache", "huggingface", "hub");
        List<HuggingFaceModel> models = new ArrayList<>();
        if (!Files.isDirectory(hub)) return models;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(hub, "models--*")) {
          for (Path repository : entries) {
            if (!Files.isDirectory(repository)) continue;
            String folder = repository.getFileName().toString();
            String name = folder.substring("models--".length()).replace("--", "/");
            Path blobs = repository.resolve("blobs");
            models.add(new HuggingFaceModel(name, FileTreeSizer.directorySize(blobs), repository));
          }
        }
        models.sort(Comparator.comparing(HuggingFaceModel::name, String.CASE_INSENSITIVE_ORDER));
        return models;
      }

      @Override
      protected void done() {
        refreshHuggingFaceButton.setEnabled(true);
        try {
          List<HuggingFaceModel> models = get();
          huggingFaceModel.setModels(models);
          long total =
              models.stream()
                  .map(HuggingFaceModel::size)
                  .filter(Objects::nonNull)
                  .mapToLong(Long::longValue)
                  .sum();
          huggingFaceStatus.setText(
              models.isEmpty()
                  ? "No cached model repositories found in the standard Hugging Face home cache."
                  : "Found "
                      + models.size()
                      + " model repositories; blob storage totals "
                      + dev.storagecleanup.StorageCleanup.formatSize(total)
                      + ".");
        } catch (Exception ex) {
          huggingFaceStatus.setText(
              "Could not read Hugging Face cache: "
                  + dev.storagecleanup.StorageCleanup.rootMessage(ex));
        }
      }
    }.execute();
  }

  private void refreshOllamaModels() {
    refreshOllamaButton.setEnabled(false);
    removeOllamaButton.setEnabled(false);
    ollamaStatus.setText("Reading installed Ollama models…");
    new SwingWorker<List<OllamaModel>, Void>() {
      @Override
      protected List<OllamaModel> doInBackground() throws Exception {
        ollamaExecutable = findOllama();
        String output = runOllama("list");
        List<OllamaModel> models = new ArrayList<>();
        for (String line : output.lines().skip(1).toList()) {
          Matcher match = OLLAMA_LIST_LINE.matcher(line.trim());
          if (!match.matches()) continue;
          String name = match.group(1);
          String id = match.group(2);
          Long size = parseOllamaSize(match.group(3), match.group(4));
          models.add(new OllamaModel(name, id, size));
        }
        models.sort(Comparator.comparing(OllamaModel::name, String.CASE_INSENSITIVE_ORDER));
        return models;
      }

      @Override
      protected void done() {
        refreshOllamaButton.setEnabled(true);
        try {
          List<OllamaModel> models = get();
          ollamaModel.setModels(models);
          removeOllamaButton.setEnabled(!models.isEmpty());
          ollamaStatus.setText("Found " + models.size() + " installed models.");
        } catch (Exception ex) {
          ollamaStatus.setText(
              "Could not list Ollama models: " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
          JOptionPane.showMessageDialog(
              frame,
              dev.storagecleanup.StorageCleanup.rootMessage(ex),
              "Ollama unavailable",
              JOptionPane.WARNING_MESSAGE);
        }
      }
    }.execute();
  }

  private static Long parseOllamaSize(String amount, String unit) {
    int exponent =
        switch (unit.toUpperCase(Locale.ROOT)) {
          case "KB" -> 1;
          case "MB" -> 2;
          case "GB" -> 3;
          case "TB" -> 4;
          default -> 0;
        };
    try {
      return new BigDecimal(amount)
          .multiply(BigDecimal.valueOf(1024).pow(exponent))
          .setScale(0, RoundingMode.HALF_UP)
          .longValueExact();
    } catch (ArithmeticException | NumberFormatException ex) {
      return null;
    }
  }

  private static Path findOllama() throws IOException {
    String pathEnv = System.getenv("PATH");
    if (pathEnv != null)
      for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
        Path candidate = Path.of(dir, "ollama");
        if (Files.isExecutable(candidate)) return candidate;
      }
    throw new IOException("Ollama was not found in PATH.");
  }

  private String runOllama(String... args) throws IOException {
    List<String> command = new ArrayList<>();
    command.add(ollamaExecutable.toString());
    command.addAll(List.of(args));
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      byte[] output = process.getInputStream().readAllBytes();
      if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroyForcibly();
        throw new IOException("Ollama command timed out.");
      }
      String text = new String(output, java.nio.charset.StandardCharsets.UTF_8).trim();
      if (process.exitValue() != 0)
        throw new IOException(
            text.isBlank() ? "ollama exited with status " + process.exitValue() : text);
      return text;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Ollama command was interrupted.", ex);
    }
  }

  private void removeOllamaModels() {
    int[] rows = ollamaTable.getSelectedRows();
    if (rows.length == 0) {
      JOptionPane.showMessageDialog(
          frame,
          "Select one or more models first.",
          "Nothing selected",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    List<OllamaModel> selected =
        Arrays.stream(rows)
            .map(ollamaTable::convertRowIndexToModel)
            .mapToObj(ollamaModel.models::get)
            .toList();
    String names =
        selected.stream().map(OllamaModel::name).reduce((a, b) -> a + "\n" + b).orElse("");
    if (JOptionPane.showConfirmDialog(
            frame,
            "Permanently remove these local Ollama models?\n\n" + names,
            "Confirm Ollama model removal",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE)
        != JOptionPane.OK_OPTION) return;
    refreshOllamaButton.setEnabled(false);
    removeOllamaButton.setEnabled(false);
    ollamaStatus.setText("Removing selected models…");
    new SwingWorker<List<String>, Void>() {
      @Override
      protected List<String> doInBackground() {
        List<String> failures = new ArrayList<>();
        for (OllamaModel model : selected) {
          try {
            runOllama("rm", model.name());
          } catch (Exception ex) {
            failures.add(model.name() + ": " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
          }
        }
        return failures;
      }

      @Override
      protected void done() {
        try {
          List<String> failures = get();
          ollamaStatus.setText(
              failures.isEmpty()
                  ? "Removal complete. Refreshing model list…"
                  : "Some removals failed: " + String.join("; ", failures));
          refreshOllamaModels();
        } catch (Exception ex) {
          refreshOllamaButton.setEnabled(true);
          ollamaStatus.setText(
              "Removal failed: " + dev.storagecleanup.StorageCleanup.rootMessage(ex));
        }
      }
    }.execute();
  }
}
