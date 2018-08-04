package edu.rpi.aris.assign.client;

import edu.rpi.aris.assign.UserType;
import javafx.util.StringConverter;

public class UserTypeConverter extends StringConverter<UserType> {
    @Override
    public String toString(UserType object) {
        return object.readableName;
    }

    @Override
    public UserType fromString(String string) {
        return UserType.getFromReadableName(string);
    }
}
