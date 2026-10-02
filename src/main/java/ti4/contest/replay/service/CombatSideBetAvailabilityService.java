package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.core.CombatSideState;
import ti4.contest.replay.entities.CombatCandidateEntity;

@Service
@RequiredArgsConstructor
public class CombatSideBetAvailabilityService {

    private final CombatReplaySideBetPayoutService payoutService;

    public List<CombatSideBetType> availableTypes(CombatCandidateEntity candidate, String targetFaction) {
        List<CombatSideBetType> availableTypes = new ArrayList<>();
        CombatSideState state = CombatSideState.forFaction(candidate, targetFaction);
        if (state == null) return availableTypes;

        for (CombatSideBetType type : CombatSideBetType.values()) {
            if (isAvailable(candidate, type, targetFaction, state)) {
                availableTypes.add(type);
            }
        }
        return availableTypes;
    }

    public boolean isAvailable(CombatCandidateEntity candidate, CombatSideBetType type, String targetFaction) {
        CombatSideState state = CombatSideState.forFaction(candidate, targetFaction);
        return state != null && isAvailable(candidate, type, targetFaction, state);
    }

    public boolean isAfbSkippedAvailable(CombatCandidateEntity candidate, String targetFaction) {
        CombatSideState state = CombatSideState.forFaction(candidate, targetFaction);
        return state != null && isAfbSkippedAvailable(candidate, targetFaction, state);
    }

    private boolean isAvailable(
            CombatCandidateEntity candidate, CombatSideBetType type, String targetFaction, CombatSideState state) {
        if (candidate == null || type == null || targetFaction == null) return false;
        if (!type.isAvailable(state.destroyerCount())) return false;
        if (type == CombatSideBetType.AFB_WHIFF) return payoutService.hasAfbUnits(candidate, targetFaction);
        if (type == CombatSideBetType.AFB_SKIPPED) return isAfbSkippedAvailable(candidate, targetFaction, state);
        return true;
    }

    private boolean isAfbSkippedAvailable(
            CombatCandidateEntity candidate, String targetFaction, CombatSideState state) {
        if (!CombatSideBetType.AFB_SKIPPED.isAvailable(state.destroyerCount())) return false;
        return !(state.destroyerCount() == 1 && opponentHasAssaultCannon(candidate, targetFaction));
    }

    private boolean opponentHasAssaultCannon(CombatCandidateEntity candidate, String targetFaction) {
        if (candidate == null || targetFaction == null) return false;
        if (targetFaction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            return Boolean.TRUE.equals(candidate.getDefenderHasAssaultCannon());
        }
        if (targetFaction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            return Boolean.TRUE.equals(candidate.getAttackerHasAssaultCannon());
        }
        return false;
    }
}
