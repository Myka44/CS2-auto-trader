package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.example.db.Database;
import org.example.integration.WhiteMarket.WhiteMarketClient;
import org.example.integration.csfloat.CSFloatClient;
import org.example.integration.dmarket.DMarketClient;
import org.example.repository.AlertRepository;
import org.example.repository.ApiConfigRepository;
import org.example.repository.PriceSnapshotRepository;
import org.example.repository.SkinRepository;
import org.example.repository.TargetRepository;
import org.example.service.AlertService;
import org.example.service.NotificationService;
import org.example.service.PriceAggregator;
import org.example.service.SchedulerService;
import org.example.service.SkinDataService;
import org.example.service.TargetService;
import org.example.ui.alerts.AlertsTab;
import org.example.ui.pricemonitor.PriceMonitorTab;
import org.example.ui.settings.SettingsTab;
import org.example.ui.skinbrowser.SkinBrowserTab;
import org.example.ui.targets.TargetsTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Application entry point. Wires repositories -> platform clients -> services
 * -> UI tabs, starts the background scheduler, and shows the main window.
 *
 * This is meant to run as a long-lived process on a home PC: closing the
 * window does not need to stop the scheduler (see stop()) -- the request
 * said "I already have my main PC as a server I'm constantly running", so
 * background polling continues to run as long as the JVM is alive,
 * independent of window focus/minimization.
 */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private SchedulerService schedulerService;

    @Override
    public void init() {
        AppConfig.ensureAppHomeExists();
        Database.initSchema();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        // --- Repositories ---
        SkinRepository skinRepository = new SkinRepository();
        TargetRepository targetRepository = new TargetRepository();
        AlertRepository alertRepository = new AlertRepository();
        PriceSnapshotRepository priceSnapshotRepository = new PriceSnapshotRepository();
        ApiConfigRepository apiConfigRepository = new ApiConfigRepository();

        // --- Seed catalog (first run only) ---
        new SkinDataService(skinRepository).loadSeedIfEmpty();

        // --- Platform clients ---
        DMarketClient dmarketClient = new DMarketClient(apiConfigRepository);
        CSFloatClient csFloatClient = new CSFloatClient(apiConfigRepository, skinRepository);
        WhiteMarketClient whiteMarketClient = new WhiteMarketClient(apiConfigRepository, skinRepository);

        PriceAggregator priceAggregator = new PriceAggregator(
                PriceAggregator.mapOfTradingPlatform(dmarketClient, csFloatClient, whiteMarketClient),
                PriceAggregator.mapOfTargetPriceRecommender(csFloatClient)

        );

        // --- Services ---
        NotificationService notificationService = new NotificationService();
        notificationService.initTrayIfSupported();

        TargetService targetService = new TargetService(targetRepository, skinRepository, priceSnapshotRepository, priceAggregator);
        AlertService alertService = new AlertService(alertRepository, skinRepository, priceSnapshotRepository, priceAggregator, notificationService);

        schedulerService = new SchedulerService(targetService, alertService);
        schedulerService.start();

        // --- UI ---
        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(buildTab("Targets", new TargetsTab(targetService, skinRepository, schedulerService).getView()));
        tabPane.getTabs().add(buildTab("Alerts", new AlertsTab(alertService, skinRepository, schedulerService).getView()));
        tabPane.getTabs().add(buildTab("Skin Browser", new SkinBrowserTab(skinRepository).getView()));
        tabPane.getTabs().add(buildTab("Price Monitor", new PriceMonitorTab(skinRepository, priceSnapshotRepository).getView()));
        tabPane.getTabs().add(buildTab("Settings", new SettingsTab(apiConfigRepository, schedulerService).getView()));
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        notificationService.setUiToastHandler(msg -> log.info("Toast: {}", msg));

        Scene scene = new Scene(tabPane, 1200, 800);
        String css = App.class.getResource("/css/app.css") != null
                ? App.class.getResource("/css/app.css").toExternalForm() : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        primaryStage.setTitle("CS2 Auto Targets");
        primaryStage.setScene(scene);

        // Keep the JVM (and the background scheduler) alive even if the
        // window is closed, matching "I run this on my server PC constantly".
        // Re-opening the window would require a tray-icon "Show" action in a
        // future iteration; for now closing simply stops rendering the UI
        // while target/alert polling keeps running until the process is killed.
        Platform.setImplicitExit(false);
        primaryStage.setOnCloseRequest(event -> {
            log.info("Window closed -- background polling continues to run in this process.");
        });

        primaryStage.show();
    }

    @Override
    public void stop() {
        if (schedulerService != null) {
            schedulerService.stop();
        }
    }

    private Tab buildTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title);
        tab.setContent(content);
        return tab;
    }

    public static void main(String[] args) {
        launch(args);
    }
}