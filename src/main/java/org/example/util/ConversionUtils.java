package org.example.util;

import java.util.List;

public class ConversionUtils {
    public static double[] toDoubleArray(List<Double> list) {
        double[] array = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
