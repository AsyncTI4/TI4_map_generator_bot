package ti4.model.enums;

import lombok.Getter;

@Getter
public enum AutomationStatus {
    FULL("automated"),
    PARTIAL("partly automated"),
    MANUAL("manual"),
    NO_RULES("no rules");

    private final String label;

    AutomationStatus(String label) {
        this.label = label;
    }

    public boolean needsPlayerAttention() {
        return this == PARTIAL || this == MANUAL;
    }

    public String cardLine(String notes) {
        String line = "-# Bot: " + label;
        if (notes == null || notes.isBlank()) return line;
        return line + " — " + notes;
    }
}
