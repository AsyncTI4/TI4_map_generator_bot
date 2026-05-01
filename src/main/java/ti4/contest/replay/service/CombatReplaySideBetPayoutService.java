package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.CombatStatsService;
import ti4.service.combat.CombatUnitSelectionHelper;

/**
 * Prices side bets from captured combat odds while preserving offered payouts for existing contests.
 */
@Service
@RequiredArgsConstructor
class CombatReplaySideBetPayoutService {

    public static final String ODDS_V1 = "ODDS_V1";

    private final CombatContestSettings settings;

    public int offeredPayout(
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            CombatSideBetType betType,
            String targetFaction) {
        if (!ODDS_V1.equalsIgnoreCase(contest.getSideBetPayoutModel())
                || (betType != CombatSideBetType.AFB_WHIFF
                        && betType != CombatSideBetType.ROUND_ONE_WHIFF
                        && betType != CombatSideBetType.ROUND_ONE_SLAM
                        && betType != CombatSideBetType.WINNER_ONE_HP)) {
            return fixedPayout(betType);
        }

        if (betType == CombatSideBetType.WINNER_ONE_HP) {
            InitialSnapshotCombatContext context = initialSnapshotCombatContext(candidate, null);
            return context == null
                    ? fixedPayout(CombatSideBetType.WINNER_ONE_HP)
                    : hpPayout(
                            context.snapshot().attackerHp(), context.snapshot().defenderHp());
        }

        if (betType == CombatSideBetType.AFB_WHIFF) {
            Double probability = afbWhiffProbabilityFromInitialSnapshot(candidate, targetFaction);
            if (probability == null) return fixedPayout(CombatSideBetType.AFB_WHIFF);
            return probability <= 0.0 ? maxDynamicPayout() : dynamicPayout(probability);
        }

        boolean slam = betType == CombatSideBetType.ROUND_ONE_SLAM;
        Double probability = openingRoundProbabilityFromInitialSnapshot(candidate, targetFaction, slam);
        if (probability == null) return fixedPayout(betType);

        return probability <= 0.0 ? maxDynamicPayout() : dynamicPayout(probability);
    }

    public int resolvedProfitPoints(CombatContestSideBetEntity sideBet) {
        Integer offeredProfitPoints = sideBet.getOfferedProfitPoints();
        return offeredProfitPoints == null ? sideBet.getBetType().profitPoints() : offeredProfitPoints;
    }

    public boolean hasAfbUnits(CombatCandidateEntity candidate, String targetFaction) {
        InitialSnapshotCombatContext context = initialSnapshotCombatContext(candidate, targetFaction);
        return context != null
                && !collectAfbUnits(context.tile(), context.player()).isEmpty();
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
        return Math.min(settings.getSideBets().getDynamicPayoutCap(), profitPoints * 2);
    }

    private Double openingRoundProbabilityFromInitialSnapshot(
            CombatCandidateEntity candidate, String targetFaction, boolean slam) {
        InitialSnapshotCombatContext context = initialSnapshotCombatContext(candidate, targetFaction);
        if (context == null) return null;

        Map<UnitModel, Integer> playerUnits =
                CombatUnitSelectionHelper.collectCombatRoundUnits(context.tile(), context.space(), context.player());
        Map<UnitModel, Integer> opponentUnits =
                CombatUnitSelectionHelper.collectCombatRoundUnits(context.tile(), context.space(), context.opponent());
        TileModel tileModel = TileHelper.getTileById(context.tile().getTileID());
        List<NamedCombatModifierModel> hitModifiers = CombatModHelper.getModifiers(
                context.player(),
                context.opponent(),
                playerUnits,
                opponentUnits,
                tileModel,
                context.game(),
                CombatRollType.combatround,
                Constants.COMBAT_MODIFIERS);
        List<NamedCombatModifierModel> extraRollModifiers = CombatModHelper.getModifiers(
                context.player(),
                context.opponent(),
                playerUnits,
                opponentUnits,
                tileModel,
                context.game(),
                CombatRollType.combatround,
                Constants.COMBAT_EXTRA_ROLLS);

        return eventProbability(
                playerUnits,
                context.player(),
                context.opponent(),
                context.game(),
                context.tile(),
                context.space(),
                hitModifiers,
                extraRollModifiers,
                CombatRollType.combatround,
                slam);
    }

