package ti4.contest.replay.service;

import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.RollSegmentType;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.service.combat.CombatRollType;

/**
 * Prices side bets from captured combat odds while preserving offered payouts for existing contests.
 */
@Service
@RequiredArgsConstructor
public class CombatReplaySideBetPayoutService {

    public static final String ODDS_V1 = "ODDS_V1";

    private static final EnumSet<RollSegmentType> OPENING_ROLL_SEGMENTS = EnumSet.of(
            RollSegmentType.PRIMARY,
            RollSegmentType.SUPERCHARGE_SELECTED_UNIT,
            RollSegmentType.SUPERCHARGE_REST,
            RollSegmentType.GRAVLEASH_SELECTED_UNIT,
            RollSegmentType.GRAVLEASH_REST);

    private final CombatContestSettings settings;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final ReplayDispatchSerializer payloadSerializer;

    public int offeredPayout(
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            CombatSideBetType betType,
            String targetFaction) {
        if (!ODDS_V1.equalsIgnoreCase(contest.getSideBetPayoutModel())
                || (betType != CombatSideBetType.ROUND_ONE_WHIFF
                        && betType != CombatSideBetType.ROUND_ONE_SLAM
                        && betType != CombatSideBetType.WINNER_ONE_HP)) {
            return fixedPayout(betType);
        }

        if (betType == CombatSideBetType.WINNER_ONE_HP) {
            return candidate.getAttackerHp() == null || candidate.getDefenderHp() == null
                    ? fixedPayout(betType)
                    : hpPayout(candidate.getAttackerHp(), candidate.getDefenderHp());
        }

        CombatRollPayload payload = roundOnePayload(candidate, targetFaction);
        if (payload == null) return fixedPayout(betType);

        double probability = eventProbability(payload.unitRolls(), betType == CombatSideBetType.ROUND_ONE_SLAM);
        return probability <= 0.0 ? maxDynamicPayout() : dynamicPayout(probability);
    }

    public int resolvedProfitPoints(CombatContestSideBetEntity sideBet) {
        Integer offeredProfitPoints = sideBet.getOfferedProfitPoints();
        return offeredProfitPoints == null ? sideBet.getBetType().profitPoints() : offeredProfitPoints;
    }

    private int fixedPayout(CombatSideBetType betType) {
        return betType.profitPoints();
    }

    private int dynamicPayout(double probability) {
        int profitPoints = (int) Math.round(settings.getSideBets().getDynamicPayoutTargetReturn() / probability);
        profitPoints = Math.max(1, profitPoints);
        return Math.min(settings.getSideBets().getDynamicPayoutCap(), profitPoints);
    }

    private int maxDynamicPayout() {
        return settings.getSideBets().getDynamicPayoutCap();
    }

    private int hpPayout(double attackerHp, double defenderHp) {
        double totalHp = attackerHp + defenderHp;
        double balance = Math.min(attackerHp, defenderHp) / Math.max(attackerHp, defenderHp);
        double x = Math.max(0.0, totalHp - 8.0) / 52.0;
        int profitPoints = (int) Math.round(3.0 + Math.pow(x, 1.35) * (64.0 + 8.0 * Math.sqrt(balance)));
        profitPoints = Math.max(3, profitPoints);
        return Math.min(settings.getSideBets().getDynamicPayoutCap(), profitPoints);
    }

    private CombatRollPayload roundOnePayload(CombatCandidateEntity candidate, String targetFaction) {
        for (CombatCandidateEventEntity event :
                candidateEventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId())) {
            if (event.getEventType() != CombatCandidateEventType.ROLL) continue;
            if (!Integer.valueOf(1).equals(event.getRoundNumber())) continue;
            if (!targetFaction.equalsIgnoreCase(event.getActorFaction())) continue;

            CombatRollPayload payload = readCombatRollPayload(event);
            if (isRoundOneCombatPayload(payload)) {
                return payload;
            }
        }
        return null;
    }

    private CombatRollPayload readCombatRollPayload(CombatCandidateEventEntity event) {
        ReplayDispatchPayload payload = payloadSerializer.read(event);
        if (payload instanceof ReplayDispatchPayload.CombatRollDispatch combatRoll) {
            return combatRoll.payload();
        }
        return null;
    }

    private boolean isRoundOneCombatPayload(CombatRollPayload payload) {
        if (payload == null) return false;
        CombatRollPayload.RollHeader header = payload.header();
        return header != null && header.rollType() == CombatRollType.combatround;
    }

    private double eventProbability(List<CombatRollPayload.UnitRoll> unitRolls, boolean slam) {
        double probability = 1.0;
        int dice = 0;
        for (CombatRollPayload.UnitRoll unitRoll : unitRolls) {
            int diceCount = openingDiceCount(unitRoll);
            if (diceCount <= 0) continue;
            double hitChance = hitChance(unitRoll.effectiveThreshold());
            probability *= Math.pow(slam ? hitChance : 1.0 - hitChance, diceCount);
            dice += diceCount;
        }
        return dice == 0 ? 0.0 : probability;
    }

    private int openingDiceCount(CombatRollPayload.UnitRoll unitRoll) {
        if (!OPENING_ROLL_SEGMENTS.contains(unitRoll.segmentType())) return 0;
        int recordedDice = unitRoll.dice().size();
        if (recordedDice > 0) return recordedDice;
        return Math.max(0, unitRoll.quantity() * unitRoll.dicePerUnit() + unitRoll.extraDice());
    }

    private double hitChance(int effectiveThreshold) {
        int boundedThreshold = Math.max(1, Math.min(11, effectiveThreshold));
        return Math.max(0.0, Math.min(1.0, (11 - boundedThreshold) / 10.0));
    }
}
