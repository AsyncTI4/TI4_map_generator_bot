package ti4.model;

public enum ComponentType {
    ACTION_CARD,
    AGENDA,
    TECHNOLOGY,
    RELIC,
    EXPLORE;

    @Override
    public String toString() {
        return switch (this) {
            case ACTION_CARD -> "action_card";
            case AGENDA -> "agenda";
            case TECHNOLOGY -> "technology";
            case RELIC -> "relic";
            case EXPLORE -> "explore";
        };
    }

    public static ComponentType fromString(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        for (ComponentType t : values()) {
            if (t.toString().equals(v)) return t;
        }
        return null;
    }
}

