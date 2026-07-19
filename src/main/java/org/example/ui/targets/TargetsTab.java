package org.example.ui.targets;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import javafx.util.Pair;
import org.controlsfx.validation.ValidationSupport;
import org.example.model.Platform;
import org.example.model.SkinCatalogEntry;
import org.example.model.Target;
import org.example.repository.SkinRepository;
import org.example.service.SchedulerService;
import org.example.service.TargetService;
import org.example.util.WearCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/** "Manage Offers" tab: lists existing targets and lets you create / edit / delete them. */
public class TargetsTab {

    private final TargetService targetService;
    private final SkinRepository skinRepository;
    private final SchedulerService schedulerService;
    private static final Logger log = LoggerFactory.getLogger(TargetsTab.class);

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
        Button bulkAddBtn = new Button("Bulk Add");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button toggleBtn = new Button("Enable/Disable");
        Button refreshBtn = new Button("Refresh");
        Button runNowBtn = new Button("Run Adjust Cycle Now");
        toolbar.getChildren().addAll(addBtn, bulkAddBtn, editBtn, deleteBtn, toggleBtn, refreshBtn, runNowBtn);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        addBtn.setOnAction(e -> openEditor(null));
        bulkAddBtn.setOnAction(e -> openBulkEditor());
        editBtn.setOnAction(e -> {
            Target selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) openEditor(selected);
            else new Alert(Alert.AlertType.INFORMATION, "Select a target to edit first").show(); //TODO pakeist ALERT TYPE
        });
        deleteBtn.setOnAction(e -> {
            Target selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                new Alert(Alert.AlertType.INFORMATION, "Select a target to delete first").show();
                return;
            }

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
            new Alert(Alert.AlertType.INFORMATION, "Adjust cycle triggered in the background. Refresh in a few seconds to see updates.").show();
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

        TableColumn<Target, Boolean> autoCalculateCol = new TableColumn<>("Auto-Calculate");
        autoCalculateCol.setCellValueFactory(new PropertyValueFactory<>("autoCalculate"));
        autoCalculateCol.setPrefWidth(110);

        TableColumn<Target, Boolean> autoAdjustCol = new TableColumn<>("Auto-Adjust");
        autoAdjustCol.setCellValueFactory(new PropertyValueFactory<>("autoAdjust"));
        autoAdjustCol.setPrefWidth(110);

        TableColumn<Target, String> lastCheckedCol = new TableColumn<>("Last Checked");
        lastCheckedCol.setCellValueFactory(new PropertyValueFactory<>("lastCheckedAt"));
        lastCheckedCol.setPrefWidth(150);

        TableColumn<Target, String> errorCol = new TableColumn<>("Last Error");
        errorCol.setCellValueFactory(new PropertyValueFactory<>("lastError"));
        errorCol.setPrefWidth(200);

        table.getColumns().setAll(List.of(skinCol, platformCol, maxPriceCol, currentPriceCol, floatCol, qtyCol, activeCol, autoCalculateCol, autoAdjustCol, lastCheckedCol, errorCol));
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
        skinResults.setPrefHeight(150);
        skinResults.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SkinCatalogEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getMarketHashName() + " (" + item.getRarity() + ")");
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
        Label maxPriceErrorLabel = new Label();
        Label minPriceErrorLabel = new Label();
        initErrorLabel(maxPriceErrorLabel, "-fx-text-fill: red;");

        // --- Min price ---
        TextField minPriceField = new TextField(existing != null && existing.getMinPriceUsdCents() > 0
                ? String.format("%.2f", existing.getMinPriceUsdCents() / 100.0) : "");
        minPriceField.setPromptText("Minimum price in USD");

        // Max price validation
        maxPriceField.textProperty().addListener((obs, oldV, newV) -> {
            validateMaxPrice(maxPriceField, minPriceField, maxPriceErrorLabel);
            validateMinPrice(minPriceField, maxPriceField, minPriceErrorLabel);
        });

        minPriceField.textProperty().addListener((obs, oldV, newV) -> {
            validateMaxPrice(maxPriceField, minPriceField, maxPriceErrorLabel);
            validateMinPrice(minPriceField, maxPriceField, minPriceErrorLabel);
        });


        // --- Auto-calculate ---
        CheckBox autoCalculateBox = new CheckBox("Auto-calculate max and min price");
        autoCalculateBox.setSelected(existing != null && existing.isAutoCalculate());
        //obs, oldV, newV) -> maxPriceField.setDisable(newV))
        autoCalculateBox.selectedProperty().addListener((observableValue, oldV, newV) -> {
            maxPriceField.setDisable(newV);
            minPriceField.setDisable(newV);

        });

        // --- Auto-calculate multipliers ---
        TextField autoCalcMinMultiplierField = new TextField(existing != null && existing.getAutoCalculateMinMultiplier() != null
                ? String.valueOf(existing.getAutoCalculateMinMultiplier()) : "1.0");
        TextField autoCalcMaxMultiplierField = new TextField(existing != null && existing.getAutoCalculateMaxMultiplier() != null
                ? String.valueOf(existing.getAutoCalculateMaxMultiplier()) : "1.0");
        autoCalcMinMultiplierField.setPromptText("Min multiplier");
        autoCalcMaxMultiplierField.setPromptText("Max multiplier");

        //Disable init TODO kodel sitas neveikia
        autoCalcMinMultiplierField.setDisable(!autoCalculateBox.isSelected());
        autoCalcMaxMultiplierField.setDisable(!autoCalculateBox.isSelected());

        autoCalculateBox.selectedProperty().addListener((obs, oldV, newV) -> {
            autoCalcMinMultiplierField.setDisable(!newV);
            autoCalcMaxMultiplierField.setDisable(!newV);
        });


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
        //Hbox priceBox = new Hbox(8, new Label("Max price (USD):"))
         grid.add(new Label("Max price (USD):"), 0, row);
        HBox priceBox = new HBox(8, maxPriceField, autoCalculateBox);
        priceBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(priceBox, 1, row++);
        grid.add(maxPriceErrorLabel, 1, row++);

        // Min price
        grid.add(new Label("Min price (USD):"), 0, row);
        grid.add(minPriceField, 1, row++);
        grid.add(minPriceErrorLabel, 1, row++);

        // Auto-calculate multipliers (only active when auto-calculate is checked)
        HBox multipliersBox = new HBox(8, new Label("Min Multiplier:"), autoCalcMinMultiplierField, 
                                       new Label("Max Multiplier:"), autoCalcMaxMultiplierField);
        multipliersBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(multipliersBox, 1, row++);

        //grid.add(maxPriceField, 1, row++);
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
            t.setAutoCalculate(autoCalculateBox.isSelected());
            t.setPriceModifierCents(parseIntOrDefault(modifierField.getText(), 1));
            t.setQuantity(parseIntOrDefault(quantityField.getText(), 10));
            t.setAutoAdjust(autoAdjustBox.isSelected());
            t.setActive(activeBox.isSelected());

            // Set min price
            t.setMinPriceUsdCents(parseUsdToCents(minPriceField.getText()));

            // Set auto-calculate multipliers
            try {
                t.setAutoCalculateMinMultiplier(Double.parseDouble(autoCalcMinMultiplierField.getText()));
            } catch (NumberFormatException e) {
                t.setAutoCalculateMinMultiplier(1.0);
            }
            try {
                t.setAutoCalculateMaxMultiplier(Double.parseDouble(autoCalcMaxMultiplierField.getText()));
            } catch (NumberFormatException e) {
                t.setAutoCalculateMaxMultiplier(1.0);
            }

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

    /**
     * Bulk-create dialog: pick many skins from a multi-select search list, apply one
     * shared set of target settings (platform, price, wear/float, flags) to all of them.
     */
    private void openBulkEditor() {
        Dialog<List<Target>> dialog = new Dialog<>();
        dialog.setTitle("Bulk Add Targets");
        dialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        // --- Skin search (multi-select) ---
        TextField skinSearchField = new TextField();
        skinSearchField.setPromptText("Type to search skins (e.g. AK-47 Redline)...");
        ListView<SkinCatalogEntry> skinResults = new ListView<>();
        skinResults.setPrefHeight(180);
        skinResults.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        skinResults.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SkinCatalogEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getMarketHashName() + " (" + item.getRarity() + ")");
            }
        });

        ObservableList<SkinCatalogEntry> pickedSkins = FXCollections.observableArrayList();
        ListView<SkinCatalogEntry> pickedView = new ListView<>(pickedSkins);
        pickedView.setPrefHeight(120);
        pickedView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SkinCatalogEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getMarketHashName());
            }
        });
        Label pickedLabel = new Label("Selected skins (0):");

        Button addSelectedBtn = new Button("Add Selected \u2193");
        Button removePickedBtn = new Button("Remove Selected \u2191");
        Button clearPickedBtn = new Button("Clear All");
        HBox pickButtons = new HBox(8, addSelectedBtn, removePickedBtn, clearPickedBtn);

        addSelectedBtn.setOnAction(e -> {
            for (SkinCatalogEntry s : skinResults.getSelectionModel().getSelectedItems()) {
                if (!pickedSkins.contains(s)) pickedSkins.add(s);
            }
            pickedLabel.setText("Selected skins (" + pickedSkins.size() + "):");
        });
        removePickedBtn.setOnAction(e -> {
            pickedSkins.removeAll(pickedView.getSelectionModel().getSelectedItems());
            pickedLabel.setText("Selected skins (" + pickedSkins.size() + "):");
        });
        clearPickedBtn.setOnAction(e -> {
            pickedSkins.clear();
            pickedLabel.setText("Selected skins (0):");
        });

        skinSearchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                skinResults.getItems().clear();
                return;
            }
            skinResults.getItems().setAll(skinRepository.search(newV, 30));
        });

        // --- Shared settings (same as single-target editor) ---
        ComboBox<Platform> platformBox = new ComboBox<>(FXCollections.observableArrayList(Platform.values()));
        platformBox.setValue(Platform.DMARKET);

        TextField maxPriceField = new TextField();
        maxPriceField.setPromptText("0.00 = use current lowest offer as ceiling");

        CheckBox autoCalculateBox = new CheckBox("Auto-calculate max price");
        autoCalculateBox.setSelected(true);
        autoCalculateBox.selectedProperty().addListener((obs, oldV, newV) -> maxPriceField.setDisable(newV));
        maxPriceField.setDisable(true);

        TextField modifierField = new TextField("1");
        TextField quantityField = new TextField("10");

        ComboBox<String> wearBox = new ComboBox<>(FXCollections.observableArrayList(
                "Any", "Factory New", "Minimal Wear", "Field-Tested", "Well-Worn", "Battle-Scarred", "Custom range"));
        wearBox.setValue("Any");

        TextField floatMinField = new TextField();
        TextField floatMaxField = new TextField();
        floatMinField.setPromptText("0.00");
        floatMaxField.setPromptText("1.00");
        floatMinField.setDisable(true);
        floatMaxField.setDisable(true);

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

        CheckBox autoAdjustBox = new CheckBox("Auto-adjust price to outbid competing targets");
        autoAdjustBox.setSelected(true);
        CheckBox activeBox = new CheckBox("Active");
        activeBox.setSelected(true);

        int row = 0;
        grid.add(new Label("Search skins:"), 0, row);
        grid.add(skinSearchField, 1, row++);
        grid.add(skinResults, 1, row++);
        grid.add(pickButtons, 1, row++);
        grid.add(pickedLabel, 0, row);
        grid.add(pickedView, 1, row++);
        grid.add(new Separator(), 0, row, 2, 1);
        row++;
        grid.add(new Label("These settings apply to every selected skin:"), 0, row, 2, 1);
        row++;
        grid.add(new Label("Platform:"), 0, row);
        grid.add(platformBox, 1, row++);
        grid.add(new Label("Max price (USD):"), 0, row);
        HBox priceBox = new HBox(8, maxPriceField, autoCalculateBox);
        priceBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(priceBox, 1, row++);
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
        ButtonType createButtonType = new ButtonType("Create Targets", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != createButtonType) return null;
            if (pickedSkins.isEmpty()) {
                new Alert(Alert.AlertType.ERROR, "Select at least one skin first.").showAndWait();
                return null;
            }

            Double floatMin = null, floatMax = null;
            if (!floatMinField.getText().isBlank() && !floatMaxField.getText().isBlank()) {
                try {
                    floatMin = Double.parseDouble(floatMinField.getText());
                    floatMax = Double.parseDouble(floatMaxField.getText());
                } catch (NumberFormatException ignored) {
                    // leave null -> "any" float range
                }
            }

            List<Target> targets = new java.util.ArrayList<>();
            for (SkinCatalogEntry skin : pickedSkins) {
                Target t = new Target();
                t.setSkinId(skin.getId());
                t.setSkinMarketHashName(skin.getMarketHashName());
                t.setPlatform(platformBox.getValue());
                t.setMaxPriceUsdCents(parseUsdToCents(maxPriceField.getText()));
                t.setAutoCalculate(autoCalculateBox.isSelected());
                t.setPriceModifierCents(parseIntOrDefault(modifierField.getText(), 1));
                t.setQuantity(parseIntOrDefault(quantityField.getText(), 10));
                t.setAutoAdjust(autoAdjustBox.isSelected());
                t.setActive(activeBox.isSelected());
                t.setFloatRangeMin(floatMin);
                t.setFloatRangeMax(floatMax);
                targets.add(t);
            }
            return targets;
        });

        Optional<List<Target>> result = dialog.showAndWait();
        result.ifPresent(targets -> {
            int failed = 0;
            for (Target t : targets) {
                try {
                    targetService.createTarget(t);
                } catch (Exception ex) {
                    failed++;
                }
            }
            refresh();
            String msg = "Created " + (targets.size() - failed) + " of " + targets.size() + " targets.";
            if (failed > 0) msg += " " + failed + " failed -- check logs.";
            new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
        });
    }

    private void showErrorLabel(Label error, String text) {
        error.setText(text);
        error.setVisible(true);
        error.setManaged(true);
    }

    private void hideErrorLabel(Label error) {
        error.setVisible(false);
        error.setManaged(false);
    }

    private void initErrorLabel(Label error, String style) {
        if (style != null) error.setStyle(style);
        hideErrorLabel(error);
    }

    @SuppressWarnings("SameParameterValue")

    private void validateMaxPrice(TextField maxPriceField, TextField minPriceField, Label maxPriceError){
        float maxPrice;
        float minPrice;

        try {
            maxPrice = maxPriceField.getText().isBlank() ? Float.MAX_VALUE : Float.parseFloat(maxPriceField.getText());
        } catch (NumberFormatException e) {
            showErrorLabel(maxPriceError, "Max price must be a number.");
            return;
        }

        try {
            minPrice = minPriceField.getText().isBlank() ? 0f : Float.parseFloat(minPriceField.getText());
        } catch (NumberFormatException e) {
            minPrice = 0f;
        }

        if (maxPrice <= 0) {
            showErrorLabel(maxPriceError, "Max price must be greater than 0.");
            return;
        }
        if (maxPrice < minPrice) {
            showErrorLabel(maxPriceError, "Max price must be greater than min price.");
            return;
        }

        hideErrorLabel(maxPriceError);
    }

    private void validateMinPrice(TextField minPriceField, TextField maxPriceField, Label minPriceError) {
        float minPrice;
        float maxPrice;

        try {
            minPrice = minPriceField.getText().isBlank() ? 0f :Float.parseFloat(minPriceField.getText());
        }
        catch (NumberFormatException e) {
            showErrorLabel(minPriceError, "Min price must be a number.");
            return;
        }

        try {
            maxPrice = maxPriceField.getText().isBlank() ? Float.MAX_VALUE : Float.parseFloat(maxPriceField.getText());
        }
        catch (NumberFormatException e) {
            maxPrice = Float.MAX_VALUE;
        }

        if (minPrice < 0) {
            showErrorLabel(minPriceError, "Min price must be greater than or equal to 0.");
            return;
        }
        if (minPrice > maxPrice) {
            showErrorLabel(minPriceError, "Min price must be less than max price.");
            return;
        }

        hideErrorLabel(minPriceError);
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