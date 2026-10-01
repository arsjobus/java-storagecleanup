package dev.storagecleanup;

import dev.storagecleanup.domain.Candidate;
import dev.storagecleanup.domain.ScanResult;
import dev.storagecleanup.domain.StorageAllocation;
import dev.storagecleanup.presentation.files.AllocationBar;
import dev.storagecleanup.presentation.files.CandidateTableModel;
import dev.storagecleanup.presentation.homebrew.HomebrewPanel;
import dev.storagecleanup.presentation.node.NodePackagesPanel;
import dev.storagecleanup.presentation.models.LocalModelPanels;
import dev.storagecleanup.presentation.support.SupportPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** A cautious, user-directed storage scanner for macOS. */
public final class Main {
    private final JFrame frame = new JFrame("Mac Storage Cleaner");
    private final CandidateTableModel model = new CandidateTableModel();
    private final JTable table = new JTable(model) {
        @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
            Component component = super.prepareRenderer(renderer, row, column);
            if (!isCellSelected(row, column)) {
                Candidate candidate = model.candidates.get(convertRowIndexToModel(row));
                Instant activity = candidate.lastActivity();
                Color background = Color.WHITE;
                if (activity != null) {
                    Instant now = Instant.now();
                    if (activity.isBefore(now.minusSeconds(60L * 24 * 60 * 60))) background = new Color(255, 220, 220);
                    else if (activity.isBefore(now.minusSeconds(30L * 24 * 60 * 60))) background = new Color(255, 246, 204);
                }
                component.setBackground(background);
            }
            return component;
        }
    };
    private final JLabel status = new JLabel("Choose Scan to look for storage candidates.");
    private final JButton scanButton = new JButton("Scan");
    private final JButton trashButton = new JButton("Move selected to Trash");
    private final JCheckBox includeHidden = new JCheckBox("Include Homebrew + hidden files");
    private final JSpinner minSize = new JSpinner(new SpinnerNumberModel(100, 1, 100_000, 50));
    private final AllocationBar allocationBar = new AllocationBar();
    private final JLabel allocationText = new JLabel("Storage summary updates when you scan.");
    private final JButton otherDetailsButton = new JButton("Break down Other");
    private StorageAllocation lastAllocation;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().show());
    }

    private void show() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(920, 540));
        frame.setLayout(new BorderLayout(12, 12));

        JPanel top = new JPanel(new BorderLayout(12, 8));
        JLabel intro = new JLabel("Review large files, application bundles, and user cache folders.");
        intro.setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
        top.add(intro, BorderLayout.NORTH);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(scanButton);
        controls.add(new JLabel("Large download threshold (MB):"));
        controls.add(minSize);
        controls.add(includeHidden);
        controls.add(trashButton);
        top.add(controls, BorderLayout.CENTER);
        JPanel storageSummary = new JPanel(new BorderLayout(4, 5));
        storageSummary.setBorder(BorderFactory.createTitledBorder("Mac storage"));
        storageSummary.setPreferredSize(new Dimension(360, 142));
        storageSummary.add(allocationBar, BorderLayout.NORTH);
        allocationText.setFont(allocationText.getFont().deriveFont(11f));
        allocationText.setToolTipText("Other is the estimated used space not counted in Applications, Caches, or the listed personal folders. It can include macOS and system data, application support files, virtual machines, and folders the scan could not read.");
        storageSummary.add(allocationText, BorderLayout.CENTER);
        otherDetailsButton.setEnabled(false);
        otherDetailsButton.addActionListener(e -> showOtherBreakdown());
        storageSummary.add(otherDetailsButton, BorderLayout.SOUTH);
        top.add(storageSummary, BorderLayout.EAST);
        JPanel scanPanel = new JPanel(new BorderLayout(12, 12));
        scanPanel.add(top, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(5, SortOrder.DESCENDING)));
        table.setDefaultRenderer(Long.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText(value instanceof Long bytes ? formatSize(bytes) : "");
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
        });
        table.setDefaultRenderer(Instant.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText(value instanceof Instant instant
                        ? DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(instant)
                        : "Unavailable");
            }
        });
        table.getColumnModel().getColumn(0).setMaxWidth(64); // Select
        table.getColumnModel().getColumn(1).setPreferredWidth(280); // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(110); // Category
        table.getColumnModel().getColumn(3).setPreferredWidth(145); // Last activity
        table.getColumnModel().getColumn(4).setPreferredWidth(400); // Location
        table.getColumnModel().getColumn(5).setPreferredWidth(100); // Size
        scanPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 10, 12));
        scanPanel.add(status, BorderLayout.SOUTH);
        scanButton.addActionListener(e -> scan());
        trashButton.addActionListener(e -> moveSelectedToTrash());
        trashButton.setToolTipText("Items go to the macOS Trash; review them there before emptying it.");
        LocalModelPanels localModelPanels = new LocalModelPanels(frame);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Files and folders", scanPanel);
        tabs.addTab("Homebrew packages", new HomebrewPanel(frame));
        tabs.addTab("Node packages", new NodePackagesPanel(frame));
        tabs.addTab("Ollama models", localModelPanels.ollamaPanel());
        tabs.addTab("Hugging Face models", localModelPanels.huggingFacePanel());
        tabs.addTab("Application Support Folders", new SupportPanel(frame));
        frame.add(tabs, BorderLayout.CENTER);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    public static String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }



    private void scan() {
        scanButton.setEnabled(false);
        trashButton.setEnabled(false);
        status.setText("Scanning… This can take a few minutes on large folders.");
        long threshold = ((Number) minSize.getValue()).longValue() * 1024 * 1024;
        boolean scanHidden = includeHidden.isSelected();
        new SwingWorker<ScanResult, Void>() {
            @Override protected ScanResult doInBackground() {
                List<Candidate> candidates = new dev.storagecleanup.infrastructure.StorageScanner().scan(threshold, scanHidden);
                return new ScanResult(candidates, StorageAllocation.scan(candidates));
            }
            @Override protected void done() {
                scanButton.setEnabled(true);
                trashButton.setEnabled(true);
                try {
                    ScanResult result = get();
                    model.setCandidates(result.candidates());
                    lastAllocation = result.allocation();
                    allocationBar.setAllocation(result.allocation());
                    allocationText.setText(result.allocation().description());
                    otherDetailsButton.setEnabled(true);
                    long total = model.candidates.stream().mapToLong(c -> c.size()).sum();
                    status.setText("Found " + model.getRowCount() + " review candidates totaling " + formatSize(total)
                            + ". Nothing has been deleted.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    status.setText("Scan was interrupted.");
                } catch (ExecutionException ex) {
                    status.setText("Scan failed: " + ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void moveSelectedToTrash() {
        List<Candidate> selected = model.selectedCandidates();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Select one or more rows first.", "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String message = "Move " + selected.size() + " selected item(s) to the macOS Trash?\n\n"
                + "Review application bundles and cache folders carefully. This does not empty the Trash.";
        if (JOptionPane.showConfirmDialog(frame, message, "Confirm moving to Trash", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;

        int moved = 0;
        List<String> failures = new ArrayList<>();
        for (Candidate candidate : selected) {
            try {
                new dev.storagecleanup.infrastructure.MacTrashGateway().move(candidate.path());
                moved++;
            } catch (Exception ex) {
                failures.add(candidate.path() + " — " + ex.getMessage());
            }
        }
        model.remove(selected);
        String result = "Moved " + moved + " item(s) to Trash.";
        if (!failures.isEmpty()) result += " Failed: " + String.join("; ", failures);
        status.setText(result);
    }










    /** Disk usage classes are estimates from readable folders; volume capacity comes from the OS. */


    private void showOtherBreakdown() {
        if (lastAllocation == null) return;
        otherDetailsButton.setEnabled(false);
        otherDetailsButton.setText("Scanning details…");
        status.setText("Measuring the folders included in Other…");
        long other = lastAllocation.other();
        new SwingWorker<Map<String, Long>, Void>() {
            @Override protected Map<String, Long> doInBackground() { return StorageAllocation.scanOtherBreakdown(other); }
            @Override protected void done() {
                otherDetailsButton.setEnabled(true);
                otherDetailsButton.setText("Break down Other");
                try {
                    String[] columns = {"Category", "Estimated size"};
                    Object[][] rows = get().entrySet().stream()
                            .map(entry -> new Object[]{entry.getKey(), formatSize(entry.getValue())})
                            .toArray(Object[][]::new);
                    JTable detailTable = new JTable(rows, columns);
                    detailTable.setEnabled(false);
                    detailTable.setRowHeight(24);
                    detailTable.getColumnModel().getColumn(0).setPreferredWidth(210);
                    detailTable.getColumnModel().getColumn(1).setPreferredWidth(125);
                    JPanel details = new JPanel(new BorderLayout(8, 8));
                    details.add(new JLabel("Estimated folders included in Other"), BorderLayout.NORTH);
                    details.add(new JScrollPane(detailTable), BorderLayout.CENTER);
                    details.add(new JLabel("Folder totals are approximate; protected files and APFS shared storage can affect the remainder."), BorderLayout.SOUTH);
                    details.setPreferredSize(new Dimension(400, 230));
                    JOptionPane.showMessageDialog(frame, details, "Other storage breakdown", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    status.setText("Other breakdown was interrupted.");
                } catch (ExecutionException ex) {
                    status.setText("Could not scan the Other breakdown: " + ex.getCause().getMessage());
                }
            }
        }.execute();
    }









    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return new DecimalFormat("#,##0.0").format(value) + " " + units[unit];
    }
}
