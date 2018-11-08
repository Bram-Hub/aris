package edu.rpi.aris.assign;

public enum GradingStatus {

    // Do not reorder as java uses this ordering when sorting a Collection of enums
    CORRECT("correct.png"),
    PARTIAL("partial.png"),
    GRADING("grading.png"),
    INCORRECT("incorrect.png"),
    NONE(null);

    public final String icon;

    GradingStatus(String icon) {
        this.icon = icon;
    }

}
