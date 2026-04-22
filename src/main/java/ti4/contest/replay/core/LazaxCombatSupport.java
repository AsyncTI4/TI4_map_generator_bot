package ti4.contest.replay.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.json.JsonMapperManager;
import ti4.service.combat.CombatStatsService;
import ti4.service.combat.CombatUnitSelectionHelper;
import ti4.spring.service.contest.CombatContestType;

@UtilityClass
public class LazaxCombatSupport {

    public boolean isEligibleGame(Game game) {
        return game != null
                && !game.isHasEnded()
                && !game.isAllianceMode()
                && !game.isFowMode()
                && !game.isAbsolMode()
                && !game.isFrankenGame()
                && !game.isTwilightsFallMode();
    }

    public boolean isEligibleCombat(Game game, Player attacker, Player defender, Tile tile) {
        if (attacker == null || defender == null || tile == null || attacker == defender) return false;
        if (attacker.isDummy() || defender.isDummy() || !attacker.isRealPlayer() || !defender.isRealPlayer()) {
            return false;
        }
        List<Player> shipPlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(Player::isRealPlayer)
                .filter(player -> !player.isDummy())
                .toList();
        return shipPlayers.size() == 2;
    }

    public boolean hasExcludedFlagship(Player attacker, Player defender) {
        return attacker.hasUnit("yin_flagship") || defender.hasUnit("yin_flagship");
    }

    public SpaceCombatSnapshot buildSpaceCombatSnapshot(Game game, Player attacker, Player defender, Tile tile) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (space == null) return null;

