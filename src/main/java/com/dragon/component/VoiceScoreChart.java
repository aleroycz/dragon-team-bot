package com.dragon.component;


import com.dragon.entity.VoiceScore;

import com.dragon.utils.chart.Chart;
import com.dragon.utils.chart.dataset.DatasetUtil;
import com.dragon.utils.chart.dataset.TemporalBasis;
import com.dragon.utils.chart.impl.ChartDetails;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Year;
import java.util.List;
import java.util.Map;

/**
 * Dedicated chart renderer for VoiceScore trend data.
 *
 * Extends the base {@link Chart} but overrides theme application to use
 * the Dragon Bot palette and the score-band colouring logic (0–10 axis,
 * coloured data point markers per band).
 */
@Component
public class VoiceScoreChart extends Chart {

    // ── Dragon Bot palette ────────────────────────────────────────────────────
    private static final Color BG_OUTER   = new Color(0x0E0F14);
    private static final Color BG_PLOT    = new Color(0x15161D);
    private static final Color GRID       = new Color(0x2A2B33);
    private static final Color TEXT       = new Color(0xF2F3F5);
    private static final Color TEXT_MUTED = new Color(0x72747D);
    private static final Color LINE       = new Color(0x5865F2);

    // Band colours matching scoring table
    private static final Color C_HIGH   = new Color(0x57F287); // 9–10
    private static final Color C_GOOD   = new Color(0x5865F2); // 7–8
    private static final Color C_MID    = new Color(0xFEE75C); // 5–6
    private static final Color C_LOW    = new Color(0xED4245); // 3–4 and below

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a score-trend chart for the given user and temporal basis.
     *
     * @param username      display name used in the chart title
     * @param scores        full ordered list of VoiceScore records for the user
     * @param temporalBasis aggregation granularity
     * @return JDA FileUpload pair ready to attach to a Discord message
     */
    public Pair<Boolean, Pair<String, FileUpload>> getScoreTrendChart(
            String username,
            List<VoiceScore> scores,
            TemporalBasis temporalBasis
    ) {
        dataset.clear();

        // VoiceScore.getScore() returns int 0–10; we cast to double for the
        // average aggregation so the trend line reflects real mean per period.
        Map<LocalDate, Double> daily = DatasetUtil.ratioToDailyBasis(
                scores,
                s -> true,   // all sessions contribute to numerator
                s -> false   // denominator trick: we handle averaging below
        );

        // Use the temporal double dataset builder directly so we keep decimals.
        // We rebuild a simple date→average map manually to avoid coupling to
        // the ratio helper's semantics.
        switch (temporalBasis) {
            case DAILY -> {
                Map<LocalDate, Double> avg = averageDailyScores(scores);
                dataset.clear();
                avg.forEach((date, v) ->
                        dataset.addValue(v, "Score", date.toString())
                );
            }
            case MONTHLY -> {
                Map<YearMonth, Double> avg = averageMonthlyScores(scores);
                dataset.clear();
                avg.forEach((ym, v) ->
                        dataset.addValue(v, "Score", ym.toString())
                );
            }
            case YEARLY -> {
                Map<Year, Double> avg = averageYearlyScores(scores);
                dataset.clear();
                avg.forEach((y, v) ->
                        dataset.addValue(v, "Score", y.toString())
                );
            }
            case TRIMESTER -> {
                // Re-use monthly grouping but map to trimester label
                Map<YearMonth, Double> avg = averageMonthlyScores(scores);
                dataset.clear();
                avg.forEach((ym, v) ->
                        dataset.addValue(v, "Score", trimesterLabel(ym))
                );
            }
        }

        ChartDetails details = new ChartDetails();
        details.setTitle("VOICE SCORE TREND — " + username.toUpperCase());
        details.setXAxisTitle("DATE");
        details.setYAxisTitle("SCORE (0–10)");
        details.setOrientation(PlotOrientation.VERTICAL);
        details.setWidth(1135);
        details.setHeight(600);

        return saveChartAsPNG(details);
    }

    @Override
    protected JFreeChart createChart(ChartDetails chartDetails) {
        JFreeChart chart = ChartFactory.createLineChart(
                chartDetails.getTitle(),
                chartDetails.getXAxisTitle(),
                chartDetails.getYAxisTitle(),
                this.dataset,
                chartDetails.getOrientation(),
                false,  // no legend — single series
                true,
                false
        );

        applyDragonTheme(chart);
        return chart;
    }

