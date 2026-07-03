package org.example.ui.alerts;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.example.model.Alert.Direction;
import org.example.model.Platform;
import org.example.model.SkinCatalogEntry;
import org.example.repository.SkinRepository;
import org.example.service.AlertService;
import org.example.service.SchedulerService;
import org.example.util.WearCondition;

import java.util.List;
import java.util.Optional;

/** Notification-only price watches: same shape as Targets, minus any platform order placement. */
public class AlertsTab {

    private final AlertService alertService;
    private final SkinRepository skinRepository;
    private final SchedulerService schedulerService;

    private final TableView<org.example.model.Alert> table = new TableView<>();
    private final ObservableList<org.example.model.Alert> data = FXCollections.observableArrayList();

    public AlertsTab(AlertService alertService, SkinRepository skinRepository, SchedulerService schedulerService) {
        this.alertService = alertService;
        this.skinRepository = skinRepository;
        this.schedulerService = schedulerService;
        buildTable();
        refresh();
    }

    public Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox toolbar = new HBox(8);
        Button addBtn = new Button("New Alert");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button toggleBtn = new Button("Enable/Disable");
        Button refreshBtn = new Button("Refresh");
        Button checkNowBtn = new Button("Check Now");
        toolbar.getChildren().addAll(addBtn, editBtn, deleteBtn, toggleBtn, refreshBtn, checkNowBtn);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        addBtn.setOnAction(e -> openEditor(null));
        editBtn.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) openEditor(selected);
        });
        deleteBtn.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this alert?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    alertService.deleteAlert(selected.getId());
                    refresh();
                }
            });
        });
        toggleBtn.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            selected.setActive(!selected.isActive());
            alertService.updateAlert(selected);
            refresh();
        });
        refreshBtn.setOnAction(e -> refresh());
        checkNowBtn.setOnAction(e -> {
            schedulerService.runAlertsNow();
            new Alert(Alert.AlertType.INFORMATION, "Alert check triggered in the background. Refresh in a few seconds to see updates.").showAndWait();
        });

        root.setTop(toolbar);
        root.setCenter(table);
        return root;
    }

    private void buildTable() {
        TableColumn<org.example.model.Alert, String> skinCol = new TableColumn<>("Skin");
        skinCol.setCellValueFactory(new PropertyValueFactory<>("skinMarketHashName"));
        skinCol.setPrefWidth(280);

        TableColumn<org.example.model.Alert, Platform> platformCol = new TableColumn<>("Platform");
        platformCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPlatform()));
        platformCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Platform item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? "Any" : item.displayName()));
            }
        });
        platformCol.setPrefWidth(90);

        TableColumn<org.example.model.Alert, Number> thresholdCol = new TableColumn<>("Threshold");
        thresholdCol.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().getThresholdUsdCents() / 100.0));
        thresholdCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item.doubleValue()));
            }
        });
        thresholdCol.setPrefWidth(90);

        TableColumn<org.example.model.Alert, Direction> directionCol = new TableColumn<>("Direction");
        directionCol.setCellValueFactory(new PropertyValueFactory<>("direction"));
        directionCol.setPrefWidth(100);

        TableColumn<org.example.model.Alert, Number> lastSeenCol = new TableColumn<>("Last Seen Price");
        lastSeenCol.setCellValueFactory(c -> {
            Integer cents = c.getValue().getLastSeenPriceCents();
            return new javafx.beans.property.SimpleDoubleProperty(cents == null ? -1 : cents / 100.0);
        });
        lastSeenCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.doubleValue() < 0) setText("-");
                else setText(String.format("$%.2f", item.doubleValue()));
            }
        });
        lastSeenCol.setPrefWidth(110);

        TableColumn<org.example.model.Alert, String> floatCol = new TableColumn<>("Float Range");
        floatCol.setCellValueFactory(c -> {
            var a = c.getValue();
            String s = (a.getFloatRangeMin() != null && a.getFloatRangeMax() != null)
                    ? String.format("%.4f - %.4f", a.getFloatRangeMin(), a.getFloatRangeMax())
                    : "Any";
            return new javafx.beans.property.SimpleStringProperty(s);
        });
        floatCol.setPrefWidth(140);

        TableColumn<org.example.model.Alert, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));
        activeCol.setPrefWidth(60);

        TableColumn<org.example.model.Alert, String> triggeredCol = new TableColumn<>("Last Triggered");
        triggeredCol.setCellValueFactory(c -> {
            var t = c.getValue().getTriggeredAt();
            return new javafx.beans.property.SimpleStringProperty(t == null ? "-" : t.toString());
        });
        triggeredCol.setPrefWidth(180);

        table.getColumns().setAll(List.of(skinCol, platformCol, thresholdCol, directionCol, lastSeenCol, floatCol, activeCol, triggeredCol));
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void refresh() {
        data.setAll(alertService.findAll());
    }

    private void openEditor(org.example.model.Alert existing) {
        Dialog<org.example.model.Alert> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Alert" : "Edit Alert");
        dialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        TextField skinSearchField = new TextField();
        skinSearchField.setPromptText("Type to search skins...");
        ListView<SkinCatalogEntry> skinResults = new ListView<>();
        skinResults.setPrefHeight(120);
        skinResults.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SkinCatalogEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getMarketHashName());
            }
        });

        SkinCatalogEntry[] selectedSkinHolder = new SkinCatalogEntry[1];
        Label selectedSkinLabel = new Label(existing != null ? existing.getSkinMarketHashName() : "(none selected)");
        if (existing != null) {
            skinRepository.findById(existing.getSkinId()).ifPresent(s -> selectedSkinHolder[0] = s);
        }

        skinSearchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                skinResults.getItems().clear();
                return;
            }
            skinResults.getItems().setAll(skinRepository.search(newV, 30));
        });
        skinResults.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectedSkinHolder[0] = newV;
                selectedSkinLabel.setText(newV.getMarketHashName());
            }
        });

        ComboBox<Platform> platformBox = new ComboBox<>(FXCollections.observableArrayList(Platform.values()));
        platformBox.setPromptText("Any platform");
        platformBox.setValue(existing != null ? existing.getPlatform() : null);
        CheckBox anyPlatformBox = new CheckBox("Any platform (lowest across all)");
        anyPlatformBox.setSelected(existing == null || existing.getPlatform() == null);
        platformBox.disableProperty().bind(anyPlatformBox.selectedProperty());

        TextField thresholdField = new TextField(existing != null ? String.format("%.2f", existing.getThresholdUsdCents() / 100.0) : "");
        thresholdField.setPromptText("e.g. 25.00");

        ComboBox<Direction> directionBox = new ComboBox<>(FXCollections.observableArrayList(Direction.values()));
        directionBox.setValue(existing != null ? existing.getDirection() : Direction.AT_OR_BELOW);

        ComboBox<String> wearBox = new ComboBox<>(FXCollections.observableArrayList(
                "Any", "Factory New", "Minimal Wear", "Field-Tested", "Well-Worn", "Battle-Scarred", "Custom range"));
        wearBox.setValue("Any");

        TextField floatMinField = new TextField();
        TextField floatMaxField = new TextField();
        floatMinField.setDisable(true);
        floatMaxField.setDisable(true);

        if (existing != null && existing.getFloatRangeMin() != null && existing.getFloatRangeMax() != null) {
            WearCondition matched = matchWear(existing.getFloatRangeMin(), existing.getFloatRangeMax());
            if (matched != null) {
                wearBox.setValue(matched.getLabel());
            } else {
                wearBox.setValue("Custom range");
                floatMinField.setDisable(false);
                floatMaxField.setDisable(false);
            }
            floatMinField.setText(String.valueOf(existing.getFloatRangeMin()));
            floatMaxField.setText(String.valueOf(existing.getFloatRangeMax()));
        }

        wearBox.valueProperty().addListener((obs, oldV, newV) -> {
            boolean custom = "Custom range".equals(newV);
            floatMinField.setDisable(!custom);
            floatMaxField.setDisable(!custom);
            if (!custom && !"Any".equals(newV)) {
                WearCondition wc = WearCondition.fromLabel(newV);
                if (wc != null) {
                    floatMinField.setText(String.valueOf(wc.getFloatMin()));
                    floatMaxField.setText(String.valueOf(wc.getFloatMax()));
                }
            } else if ("Any".equals(newV)) {
                floatMinField.clear();
                floatMaxField.clear();
            }
        });

        TextField cooldownField = new TextField(existing != null ? String.valueOf(existing.getCooldownMinutes()) : "60");
        CheckBox activeBox = new CheckBox("Active");
        activeBox.setSelected(existing == null || existing.isActive());

        int row = 0;
        grid.add(new Label("Skin:"), 0, row);
        grid.add(skinSearchField, 1, row++);
        grid.add(selectedSkinLabel, 1, row++);
        grid.add(skinResults, 1, row++);
        grid.add(anyPlatformBox, 1, row++);
        grid.add(new Label("Platform:"), 0, row);
        grid.add(platformBox, 1, row++);
        grid.add(new Label("Threshold price (USD):"), 0, row);
        grid.add(thresholdField, 1, row++);
        grid.add(new Label("Direction:"), 0, row);
        grid.add(directionBox, 1, row++);
        grid.add(new Label("Wear / float range:"), 0, row);
        grid.add(wearBox, 1, row++);
        HBox floatBox = new HBox(8, new Label("Min:"), floatMinField, new Label("Max:"), floatMaxField);
        floatBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(floatBox, 1, row++);
        grid.add(new Label("Cooldown (minutes):"), 0, row);
        grid.add(cooldownField, 1, row++);
        grid.add(activeBox, 1, row++);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != ButtonType.OK) return null;
            if (selectedSkinHolder[0] == null) {
                new Alert(Alert.AlertType.ERROR, "Please select a skin from the search results.").showAndWait();
                return null;
            }
            org.example.model.Alert a = existing != null ? existing : new org.example.model.Alert();
            a.setSkinId(selectedSkinHolder[0].getId());
            a.setSkinMarketHashName(selectedSkinHolder[0].getMarketHashName());
            a.setPlatform(anyPlatformBox.isSelected() ? null : platformBox.getValue());
            a.setThresholdUsdCents(parseUsdToCents(thresholdField.getText()));
            a.setDirection(directionBox.getValue());
            a.setCooldownMinutes(parseIntOrDefault(cooldownField.getText(), 60));
            a.setActive(activeBox.isSelected());

            if (!floatMinField.getText().isBlank() && !floatMaxField.getText().isBlank()) {
                try {
                    a.setFloatRangeMin(Double.parseDouble(floatMinField.getText()));
                    a.setFloatRangeMax(Double.parseDouble(floatMaxField.getText()));
                } catch (NumberFormatException ex) {
                    a.setFloatRangeMin(null);
                    a.setFloatRangeMax(null);
                }
            } else {
                a.setFloatRangeMin(null);
                a.setFloatRangeMax(null);
            }
            a.setWearCondition("Any".equals(wearBox.getValue()) ? null : wearBox.getValue());
            return a;
        });

        Optional<org.example.model.Alert> result = dialog.showAndWait();
        result.ifPresent(a -> {
            if (existing == null) {
                alertService.createAlert(a);
            } else {
                alertService.updateAlert(a);
            }
            refresh();
        });
    }

    private WearCondition matchWear(double min, double max) {
        for (WearCondition wc : WearCondition.values()) {
            if (Math.abs(wc.getFloatMin() - min) < 1e-6 && Math.abs(wc.getFloatMax() - max) < 1e-6) {
                return wc;
            }
        }
        return null;
    }

    private int parseUsdToCents(String text) {
        if (text == null || text.isBlank()) return 0;
        try {
            return Math.round(Float.parseFloat(text.trim()) * 100f);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseIntOrDefault(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
