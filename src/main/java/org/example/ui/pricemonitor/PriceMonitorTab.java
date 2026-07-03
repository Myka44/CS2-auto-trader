package org.example.ui.pricemonitor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.example.model.PriceSnapshot;
import org.example.model.SkinCatalogEntry;
import org.example.repository.PriceSnapshotRepository;
import org.example.repository.SkinRepository;

import java.util.List;

/** Simple price-history viewer: search a skin, see a line chart of recorded snapshots per platform. */
public class PriceMonitorTab {

    private final SkinRepository skinRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;

    private final ListView<SkinCatalogEntry> resultsList = new ListView<>();
    private final LineChart<Number, Number> chart;
    private final Label infoLabel = new Label("Search for a skin to view its recorded price history.");

    public PriceMonitorTab(SkinRepository skinRepository, PriceSnapshotRepository priceSnapshotRepository) {
        this.skinRepository = skinRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Snapshot # (most recent first)");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (USD)");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Recorded Price History");
        chart.setAnimated(false);

        resultsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SkinCatalogEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getMarketHashName());
            }
        });
        resultsList.setPrefHeight(150);
        resultsList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) loadChart(newV);
        });
    }

    public Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        TextField searchField = new TextField();
        searchField.setPromptText("Search by weapon or skin name...");
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                resultsList.getItems().clear();
            } else {
                resultsList.getItems().setAll(skinRepository.search(newV, 30));
            }
        });

        VBox top = new VBox(8, searchField, resultsList, infoLabel);
        root.setTop(top);
        root.setCenter(chart);
        return root;
    }

    private void loadChart(SkinCatalogEntry skin) {
        List<PriceSnapshot> history = priceSnapshotRepository.findRecentForSkin(skin.getId(), 200);
        chart.getData().clear();

        if (history.isEmpty()) {
            infoLabel.setText("No price history recorded yet for " + skin.getMarketHashName()
                    + ". History accumulates automatically as Targets/Alerts are checked by the scheduler.");
            return;
        }
        infoLabel.setText(history.size() + " snapshots recorded for " + skin.getMarketHashName());

        var byPlatform = new java.util.EnumMap<org.example.model.Platform, ObservableList<XYChart.Data<Number, Number>>>(org.example.model.Platform.class);
        for (org.example.model.Platform p : org.example.model.Platform.values()) {
            byPlatform.put(p, FXCollections.observableArrayList());
        }

        // history is ordered most-recent-first; reverse index so the chart reads left-to-right chronologically
        int n = history.size();
        for (int i = 0; i < n; i++) {
            PriceSnapshot snap = history.get(i);
            if (snap.getPriceUsdCents() == null) continue;
            int chronologicalIndex = n - 1 - i;
            byPlatform.get(snap.getPlatform()).add(new XYChart.Data<>(chronologicalIndex, snap.getPriceUsdCents() / 100.0));
        }

        for (var entry : byPlatform.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(entry.getKey().displayName());
            series.getData().addAll(entry.getValue());
            chart.getData().add(series);
        }
    }
}
