package ti4.spring.service.statistics.matchmaking.queue;

public record JoinBlocker(String reason, boolean canBeAddedByMember) {}
