package org.example.util;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import java.util.List;

public class MathUtils {
    public static List<Double> removeOutliersIQR(List<Double> data, double k){
        DescriptiveStatistics stats = new DescriptiveStatistics(ConversionUtils.toDoubleArray(data));

        double q1 = stats.getPercentile(25);
        double q3 = stats.getPercentile(75);
        double iqr = q3 - q1;

        double lowerBound = q1 - k * iqr;
        double upperBound = q3 + k * iqr;

        return data.stream()
                .filter(x -> x >= lowerBound && x <= upperBound)
                .toList();
    }
}
