package ti4.spring.service.statistics.matchmaking.queue;

import java.util.List;

public record GroupVerificationResult(
        long partyId, List<String> memberIds, boolean tigl, List<String> invalidRestrictions) {

    public boolean valid() {
        return invalidRestrictions.isEmpty();
    }
}
