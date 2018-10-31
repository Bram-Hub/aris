package edu.rpi.aris.assign.client.exceptions;

import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.Message;

import java.util.concurrent.CancellationException;

public class PasswordResetRequiredException extends ArisCommunicationException {

    public PasswordResetRequiredException() {
        super("Your password has expired and must be reset");
    }

    @Override
    public <T extends Message> void handleError(ResponseHandler<T> handler, T message) {
        AssignClient.displayErrorMsg("Password Reset", getMessage(), true);
        Client.getInstance().disconnect();
        boolean retry = false;
        do {
            try {
                retry = false;
                Client.getInstance().resetPassword();
                handler.onError(true, message);
            } catch (CancellationException e) {
                handler.onError(false, message);
            } catch (InvalidCredentialsException e) {
                AssignClient.displayErrorMsg("Incorrect Password", "Your current password is incorrect", true);
                retry = true;
            } catch (WeakPasswordException e) {
                AssignClient.displayErrorMsg("Weak Password", "Your new password does not meet the complexity requirements", true);
                retry = true;
            } catch (Throwable e) {
                AssignClient.displayErrorMsg("Error", e.getMessage());
                handler.onError(false, message);
            }
        } while (retry);
    }
}
