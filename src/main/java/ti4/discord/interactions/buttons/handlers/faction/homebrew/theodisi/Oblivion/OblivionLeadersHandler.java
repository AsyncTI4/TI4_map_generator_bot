package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.model.UnitModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class OblivionLeadersHandler {
    private static final String AGENT = "oblivionagent";
    private static final String USE_AGENT = "useOblivionAgent";
    private static final String ADD_TOKEN = "addOblivionFrontierToken_";
    private static final String DISCARD = "discardPeekedFrontier_";
    private static final String AGENT_TARGET = "oblivionAgentTarget_";
    // Hero
    private static final String HERO_DRAWN = "oblivionHeroDrawn_";
    private static final String HERO_SELECTED = "oblivionHeroSelected_";
    private static final String HERO_CHOOSE = "chooseOblivionHeroTile_";
    private static final String HERO_PLACE = "placeOblivionHeroTile_";
    // Commander
    private static final String COMMANDER = "oblivioncommander";
    private static final String COMMANDER_SYSTEM = "oblivionCommanderSystem_";
    private static final String COMMANDER_UNIT = "oblivionCommanderUnit_";

    // Agent
    public static Button getOblivionAgentButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_AGENT, "Use Avaris the Seer", FactionEmojis.oblivion);
    }

    @ButtonHandler("useOblivionAgent_other")
    public static void chooseOblivionAgentTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return;
        }

        List<Button> targetButtons = new ArrayList<>();
        for (Player target : game.getRealPlayersExcludingThis(player)) {
            targetButtons.add(Buttons.green(
                    player.factionButtonChecker() + AGENT_TARGET + target.getFaction(),
                    target.getFactionNameOrColor(),
                    target.getFactionEmojiOrColor()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                "Please choose the target for Avaris the Seer, the Oblivion agent.",
                targetButtons);
    }

    @ButtonHandler(AGENT_TARGET)
    public static void resolveOblivionAgentTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        String faction = buttonID.replace(AGENT_TARGET, "");
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null
                || target == player
                || !game.getRealPlayersExcludingThis(player).contains(target)) {
            return;
        }

        Leader agent = player.getLeaderByID(AGENT).orElse(null);
        List<Button> buttons = getFrontierTokenButtons(game, target);
        if (agent == null || buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no eligible systems for a frontier token.");
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, player, agent);

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentation() + ", please choose the system in which to place a frontier token.",
                NewStuffHelper.buttonPagination(buttons, target.factionButtonChecker() + ADD_TOKEN, 0));
    }

    @ButtonHandler(USE_AGENT)
    public static void useOblivionAgent(ButtonInteractionEvent event, Player player, Game game) {
        if (game == null || player == null || !player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        Leader agent = player.getLeaderByID(AGENT).orElse(null);
        List<Button> buttons = getFrontierTokenButtons(game, player);
        if (agent == null || buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no eligible systems for a frontier token.");
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, player, agent);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the system in which to place a frontier token.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + ADD_TOKEN, 0));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(ADD_TOKEN)
    public static void resolveOblivionAgentToken(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = getFrontierTokenButtons(game, player);
        String message = player.getRepresentation() + ", please choose the system in which to place a frontier token.";
        String buttonPrefix = player.factionButtonChecker() + ADD_TOKEN;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        ButtonHelper.deleteMessage(event);

        String tile = buttonID.replace(ADD_TOKEN, "");
        Tile tilePos = game.getTileByPosition(tile);
        if (tilePos == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to locate that tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        tilePos.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Frontier token has been placed in " + tilePos.getRepresentation()
                        + ". The top card of the frontier deck has been sent to your `#cards-info` thread.");

        resolveAgentStep2(player, game);
    }

    public static void resolveAgentStep2(Player player, Game game) {
        if (player == null || game == null) {
            return;
        }

        List<String> frontierDeck = game.getExploreDeck(Constants.FRONTIER);
        if (frontierDeck.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "The frontier deck is empty.");
            return;
        }

        String cardId = frontierDeck.getFirst();
        ExploreModel card = Mapper.getExplore(cardId);

        List<Button> buttons = List.of(
                Buttons.red(player.factionButtonChecker() + DISCARD + cardId, "Discard"),
                Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you looked at the top card of the " + ExploreEmojis.Frontier
                        + " frontier deck and saw _" + card.getName() + "_.",
                List.of(card.getRepresentationEmbed()),
                buttons);
    }

    @ButtonHandler(DISCARD)
    public static void resolveDiscardFrontier(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String cardId = buttonID.replace(DISCARD, "");
        List<String> frontierDeck = game.getExploreDeck(Constants.FRONTIER);

        if (!frontierDeck.isEmpty() && cardId.equals(frontierDeck.getFirst())) {
            game.discardExplore(cardId);
        }

        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getFrontierTokenButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String frontierToken = Mapper.getTokenID(Constants.FRONTIER);

        for (Tile tile : game.getTileMap().values()) {
            if (!tile.getPlanetUnitHolders().isEmpty()
                    || !Mapper.getFrontierTileIds().contains(tile.getTileID())
                    || tile.getSpaceUnitHolder().getTokenList().contains(frontierToken)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + ADD_TOKEN + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    // Commander
    public static void offerCommanderProduction(Game game, Player player) {
        if (game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER)
                || getCommanderUnits(game, player).isEmpty()) {
            return;
        }

        List<Button> buttons = game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + COMMANDER_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }

        List<Button> offeredButtons = new ArrayList<>(buttons);
        offeredButtons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may produce 1 unit with a cost of 4 or less in a system that contains 1 of your ships using Deyra the Voidborn, the Oblivion commander.",
                offeredButtons);
    }

    @ButtonHandler(COMMANDER_SYSTEM)
    public static void chooseCommanderUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(COMMANDER_SYSTEM.length());
        Tile tile = game == null ? null : game.getTileByPosition(position);
        if (player == null
                || tile == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER)
                || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getCommanderUnits(game, player).stream()
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + COMMANDER_UNIT + position + "|" + unit.getAsyncId(),
                        "Produce 1 " + unit.getUnitType().humanReadableName(),
                        unit.getUnitEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose the unit to produce using Deyra the Voidborn, the Oblivion commander.",
                buttons);
    }

    @ButtonHandler(COMMANDER_UNIT)
    public static void resolveCommanderProduction(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(COMMANDER_UNIT.length()).split("\\|", 3);
        Tile tile = payload.length >= 2 && game != null ? game.getTileByPosition(payload[0]) : null;
        UnitModel unit = payload.length >= 2
                ? getCommanderUnits(game, player).stream()
                        .filter(model -> model.getAsyncId().equals(payload[1]))
                        .findFirst()
                        .orElse(null)
                : null;
        if (player == null
                || tile == null
                || unit == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER)
                || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (payload.length == 2 && !unit.getIsShip()) {
            List<Button> buttons = new ArrayList<>();
            for (Planet planet : tile.getPlanetUnitHolders()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + COMMANDER_UNIT + payload[0] + "|" + payload[1] + "|"
                                + planet.getName(),
                        "Produce on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + COMMANDER_UNIT + payload[0] + "|" + payload[1] + "|space",
                    "Produce in Space"));

            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", please choose where to produce the "
                            + unit.getUnitType().humanReadableName() + ".",
                    buttons);
            return;
        }

        String holder = payload.length == 3 ? payload[2] : "space";
        if ((unit.getIsShip() && !"space".equals(holder))
                || (!"space".equals(holder) && tile.getUnitHolderFromPlanet(holder) == null)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String placement = "1 " + unit.getAsyncId() + ("space".equals(holder) ? "" : " " + holder);
        AddUnitService.addUnits(event, tile, game, player.getColor(), placement);
        int cost = Math.max(0, (int) Math.ceil(unit.getCost()) - 1);
        game.setStoredValue("producedUnitCostFor" + player.getFaction(), Integer.toString(cost));
        player.setTotalExpenses(player.getTotalExpenses() + cost);

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " produced 1 "
                        + unit.getUnitType().humanReadableName().toLowerCase()
                        + " using Deyra the Voidborn, the Oblivion commander.");

        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "res"));
        paymentButtons.add(Buttons.red("deleteButtons_oblivionCommander", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the planets to exhaust to pay " + cost + ".\n"
                        + "-# The unit's cost was reduced by 1 by Deyra the Voidborn, the Oblivion commander.",
                paymentButtons);
    }

    private static List<UnitModel> getCommanderUnits(Game game, Player player) {
        if (game == null || player == null) {
            return List.of();
        }
        return player.getUnitModels().stream()
                .filter(unit -> unit.getCost() > 0 && unit.getCost() <= 4)
                .filter(unit -> !unit.getIsStructure())
                .filter(unit -> {
                    UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
                    return unitKey != null && ButtonHelperFactionSpecific.remainingUnitsOfType(game, unitKey) > 0;
                })
                .toList();
    }

    // Hero
    public static boolean canStartOblivionHero(Game game) {
        List<String> drawnTiles = OblivionTileHelper.drawUnusedTiles(game, 2, 2);
        return drawnTiles.size() == 4
                && drawnTiles.stream().anyMatch(tileId -> OblivionTileHelper.hasLegalPlacement(game, tileId));
    }

    public static void startOblivionHero(GenericInteractionCreateEvent event, Game game, Player player) {
        List<String> drawnTiles = OblivionTileHelper.drawUnusedTiles(game, 2, 2);
        if (drawnTiles.size() != 4
                || drawnTiles.stream().noneMatch(tileId -> OblivionTileHelper.hasLegalPlacement(game, tileId))) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", Frontiersman Nothi, the Oblivion hero, cannot be resolved because there are not enough unused red-backed and blue-backed tiles or legal edge positions.");
            return;
        }

        game.setStoredValue(HERO_DRAWN + player.getFaction(), String.join(",", drawnTiles));
        game.removeStoredValue(HERO_SELECTED + player.getFaction());

        List<MessageEmbed> embeds = drawnTiles.stream()
                .map(TileHelper::getTileById)
                .map(tile -> tile.getRepresentationEmbed(false))
                .toList();

        List<Button> buttons = drawnTiles.stream()
                .map(tileId -> Buttons.green(
                        player.factionButtonChecker() + HERO_CHOOSE + tileId,
                        TileHelper.getTileById(tileId).getName()))
                .toList();

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose 1 red-backed tile and 1 blue-backed tile for Frontiersman Nothi, the Oblivion hero.",
                embeds,
                buttons);
    }

    @ButtonHandler(HERO_CHOOSE)
    public static void chooseOblivionHeroTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tileId = buttonID.substring(HERO_CHOOSE.length());
        List<String> drawnTiles = getStoredHeroTiles(game, HERO_DRAWN, player);
        List<String> selectedTiles = getStoredHeroTiles(game, HERO_SELECTED, player);
        TileModel chosenTile = TileHelper.getTileById(tileId);

        if (drawnTiles.size() != 4
                || !drawnTiles.contains(tileId)
                || selectedTiles.contains(tileId)
                || chosenTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (selectedTiles.isEmpty()) {
            game.setStoredValue(HERO_SELECTED + player.getFaction(), tileId);

            TileBack requiredBack = chosenTile.getTileBack() == TileBack.RED ? TileBack.BLUE : TileBack.RED;

            List<String> secondChoices = drawnTiles.stream()
                    .filter(id -> !id.equals(tileId))
                    .filter(id -> {
                        TileModel tile = TileHelper.getTileById(id);
                        return tile != null && tile.getTileBack() == requiredBack;
                    })
                    .toList();

            List<MessageEmbed> embeds = secondChoices.stream()
                    .map(TileHelper::getTileById)
                    .map(tile -> tile.getRepresentationEmbed(false))
                    .toList();

            List<Button> buttons = secondChoices.stream()
                    .map(id -> Buttons.green(
                            player.factionButtonChecker() + HERO_CHOOSE + id,
                            TileHelper.getTileById(id).getName()))
                    .toList();

            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", please choose 1 " + requiredBack.toValue()
                            + "-backed tile for Frontiersman Nothi, the Oblivion hero.",
                    embeds,
                    buttons);
            return;
        }

        if (selectedTiles.size() != 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        TileModel firstTile = TileHelper.getTileById(selectedTiles.getFirst());
        if (firstTile == null || firstTile.getTileBack() == chosenTile.getTileBack()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> chosenTiles = List.of(selectedTiles.getFirst(), tileId);
        game.setStoredValue(HERO_SELECTED + player.getFaction(), String.join(",", chosenTiles));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " selected 1 red-backed tile and 1 blue-backed tile.\n-# The other 2 tiles will be purged after both selected tiles are placed.");

        sendOblivionHeroPlacementButtons(game, player, chosenTiles.getFirst());
    }

    private static void sendOblivionHeroPlacementButtons(Game game, Player player, String tileId) {
        String buttonPrefix = player.factionButtonChecker() + HERO_PLACE;
        List<Button> buttons = OblivionTileHelper.getPlacementButtons(game, player, tileId, buttonPrefix);

        if (buttons.isEmpty()) {
            finishOblivionHeroManually(game, player);
            return;
        }

        String message = player.getRepresentation() + ", please choose an edge position for "
                + TileHelper.getTileById(tileId).getName() + ".";

        String paginationPrefix = player.factionButtonChecker() + HERO_PLACE + tileId + "_";

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(), message, NewStuffHelper.buttonPagination(buttons, paginationPrefix, 0));
    }

    @ButtonHandler(HERO_PLACE)
    public static void placeOblivionHeroTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String placementData = buttonID.substring(HERO_PLACE.length());
        int tileIdEnd = placementData.lastIndexOf('_');
        if (tileIdEnd <= 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tileId = placementData.substring(0, tileIdEnd);
        List<String> remainingTiles = new ArrayList<>(getStoredHeroTiles(game, HERO_SELECTED, player));

        if (!remainingTiles.contains(tileId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = player.getRepresentation() + ", please choose an edge position for "
                + TileHelper.getTileById(tileId).getName() + ".";
        String buttonPrefix = player.factionButtonChecker() + HERO_PLACE;
        String paginationPrefix = player.factionButtonChecker() + HERO_PLACE + tileId + "_";
        List<Button> buttons = OblivionTileHelper.getPlacementButtons(game, player, tileId, buttonPrefix);

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, paginationPrefix, buttonID)) {
            return;
        }

        String position = placementData.substring(tileIdEnd + 1);
        Tile placedTile = OblivionTileHelper.placeTile(game, tileId, position);
        if (placedTile == null) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", that placement is no longer legal. Please choose another edge position.");
            sendOblivionHeroPlacementButtons(game, player, tileId);
            return;
        }

        remainingTiles.remove(tileId);
        List<String> drawnTiles = new ArrayList<>(getStoredHeroTiles(game, HERO_DRAWN, player));
        drawnTiles.remove(tileId);
        if (remainingTiles.isEmpty()) {
            OblivionTileHelper.purgeTiles(game, drawnTiles);
            game.removeStoredValue(HERO_DRAWN + player.getFaction());
            game.removeStoredValue(HERO_SELECTED + player.getFaction());
        } else {
            game.setStoredValue(HERO_DRAWN + player.getFaction(), String.join(",", drawnTiles));
            game.setStoredValue(HERO_SELECTED + player.getFaction(), String.join(",", remainingTiles));
        }

        ButtonHelper.deleteMessage(event);

        String result =
                player.getRepresentation() + " placed " + placedTile.getRepresentationForButtons(game, player) + ".";
        if (placedTile.getPlanetUnitHolders().isEmpty()) {
            result += " A frontier token was placed in that system.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);

        if (remainingTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " finished resolving Frontiersman Nothi, the Oblivion hero, and purged the 2 unchosen tiles.");
        } else {
            sendOblivionHeroPlacementButtons(game, player, remainingTiles.getFirst());
        }
    }

    private static void finishOblivionHeroManually(Game game, Player player) {
        List<String> drawnTiles = getStoredHeroTiles(game, HERO_DRAWN, player);
        List<String> remainingSelectedTiles = getStoredHeroTiles(game, HERO_SELECTED, player);
        OblivionTileHelper.purgeTiles(
                game,
                drawnTiles.stream()
                        .filter(tileId -> !remainingSelectedTiles.contains(tileId))
                        .toList());
        game.removeStoredValue(HERO_DRAWN + player.getFaction());
        game.removeStoredValue(HERO_SELECTED + player.getFaction());

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation()
                        + ", there are no legal edge positions for the remaining selected system tiles. The 2 unchosen tiles were purged and the remaining placements must be resolved manually.");
    }

    private static List<String> getStoredHeroTiles(Game game, String key, Player player) {
        return Arrays.stream(game.getStoredValue(key + player.getFaction()).split(","))
                .filter(value -> !value.isBlank())
                .toList();
    }
}
