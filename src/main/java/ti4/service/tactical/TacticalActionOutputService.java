package ti4.service.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.GMService;

@UtilityClass
public class TacticalActionOutputService {

    public void refreshButtonsAndMessageForChoosingTile(ButtonInteractionEvent event, Game game, Player player) {
        String message = buildMessageForTacticalAction(game, player);
        List<Button> systemButtons = TacticalActionService.getTilesToMoveFrom(player, game, event);
        if (event == null) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, systemButtons);
        } else {
            MessageHelper.editMessageWithButtons(event, message, systemButtons);
        }
    }

    public void refreshButtonsAndMessageForTile(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        String message = buildMessageForSingleSystem(game, player, tile);
        List<Button> systemButtons =
                ButtonHelperTacticalAction.getButtonsForAllUnitsInSystem(player, game, tile, moveOrRemove);
        if (event == null) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, systemButtons);
        } else {
            MessageHelper.editMessageWithButtons(event, message, systemButtons);
        }
    }

    private Set<String> positionsMovedFrom(Game game) {
        return game.getTacticalActionDisplacement().keySet().stream()
                .map(uhKey -> uhKey.split("-")[0])
                .filter(pos -> game.getTileByPosition(pos) != null)
                .collect(Collectors.toSet());
    }

    private List<String> summariesPerSystem(Game game, Player player, Set<String> positions, boolean condensed) {
        List<String> summaries = new ArrayList<>(positions.stream()
                .map(game::getTileByPosition)
                .map(tile -> buildMessageForSingleSystem(game, player, tile, condensed, false))
                .toList());
        String remainder = buildShortSummary(game, positions);
        if (remainder != null) summaries.add(remainder);
        return summaries;
    }

    private String buildMessageForTacticalAction(Game game, Player player) {
        StringBuilder sb = new StringBuilder("## Tactical Action in system ");
        Tile activeSystem = getActiveSystem(game);
        sb.append(activeSystem.getRepresentationForButtons(game, player)).append(":\n\n");

        Set<String> positions = positionsMovedFrom(game);
        List<String> summaries = summariesPerSystem(game, player, positions, false);
        sb.append(String.join("\n\n", summaries));
        if (sb.length() > 1950) return buildCondensedMessageForTacticalAction(game, player);
        return sb.toString();
    }

    private String buildCondensedMessageForTacticalAction(Game game, Player player) {
        StringBuilder sb = new StringBuilder("## Tactical Action in system ");
        Tile activeSystem = getActiveSystem(game);
        sb.append(activeSystem.getRepresentationForButtons(game, player)).append(":\n\n");

        Set<String> positions = positionsMovedFrom(game);
        List<String> summaries = summariesPerSystem(game, player, positions, true);
        sb.append(String.join("\n\n", summaries));
        return sb.toString();
    }

    public String buildMessageForSingleSystem(Game game, Player player, Tile tile) {
        return buildMessageForSingleSystem(game, player, tile, false, true);
    }

    private String buildMessageForSingleSystem(
            Game game, Player player, Tile tile, boolean condensed, boolean inclSummary) {
        String linePrefix = "> " + player.getFactionEmoji();
        int distance = CheckDistanceHelper.getDistanceBetweenTwoTiles(
                game, player, tile.getPosition(), game.getActiveSystem(), true);
        int riftDistance = CheckDistanceHelper.getDistanceBetweenTwoTiles(
                game, player, tile.getPosition(), game.getActiveSystem(), false);

        var displaced = game.getTacticalActionDisplacement();
        Set<UnitKey> movingUnitsFromTile = displaced.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(tile.getPosition() + "-"))
                .map(Entry::getValue)
                .filter(Objects::nonNull)
                .flatMap(f -> f.entrySet().stream())
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(Entry::getKey)
                .collect(Collectors.toSet());

        String header = (condensed ? "-# From system " : "From system ");
        StringBuilder summary = new StringBuilder(header).append(tile.getRepresentationForButtons(game, player));
        if (!condensed) {
            summary.append(" (")
                    .append(distance)
                    .append(" tile")
                    .append(distance == 1 ? "" : "s")
                    .append(" away)")
                    .append("\n");
        } else {
            summary.append(" (").append(distance).append(" away)").append("\n");
        }
        if (movingUnitsFromTile.isEmpty()) {
            if (condensed) return null;
            return summary + "> Nothing";
        }

        List<String> lines = new ArrayList<>();
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            String uhKey = tile.getPosition() + "-" + uh.getName();
            if (!displaced.containsKey(uhKey)) continue;

            Map<UnitKey, List<Integer>> unitMap = displaced.get(uhKey);
            if (unitMap == null) {
                displaced.remove(uhKey);
                continue;
            }

            for (UnitKey key : new HashSet<>(unitMap.keySet())) {
                if (unitMap.get(key) == null) {
                    unitMap.remove(key);
                    continue;
                }

                List<Integer> states = unitMap.get(key);
                if (condensed) {
                    int amt = states.stream().mapToInt(i -> i).sum();
                    String unitStr = key.unitEmoji().emojiString().repeat(amt);
                    if (amt > 2) unitStr = amt + "x " + key.unitEmoji();
                    lines.add(unitStr);
                    continue;
                }
                String color = "";
                if (!key.getColor().equalsIgnoreCase(player.getColor())) {
                    color = " " + key.getColor() + " ";
                }
                for (UnitState state : UnitState.values()) {
                    int amt = states.get(state.ordinal());
                    if (amt == 0) continue;

                    String stateStr = (state == UnitState.none) ? "" : " " + state.humanDescr();
                    String unitMoveStr = linePrefix + " moved " + amt + color + stateStr + " " + key.unitEmoji();

                    String unitHolderStr =
                            (uh instanceof Planet p) ? " from the planet " + p.getRepresentation(game) : "";
                    unitMoveStr += unitHolderStr;

                    String distanceStr = validateMoveValue(game, player, tile, key, distance, riftDistance);
                    unitMoveStr += distanceStr;
                    lines.add(unitMoveStr);
                }
            }
        }
        if (condensed) {
            summary.append(String.join(", ", lines));
            return summary.toString();
        }
        summary.append(String.join("\n", lines));
        String extraSummary = buildShortSummary(game, Set.of(tile.getPosition()));
        if (extraSummary != null && inclSummary) summary.append("\n").append(extraSummary);
        return summary.toString();
    }

    private String buildShortSummary(Game game, Set<String> excludeTiles) {
        StringBuilder sb = new StringBuilder("-# Units from elsewhere: ");
        Map<UnitKey, Integer> quantities = new HashMap<>();
        for (var entry : game.getTacticalActionDisplacement().entrySet()) {
            String pos = entry.getKey().split("-")[0];
            if (excludeTiles.contains(pos)) continue;
            for (var unitEntry : entry.getValue().entrySet()) {
                int amt = unitEntry.getValue().stream().mapToInt(a -> a).sum();
                UnitKey key = unitEntry.getKey();
                quantities.put(key, quantities.getOrDefault(key, 0) + amt);
            }
        }
        List<String> units = new ArrayList<>();
        for (Entry<UnitKey, Integer> entry : quantities.entrySet()) {
            UnitKey key = entry.getKey();
            int amt = entry.getValue();
            String unitStr = key.unitEmoji().emojiString().repeat(amt);
            if (amt > 2) unitStr = amt + "x " + key.unitEmoji();
            units.add(unitStr);
        }
        if (units.isEmpty()) return null;
        sb.append(String.join(", ", units));
        return sb.toString();
    }

    private String validateMoveValue(
            Game game, Player player, Tile tile, UnitKey unit, int distance, int riftDistance) {
        int moveValue = getUnitMoveValue(game, player, tile, unit, false);
        if (moveValue == 0) return "";

        String output = "";
        if (distance > moveValue && distance < 90) {
            output += " (distance exceeds move value (" + distance + " > " + moveValue + ")";
            int maxBonus = 0;
            if (player.hasTech("gd")) {
                maxBonus++;
                output += ", used _Gravity Drive_)";
            } else {
                output += ", __does not have _Gravity Drive___)";
            }
            if (player.getTechs().contains("dsgledb")) {
                maxBonus++;
                output += " (has _Lightning Drives_ for +1 movement if not transporting)";
            }
            if (riftDistance < distance) {
                // maxBonus += distance - riftDistance; // Don't automatically count rifts, allow the GM to verify
                output += " (gravity rifts along a path could add +" + (distance - riftDistance) + " movement if used)";
                game.setStoredValue("possiblyUsedRift", "yes");
            }
            if ((distance > (moveValue + maxBonus)) && game.isFowMode()) {
                GMService.logPlayerActivity(game, player, output);
            }
        }
        if (riftDistance < distance) {
            game.setStoredValue("possiblyUsedRift", "yes");
        }
        if (player.hasAbility("celestial_guides")) {
            game.setStoredValue("possiblyUsedRift", "");
        }
        return output;
    }

    private int getUnitMoveValue(Game game, Player player, Tile tile, UnitKey unit, boolean skipBonus) {
        UnitModel model = player.getUnitFromUnitKey(unit);
        if (model == null) {
            return 0;
        }

        boolean movingFromHome = tile == player.getHomeSystemTile();
        boolean tileHasWormhole = FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition());

        // Calculate base move value (pretty easy)
        int baseMoveValue = model.getMoveValue();
        if (baseMoveValue == 0) return 0;
        if (tile.isNebula()
                && !player.hasAbility("voidborn")
                && !player.hasAbility("celestial_being")
                && !player.hasTech("absol_amd")
                && !player.getRelics().contains("circletofthevoid")) {
            baseMoveValue = 1;
        }
        if (skipBonus) return baseMoveValue;

        // Calculate bonus move value
        int bonusMoveValue = 0;
        if (player.hasTech("as") && FoWHelper.isTileAdjacentToAnAnomaly(game, game.getActiveSystem(), player)) {
            bonusMoveValue++;
        }
        if (player.hasAbility("slipstream") && (tileHasWormhole || movingFromHome)) {
            bonusMoveValue++;
        }
        if (!game.getStoredValue("crucibleBoost").isEmpty()) {
            bonusMoveValue += 1;
        }
        if (!game.getStoredValue("flankspeedBoost").isEmpty()) {
            bonusMoveValue += 1;
        }
        if (!game.getStoredValue("baldrickGDboost").isEmpty()) {
            bonusMoveValue += 1;
        }

        Tile activeSystem = getActiveSystem(game);
        for (UnitHolder uhPlanet : activeSystem.getPlanetUnitHolders()) {
            if (player.getPlanets().contains(uhPlanet.getName())) {
                continue;
            }
            for (String attachment : uhPlanet.getTokenList()) {
                if (attachment.contains("sigma_weirdway")) {
                    bonusMoveValue -= 1;
                    break;
                }
            }
        }

        return baseMoveValue + bonusMoveValue;
    }

    private Tile getActiveSystem(Game game) {
        return FOWPlusService.isVoid(game, game.getActiveSystem())
                ? FOWPlusService.voidTile(game.getActiveSystem())
                : game.getTileByPosition(game.getActiveSystem());
    }
}
