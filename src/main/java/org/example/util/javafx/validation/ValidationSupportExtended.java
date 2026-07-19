package org.example.util.javafx.validation;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import org.controlsfx.validation.*;


import java.util.HashMap;
import java.util.Map;

public class ValidationSupportExtended extends ValidationSupport {
    private Map<Control, ErrorLabel> errorLabelMap = new HashMap<>();

    public void registerValidatorWithLabel(Control control, Validator<?> validator, ErrorLabel errorLabel) {
        // Store the mapping
        errorLabelMap.put(control, errorLabel);

        // Register the validator normally
        super.registerValidator(control, validator);

        // Add listener if not already added
        if (errorLabelMap.size() == 1) {
            super.validationResultProperty().addListener(this::updateLabels);
        }
    }

    private void updateLabels(ObservableValue<?> obs, ValidationResult oldResult, ValidationResult newResult) {
        // Clear all labels
        errorLabelMap.values().forEach(ErrorLabel::hide);

        // Update labels with errors
        for (ValidationMessage message : newResult.getMessages()) {
            if (message.getSeverity() == Severity.ERROR) {
                ErrorLabel label = errorLabelMap.get(message.getTarget());
                if (label != null) {
                    label.show(message.getText());
                }
            }
        }
    }
}