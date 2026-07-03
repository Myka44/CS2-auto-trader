package org.example.integration;

public interface TargetPriceRecommendation {
    public int calculateRecommendedTargetPrice(String marketHashName, double floatFrom, double floatTo);

}