        FleetStrength attackerStrength = calculateFleetStrength(game, attacker, defender, tile, space);
        FleetStrength defenderStrength = calculateFleetStrength(game, defender, attacker, tile, space);
        String summary = extractSpaceOnlySummary(ButtonHelper.getCombatTileSummaryMessage(
                game, tile, attacker, null, "space", Constants.SPACE, List.of(attacker, defender)));
        String activePlayerSummary = formatActivePlayerSummary(game);
        String openerText = formatReplayHeader(game, tile, attacker, defender, activePlayerSummary);
        String parentPostText = openerText + "\n\n" + summary;
        return new SpaceCombatSnapshot(
                CombatContestType.SPACE,
                parentPostText,
                activePlayerSummary,
                summary,
                attackerStrength.value(),
                defenderStrength.value(),
                attackerStrength.hp(),
                defenderStrength.hp(),
                attackerStrength.expectedHits(),
                defenderStrength.expectedHits());
    }

    public FleetStrength calculateFleetStrength(
            Game game, Player player, Player opponent, Tile tile, UnitHolder space) {
        double total = 0;
        double hp = 0;
        double expectedHits = 0;
        Map<String, Integer> damagedCountsByAsyncId = getDamagedCountsByAsyncId(tile, player);
        for (Map.Entry<ti4.model.UnitModel, Integer> entry : CombatUnitSelectionHelper.collectCombatRoundUnits(
                        tile, space, player)
                .entrySet()) {
            ti4.model.UnitModel unitModel = entry.getKey();
            if (unitModel == null) continue;
            int totalUnits = entry.getValue();
            int damagedUnits = Math.min(totalUnits, damagedCountsByAsyncId.getOrDefault(unitModel.getAsyncId(), 0));
            int undamagedUnits = Math.max(0, totalUnits - damagedUnits);

            total += unitModel.getCost() * totalUnits;
            hp += totalUnits;
            CombatStatsService.CombatRoundProfile combatProfile =
                    CombatStatsService.getCombatRoundProfile(true, unitModel, player, tile, opponent);
            expectedHits += computeExpectedHits(totalUnits * combatProfile.diceCount(), combatProfile.hitsOn());
            if (unitModel.getSustainDamage(player, space)) {
                hp += undamagedUnits;
                if (player.hasTech("nes")) {
                    hp += undamagedUnits;
                }
            }
        }
        return new FleetStrength(total, hp, expectedHits);
    }

    public String formatReplayAnnouncement(
            Game game, CombatObservationEntity observation, CombatCandidateEntity candidate, String roleMention) {
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        String tileRepresentation = tile == null ? candidate.getTilePosition() : tile.getRepresentationForButtons();
        String attackerLine = attacker == null
                ? "- `" + candidate.getAttackerFaction() + "`"
                : "- " + attacker.getFactionEmoji() + " = " + attacker.getUserName();
        String defenderLine = defender == null
                ? "- `" + candidate.getDefenderFaction() + "`"
                : "- " + defender.getFactionEmoji() + " = " + defender.getUserName();
        return "## Lazax War Archives Replay\n"
                + roleMention
                + "\n"
                + "**Game:** `" + candidate.getGameName() + "`\n"
                + "**Game Link:** [Open Game](https://asyncti4.com/game/" + candidate.getGameName() + ")\n"
                + "**System:** " + tileRepresentation + "\n"
                + "**Combat:** Space Combat\n"
                + "**Joint Score:** `" + formatDecimal(observation.getJointScore()) + "`\n"
                + "**Predict the winner by reacting below.**\n"
                + "**Replay begins in 10 minutes.**\n"
                + attackerLine + "\n"
                + defenderLine;
    }

    public double computeFairnessRatio(
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp,
            double attackerExpectedHits,
            double defenderExpectedHits) {
        double strengthFairnessRatio =
                safeRatio(Math.min(attackerStrength, defenderStrength), Math.max(attackerStrength, defenderStrength));
        double hpFairnessRatio = safeRatio(Math.min(attackerHp, defenderHp), Math.max(attackerHp, defenderHp));
        double expectedHitsFairnessRatio = safeRatio(
                Math.min(attackerExpectedHits, defenderExpectedHits),
                Math.max(attackerExpectedHits, defenderExpectedHits));
        return (strengthFairnessRatio + hpFairnessRatio + expectedHitsFairnessRatio) / 3.0;
    }

    private Map<String, Integer> getDamagedCountsByAsyncId(Tile tile, Player player) {
        Map<String, Integer> damagedCountsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                if (!unitKey.getColorID().equalsIgnoreCase(player.getColorID())) continue;
                int damagedUnits = unitHolder.getDamagedUnitCount(unitKey);
                if (damagedUnits <= 0) continue;
                damagedCountsByAsyncId.merge(unitKey.asyncID(), damagedUnits, Integer::sum);
            }
        }
        return damagedCountsByAsyncId;
    }

    private double computeExpectedHits(int totalDice, int hitsOn) {
        if (totalDice <= 0 || hitsOn <= 0) return 0;
        return totalDice * Math.max(0, 11 - hitsOn) / 10.0;
    }

    private String extractSpaceOnlySummary(String summary) {
        String[] lines = summary.split("\n");
        StringBuilder trimmed = new StringBuilder();
        boolean inSpaceSection = false;
        for (String line : lines) {
            if (trimmed.isEmpty()) {
                trimmed.append(line).append('\n');
                continue;
            }
            if (!inSpaceSection && "Space".equalsIgnoreCase(line.trim())) {
                inSpaceSection = true;
                trimmed.append(line).append('\n');
                continue;
            }
            if (!inSpaceSection) continue;
            trimmed.append(line).append('\n');
            if ("----------".equals(line.trim())) break;
        }
        return trimmed.toString().trim();
    }

    private String formatActivePlayerSummary(Game game) {
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) {
            return "**Active Player:** None\n";
        }
        String factionName = activePlayer.getFactionModel() == null
                ? activePlayer.getFaction()
                : activePlayer.getFactionModel().getFactionName();
        return "**Active Player:** " + activePlayer.getFactionEmoji() + " " + factionName + " "
                + activePlayer.getUserName() + "\n";
    }

    private String formatReplayHeader(
            Game game, Tile tile, Player attacker, Player defender, String activePlayerSummary) {
        return "## Lazax Candidate Recorded\n"
                + "**Game:** `" + game.getName() + "`\n"
                + "**Game Link:** [Open Game](https://asyncti4.com/game/" + game.getName() + ")\n"
                + activePlayerSummary
                + "**System:** " + tile.getRepresentationForButtons() + "\n"
                + "**Combat:** Space Combat\n"
                + "**Participants:** " + attacker.getFactionEmoji() + " `" + attacker.getFaction() + "` vs "
                + defender.getFactionEmoji() + " `" + defender.getFaction() + "`";
    }

    private String formatDecimal(double value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    public String toJson(Object value) {
        try {
            return JsonMapperManager.basic().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private double safeRatio(double weaker, double stronger) {
        if (stronger <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, weaker / stronger));
    }

    public record SpaceCombatSnapshot(
            CombatContestType combatType,
            String replaySummaryText,
            String activePlayerSummary,
            String boardSummary,
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp,
            double attackerExpectedHits,
            double defenderExpectedHits) {}

    public record FleetStrength(double value, double hp, double expectedHits) {}
}
