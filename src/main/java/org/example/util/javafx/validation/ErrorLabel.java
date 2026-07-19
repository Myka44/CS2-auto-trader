package org.example.util.javafx.validation;

import javafx.scene.control.Label;

public class ErrorLabel extends Label {
    public ErrorLabel(){
        super();
        this.setStyle("-fx-text-fill: red;");
    }

    public ErrorLabel(String text){
        super(text);
        this.setStyle("-fx-text-fill: red;");
    }

    public void show(){
        this.setVisible(true);
        this.setManaged(true);
    }

    public void show(String text){
        this.setText(text);
        show();
    }

    public void hide(){
        this.setVisible(false);
        this.setManaged(false);
    }
}
