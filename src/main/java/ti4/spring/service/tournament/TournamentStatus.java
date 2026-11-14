package ti4.spring.service.tournament;

public enum TournamentStatus {
    OPEN,
    CLOSED,
    GROUPED,
    CREATING,
    CREATED,
    ARCHIVED;

    public boolean acceptsSignups() {
        return this == OPEN;
    }

    public boolean allowsRosterChanges() {
        return this == OPEN || this == CLOSED || this == GROUPED;
    }

    public boolean isActive() {
        return this != ARCHIVED;
    }
}
