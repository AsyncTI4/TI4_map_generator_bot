package ti4.ai.fallback;

public record DelegatedPress(
        String gameName,
        DelegationChoice choice,
        String chooserName,
        String label,
        String delegationChannelId,
        String delegationMessageId) {}
