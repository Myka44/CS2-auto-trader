package org.example.ui.targets;

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
import org.example.model.Platform;
import org.example.model.SkinCatalogEntry;
import org.example.model.Target;
import org.example.repository.SkinRepository;
import org.example.service.SchedulerService;
import org.example.service.TargetService;
import org.example.util.WearCondition;

import java.util.List;
import java.util.Optional;

/** "Manage Offers" tab: lists existing targets and lets you create / edit / delete them. */
public class TargetsTab {

    private final TargetService targetService;
    private final SkinRepository skinRepository;
    private final SchedulerService schedulerService;

    private final TableView<Target> table = new TableView<>();
    private final ObservableList<Target> data = FXCollections.observableArrayList();

    public TargetsTab(TargetService targetService, SkinRepository skinRepository, SchedulerService schedulerService) {
        this.targetService = targetService;
        this.skinRepository = skinRepository;
        this.schedulerService = schedulerService;
        buildTable();
        refresh();
    }

    public Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox toolbar = new HBox(8);
        Button addBtn = new Button("New Target");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button toggleBtn = new Button("Enable/Disable");
        Button refreshBtn = new Button("Refresh");
        Button runNowBtn = new Button("Run Adjust Cycle Now");
        toolbar.getChildren().addAll(addBtn, editBtn, deleteBtn, toggleBtn, refreshBtn, runNowBtn);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        addBtn.setOnAction(e -> openEditor(null));
        editBtn.setOnAction(e -> {
            Target selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) openEditor(selected);
        });
        deleteBtn.setOnAction(e -> {
            Target selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete target for " + selected.getSkinMarketHashName() + " on " + selected.getPlatform().displayName() + "?\n"
                            + "Also remove the matching order from the platform?");
            ButtonType yesPlatform = new ButtonType("Yes, remove from platform too");
            ButtonType yesLocalOnly = new ButtonType("Yes, local only");
            ButtonType cancel = ButtonType.CANCEL;
            confirm.getButtonTypes().setAll(yesPlatform, yesLocalOnly, cancel);
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == yesPlatform) {
                targetService.deleteTarget(selected, true);
                refresh();
            } else if (result.isPresent() && result.get() == yesLocalOnly) {
                targetService.deleteTarget(selected, false);
                refresh();
            }
        });
        toggleBtn.setOnAction(e -> {
            Target selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            selected.setActive(!selected.isActive());
            targetService.updateTargetSettings(selected);
            refresh();
        });
        refreshBtn.setOnAction(e -> refresh());
        runNowBtn.setOnAction(e -> {
            schedulerService.runTargetsNow();
            new Alert(Alert.AlertType.INFORMATION, "Adjust cycle triggered in the background. Refresh in a few seconds to see updates.").showAndWait();
        });

        root.setTop(toolbar);
        root.setCenter(table);
        return root;
    }

    private void buildTable() {
        TableColumn<Target, String> skinCol = new TableColumn<>("Skin");
        skinCol.setCellValueFactory(new PropertyValueFactory<>("skinMarketHashName"));
        skinCol.setPrefWidth(280);

        TableColumn<Target, Platform> platformCol = new TableColumn<>("Platform");
        platformCol.setCellValueFactory(new PropertyValueFactory<>("platform"));
        platformCol.setPrefWidth(90);

        TableColumn<Target, Number> maxPriceCol = new TableColumn<>("Max Price");
        maxPriceCol.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().getMaxPriceUsdCents() / 100.0));
        maxPriceCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item.doubleValue()));
            }
        });
        maxPriceCol.setPrefWidth(90);

        TableColumn<Target, Number> currentPriceCol = new TableColumn<>("Current Bid");
        currentPriceCol.setCellValueFactory(c -> {
            Integer cents = c.getValue().getLastPriceCents();
            return new javafx.beans.property.SimpleDoubleProperty(cents == null ? -1 : cents / 100.0);
        });
        currentPriceCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.doubleValue() < 0) setText("-");
                else setText(String.format("$%.2f", item.doubleValue()));
            }
        });
        currentPriceCol.setPrefWidth(90);

        TableColumn<Target, String> floatCol = new TableColumn<>("Float Range");
        floatCol.setCellValueFactory(c -> {
            Target t = c.getValue();
            String s = (t.getFloatRangeMin() != null && t.getFloatRangeMax() != null)
                    ? String.format("%.4f - %.4f", t.getFloatRangeMin(), t.getFloatRangeMax())
                    : "Any";
            return new javafx.beans.property.SimpleStringProperty(s);
        });
        floatCol.setPrefWidth(140);

        TableColumn<Target, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        qtyCol.setPrefWidth(50);

        TableColumn<Target, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));
        activeCol.setPrefWidth(60);

        TableColumn<Target, Boolean> autoCol = new TableColumn<>("Auto-Adjust");
        autoCol.setCellValueFactory(new PropertyValueFactory<>("autoAdjust"));
        autoCol.setPrefWidth(90);

        TableColumn<Target, String> lastCheckedCol = new TableColumn<>("Last Checked");
        lastCheckedCol.setCellValueFactory(new PropertyValueFactory<>("lastCheckedAt"));
        lastCheckedCol.setPrefWidth(150);

        TableColumn<Target, String> errorCol = new TableColumn<>("Last Error");
        errorCol.setCellValueFactory(new PropertyValueFactory<>("lastError"));
        errorCol.setPrefWidth(200);

        table.getColumns().setAll(List.of(skinCol, platformCol, maxPriceCol, currentPriceCol, floatCol, qtyCol, activeCol, autoCol, lastCheckedCol, errorCol));
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void refresh() {
        data.setAll(targetService.findAll());
    }

    private void openEditor(Target existing) {
        Dialog<Target> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Target" : "Edit Target");
        dialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        // --- Skin search ---
        TextField skinSearchField = new TextField();
        skinSearchField.setPromptText("Type to search skins (e.g. AK-47 Redline)...");
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

        // --- Platform ---
        ComboBox<Platform> platformBox = new ComboBox<>(FXCollections.observableArrayList(Platform.values()));
        platformBox.setValue(existing != null ? existing.getPlatform() : Platform.DMARKET);

        // --- Max price ---
        TextField maxPriceField = new TextField(existing != null ? String.format("%.2f", existing.getMaxPriceUsdCents() / 100.0) : "");
        maxPriceField.setPromptText("0.00 = use current lowest offer as ceiling");

        // --- Price modifier (outbid increment) ---
        TextField modifierField = new TextField(existing != null ? String.valueOf(existing.getPriceModifierCents()) : "1");

        // --- Quantity ---
        TextField quantityField = new TextField(existing != null ? String.valueOf(existing.getQuantity()) : "10");

        // --- Wear condition / float range ---
        ComboBox<String> wearBox = new ComboBox<>(FXCollections.observableArrayList(
                "Any", "Factory New", "Minimal Wear", "Field-Tested", "Well-Worn", "Battle-Scarred", "Custom range"));
        wearBox.setValue("Any");

        TextField floatMinField = new TextField();
        TextField floatMaxField = new TextField();
        floatMinField.setPromptText("0.00");
        floatMaxField.setPromptText("1.00");
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

        // --- Auto-adjust / active ---
        CheckBox autoAdjustBox = new CheckBox("Auto-adjust price to outbid competing targets");
        autoAdjustBox.setSelected(existing == null || existing.isAutoAdjust());
        CheckBox activeBox = new CheckBox("Active");
        activeBox.setSelected(existing == null || existing.isActive());

        int row = 0;
        grid.add(new Label("Skin:"), 0, row);
        grid.add(skinSearchField, 1, row++);
        grid.add(selectedSkinLabel, 1, row++);
        grid.add(skinResults, 1, row++);
        grid.add(new Label("Platform:"), 0, row);
        grid.add(platformBox, 1, row++);
        grid.add(new Label("Max price (USD):"), 0, row);
        grid.add(maxPriceField, 1, row++);
        grid.add(new Label("Outbid increment (cents):"), 0, row);
        grid.add(modifierField, 1, row++);
        grid.add(new Label("Quantity:"), 0, row);
        grid.add(quantityField, 1, row++);
        grid.add(new Label("Wear / float range:"), 0, row);
        grid.add(wearBox, 1, row++);
        HBox floatBox = new HBox(8, new Label("Min:"), floatMinField, new Label("Max:"), floatMaxField);
        floatBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(floatBox, 1, row++);
        grid.add(autoAdjustBox, 1, row++);
        grid.add(activeBox, 1, row++);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != ButtonType.OK) return null;
            if (selectedSkinHolder[0] == null) {
                new Alert(Alert.AlertType.ERROR, "Please select a skin from the search results.").showAndWait();
                return null;
            }
            Target t = existing != null ? existing : new Target();
            t.setSkinId(selectedSkinHolder[0].getId());
            t.setSkinMarketHashName(selectedSkinHolder[0].getMarketHashName());
            t.setPlatform(platformBox.getValue());
            t.setMaxPriceUsdCents(parseUsdToCents(maxPriceField.getText()));
            t.setPriceModifierCents(parseIntOrDefault(modifierField.getText(), 1));
            t.setQuantity(parseIntOrDefault(quantityField.getText(), 10));
            t.setAutoAdjust(autoAdjustBox.isSelected());
            t.setActive(activeBox.isSelected());

            if (!floatMinField.getText().isBlank() && !floatMaxField.getText().isBlank()) {
                try {
                    t.setFloatRangeMin(Double.parseDouble(floatMinField.getText()));
                    t.setFloatRangeMax(Double.parseDouble(floatMaxField.getText()));
                } catch (NumberFormatException ex) {
                    t.setFloatRangeMin(null);
                    t.setFloatRangeMax(null);
                }
            } else {
                t.setFloatRangeMin(null);
                t.setFloatRangeMax(null);
            }
            return t;
        });

        Optional<Target> result = dialog.showAndWait();
        result.ifPresent(t -> {
            if (existing == null) {
                targetService.createTarget(t);
            } else {
                targetService.updateTargetSettings(t);
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