    private Double afbWhiffProbabilityFromInitialSnapshot(CombatCandidateEntity candidate, String targetFaction) {
        InitialSnapshotCombatContext context = initialSnapshotCombatContext(candidate, targetFaction);
        if (context == null) return null;

        Map<UnitModel, Integer> playerUnits = collectAfbUnits(context.tile(), context.player());
        if (playerUnits.isEmpty()) return null;
        Map<UnitModel, Integer> opponentUnits =
                CombatUnitSelectionHelper.collectCombatRoundUnits(context.tile(), context.space(), context.opponent());
        TileModel tileModel = TileHelper.getTileById(context.tile().getTileID());
        List<NamedCombatModifierModel> hitModifiers = CombatModHelper.getModifiers(
                context.player(),
                context.opponent(),
                playerUnits,
                opponentUnits,
                tileModel,
                context.game(),
                CombatRollType.AFB,
                Constants.COMBAT_MODIFIERS);
        List<NamedCombatModifierModel> extraRollModifiers = CombatModHelper.getModifiers(
                context.player(),
                context.opponent(),
                playerUnits,
                opponentUnits,
                tileModel,
                context.game(),
                CombatRollType.AFB,
                Constants.COMBAT_EXTRA_ROLLS);

        return eventProbability(
                playerUnits,
                context.player(),
                context.opponent(),
                context.game(),
                context.tile(),
                context.space(),
                hitModifiers,
                extraRollModifiers,
                CombatRollType.AFB,
                false);
    }

    private Map<UnitModel, Integer> collectAfbUnits(Tile tile, Player player) {
        Map<String, Integer> unitsByAsyncId = new java.util.HashMap<>();
        String colorId = Mapper.getColorID(player.getColor());
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (Map.Entry<String, Integer> entry :
                    holder.getUnitAsyncIdsOnHolder(colorId).entrySet()) {
                unitsByAsyncId.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        Map<UnitModel, Integer> afbUnits = new java.util.HashMap<>();
        for (Map.Entry<String, Integer> entry : unitsByAsyncId.entrySet()) {
            UnitModel unit = player.getPriorityUnitByAsyncID(entry.getKey(), null);
            if (unit != null && unit.getAfbDieCount(player) > 0) {
                afbUnits.put(unit, entry.getValue());
            }
        }
        if (player.hasRelic("metalivoidarmaments")) {
            afbUnits.put(CombatRollService.getMetaliAFBUnit(player), 1);
        }
        return afbUnits;
    }

    private InitialSnapshotCombatContext initialSnapshotCombatContext(
            CombatCandidateEntity candidate, String targetFaction) {
        String snapshotJson = candidate.getInitialRenderSnapshotJson();
        if (snapshotJson == null || snapshotJson.isBlank()) return null;

        Game game = CombatReplayTileRenderer.render(snapshotJson, snapshotJson);
        if (game == null) return null;
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        Player player = targetFaction == null ? attacker : game.getPlayerFromColorOrFaction(targetFaction);
        Player opponent = targetFaction == null ? defender : opponentFor(game, candidate, targetFaction);
        UnitHolder space = tile == null ? null : tile.getUnitHolders().get(Constants.SPACE);
        if (tile == null
                || attacker == null
                || defender == null
                || player == null
                || opponent == null
                || space == null) {
            return null;
        }
        game.setActivePlayerID(attacker.getUserID());
        LazaxCombatSupport.SpaceCombatSnapshot snapshot =
                LazaxCombatSupport.buildSpaceCombatSnapshot(game, attacker, defender, tile);
        if (snapshot == null) return null;
        return new InitialSnapshotCombatContext(game, tile, space, player, opponent, snapshot);
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
            CombatRollType rollType,
            boolean slam) {
        double probability = 1.0;
        int dice = 0;
        List<UnitModel> playerUnitTypes = new ArrayList<>(unitCounts.keySet());
        for (Map.Entry<UnitModel, Integer> entry : unitCounts.entrySet()) {
            UnitModel unit = entry.getKey();
            int quantity = entry.getValue();
            int dicePerUnit;
            int hitsOn;
            if (rollType == CombatRollType.combatround) {
                CombatStatsService.CombatRoundProfile profile =
                        CombatStatsService.getCombatRoundProfile(true, unit, player, tile, opponent, false);
                dicePerUnit = profile.diceCount();
                hitsOn = profile.hitsOn();
            } else {
                dicePerUnit = unit.getCombatDieCountForAbility(rollType, player);
                hitsOn = unit.getCombatDieHitsOnForAbility(rollType, player);
            }
            int modifier = CombatModHelper.getCombinedModifierForUnit(
                    unit, quantity, hitModifiers, player, opponent, game, playerUnitTypes, rollType, tile, space);
            int extraDice = CombatModHelper.getCombinedModifierForUnit(
                    unit, quantity, extraRollModifiers, player, opponent, game, playerUnitTypes, rollType, tile, space);
            int diceCount = Math.max(0, quantity * dicePerUnit + extraDice);
            if (diceCount == 0) continue;
            double hitChance = hitChance(hitsOn - modifier);
            probability *= Math.pow(slam ? hitChance : 1.0 - hitChance, diceCount);
            dice += diceCount;
        }
        return dice == 0 ? 0.0 : probability;
    }

    private double hitChance(int effectiveThreshold) {
        int boundedThreshold = Math.max(1, Math.min(11, effectiveThreshold));
        return Math.max(0.0, Math.min(1.0, (11 - boundedThreshold) / 10.0));
    }

    private record InitialSnapshotCombatContext(
            Game game,
            Tile tile,
            UnitHolder space,
            Player player,
            Player opponent,
            LazaxCombatSupport.SpaceCombatSnapshot snapshot) {}
}
