package ti4.model;

public interface ColorableModelInterface<SELF extends ColorableModelInterface<SELF>> extends ModelInterface {
    boolean isDupe();
    boolean isColorable();
    SELF duplicateAndSetColor(String color);
}