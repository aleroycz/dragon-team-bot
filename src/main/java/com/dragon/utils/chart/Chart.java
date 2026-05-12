package com.dragon.utils.chart;

/*
---------------------------------------------------------------------------------
File Name : Chart.java

Developer : vakea
Email     : vakea@fluffici.eu
Real Name : Alex Guy Yann Le Roy

Date Created  : 02/06/2024
Last Modified : 02/06/2024

---------------------------------------------------------------------------------
*/



/*
                            LICENCE PRO PROPRIETÁRNÍ SOFTWARE
            Verze 1, Organizace: Fluffici, z.s. IČO: 19786077, Rok: 2024
                            PODMÍNKY PRO POUŽÍVÁNÍ

    a. Použití: Software lze používat pouze podle přiložené dokumentace.
    b. Omezení reprodukce: Kopírování softwaru bez povolení je zakázáno.
    c. Omezení distribuce: Distribuce je povolena jen přes autorizované kanály.
    d. Oprávněné kanály: Distribuci určuje výhradně držitel autorských práv.
    e. Nepovolené šíření: Šíření mimo povolené podmínky je zakázáno.
    f. Právní důsledky: Porušení podmínek může vést k právním krokům.
    g. Omezení úprav: Úpravy softwaru jsou zakázány bez povolení.
    h. Rozsah oprávněných úprav: Rozsah úprav určuje držitel autorských práv.
    i. Distribuce upravených verzí: Distribuce upravených verzí je povolena jen s povolením.
    j. Zachování autorských atribucí: Kopie musí obsahovat všechny autorské atribuce.
    k. Zodpovědnost za úpravy: Držitel autorských práv nenese odpovědnost za úpravy.

    Celý text licence je dostupný na adrese:
    https://autumn.fluffici.eu/attachments/xUiAJbvhZaXW3QIiLMFFbVL7g7nPC2nfX7v393UjEn/fluffici_software_license_cz.pdf
*/


import com.dragon.utils.chart.dataset.CombinedDataset;
import com.dragon.utils.chart.impl.Category;
import com.dragon.utils.chart.impl.ChartDetails;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("ALL")
public class Chart {
    protected final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
    protected final AtomicInteger cId = new AtomicInteger(0);

    /**
     * Creates a dataset for generating a chart.
     *
     * @param categories a List of Category objects representing the data to be added to the dataset
     */
    public void createDataset(List<Category> categories) { categories.forEach(category -> dataset.addValue(cId.incrementAndGet(), category.getName(), category.getValue())); }

