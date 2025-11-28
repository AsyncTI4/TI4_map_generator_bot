package ti4.commands;

public enum SuspicionLevel {
    NONE,
    LITTLE,
    SUSPICIOUS;

    public boolean isSuspicious() {
        return this != NONE;
    }

    public boolean isEscalated() {
        return this == SUSPICIOUS;
    }
}
