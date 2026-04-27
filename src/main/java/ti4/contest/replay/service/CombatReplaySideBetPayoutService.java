package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.RollSegmentType;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.CombatStatsService;
import ti4.service.combat.CombatUnitSelectionHelper;

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

        boolean slam = betType == CombatSideBetType.ROUND_ONE_SLAM;
        Double probability = openingRoundProbabilityFromInitialSnapshot(candidate, targetFaction, slam);
        if (probability == null) {
            CombatRollPayload payload = roundOnePayload(candidate, targetFaction);
            if (payload == null) return fixedPayout(betType);
            probability = eventProbability(payload.unitRolls(), slam);
        }

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
        if (probability >= 0.20) return 4;
        if (probability >= 0.10) return 6;
        if (probability >= 0.05) return 10;
        if (probability >= 0.025) return 15;
        if (probability >= 0.01) return 30;
        if (probability >= 0.005) return 75;
        return maxDynamicPayout();
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

    private Double openingRoundProbabilityFromInitialSnapshot(
            CombatCandidateEntity candidate, String targetFaction, boolean slam) {
        String snapshotJson = candidate.getInitialRenderSnapshotJson();
        if (snapshotJson == null || snapshotJson.isBlank()) return null;

        Game game = CombatReplayTileRenderer.render(snapshotJson, snapshotJson);
        if (game == null) return null;
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        if (tile == null) tile = game.getTileByPosition(game.getActiveSystem());
        Player player = game.getPlayerFromColorOrFaction(targetFaction);
        Player opponent = opponentFor(game, candidate, targetFaction);
        UnitHolder space = tile == null ? null : tile.getUnitHolders().get(Constants.SPACE);
        if (tile == null || player == null || opponent == null || space == null) return null;

        Map<UnitModel, Integer> playerUnits = CombatUnitSelectionHelper.collectCombatRoundUnits(tile, space, player);
        Map<UnitModel, Integer> opponentUnits =
                CombatUnitSelectionHelper.collectCombatRoundUnits(tile, space, opponent);
        TileModel tileModel = TileHelper.getTileById(tile.getTileID());
        List<NamedCombatModifierModel> hitModifiers = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnits,
                opponentUnits,
                tileModel,
                game,
                CombatRollType.combatround,
                Constants.COMBAT_MODIFIERS);
        List<NamedCombatModifierModel> extraRollModifiers = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnits,
                opponentUnits,
                tileModel,
                game,
                CombatRollType.combatround,
                Constants.COMBAT_EXTRA_ROLLS);

        return eventProbability(
                playerUnits, player, opponent, game, tile, space, hitModifiers, extraRollModifiers, slam);
    }

    private Player opponentFor(Game game, CombatCandidateEntity candidate, String targetFaction) {
        String opponentFaction = targetFaction.equalsIgnoreCase(candidate.getAttackerFaction())
                ? candidate.getDefenderFaction()
                : candidate.getAttackerFaction();
        return game.getPlayerFromColorOrFaction(opponentFaction);
    }

    private double eventProbability(
            Map<UnitModel, Integer> unitCounts,
            Player player,
            Player opponent,
            Game game,
            Tile tile,
            UnitHolder space,
            List<NamedCombatModifierModel> hitModifiers,
            List<NamedCombatModifierModel> extraRollModifiers,
            boolean slam) {
        double probability = 1.0;
        int dice = 0;
        List<UnitModel> playerUnitTypes = new ArrayList<>(unitCounts.keySet());
        for (Map.Entry<UnitModel, Integer> entry : unitCounts.entrySet()) {
            UnitModel unit = entry.getKey();
            int quantity = entry.getValue();
            CombatStatsService.CombatRoundProfile profile =
                    CombatStatsService.getCombatRoundProfile(true, unit, player, tile, opponent, false);
            int modifier = CombatModHelper.getCombinedModifierForUnit(
                    unit,
                    quantity,
                    hitModifiers,
                    player,
                    opponent,
                    game,
                    playerUnitTypes,
                    CombatRollType.combatround,
                    tile,
                    space);
            int extraDice = CombatModHelper.getCombinedModifierForUnit(
                    unit,
                    quantity,
                    extraRollModifiers,
                    player,
                    opponent,
                    game,
                    playerUnitTypes,
                    CombatRollType.combatround,
                    tile,
                    space);
            int diceCount = Math.max(0, quantity * profile.diceCount() + extraDice);
            if (diceCount == 0) continue;
            double hitChance = hitChance(profile.hitsOn() - modifier);
            probability *= Math.pow(slam ? hitChance : 1.0 - hitChance, diceCount);
            dice += diceCount;
        }
        return dice == 0 ? 0.0 : probability;
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
