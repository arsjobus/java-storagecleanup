package dev.storagecleanup.presentation.homebrew;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import dev.storagecleanup.infrastructure.FileTreeSizer;
import dev.storagecleanup.domain.BrewPackage;

public final class HomebrewPanel extends JPanel {
    private final JFrame frame;
    private final JButton refreshBrewButton = new JButton("Refresh packages");
    private final JButton uninstallBrewButton = new JButton("Uninstall selected");
    private final JLabel brewStatus = new JLabel("Refresh to list installed Homebrew packages.");
    private final BrewTableModel brewModel = new BrewTableModel();
    private final JTable brewTable = new JTable(brewModel);
    private Path brewExecutable;

    public HomebrewPanel(JFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        add(createBrewPanel(), BorderLayout.CENTER);
    }

    private JPanel createBrewPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(refreshBrewButton);
        controls.add(uninstallBrewButton);
        JLabel note = new JLabel("Size is measured from installed package folders; shared dependencies can appear in multiple totals.");
        note.setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
        JPanel north = new JPanel(new BorderLayout());
        north.add(note, BorderLayout.NORTH);
        north.add(controls, BorderLayout.CENTER);
        panel.add(north, BorderLayout.NORTH);
        brewTable.setFillsViewportHeight(true);
        brewTable.setRowHeight(26);
        brewTable.setAutoCreateRowSorter(true);
        brewTable.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(4, SortOrder.DESCENDING)));
        brewTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        brewTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        brewTable.getColumnModel().getColumn(1).setPreferredWidth(260);
        brewTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        brewTable.getColumnModel().getColumn(3).setPreferredWidth(160);
        brewTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        brewTable.setDefaultRenderer(Long.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText(value instanceof Long bytes ? dev.storagecleanup.Main.formatSize(bytes) : "Unavailable");
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
        });
        panel.add(new JScrollPane(brewTable), BorderLayout.CENTER);
        brewStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 10, 12));
        panel.add(brewStatus, BorderLayout.SOUTH);
        refreshBrewButton.addActionListener(e -> refreshBrewPackages());
        uninstallBrewButton.addActionListener(e -> uninstallBrewPackages());
        uninstallBrewButton.setEnabled(false);
        return panel;
    }

    private void refreshBrewPackages() {
        refreshBrewButton.setEnabled(false);
        uninstallBrewButton.setEnabled(false);
        brewStatus.setText("Reading installed Homebrew packages…");
        new SwingWorker<List<BrewPackage>, Void>() {
            @Override protected List<BrewPackage> doInBackground() throws Exception {
                brewExecutable = findBrew();
                List<BrewPackage> packages = new ArrayList<>();
                ExecutorService workers = Executors.newFixedThreadPool(Math.min(4,
                        Math.max(1, Runtime.getRuntime().availableProcessors())));
                try {
                Path cellar = Path.of(runBrew("--cellar").trim());
                Path caskroom = Path.of(runBrew("--caskroom").trim());
                List<Future<BrewPackage>> sizedPackages = new ArrayList<>();
                for (String type : List.of("formula", "cask")) {
                    String output = runBrew("list", "--" + type, "--versions");
                    for (String line : output.lines().toList()) {
                        String[] parts = line.trim().split("\\s+", 2);
                        if (!parts[0].isBlank()) {
                            String name = parts[0];
                            String version = parts.length > 1 ? parts[1] : "";
                            Path packageRoot = (type.equals("formula") ? cellar : caskroom).resolve(name);
                            sizedPackages.add(workers.submit(() -> new BrewPackage(type, name, "", version,
                                    brewPackageSize(type, name, packageRoot))));
                        }
                    }
                }
                for (Future<BrewPackage> future : sizedPackages) packages.add(future.get());
                packages = addParentPackages(packages);
                packages.sort(Comparator.comparing(BrewPackage::type).thenComparing(BrewPackage::name, String.CASE_INSENSITIVE_ORDER));
                return packages;
                } finally {
                    workers.shutdownNow();
                }
            }
            @Override protected void done() {
                refreshBrewButton.setEnabled(true);
                try {
                    List<BrewPackage> packages = get();
                    brewModel.setPackages(packages);
                    uninstallBrewButton.setEnabled(!packages.isEmpty());
                    brewStatus.setText("Found " + packages.size() + " installed packages.");
                } catch (Exception ex) {
                    brewStatus.setText("Could not list Homebrew packages: " + dev.storagecleanup.Main.rootMessage(ex));
                    JOptionPane.showMessageDialog(frame, dev.storagecleanup.Main.rootMessage(ex), "Homebrew unavailable", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private List<BrewPackage> addParentPackages(List<BrewPackage> packages) {
        Map<String, List<String>> parentsByDependency = new HashMap<>();
        Set<String> installedFormulas = new HashSet<>();
        Set<String> installedPackages = new HashSet<>();
        for (BrewPackage item : packages) {
            installedPackages.add(item.name());
            if (item.type().equals("formula")) installedFormulas.add(item.name());
        }
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(4,
                Math.max(1, Runtime.getRuntime().availableProcessors())));
        try {
            Map<String, Future<List<String>>> lookups = new HashMap<>();
            for (String dependency : installedFormulas) {
                lookups.put(dependency, workers.submit(() -> runBrew("uses", "--installed", dependency)
                        .lines().map(String::trim).filter(installedPackages::contains).toList()));
            }
            for (Map.Entry<String, Future<List<String>>> lookup : lookups.entrySet()) {
                try { parentsByDependency.put(lookup.getKey(), lookup.getValue().get()); }
                catch (Exception ignored) {
                    // Dependency metadata can be unavailable for third-party or outdated formulae.
                }
            }
        } finally {
            workers.shutdownNow();
        }
        return new ArrayList<>(packages.stream().map(item -> {
            String parents = String.join(", ", parentsByDependency.getOrDefault(item.name(), List.of()));
            return new BrewPackage(item.type(), item.name(), parents, item.version(), item.size());
        }).toList());
    }

    private Long brewPackageSize(String type, String name, Path packageRoot) {
        if (type.equals("formula")) return FileTreeSizer.directorySize(packageRoot);

        // Casks commonly install an app outside Caskroom and leave a link to it there.
        // Ask Homebrew for the cask's artifact paths and measure their real locations.
        try {
            Set<Path> roots = new LinkedHashSet<>();
            addMeasuredRoot(roots, packageRoot);
            String artifacts = runBrew("list", "--cask", "--verbose", name);
            for (String line : artifacts.lines().toList()) {
                Path artifact;
                try { artifact = Path.of(line.trim()); }
                catch (InvalidPathException ignored) { continue; }
                if (artifact.isAbsolute()) addMeasuredRoot(roots, artifact);
            }
            long total = 0;
            for (Path root : roots) {
                Long size = FileTreeSizer.directorySize(root);
                if (size == null) return null;
                total = Math.addExact(total, size);
            }
            return total;
        } catch (Exception ignored) { return null; }
    }

    private static void addMeasuredRoot(Set<Path> roots, Path path) throws IOException {
        if (!Files.exists(path)) return;
        Path real = path.toRealPath();
        if (roots.stream().anyMatch(real::startsWith)) return;
        roots.removeIf(existing -> existing.startsWith(real));
        roots.add(real);
    }

    private void uninstallBrewPackages() {
        int[] rows = brewTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(frame, "Select one or more packages first.", "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<BrewPackage> selected = Arrays.stream(rows).map(brewTable::convertRowIndexToModel).mapToObj(brewModel.packages::get).toList();
        String names = selected.stream().map(p -> p.type() + " " + p.name()).reduce((a, b) -> a + "\n" + b).orElse("");
        if (JOptionPane.showConfirmDialog(frame, "Uninstall these Homebrew packages?\n\n" + names + "\n\nHomebrew may also remove dependencies it considers unused.",
                "Confirm Homebrew uninstall", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        refreshBrewButton.setEnabled(false);
        uninstallBrewButton.setEnabled(false);
        brewStatus.setText("Uninstalling selected packages…");
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() {
                List<String> failures = new ArrayList<>();
                for (BrewPackage item : selected) {
                    try { runBrew("uninstall", "--" + item.type(), item.name()); }
                    catch (Exception ex) { failures.add(item.name() + ": " + dev.storagecleanup.Main.rootMessage(ex)); }
                }
                return failures;
            }
            @Override protected void done() {
                try {
                    List<String> failures = get();
                    brewStatus.setText(failures.isEmpty() ? "Uninstall complete. Refreshing package list…" : "Some uninstalls failed: " + String.join("; ", failures));
                    refreshBrewPackages();
                } catch (Exception ex) {
                    refreshBrewButton.setEnabled(true);
                    brewStatus.setText("Uninstall failed: " + dev.storagecleanup.Main.rootMessage(ex));
                }
            }
        }.execute();
    }

    private static Path findBrew() throws IOException {
        for (Path path : List.of(Path.of("/opt/homebrew/bin/brew"), Path.of("/usr/local/bin/brew"), Path.of("/home/linuxbrew/.linuxbrew/bin/brew")))
            if (Files.isExecutable(path)) return path;
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(dir, "brew");
            if (Files.isExecutable(candidate)) return candidate;
        }
        throw new IOException("Homebrew was not found in common locations or PATH.");
    }

    private String runBrew(String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(brewExecutable.toString());
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(5, TimeUnit.MINUTES)) { process.destroyForcibly(); throw new IOException("Homebrew command timed out."); }
            String text = new String(output, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) throw new IOException(text.isBlank() ? "brew exited with status " + process.exitValue() : text);
            return text;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Homebrew command was interrupted.", ex);
        }
    }

}