    private void applyDragonTheme(JFreeChart chart) {
        StandardChartTheme theme = (StandardChartTheme) StandardChartTheme.createJFreeTheme();

        Font font;
        try (InputStream is = getClass().getResourceAsStream("/fonts/lexend.ttf")) {
            font = is != null
                    ? Font.createFont(Font.TRUETYPE_FONT, is)
                    : new Font("SansSerif", Font.PLAIN, 12);
        } catch (Exception e) {
            font = new Font("SansSerif", Font.PLAIN, 12);
        }

        theme.setExtraLargeFont(font.deriveFont(Font.BOLD, 16f));
        theme.setLargeFont(font.deriveFont(Font.BOLD, 14f));
        theme.setRegularFont(font.deriveFont(Font.PLAIN, 12f));

        theme.setTitlePaint(TEXT);
        theme.setAxisLabelPaint(TEXT_MUTED);
        theme.setTickLabelPaint(TEXT_MUTED);
        theme.setChartBackgroundPaint(BG_OUTER);
        theme.setPlotBackgroundPaint(BG_PLOT);
        theme.setRangeGridlinePaint(GRID);
        theme.setLegendBackgroundPaint(BG_OUTER);
        theme.setLegendItemPaint(TEXT);

        theme.apply(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setOutlinePaint(null);
        plot.setBackgroundPaint(BG_PLOT);
        plot.setDomainGridlinePaint(GRID);
        plot.setRangeGridlinePaint(GRID);
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        plot.setAxisOffset(new RectangleInsets(8, 8, 8, 8));

        // Y-axis fixed 0–10 with integer ticks
        NumberAxis rangeAxis = new NumberAxis(plot.getRangeAxis().getLabel());
        rangeAxis.setRange(0.0, 10.0);
        rangeAxis.setTickUnit(new NumberTickUnit(1));
        rangeAxis.setTickLabelPaint(TEXT_MUTED);
        rangeAxis.setLabelPaint(TEXT_MUTED);
        rangeAxis.setAxisLinePaint(GRID);
        plot.setRangeAxis(rangeAxis);

        // Renderer
        LineAndShapeRenderer r = (LineAndShapeRenderer) plot.getRenderer();
        r.setSeriesPaint(0, LINE);
        r.setSeriesStroke(0, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        r.setSeriesShapesVisible(0, true);
        r.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10));

        // Colour each data point marker individually by score band
        int itemCount = dataset.getColumnCount();
        for (int i = 0; i < itemCount; i++) {
            Number val = dataset.getValue(0, i);
            if (val != null) {
                Color dotColor = bandColor(val.doubleValue());
                r.setSeriesItemLabelPaint(0, dotColor);
            }
        }

        r.setDefaultItemLabelGenerator(
                new StandardCategoryItemLabelGenerator("{2}", new java.text.DecimalFormat("0.0"))
        );
        r.setDefaultItemLabelsVisible(true);
        r.setDefaultPositiveItemLabelPosition(
                new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER)
        );
        r.setDefaultItemLabelPaint(TEXT);

        chart.getRenderingHints().put(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        chart.getRenderingHints().put(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
        chart.getRenderingHints().put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    // ── Aggregation helpers ───────────────────────────────────────────────────

    private Map<LocalDate, Double> averageDailyScores(List<VoiceScore> scores) {
        return scores.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        s -> s.getRecordedAt().toLocalDate(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.averagingDouble(VoiceScore::getScore)
                )
        );
    }

    private Map<YearMonth, Double> averageMonthlyScores(List<VoiceScore> scores) {
        return scores.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        s -> YearMonth.from(s.getRecordedAt()),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.averagingDouble(VoiceScore::getScore)
                )
        );
    }

    private Map<Year, Double> averageYearlyScores(List<VoiceScore> scores) {
        return scores.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        s -> Year.from(s.getRecordedAt()),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.averagingDouble(VoiceScore::getScore)
                )
        );
    }

    private String trimesterLabel(YearMonth ym) {
        int q = (ym.getMonthValue() - 1) / 3 + 1;
        return "Q" + q + " " + ym.getYear();
    }

    private Color bandColor(double score) {
        if (score >= 9) return C_HIGH;
        if (score >= 7) return C_GOOD;
        if (score >= 5) return C_MID;
        return C_LOW;
    }
}