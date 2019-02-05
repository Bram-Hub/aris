package edu.rpi.aris.assign;

public enum GradingStatus {

    // Do not reorder as java uses this ordering when sorting a Collection of enums
    CORRECT("correct.png"),
    PARTIAL("warning.png"),
    GRADING(null),
    INCORRECT("incorrect.png"),
    ERROR("warning.png"),
    NONE(null);

    public final String icon;

    GradingStatus(String icon) {
        this.icon = icon;
    }

}
