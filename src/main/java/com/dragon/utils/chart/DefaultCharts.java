package com.dragon.utils.chart;

/*
---------------------------------------------------------------------------------
File Name : DefaultCharts.java

Developer : vakea
Email     : vakea@fluffici.eu
Real Name : Alex Guy Yann Le Roy

Date Created  : 02/06/2024
Last Modified : 02/06/2024

---------------------------------------------------------------------------------
*/



import com.dragon.utils.chart.impl.ChartDetails;

import org.jfree.chart.plot.PlotOrientation;

public class DefaultCharts {
    private final Chart messageChart = new Chart();
    private final ChartDetails details = new ChartDetails();

    public DefaultCharts() {
        this.details.setOrientation(PlotOrientation.VERTICAL);
        this.details.setWidth(1135);
        this.details.setHeight(600);
    }
}
