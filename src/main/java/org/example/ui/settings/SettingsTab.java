package org.example.ui.settings;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.AppConfig;
import org.example.model.ApiConfig;
import org.example.model.Platform;
import org.example.repository.ApiConfigRepository;
import org.example.service.SchedulerService;

/** API credential entry + scheduler interval configuration. Everything saved here goes straight into SQLite. */
public class SettingsTab {

    private final ApiConfigRepository apiConfigRepository;
    private final SchedulerService schedulerService;

    public SettingsTab(ApiConfigRepository apiConfigRepository, SchedulerService schedulerService) {
        this.apiConfigRepository = apiConfigRepository;
        this.schedulerService = schedulerService;
    }

    public Node getView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(15));

        root.getChildren().add(buildDMarketSection());
        root.getChildren().add(new Separator());
        root.getChildren().add(buildCSFloatSection());
        root.getChildren().add(new Separator());
        root.getChildren().add(buildWhiteMarketSection());
        root.getChildren().add(new Separator());
        root.getChildren().add(buildSchedulerSection());
        root.getChildren().add(new Separator());
        root.getChildren().add(buildInfoSection());

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private Node buildDMarketSection() {
        ApiConfig existing = apiConfigRepository.findByPlatform(Platform.DMARKET).orElse(new ApiConfig(Platform.DMARKET, "", "", "", true));

        Label title = new Label("DMarket API Credentials");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        TextField publicKeyField = new TextField(existing.getPublicKey());
        publicKeyField.setPromptText("Public Key (X-Api-Key)");

        PasswordField secretKeyField = new PasswordField();
        secretKeyField.setText(existing.getSecretKey());
        secretKeyField.setPromptText("Secret Key (hex, used for Ed25519 signing)");

        PasswordField jwtTokenField = new PasswordField();
        jwtTokenField.setText(existing.getJwtToken());
        jwtTokenField.setPromptText("JWT Token (used for buy orders)");

        CheckBox enabledBox = new CheckBox("Enabled");
        enabledBox.setSelected(existing.isEnabled());

        grid.addRow(0, new Label("Public Key:"), publicKeyField);
        grid.addRow(1, new Label("Secret Key:"), secretKeyField);
        grid.addRow(2, new Label("JWT Token:"), jwtTokenField);
        grid.addRow(3, enabledBox);

        Button saveBtn = new Button("Save DMarket Credentials");
        Label status = new Label();
        saveBtn.setOnAction(e -> {
            ApiConfig cfg = new ApiConfig(Platform.DMARKET, publicKeyField.getText(), secretKeyField.getText(), jwtTokenField.getText(), enabledBox.isSelected());
            apiConfigRepository.upsert(cfg);
            status.setText("Saved.");
        });

        VBox box = new VBox(8, title,
                new Label("Get these from your DMarket account API settings page. The secret key never leaves your local database."),
                grid, saveBtn, status);
        return box;
    }

    private Node buildCSFloatSection() {
        ApiConfig existing = apiConfigRepository.findByPlatform(Platform.CSFLOAT).orElse(new ApiConfig(Platform.CSFLOAT, "", "", "", true));

        Label title = new Label("CSFloat API Credentials");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        TextField apiKeyField = new TextField(existing.getPublicKey());
        apiKeyField.setPromptText("API Key (from csfloat.com/profile -> Developer tab)");
        TextField sessionCookieField = new TextField(existing.getJwtToken());
        sessionCookieField.setText(existing.getJwtToken());
        sessionCookieField.setPromptText("Session cookie (only needed for buy orders -- see note below)");
        CheckBox enabledBox = new CheckBox("Enabled");
        enabledBox.setSelected(existing.isEnabled());

        grid.addRow(0, new Label("API Key:"), apiKeyField);
        grid.addRow(1, new Label("Session Cookie:"), sessionCookieField);
        grid.addRow(2, enabledBox);

        Button saveBtn = new Button("Save CSFloat Credentials");
        Label status = new Label();
        saveBtn.setOnAction(e -> {
            ApiConfig cfg = new ApiConfig(Platform.CSFLOAT, apiKeyField.getText(), null, sessionCookieField.getText(), enabledBox.isSelected());
            apiConfigRepository.upsert(cfg);
            status.setText("Saved.");
        });

        Label note = new Label(
                "Note: CSFloat's documented API key only covers listings (sell offers). The buy-order " +
                "endpoints used for auto-targets are undocumented and currently require your logged-in " +
                "session cookie instead. Get it via your browser's dev tools (Application/Storage -> " +
                "Cookies on csfloat.com) -- this may break if CSFloat changes their session handling.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: -fx-accent; -fx-font-size: 11px;");

        VBox box = new VBox(8, title, grid, saveBtn, status, note);
        return box;
    }

    private Node buildWhiteMarketSection() {
        ApiConfig existing = apiConfigRepository.findByPlatform(Platform.WHITEMARKET).orElse(new ApiConfig(Platform.WHITEMARKET, "", "", "", true));

        Label title = new Label("WhiteMarket API Credentials");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        TextField apiKeyField = new TextField(existing.getSecretKey());
        apiKeyField.setText(existing.getSecretKey());
        apiKeyField.setPromptText("API Key (from white.market.com/profile -> profile settings)");
        PasswordField sessionCookieField = new PasswordField();
        sessionCookieField.setText(existing.getJwtToken());
        sessionCookieField.setPromptText("JWT token");
        CheckBox enabledBox = new CheckBox("Enabled");
        enabledBox.setSelected(existing.isEnabled());

        grid.addRow(0, new Label("API Key:"), apiKeyField);
        grid.addRow(1, new Label("JWT token:"), sessionCookieField);
        grid.addRow(2, enabledBox);

        Button saveBtn = new Button("Save WhiteMarket Credentials");
        Label status = new Label();
        saveBtn.setOnAction(e -> {
            ApiConfig cfg = new ApiConfig(Platform.WHITEMARKET, null, apiKeyField.getText(), sessionCookieField.getText(), enabledBox.isSelected());
            apiConfigRepository.upsert(cfg);
            status.setText("Saved.");
        });

        VBox box = new VBox(8, title, grid, saveBtn, status);
        return box;
    }



    private Node buildSchedulerSection() {
        Label title = new Label("Background Polling");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        TextField targetIntervalField = new TextField(String.valueOf(AppConfig.DEFAULT_TARGET_POLL_INTERVAL_MINUTES));
        TextField alertIntervalField = new TextField(String.valueOf(AppConfig.DEFAULT_ALERT_POLL_INTERVAL_MINUTES));

        grid.addRow(0, new Label("Target auto-adjust interval (minutes):"), targetIntervalField);
        grid.addRow(1, new Label("Alert check interval (minutes):"), alertIntervalField);

        Button applyBtn = new Button("Apply Intervals");
        Label status = new Label();
        applyBtn.setOnAction(e -> {
            try {
                int targetMin = Integer.parseInt(targetIntervalField.getText().trim());
                int alertMin = Integer.parseInt(alertIntervalField.getText().trim());
                schedulerService.setTargetIntervalMinutes(targetMin);
                schedulerService.setAlertIntervalMinutes(alertMin);
                status.setText("Applied. Takes effect on the next scheduled cycle (restart the app to apply immediately to the running schedule).");
            } catch (NumberFormatException ex) {
                status.setText("Please enter valid numbers.");
            }
        });

        return new VBox(8, title, grid, applyBtn, status);
    }

    private Node buildInfoSection() {
        Label title = new Label("Local Data");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label dbPath = new Label("Database location: " + AppConfig.DB_PATH);
        Label logPath = new Label("Logs location: " + AppConfig.LOG_DIR);
        return new VBox(6, title, dbPath, logPath);
    }
}
