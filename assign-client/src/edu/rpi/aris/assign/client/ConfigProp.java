package edu.rpi.aris.assign.client;

import javafx.beans.property.SimpleObjectProperty;

import java.util.prefs.Preferences;

public class ConfigProp<T> {

    private final Preferences preferences;
    private final String prefKey;
    private SimpleObjectProperty<T> property;

    public ConfigProp(Preferences preferences, String prefKey, T currentValue) {
        property = new SimpleObjectProperty<>(currentValue);
        this.preferences = preferences;
        this.prefKey = prefKey;
    }

    public T getValue() {
        return property.get();
    }

    public void setValue(T value) {
        property.set(value);
        if (preferences != null && prefKey != null) {
            if (value == null)
                preferences.remove(prefKey);
            else
                preferences.put(prefKey, value.toString());
        }
    }

    public SimpleObjectProperty<T> getProperty() {
        return property;
    }

}
