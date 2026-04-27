package ti4.contest.replay.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.model.LeaderModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.combat.CombatStatsService;
import ti4.service.combat.CombatUnitSelectionHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.spring.service.contest.CombatContestType;

@UtilityClass
/**
 * Shared combat helper logic for replay candidate filtering, scoring inputs, and Lazax replay text generation.
 */
public class LazaxCombatSupport {

    private static final Set<String> COMBAT_SUMMARY_TECH_ALIASES = Set.of("da", "asc", "gls", "x89", "x89c4");
    private static final Set<String> COMBAT_SUMMARY_RELIC_ALIASES = Set.of(
            "metalivoidarmaments",
            "metalivoidshielding",
            "lightrailordnance",
            "thalnos",
            "baldrick_crownofthalnos",
            "pi_thalnos");
    private static final Set<String> SPACE_COMBAT_AGENT_IDS =
            Set.of("titansagent", "bastionagent", "letnevagent", "nomadagentthundarian", "yinagent");
    private static final Set<String> SPACE_COMBAT_COMMANDER_IDS =
            Set.of("redcreusscommander", "crimsoncommander", "mentakcommander", "ralnelcommander");
    private static final Set<String> SPACE_COMBAT_HERO_IDS =
            Set.of("mentakhero", "keleresherokuuasi", "redcreusshero", "crimsonhero", "bastionhero");
    private static final Comparator<TechnologyModel> TECH_COMPARATOR = Comparator.comparingInt(
                    LazaxCombatSupport::getTechTypeOrder)
            .thenComparingInt(tech -> tech.getRequirements().orElse("").length())
            .thenComparing(TechnologyModel::getName, String.CASE_INSENSITIVE_ORDER);

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
        for (Map.Entry<UnitModel, Integer> entry : CombatUnitSelectionHelper.collectCombatRoundUnits(
                        tile, space, player)
                .entrySet()) {
            UnitModel unitModel = entry.getKey();
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

    public String formatCombatTechSummary(Tile tile, Player attacker, Player defender) {
        if (attacker == null || defender == null) return null;

        StringBuilder message = new StringBuilder("## Combat Technologies\n")
                .append(formatCombatTechLine(attacker))
                .append("\n")
                .append(formatCombatTechLine(defender));
        String relicSection = formatCombatRelicSection(attacker, defender);
        if (relicSection != null) {
            message.append("\n").append(relicSection);
        }
        String lawSection = formatCombatLawSection(attacker, defender);
        if (lawSection != null) {
            message.append("\n").append(lawSection);
        }
        String effectSection = formatCombatEffectSection(tile, attacker.getGame());
        if (effectSection != null) {
            message.append("\n").append(effectSection);
        }
        String actionCardSection = formatActionCardSection(attacker, defender);
        if (actionCardSection != null) {
            message.append("\n").append(actionCardSection);
        }
        String leaderSection = formatCombatLeaderSection(tile, attacker, defender);
        if (leaderSection != null) {
            message.append("\n").append(leaderSection);
        }
        return message.toString();
    }

    public String formatReplayAnnouncement(
            Game game,
            CombatObservationEntity observation,
            CombatCandidateEntity candidate,
            String roleMention,
            String startSummaryText) {
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        String tileRepresentation = tile == null ? candidate.getTilePosition() : tile.getRepresentationForButtons();
        String attackerLine = formatReplayLegendLine(attacker, candidate.getAttackerFaction());
        String defenderLine = formatReplayLegendLine(defender, candidate.getDefenderFaction());

        String openerText = "## A New Combat Contest Has Emerged!\n"
                + roleMention
                + "\n"
                + "**System:** " + tileRepresentation + "\n"
                + "**Combat:** Space Combat\n"
                + "**Predict the winner by reacting below.**\n"
                + "_Losers get -4 points._\n"
                + attackerLine + "\n"
                + defenderLine;
        String boardSummary = extractRecordedBoardSummary(startSummaryText);
        if (boardSummary == null || boardSummary.isBlank()) {
            return openerText;
        }
        return openerText + "\n\n" + boardSummary;
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

    private String formatCombatTechLine(Player player) {
        String techSummary = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !player.getPurgedTechs().contains(tech.getAlias()))
                .filter(tech -> tech.isFactionTech()
                        || tech.isUnitUpgrade()
                        || COMBAT_SUMMARY_TECH_ALIASES.contains(tech.getAlias()))
                .sorted(TECH_COMPARATOR)
                .map(tech -> formatCombatTech(player, tech))
                .reduce((left, right) -> left + ", " + right)
                .orElse("No technologies");
        return formatPlayerSummaryLine(player, techSummary);
    }

    private String formatCombatTech(Player player, TechnologyModel tech) {
        String techSummary = tech.getCondensedReqsEmojis(false) + " " + tech.getName();
        if (!player.getExhaustedTechs().contains(tech.getAlias())) {
            return techSummary;
        }
        return "~~" + techSummary + "~~";
    }

    private String formatCombatRelicSection(Player attacker, Player defender) {
        String attackerRelics = formatCombatRelicLine(attacker);
        String defenderRelics = formatCombatRelicLine(defender);
        if (attackerRelics == null && defenderRelics == null) return null;

        StringBuilder section = new StringBuilder("## Relics");
        if (attackerRelics != null) {
            section.append("\n").append(attackerRelics);
        }
        if (defenderRelics != null) {
            section.append("\n").append(defenderRelics);
        }
        return section.toString();
    }

    private String formatCombatRelicLine(Player player) {
        String relicSummary = player.getRelics().stream()
                .distinct()
                .filter(COMBAT_SUMMARY_RELIC_ALIASES::contains)
                .map(Mapper::getRelic)
                .filter(Objects::nonNull)
                .map(relic -> ExploreEmojis.Relic + " " + relic.getName())
                .sorted(String::compareToIgnoreCase)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        if (relicSummary == null) return null;

        return formatPlayerSummaryLine(player, relicSummary);
    }

    private String formatCombatLawSection(Player attacker, Player defender) {
        String attackerLaw = formatCombatLawLine(attacker);
        String defenderLaw = formatCombatLawLine(defender);
        if (attackerLaw == null && defenderLaw == null) return null;

        StringBuilder section = new StringBuilder("## Laws");
        if (attackerLaw != null) {
            section.append("\n").append(attackerLaw);
        }
        if (defenderLaw != null) {
            section.append("\n").append(defenderLaw);
        }
        return section.toString();
    }

    private String formatCombatLawLine(Player player) {
        Game game = player.getGame();
        if (game == null || !IsPlayerElectedService.isPlayerElected(game, player, "prophecy")) {
            return null;
        }
        return formatPlayerSummaryLine(player, CardEmojis.Agenda + " Prophecy of Ixth");
    }

    private String formatCombatEffectSection(Tile tile, Game game) {
        if (!isQuietusActive(tile, game)) {
            return null;
        }
        return "## Combat Effects\n- " + FactionEmojis.Crimson + " " + UnitEmojis.flagship
                + " Quietus: active Breach in play, opposing unit abilities are canceled.";
    }

    private String formatCombatLeaderSection(Tile tile, Player attacker, Player defender) {
        Game game = attacker.getGame();
        if (game == null || tile == null) return null;

        StringBuilder section = new StringBuilder("## Leaders");
        boolean hasLines = false;

        String attackerLine = formatCombatParticipantLeaderLine(attacker);
        if (attackerLine != null) {
            section.append("\n").append(attackerLine);
            hasLines = true;
        }

        String defenderLine = formatCombatParticipantLeaderLine(defender);
        if (defenderLine != null) {
            section.append("\n").append(defenderLine);
            hasLines = true;
        }

        String otherLeaderSection = formatOtherLeaderSection(game, attacker, defender);
        if (otherLeaderSection != null) {
            section.append("\n").append(otherLeaderSection);
            hasLines = true;
        }

        return hasLines ? section.toString() : null;
    }

    private String formatOtherLeaderSection(Game game, Player attacker, Player defender) {
        List<String> lines = game.getRealPlayers().stream()
                .filter(player -> !player.equals(attacker) && !player.equals(defender))
                .map(LazaxCombatSupport::formatOtherCombatLeaderLine)
                .filter(Objects::nonNull)
                .toList();
        if (lines.isEmpty()) return null;
        return "### Other Leaders\n" + String.join("\n", lines);
    }

    private String formatOtherCombatLeaderLine(Player player) {
        List<String> leaders = player.getLeaders().stream()
                .filter(leader -> SPACE_COMBAT_AGENT_IDS.contains(leader.getId()))
                .map(LazaxCombatSupport::formatLeaderSummary)
                .toList();
        return leaders.isEmpty() ? null : formatPlayerLeaderBlock(player, leaders);
    }

    private String formatCombatParticipantLeaderLine(Player player) {
        List<String> leaders = player.getLeaders().stream()
                .map(LazaxCombatSupport::formatLeaderSummary)
                .toList();
        return leaders.isEmpty() ? null : formatPlayerLeaderBlock(player, leaders);
    }

    private String formatLeaderSummary(Leader leader) {
        Optional<LeaderModel> leaderModel = leader.getLeaderModel();
        if (leaderModel.isEmpty()) {
            return leader.getId();
        }

        String summary = LeaderEmojis.getLeaderTypeEmoji(leaderModel.get().getType()) + " "
                + leaderModel.get().getLeaderEmoji() + " " + leaderModel.get().getName();
        if (leader.isLocked() || leader.isExhausted() || leader.isActive()) {
            return "~~" + summary + "~~";
        }
        return summary;
    }

    private String formatActionCardSection(Player attacker, Player defender) {
        List<String> lines = new ArrayList<>();
        lines.add(formatPlayerSummaryLine(
                attacker, CardEmojis.ActionCard + " " + attacker.getAcCount() + " action cards"));
        lines.add(formatPlayerSummaryLine(
                defender, CardEmojis.ActionCard + " " + defender.getAcCount() + " action cards"));
        return "## Action Cards\n" + String.join("\n", lines);
    }

    private String formatPlayerLeaderBlock(Player player, List<String> leaders) {
        StringBuilder builder = new StringBuilder(formatPlayerSummaryHeader(player));
        for (String leader : leaders) {
            builder.append("\n  - ").append(leader);
        }
        return builder.toString();
    }

    private boolean isQuietusActive(Tile combatTile, Game game) {
        if (combatTile == null || game == null) return false;
        Player quietusOwner = Helper.getPlayerFromUnit(game, "crimson_flagship");
        if (quietusOwner == null) return false;
        UnitHolder combatSpace = combatTile.getSpaceUnitHolder();
        return combatSpace != null && combatSpace.getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE);
    }

