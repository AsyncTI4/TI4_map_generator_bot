package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Space;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class AshenLeadersHandler {

    private static final String COMMANDER_ID = "ashencommander";
    private static final String HERO_TARGET_SYSTEM_PREFIX = "ashenHeroTargetSystem_";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void postHeroButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (event == null || game == null || player == null) {
            return;
        }

        List<Tile> targetTiles = getHeroTargetTiles(game);
        if (targetTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " has no system containing planets for _Asera, the Ashen hero_.");
            return;
        }

        String message = player.getRepresentationUnfogged()
                + ", choose a system for _Asera, the Ashen hero_. Each of your units with BOMBARDMENT will roll "
                + "separately against each planet in that system.";
        String prefix = player.factionButtonChecker() + HERO_TARGET_SYSTEM_PREFIX;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(getHeroTargetButtons(player, game, targetTiles), prefix, 0));
    }

    @ButtonHandler(HERO_TARGET_SYSTEM_PREFIX)
    public static void resolveHeroTargetSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Tile> targetTiles = getHeroTargetTiles(game);
        String message = player.getRepresentationUnfogged()
                + ", choose a system for _Asera, the Ashen hero_. Each of your units with BOMBARDMENT will roll "
                + "separately against each planet in that system.";
        String prefix = player.factionButtonChecker() + HERO_TARGET_SYSTEM_PREFIX;
        List<Button> buttons = getHeroTargetButtons(player, game, targetTiles);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(HERO_TARGET_SYSTEM_PREFIX.length());
        Tile targetTile = targetTiles.stream()
                .filter(tile -> tile.getPosition().equals(position))
                .findFirst()
                .orElse(null);
        if (targetTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile bombardmentTile = createHeroBombardmentTile(game, player, targetTile);
        if (CombatRollService.getUnitsInBombardment(bombardmentTile, player, event)
                .isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No units with BOMBARDMENT are available to roll.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " is resolving _Asera, the Ashen hero_ against "
                        + targetTile.getRepresentationForButtons(game, player) + ".");
        AshenUnitHandler.clearFlagshipBombardmentContexts(game);
        for (Planet targetPlanet : targetTile.getPlanetUnitHolders()) {
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    MAPPER.writeValueAsString(
                            buildHeroBombardmentAssignments(player, bombardmentTile, targetPlanet.getName())));
            game.setStoredValue("bombardmentTarget" + player.getFaction(), targetPlanet.getName());
            CombatRollService.secondHalfOfCombatRoll(
                    player, game, event, bombardmentTile, "space", CombatRollType.bombardment, false);
        }
        game.removeStoredValue("assignedBombardment" + player.getFaction());
        game.removeStoredValue("bombardmentTarget" + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static void offerCommanderBombardmentButtons(
            GenericInteractionCreateEvent event, Game game, Player player, int hits) {
        if (event == null
                || game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER_ID)) {
            return;
        }

        int displayedHits = player.hasTech("x89c4") ? hits / 2 : hits;
        List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(player, true).stream()
                .filter(button -> !"deleteButtons".equals(button.getCustomId()))
                .toList();
        for (int hit = 0; hit < displayedHits; hit++) {
            String message = player.getRepresentationUnfogged()
                    + ", **Karos**: for this BOMBARDMENT hit, you may either gain 1 commodity or convert 1 of "
                    + "your commodities to a trade good."
                    + "\n-# You have (" + player.getCommoditiesRepresentation() + ") commodities.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
    }

    private static List<Tile> getHeroTargetTiles(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getPlanetUnitHolders().isEmpty())
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList();
    }

    private static List<Button> getHeroTargetButtons(Player player, Game game, List<Tile> targetTiles) {
        return targetTiles.stream()
                .map(tile -> Buttons.red(
                        player.factionButtonChecker() + HERO_TARGET_SYSTEM_PREFIX + tile.getPosition(),
                        "Bombard " + tile.getRepresentationForButtons(game, player),
                        FactionEmojis.ashen))
                .toList();
    }

    private static Tile createHeroBombardmentTile(Game game, Player player, Tile targetTile) {
        Space aggregateSpace = new Space(Constants.SPACE, Constants.SPACE_CENTER_POSITION);
        for (Tile sourceTile : game.getTileMap().values()) {
            Map<Pair<UnitModel, UnitHolder>, Integer> bombardmentUnits =
                    CombatRollService.getUnitsInBombardment(sourceTile, player, null);
            for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : bombardmentUnits.entrySet()) {
                aggregateSpace.addUnit(
                        Units.getUnitKey(entry.getKey().getLeft().getUnitType(), player.getColor()), entry.getValue());
            }
        }
        return new Tile(targetTile.getTileID(), targetTile.getPosition(), aggregateSpace);
    }

    private static List<BombardmentAssignment> buildHeroBombardmentAssignments(
            Player player, Tile sourceTile, String targetPlanet) {
        List<BombardmentAssignment> assignments = new ArrayList<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> bombardmentUnits =
                CombatRollService.getUnitsInBombardment(sourceTile, player, null);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : bombardmentUnits.entrySet()) {
            Pair<UnitModel, UnitHolder> unit = entry.getKey();
            int galvanizedCount =
                    unit.getRight().getGalvanizedUnitCount(unit.getLeft().getUnitType(), player.getColorID());
            for (int count = 0; count < entry.getValue(); count++) {
                assignments.add(new BombardmentAssignment(
                        unit.getLeft().getAsyncId(),
                        targetPlanet,
                        galvanizedCount > 0,
                        BombardmentAssignmentType.UNIT));
                galvanizedCount--;
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            assignments.add(
                    new BombardmentAssignment("plasmascoring", targetPlanet, false, BombardmentAssignmentType.TECH));
        }
        if (player.getGame() != null
                && (player.getGame().playerHasLeaderUnlockedOrAlliance(player, "argentcommander")
                        || player.hasTech("tf-zealous"))) {
            assignments.add(new BombardmentAssignment(
                    "argentcommander", targetPlanet, false, BombardmentAssignmentType.LEADER));
        }
        return assignments;
    }
}
