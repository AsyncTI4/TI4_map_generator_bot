package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Space;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class AshenLeadersHandler {

    private static final String AGENT_ID = "ashenagent";
    private static final String AGENT_SELECT_TARGET = "ashenAgentSelectTarget";
    private static final String AGENT_USE_ON_TARGET_PREFIX = "ashenAgentUseOn_";
    private static final String AGENT_CHOOSE_UNIT_PREFIX = "ashenAgentChooseUnit_";
    private static final String AGENT_PLACE_DESTINATION_PREFIX = "ashenAgentPlace_";
    private static final String COMMANDER_ID = "ashencommander";
    private static final String HERO_TARGET_SYSTEM_PREFIX = "ashenHeroTargetSystem_";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Button getAshTenderCardsInfoButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + AGENT_SELECT_TARGET, "Use Orrun", FactionEmojis.ashen);
    }

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
        String message = displayedHits > 0
                ? player.getRepresentationUnfogged() + ", **Karos**: for each of these "
                        + StringHelper.pluralize(displayedHits, "hit")
                        + ", you may either gain 1 commodity or convert 1 of your commodities to a trade good."
                        + "\n-# You have (" + player.getCommoditiesRepresentation() + ") commodities."
                : player.getRepresentationUnfogged()
                        + ", **Karos**: if you produced 1 or more BOMBARDMENT hits before modifiers, you may either gain 1 commodity or convert 1 of your commodities to a trade good for each such hit."
                        + "\n-# You have (" + player.getCommoditiesRepresentation() + ") commodities.";
        List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(player, false);
        MessageChannel primaryChannel = event.getMessageChannel();
        MessageHelper.sendMessageToChannelWithButtons(primaryChannel, message, buttons);
    }

    @ButtonHandler(AGENT_SELECT_TARGET)
    public static void offerAshTenderTargetButtons(ButtonInteractionEvent event, Game game, Player ashenPlayer) {
        if (!ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> !getEligibleUnitModels(target).isEmpty())
                .filter(target -> !getEligibleDestinationTiles(game, target).isEmpty())
                .map(target -> Buttons.gray(
                        ashenPlayer.factionButtonChecker()
                                + AGENT_USE_ON_TARGET_PREFIX
                                + ashenPlayer.getFaction()
                                + "~"
                                + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "No player currently has both an eligible **Orrun** unit choice and an eligible destination system.");
            return;
        }

        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event, ashenPlayer.getRepresentationUnfogged() + ", choose the player who may use **Orrun**.", buttons);
    }

    @ButtonHandler(AGENT_USE_ON_TARGET_PREFIX)
    public static void useAshTenderOnTarget(
            ButtonInteractionEvent event, Game game, Player ashenPlayer, String buttonID) {
        if (!ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        String payload = buttonID.substring(AGENT_USE_ON_TARGET_PREFIX.length());
        String[] parts = payload.split("~", 2);
        if (parts.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** choice.");
            return;
        }

        String targetFaction = parts[1];
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }

        List<UnitModel> eligibleUnitModels = getEligibleUnitModels(target);
        List<Tile> eligibleTiles = getEligibleDestinationTiles(game, target);
        if (eligibleUnitModels.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentation(false, false)
                            + " has no eligible non-fighter, non-infantry ship units for **Orrun**.");
            return;
        }
        if (eligibleTiles.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentation(false, false)
                            + " no longer has any eligible destination systems for **Orrun**.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitModel unitModel : eligibleUnitModels) {
            buttons.add(Buttons.gray(
                    target.factionButtonChecker()
                            + AGENT_CHOOSE_UNIT_PREFIX
                            + ashenPlayer.getFaction()
                            + "~"
                            + unitModel.getAsyncId(),
                    unitModel.getName(),
                    unitModel.getUnitEmoji()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", choose which eligible unit **Orrun** should place for you.",
                buttons);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent **Orrun** unit-choice buttons to " + target.getRepresentationUnfoggedNoPing() + ".");
    }

    @ButtonHandler(AGENT_CHOOSE_UNIT_PREFIX)
    public static void chooseAshTenderUnit(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String payload = buttonID.substring(AGENT_CHOOSE_UNIT_PREFIX.length());
        String[] parts = payload.split("~", 2);
        if (parts.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** unit choice.");
            return;
        }

        Player ashenPlayer = game.getPlayerFromColorOrFaction(parts[0]);
        if (ashenPlayer == null || !ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        UnitModel selectedUnit = target.getUnitFromAsyncID(parts[1]);
        if (!isEligibleUnitModel(selectedUnit)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit is not eligible for **Orrun**.");
            return;
        }

        List<Tile> eligibleTiles = getEligibleDestinationTiles(game, target);
        if (eligibleTiles.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You no longer have any eligible destination systems for **Orrun**.");
            return;
        }

        List<Button> buttons = eligibleTiles.stream()
                .map(tile -> Buttons.gray(
                        target.factionButtonChecker()
                                + AGENT_PLACE_DESTINATION_PREFIX
                                + ashenPlayer.getFaction()
                                + "~"
                                + selectedUnit.getAsyncId()
                                + "~"
                                + tile.getPosition(),
                        "Place " + selectedUnit.getName() + " in " + tile.getRepresentationForButtons(game, target)))
                .toList();

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose which eligible system **Orrun** should place your "
                        + selectedUnit.getName()
                        + " in.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(AGENT_PLACE_DESTINATION_PREFIX)
    public static void placeAshTenderDestroyedShip(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String payload = buttonID.substring(AGENT_PLACE_DESTINATION_PREFIX.length());
        String[] parts = payload.split("~", 3);
        if (parts.length != 3) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** choice.");
            return;
        }

        Player ashenPlayer = game.getPlayerFromColorOrFaction(parts[0]);
        if (ashenPlayer == null || !ashenPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Orrun** is no longer available.");
            return;
        }

        UnitModel selectedUnit = target.getUnitFromAsyncID(parts[1]);
        if (!isEligibleUnitModel(selectedUnit)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit is not eligible for **Orrun**.");
            return;
        }

        Tile destination = game.getTileByPosition(parts[2]);
        if (destination == null
                || getEligibleDestinationTiles(game, target).stream()
                        .noneMatch(tile -> tile.getPosition().equals(destination.getPosition()))) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That destination is no longer eligible for **Orrun**.");
            return;
        }

        Leader agent = ashenPlayer.getLeader(AGENT_ID).orElse(null);
        if (agent == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find **Orrun**.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, ashenPlayer, agent);
        AddUnitService.addUnits(event, destination, game, target.getColor(), "1 " + selectedUnit.getAsyncId());
        ButtonHelper.deleteMessage(event);

        String message = ashenPlayer.getRepresentation()
                + " exhausted **Orrun**, the Ashen agent, to place "
                + target.getRepresentation()
                + "'s "
                + selectedUnit.getName()
                + " in "
                + destination.getRepresentationForButtons(game, target)
                + ".";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    ashenPlayer.getRepresentationUnfogged()
                            + " used **Orrun** on you and placed your "
                            + selectedUnit.getName()
                            + " in "
                            + destination.getRepresentationForButtons(game, target)
                            + ".");
        }
    }

    private static List<Tile> getEligibleDestinationTiles(Game game, Player target) {
        List<Tile> tiles = new ArrayList<>();
        String activeSystem = game.getActiveSystem();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().equalsIgnoreCase(activeSystem)) {
                continue;
            }
            if (FoWHelper.playerHasActualShipsInSystem(target, tile) && CommandCounterHelper.hasCC(target, tile)) {
                tiles.add(tile);
            }
        }
        return tiles;
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
                        galvanizedCount-- > 0,
                        BombardmentAssignmentType.UNIT));
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

    private static List<UnitModel> getEligibleUnitModels(Player target) {
        return target.getUnitModels().stream()
                .filter(AshenLeadersHandler::isEligibleUnitModel)
                .sorted(Comparator.comparing(UnitModel::getName))
                .toList();
    }

    private static boolean isEligibleUnitModel(UnitModel unitModel) {
        return unitModel != null
                && unitModel.getIsShip()
                && unitModel.getUnitType() != UnitType.Fighter
                && unitModel.getUnitType() != UnitType.Infantry;
    }
}
