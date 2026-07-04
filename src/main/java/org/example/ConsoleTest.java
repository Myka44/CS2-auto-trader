package org.example;

import org.example.integration.WhiteMarket.WhiteMarketClient;
import org.example.integration.csfloat.CSFloatClient;
import org.example.integration.dmarket.DMarketClient;
import org.example.repository.*;

import java.io.IOException;

public class ConsoleTest {
    // --- Repositories ---


    public static void main(String[] args) throws IOException {
        SkinRepository skinRepository = new SkinRepository();
        TargetRepository targetRepository = new TargetRepository();
        AlertRepository alertRepository = new AlertRepository();
        PriceSnapshotRepository priceSnapshotRepository = new PriceSnapshotRepository();
        ApiConfigRepository apiConfigRepository = new ApiConfigRepository();



        // --- Platform clients ---
        //DMarketClient dmarketClient = new DMarketClient(apiConfigRepository);
        CSFloatClient csFloatClient = new CSFloatClient(apiConfigRepository, skinRepository);
        //WhiteMarketClient whiteMarketClient = new WhiteMarketClient(apiConfigRepository, skinRepository);

        /*
        System.out.println("lowest offer (cents): " + whiteMarketClient.getLowestOfferPriceCents("AWP | Dragon Lore (Factory New)", 0.0, 0.01));
        System.out.println("highest target (cents): " + whiteMarketClient.getHighestPublicTargetPriceCents("AWP | Dragon Lore (Factory New)", 0.0, 0.07));
        System.out.println("public targets: " + whiteMarketClient.getPublicTargets("AWP | Dragon Lore (Factory New)"));

         */

        //System.out.println("recommended price (cents): " + csFloatClient.calculateRecommendedTargetPrice("M4A4 | Polysoup (Factory New)", 0.0, 0.07));
        //System.out.println("recommended price (cents): " + csFloatClient.calculateRecommendedTargetPrice("AUG | Lil' Pig (Factory New)", 0.05, 0.07));
        System.out.println("recommended price (cents): " + csFloatClient.calculateRecommendedTargetPrice("CZ75-Auto | Syndicate (Factory New)", 0.05, 0.07));
    }

}