    private String formatPlayerSummaryLine(Player player, String summary) {
        return formatPlayerSummaryHeader(player) + ": " + summary;
    }

    private String formatPlayerSummaryHeader(Player player) {
        String emoji = player.getFactionEmoji();
        if (emoji == null || emoji.isBlank()) {
            return "- " + player.getFaction();
        }
        return "- " + emoji;
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
                + "**Game " + game.getName() + ":** [Open Game](https://asyncti4.com/game/" + game.getName()
                + ")\n"
                + activePlayerSummary
                + "**System:** " + tile.getRepresentationForButtons() + "\n"
                + "**Combat:** Space Combat\n"
                + "**Participants:** " + attacker.getFactionEmoji() + " `" + attacker.getFaction() + "` vs "
                + defender.getFactionEmoji() + " `" + defender.getFaction() + "`";
    }

    private static int getTechTypeOrder(TechnologyModel tech) {
        TechnologyModel.TechnologyType type = tech.getFirstType();
        return switch (type) {
            case PROPULSION -> 0;
            case BIOTIC -> 1;
            case CYBERNETIC -> 2;
            case WARFARE -> 3;
            case UNITUPGRADE -> 4;
            case GENERICTF -> 5;
            case NONE -> 6;
        };
    }

    private String formatReplayLegendLine(Player player, String fallbackFaction) {
        if (player == null) {
            return "- `" + fallbackFaction + "`";
        }
        return "- " + ColorEmojis.getColorEmoji(player.getColor()) + " " + player.getFactionEmoji() + " = "
                + player.getUserName();
    }

    private String extractRecordedActivePlayerSummary(String summaryText) {
        if (summaryText == null || summaryText.isBlank()) return null;
        for (String line : summaryText.split("\n")) {
            if (line.startsWith("**Active Player:**")) {
                return line + "\n";
            }
        }
        return null;
    }

    private String extractRecordedBoardSummary(String summaryText) {
        if (summaryText == null || summaryText.isBlank()) return null;
        int separator = summaryText.indexOf("\n\n");
        if (separator < 0 || separator + 2 >= summaryText.length()) {
            return null;
        }
        return summaryText.substring(separator + 2).trim();
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
