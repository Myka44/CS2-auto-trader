package org.example.ui.skinbrowser;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.example.model.SkinCatalogEntry;
import org.example.repository.SkinRepository;

import java.util.List;

/** Read-only browser over the local skin catalog -- mainly useful to sanity-check the seed data. */
public class SkinBrowserTab {

    private final SkinRepository skinRepository;
    private final TableView<SkinCatalogEntry> table = new TableView<>();
    private final ObservableList<SkinCatalogEntry> data = FXCollections.observableArrayList();
    private final Label countLabel = new Label();

    public SkinBrowserTab(SkinRepository skinRepository) {
        this.skinRepository = skinRepository;
        buildTable();
        refreshCount();
        loadPage("");
    }

    public Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        TextField searchField = new TextField();
        searchField.setPromptText("Search by weapon or skin name...");
        searchField.textProperty().addListener((obs, oldV, newV) -> loadPage(newV));

        VBox top = new VBox(8, countLabel, searchField);
        root.setTop(top);
        root.setCenter(table);
        return root;
    }

    private void buildTable() {
        TableColumn<SkinCatalogEntry, String> nameCol = new TableColumn<>("Market Hash Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("marketHashName"));
        nameCol.setPrefWidth(320);

        TableColumn<SkinCatalogEntry, String> weaponCol = new TableColumn<>("Weapon");
        weaponCol.setCellValueFactory(new PropertyValueFactory<>("weapon"));
        weaponCol.setPrefWidth(150);

        TableColumn<SkinCatalogEntry, String> skinCol = new TableColumn<>("Skin");
        skinCol.setCellValueFactory(new PropertyValueFactory<>("skinName"));
        skinCol.setPrefWidth(180);

        TableColumn<SkinCatalogEntry, String> wearCol = new TableColumn<>("Wear");
        wearCol.setCellValueFactory(new PropertyValueFactory<>("wear"));
        wearCol.setPrefWidth(110);

        TableColumn<SkinCatalogEntry, Number> floatMinCol = new TableColumn<>("Float Min");
        floatMinCol.setCellValueFactory(new PropertyValueFactory<>("floatMin"));
        floatMinCol.setPrefWidth(80);

        TableColumn<SkinCatalogEntry, Number> floatMaxCol = new TableColumn<>("Float Max");
        floatMaxCol.setCellValueFactory(new PropertyValueFactory<>("floatMax"));
        floatMaxCol.setPrefWidth(80);

        TableColumn<SkinCatalogEntry, String> rarityCol = new TableColumn<>("Rarity");
        rarityCol.setCellValueFactory(new PropertyValueFactory<>("rarity"));
        rarityCol.setPrefWidth(120);

        TableColumn<SkinCatalogEntry, String> collectionCol = new TableColumn<>("Collection");
        collectionCol.setCellValueFactory(new PropertyValueFactory<>("collection"));
        collectionCol.setPrefWidth(200);

        table.getColumns().setAll(List.of(nameCol, weaponCol, skinCol, wearCol, floatMinCol, floatMaxCol, rarityCol, collectionCol));
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void loadPage(String query) {
        if (query == null || query.isBlank()) {
            data.setAll(skinRepository.findAll(500, 0));
        } else {
            data.setAll(skinRepository.search(query, 500));
        }
    }

    private void refreshCount() {
        countLabel.setText(skinRepository.count() + " skins loaded in the local catalog (showing up to 500 at a time -- type to search the full set).");
    }
}
