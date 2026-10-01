package dev.storagecleanup.presentation.files;

import javax.swing.*;
import java.awt.*;
import dev.storagecleanup.domain.StorageAllocation;

public final class AllocationBar extends JComponent {
    private StorageAllocation allocation;
    private final Color[] colors = {new Color(72, 146, 239), new Color(164, 110, 210),
            new Color(239, 156, 62), new Color(141, 151, 164), new Color(222, 226, 232)};

    public AllocationBar() { setPreferredSize(new Dimension(300, 18)); setToolTipText("Estimated storage allocation; folder categories may omit protected data."); }
    public void setAllocation(StorageAllocation value) { allocation = value; repaint(); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        int x = 0, width = getWidth(), height = getHeight();
        g.setColor(new Color(230, 233, 238));
        g.fillRoundRect(0, 1, width, height - 2, 8, 8);
        if (allocation != null && allocation.total() > 0) {
            long used = Math.max(0, allocation.total() - allocation.free());
            long[] categories = {allocation.applications(), allocation.caches(), allocation.personal(), allocation.other()};
            long estimated = 0;
            for (long size : categories) estimated = Long.MAX_VALUE - estimated < size ? Long.MAX_VALUE : estimated + size;
            double scale = estimated > used && estimated > 0 ? (double) used / estimated : 1.0;
            for (int i = 0; i < categories.length; i++) {
                int segment = (int) Math.round((double) Math.min(used, (long) (categories[i] * scale)) / allocation.total() * width);
                if (i == categories.length - 1) segment = Math.max(0, (int) Math.round((double) used / allocation.total() * width) - x);
                if (segment > 0) {
                    g.setColor(colors[i]);
                    g.fillRect(x, 1, segment, height - 2);
                    x += segment;
                }
            }
            int freeWidth = Math.max(0, width - x);
            if (freeWidth > 0) { g.setColor(colors[4]); g.fillRect(x, 1, freeWidth, height - 2); }
            g.setColor(new Color(120, 125, 132));
            g.drawRoundRect(0, 1, width - 1, height - 3, 8, 8);
        }
        g.dispose();
    }
}
