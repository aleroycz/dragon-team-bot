package com.dragon.component;


import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Generates badge/pill PNG icons for the Dragon Bot stats embeds.
 *
 * Three icon families:
 *   - Score band pills  (scoreBandIcon)
 *   - Action buttons    (actionButtonIcon)
 *   - Stat metric cards (statMetricIcon)
 */
@Component
public class IconRenderer {

    private static final Color BG_DARK    = new Color(0x0E0F14);
    private static final Color BG_CARD    = new Color(0x15161D);
    private static final Color GRID       = new Color(0x2A2B33);
    private static final Color TEXT_PRI   = new Color(0xF2F3F5);
    private static final Color TEXT_MUT   = new Color(0x72747D);
    private static final Color ACCENT     = new Color(0x5865F2);

    private static final Color C_HIGH     = new Color(0x57F287);  // 9–10 Actionable
    private static final Color C_GOOD     = new Color(0x5865F2);  // 7–8  Clear
    private static final Color C_MID      = new Color(0xFEE75C);  // 5–6  Vague
    private static final Color C_LOW      = new Color(0xED4245);  // 0–4  Confusing / Incoherent

    public enum Band   { ACTIONABLE, CLEAR, VAGUE, CONFUSING, INCOHERENT, NO_CONTENT }
    public enum Action { REFRESH, FULL_HISTORY }
    public enum Metric { SESSIONS, AVERAGE, BEST, WORST, RATING_TIER }

