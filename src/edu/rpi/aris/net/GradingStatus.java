package edu.rpi.aris.net;

public enum GradingStatus {

    // Do not reorder as java uses this ordering when sorting a Collection of enums
    CORRECT_WARN("correct_warn.png"),
    CORRECT("correct.png"),
    GRADING("grading.png"),
    INCORRECT_WARN("incorrect_warn.png"),
    INCORRECT("incorrect.png"),
    NONE(null);

    public final String icon;

    GradingStatus(String icon) {
        this.icon = icon;
    }

}