    /**
     * Creates a dataset for adding data to a chart.
     *
     * @param rowKey the row key representing the data category
     * @param datasets a Map of LocalDate and Long representing the dataset to be added to the chart
     */
    public void createDataset(String rowKey, Map<LocalDate, Long> datasets) {
        dataset.clear();

        datasets.forEach((date, count) -> dataset.addValue(count, rowKey, date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("cs"))));
    }

    /**
     * Creates a dataset for adding data to a chart based on temporal values.
     *
     * @param rowKey   the row key representing the data category
     * @param datasets a Map of temporal values and their corresponding counts
     * @param <T>      a type parameter representing a subclass of Temporal
     * @throws InvalidDatasetException if the date format or type is not supported
     */
    public <T extends Temporal> void createTemporalDataset(String rowKey, Map<T, Long> datasets) {
        dataset.clear();

        datasets.forEach((dateHandle, count) -> {
            if (dateHandle instanceof LocalDate daily) {
                dataset.addValue(count, rowKey, daily.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("cs")));
            } else if (dateHandle instanceof YearMonth yearMonth) {
                dataset.addValue(count, rowKey, yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("cs")));
            } else if (dateHandle instanceof Year year) {
                dataset.addValue(count, rowKey, String.valueOf(year.getValue()));
            } else {
                throw new IllegalArgumentException("Wrong date format / type -> " + dateHandle.getClass().getCanonicalName() + ": is not allowed.");
            }
        });
    }

    /**
     * Creates a dataset for adding data to a chart based on temporal values.
     *
     * @param rowKey   the row key representing the data category
     * @param datasets a Map of temporal values and their corresponding counts
     * @param <T>      a type parameter representing a subclass of Temporal
     * @throws InvalidDatasetException if the date format or type is not supported
     */
    public <T extends Temporal> void createTemporalDoubleDataset(String rowKey, Map<T, Double> datasets) {
        dataset.clear();

        datasets.forEach((dateHandle, count) -> {
            if (dateHandle instanceof LocalDate daily) {
                dataset.addValue((double) count, rowKey, daily.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("cs")));
            } else if (dateHandle instanceof YearMonth yearMonth) {
                dataset.addValue((double) count, rowKey, yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("cs")));
            } else if (dateHandle instanceof Year year) {
                dataset.addValue((double) count, rowKey, String.valueOf(year.getValue()));
            } else {
                throw new IllegalArgumentException("Wrong date format / type -> " + dateHandle.getClass().getCanonicalName() + ": is not allowed.");
            }
        });
    }



    /**
     * Creates combined datasets based on the provided CombinedDataset object.
     * Each dataset is added to the chart using the appropriate date format and category label.
     *
     * @param combinedDataset the CombinedDataset object containing the datasets to be combined
     * @throws IllegalArgumentException if the number of datasets is less than 2
     * @throws InvalidDatasetException if the date format or type is not supported
     */
    public <T extends Temporal> void createCombinedDatasets(CombinedDataset<T> combinedDataset) {
        dataset.clear();

        if (combinedDataset.getDatasets().size() < 2)
            throw new IllegalArgumentException("At least 2 dataset(s) is needed to use 'createCombinedDatasets'.");

        combinedDataset.getDatasets().forEach(((rowKey, map) -> map.forEach((dateHandle, count) -> {
            if (dateHandle instanceof LocalDate daily) {
                dataset.addValue(count, rowKey, daily.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("cs")));
            } else if (dateHandle instanceof YearMonth yearMonth) {
                dataset.addValue(count, rowKey, yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("cs")));
            } else if (dateHandle instanceof Year year) {
                dataset.addValue(count, rowKey, String.valueOf(year.getValue()));
            } else {
                throw new IllegalArgumentException("Wrong date format / type -> " + dateHandle.getClass().getCanonicalName() + ": is not allowed.");
            }
        })));
    }

    /**
     * Creates a JFreeChart object with the provided chart details and category dataset.
     *
     * @param chartDetails the details of the chart, including the title, x-axis title, y-axis title, and orientation
     * @return a JFreeChart object representing the created chart
     */
    protected JFreeChart createChart(ChartDetails chartDetails) {
        JFreeChart chart = ChartFactory.createLineChart(
                chartDetails.getTitle().toUpperCase(),
                chartDetails.getXAxisTitle().toUpperCase(),
                chartDetails.getYAxisTitle().toUpperCase(),
                this.dataset,
                chartDetails.getOrientation(),
                true,
                true,
                false
        );

        this.applyDarkTheme(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        LineAndShapeRenderer renderer = (LineAndShapeRenderer ) plot.getRenderer();

        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", java.text.NumberFormat.getInstance()));
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BASELINE_CENTER));

        return chart;
    }

    /**
     * Apply a dark theme to the given JFreeChart object.
     *
     * @param chart the JFreeChart object to apply the dark theme to
     */
    @SneakyThrows
    private void applyDarkTheme(JFreeChart chart) {
        StandardChartTheme theme = (StandardChartTheme) StandardChartTheme.createJFreeTheme();

        // Dark theme customization
        Color backgroundColor = Color.decode("#0E1414"); // Dark background color
        Color innerColor = Color.decode("#1a1c1c"); // Dark background color

        Color textColor = Color.decode("#FFFFFF"); // Light text color
        Color gridColor = Color.decode("#605858"); // Gridline color
        Color lineColor = Color.decode("#FF002E"); // Line color

        theme.setTitlePaint(textColor);

        // Load Lexend Deca font
        Font lexendDecaFont;
        InputStream is = getClass().getResourceAsStream("/fonts/lexend.ttf");
        if (is != null)
            lexendDecaFont = Font.createFont(Font.TRUETYPE_FONT, is);
        else
            lexendDecaFont = new Font("SansSerif", Font.BOLD, 16);

        theme.setExtraLargeFont(lexendDecaFont.deriveFont(Font.BOLD, 16));
        theme.setLargeFont(lexendDecaFont.deriveFont(Font.BOLD, 14));
        theme.setRegularFont(lexendDecaFont.deriveFont(Font.PLAIN, 12));

        theme.setRangeGridlinePaint(gridColor);
        theme.setPlotBackgroundPaint(innerColor);
        theme.setChartBackgroundPaint(backgroundColor);
        theme.setAxisLabelPaint(textColor);
        theme.setTickLabelPaint(textColor);

        theme.setLegendBackgroundPaint(backgroundColor);
        theme.setLegendItemPaint(textColor);

        theme.apply(chart);

        // Additional plot customizations
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setOutlinePaint(null);
        plot.setBackgroundPaint(innerColor);
        plot.setDomainGridlinePaint(gridColor);
        plot.setRangeGridlinePaint(gridColor);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        plot.setDomainGridlinesVisible(true); // Display domain gridlines
        plot.setRangeGridlinesVisible(true); // Display range gridlines

        // Line renderer customization
        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, lineColor);
        renderer.setSeriesStroke(0, new BasicStroke(8.0f)); // Increased line thickness
        renderer.setSeriesShapesVisible(0, false); // No shapes
        renderer.setDefaultShapesFilled(false); // No filled shapes
        renderer.setDefaultShapesVisible(true); // Show data points
        renderer.setDefaultItemLabelPaint(Color.WHITE);

        renderer.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER)); // Adjust label position
        renderer.setDefaultNegativeItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.TOP_CENTER)); // Adjust label position

        RenderingHints renderingHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        chart.getRenderingHints().put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        chart.getRenderingHints().put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        chart.getRenderingHints().put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * Saves the given JFreeChart object as a PNG image file.
     *
     * @param chart the JFreeChart object to be saved
     * @return a Pair object containing a Boolean indicating the success of the operation and a FileUpload object
     * representing the saved chart file
     */
    public Pair<Boolean, Pair<String, FileUpload>> saveChartAsPNG(ChartDetails chart) {
       try {
           String chartId = "chart.png";
           File savedChart = new File(chartId);
           ChartUtils.saveChartAsPNG(savedChart, this.createChart(chart), chart.getWidth(), chart.getHeight());

           return Pair.of(true, Pair.of(chartId, FileUpload.fromData(savedChart, chartId)));
       } catch (Exception e) {
           return Pair.of(false, null);
       }
    }
}
