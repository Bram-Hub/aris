package edu.rpi.aris.assign;

public class ModuleUIOptions {

    private final EditMode editMode;
    private final String guiDescription;
    private final boolean allowDefaultSave;
    private final boolean allowSaveAs;
    private final boolean arisHandleDefaultSave;
    private final boolean showUploadButton;
    private final boolean warnBeforeUnsavedClose;

    public ModuleUIOptions(EditMode editMode, String guiDescription, boolean allowDefaultSave, boolean allowSaveAs, boolean arisHandleDefaultSave, boolean showUploadButton, boolean warnBeforeUnsavedClose) {
        this.editMode = editMode;
        this.guiDescription = guiDescription;
        this.allowDefaultSave = allowDefaultSave;
        this.allowSaveAs = allowSaveAs;
        this.arisHandleDefaultSave = arisHandleDefaultSave;
        this.showUploadButton = showUploadButton;
        this.warnBeforeUnsavedClose = warnBeforeUnsavedClose;
    }

    public boolean allowSaveAs() {
        return allowSaveAs;
    }

    public boolean showUploadButton() {
        return showUploadButton;
    }

    public EditMode getEditMode() {
        return editMode;
    }

    public String getGuiDescription() {
        return guiDescription;
    }

    public boolean allowDefaultSave() {
        return allowDefaultSave;
    }

    public boolean arisHandleDefaultSave() {
        return arisHandleDefaultSave;
    }

    public boolean warnBeforeUnsavedClose() {
        return warnBeforeUnsavedClose;
    }
}
