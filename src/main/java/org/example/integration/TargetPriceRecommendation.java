package org.example.integration;

import java.io.IOException;

public interface TargetPriceRecommendation {
    int calculateRecommendedTargetPrice(String marketHashName, Double floatMin, Double floatMax) throws IOException;
    int calculateRecommendedTargetPrice(String marketHashName) throws IOException;

}
