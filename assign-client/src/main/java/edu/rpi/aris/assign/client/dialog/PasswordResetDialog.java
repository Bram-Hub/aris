package edu.rpi.aris.assign.client.dialog;

import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.client.model.LocalConfig;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

public class PasswordResetDialog extends Dialog<Pair<String, String>> {

    public PasswordResetDialog(String titleText, boolean useStrictRules, boolean expired, boolean requireCurrentPass) {
        setTitle(titleText == null ? "Reset Password:" : titleText);
        String headerText = "";
        if (expired)
            headerText = "Password has expired.\n";
        headerText += getTitle() + "\n";
        if (useStrictRules)
            headerText += DBUtils.COMPLEXITY_RULES;
        setHeaderText(headerText);
        ButtonType loginButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setPadding(new Insets(20));
        PasswordField currentPass = null;
        if (requireCurrentPass) {
            currentPass = new PasswordField();
            currentPass.setPromptText("Current Password");
        }
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New Password");
        PasswordField retypePassword = new PasswordField();
        retypePassword.setPromptText("Retype Password");
        if (requireCurrentPass) {
            grid.add(new Label("Current Password:"), 0, 0);
            grid.add(currentPass, 1, 0);
        }
        grid.add(new Label("New Password:"), 0, 1);
        grid.add(newPassword, 1, 1);
        grid.add(new Label("Retype Password:"), 0, 2);
        grid.add(retypePassword, 1, 2);
        Node loginButton = getDialogPane().lookupButton(loginButtonType);
        BooleanBinding binding = newPassword.textProperty().isEmpty().or
                (retypePassword.textProperty().isEmpty()).or
                (newPassword.textProperty().isNotEqualTo(retypePassword.textProperty()));
        if (requireCurrentPass) {
            binding = binding.or(currentPass.textProperty().isEmpty()).or
                    (currentPass.textProperty().isEqualTo(newPassword.textProperty()));
        }
        if (useStrictRules) {
            PasswordField finalCurrentPass1 = currentPass;
            binding = binding.or(Bindings.createBooleanBinding(() -> !DBUtils.checkPasswordComplexity(LocalConfig.USERNAME.getValue(), newPassword.getText(), requireCurrentPass ? finalCurrentPass1.getText() : null), newPassword.textProperty()));
        }
        loginButton.disableProperty().bind(binding);
        getDialogPane().setContent(grid);
        PasswordField finalCurrentPass = currentPass;
        setResultConverter(buttonType -> buttonType == ButtonType.CANCEL ? null : new Pair<>(finalCurrentPass == null ? null : finalCurrentPass.getText(), newPassword.getText()));
        if (requireCurrentPass)
            currentPass.requestFocus();
        else
            newPassword.requestFocus();
    }

}