    /**
     * Score-band pill (e.g. "Actionable" / "Clear").
     * Recommended size: w=160, h=40
     */
    public byte[] scoreBandIcon(Band band, int w, int h) throws Exception {
        BufferedImage img = createImage(w, h);
        Graphics2D g = setup(img);

        String label  = bandLabel(band);
        String range  = bandRange(band);
        Color  accent = bandColor(band);
        float  alpha  = band == Band.NO_CONTENT ? 0f : 0.13f;
        boolean dashed = band == Band.INCOHERENT;

        // Pill fill
        if (alpha > 0) {
            g.setColor(withAlpha(accent, (int)(alpha * 255)));
            g.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h - 2, h - 2));
        } else {
            g.setColor(new Color(0x2A2B33));
            g.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h - 2, h - 2));
        }

        // Pill border
        g.setColor(accent);
        if (dashed) {
            float[] dash = {5f, 3f};
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
        } else {
            g.setStroke(new BasicStroke(1.5f));
        }
        g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h - 2, h - 2));

        // Dot
        int dotR = h / 4;
        int dotX = h / 2;
        int dotY = h / 2;
        g.setStroke(new BasicStroke(1f));
        g.setColor(band == Band.INCOHERENT ? withAlpha(accent, 155) : accent);
        g.fillOval(dotX - dotR, dotY - dotR, dotR * 2, dotR * 2);

        // Range label inside dot (tiny)
        Font rangeFont = new Font("SansSerif", Font.BOLD, 8);
        g.setFont(rangeFont);
        g.setColor(BG_DARK);
        FontMetrics rfm = g.getFontMetrics();
        g.drawString(range, dotX - rfm.stringWidth(range) / 2, dotY + rfm.getAscent() / 2 - 1);

        // Band label
        Font labelFont = new Font("SansSerif", Font.BOLD, 13);
        g.setFont(labelFont);
        FontMetrics lfm = g.getFontMetrics();
        int textX = h + 6;
        int textY = (h + lfm.getAscent() - lfm.getDescent()) / 2;
        g.setColor(band == Band.INCOHERENT ? withAlpha(accent, 210) : accent);
        g.drawString(label, textX, textY);

        g.dispose();
        return toPng(img);
    }

    /**
     * Action button pill (Refresh / Full History).
     * Recommended size: w=140, h=40
     */
    public byte[] actionButtonIcon(Action action, int w, int h) throws Exception {
        BufferedImage img = createImage(w, h);
        Graphics2D g = setup(img);

        // Pill background
        g.setColor(BG_CARD);
        g.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h - 2, h - 2));

        // Border
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h - 2, h - 2));

        // Icon + label
        int cx = h / 2;
        int cy = h / 2;
        int r  = h / 4;
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ACCENT);

        if (action == Action.REFRESH) {
            // Arc refresh icon
            g.draw(new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, 30, 300, Arc2D.OPEN));
            // Arrowhead on arc end
            int ax = cx + (int)(r * Math.cos(Math.toRadians(30)));
            int ay = cy - (int)(r * Math.sin(Math.toRadians(30)));
            g.drawLine(ax, ay, ax - 4, ay - 4);
            g.drawLine(ax, ay, ax + 4, ay - 1);
        } else {
            // Scroll/list icon
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new RoundRectangle2D.Float(cx - r, cy - r, r * 2, r * 2, 3, 3));
            g.drawLine(cx - r + 3, cy - 3, cx + r - 3, cy - 3);
            g.drawLine(cx - r + 3, cy + 1, cx + r - 3, cy + 1);
            g.drawLine(cx - r + 3, cy + 5, cx,          cy + 5);
        }

        // Label
        String label = action == Action.REFRESH ? "Refresh" : "Full History";
        Font font = new Font("SansSerif", Font.BOLD, 13);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int textX = h + 4;
        int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(ACCENT);
        g.drawString(label, textX, textY);

        g.dispose();
        return toPng(img);
    }

    /**
     * Stat metric card.
     * Recommended size: w=160, h=64
     */
    public byte[] statMetricIcon(Metric metric, String value, int w, int h) throws Exception {
        BufferedImage img = createImage(w, h);
        Graphics2D g = setup(img);

        Color accent = metricAccent(metric);

        // Card background
        g.setColor(BG_CARD);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));

        // Border
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12));

        // Left accent bar
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Float(0, 0, 5, h, 3, 3));

        // Mini icon
        int ix = 16, iy = h / 2;
        drawMetricIcon(g, metric, ix, iy, accent);

        // Label
        Font labelFont = new Font("SansSerif", Font.BOLD, 11);
        g.setFont(labelFont);
        g.setColor(TEXT_MUT);
        g.drawString(metricLabel(metric), 36, h / 2 - 3);

        // Value
        Font valueFont = new Font("SansSerif", Font.BOLD, 18);
        g.setFont(valueFont);
        g.setColor(metric == Metric.BEST ? C_HIGH
                : metric == Metric.WORST ? C_LOW
                : TEXT_PRI);
        g.drawString(value != null ? value : "—", 36, h / 2 + 16);

        g.dispose();
        return toPng(img);
    }

    private void drawMetricIcon(Graphics2D g, Metric metric, int cx, int cy, Color accent) {
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int r = 8;

        switch (metric) {
            case SESSIONS -> {
                // Bar chart
                g.fillRoundRect(cx - 8, cy + 1,  4, 7, 2, 2);
                g.fillRoundRect(cx - 3, cy - 3,  4, 11, 2, 2);
                g.fillRoundRect(cx + 2, cy - 7,  4, 15, 2, 2);
            }
            case AVERAGE -> {
                // Tilde wave
                g.draw(new Arc2D.Float(cx - 8, cy - 5, 8, 8, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Float(cx,     cy - 5, 8, 8, 180, 180, Arc2D.OPEN));
                g.drawLine(cx - 8, cy + 3, cx + 8, cy + 3);
            }
            case BEST -> {
                // Star (simplified 5-pt)
                int[] px = starX(cx, 9, 4.5);
                int[] py = starY(cy, 9, 4.5);
                g.fillPolygon(px, py, 10);
            }
            case WORST -> {
                // Down arrow
                g.drawLine(cx, cy - r, cx, cy + r - 3);
                g.drawLine(cx - 5, cy + r - 8, cx, cy + r - 2);
                g.drawLine(cx + 5, cy + r - 8, cx, cy + r - 2);
            }
            case RATING_TIER -> {
                // Shield outline
                GeneralPath shield = new GeneralPath();
                shield.moveTo(cx, cy - r);
                shield.lineTo(cx + r, cy - r + 3);
                shield.lineTo(cx + r, cy + 1);
                shield.quadTo(cx + r, cy + r, cx, cy + r + 2);
                shield.quadTo(cx - r, cy + r, cx - r, cy + 1);
                shield.lineTo(cx - r, cy - r + 3);
                shield.closePath();
                g.draw(shield);
            }
        }
    }

    // 5-pointed star coordinate helpers
    private int[] starX(int cx, double outer, double inner) {
        int[] xs = new int[10];
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outer : inner;
            double angle = Math.toRadians(i * 36 - 90);
            xs[i] = (int)(cx + r * Math.cos(angle));
        }
        return xs;
    }

    private int[] starY(int cy, double outer, double inner) {
        int[] ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outer : inner;
            double angle = Math.toRadians(i * 36 - 90);
            ys[i] = (int)(cy + r * Math.sin(angle));
        }
        return ys;
    }

    private Color bandColor(Band b) {
        return switch (b) {
            case ACTIONABLE  -> C_HIGH;
            case CLEAR       -> C_GOOD;
            case VAGUE       -> C_MID;
            case CONFUSING,
                 INCOHERENT  -> C_LOW;
            case NO_CONTENT  -> TEXT_MUT;
        };
    }

    private String bandLabel(Band b) {
        return switch (b) {
            case ACTIONABLE  -> "Actionable";
            case CLEAR       -> "Clear";
            case VAGUE       -> "Vague";
            case CONFUSING   -> "Confusing";
            case INCOHERENT  -> "Incoherent";
            case NO_CONTENT  -> "No content";
        };
    }

    private String bandRange(Band b) {
        return switch (b) {
            case ACTIONABLE -> "9-10";
            case CLEAR      -> "7-8";
            case VAGUE      -> "5-6";
            case CONFUSING  -> "3-4";
            case INCOHERENT -> "1-2";
            case NO_CONTENT -> "0";
        };
    }

    private Color metricAccent(Metric m) {
        return switch (m) {
            case BEST        -> C_HIGH;
            case WORST       -> C_LOW;
            default          -> ACCENT;
        };
    }

    private String metricLabel(Metric m) {
        return switch (m) {
            case SESSIONS    -> "Sessions";
            case AVERAGE     -> "Average";
            case BEST        -> "Best score";
            case WORST       -> "Worst score";
            case RATING_TIER -> "Rating tier";
        };
    }

    private BufferedImage createImage(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private Graphics2D setup(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private byte[] toPng(BufferedImage img) throws Exception {
        var baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }
}