package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class DreamButtonHandler {

    private static final String DREAM_FACTION = "dream";
    private static final String DREAM_FLAGSHIP_UNIT = "dream_flagship";
    private static final String NEXUS_TOKEN_ALIAS = "beansnexus";
    private static final String NO_SOURCE_PLANET = "none";

    private static final String LITURGY_I_UNIT = "dream_destroyer";
    private static final String LITURGY_II_UNIT = "dream_destroyer2";
    private static final String LITURGY_II_TECH = "bedreamdd";
    private static final String LITURGY_MENU_BUTTON_ID = "dream_liturgy_menu_back";

    private static final String AGENT_IGNORED_ANOMALY_TILE_KEY = "dreamAgentIgnoredAnomalyTile";
    private static final String AGENT_IGNORED_ANOMALY_PLAYER_KEY = "dreamAgentIgnoredAnomalyPlayer";
    private static final String HERO_NEXUS_USES_KEY = "dreamHeroNexusUses";
    private static final int HERO_BUTTON_LIMIT = 25;

    // Liturgy I / II

    public static void offerLiturgyButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        Tile tile = getActiveLiturgyTile(game, player);
        if (tile == null) {
            return;
        }
        sendLiturgyMenu(game, player);
    }

    @ButtonHandler("dream_liturgy_menu")
    public static void showLiturgyMenu(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = getActiveLiturgyTile(game, player);
        if (tile == null) return;

        ButtonHelper.deleteMessage(event);
        sendLiturgyMenu(game, player);
    }

    private static void sendLiturgyMenu(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("dream_offer_add_nexus", "Add Nexus Token"));
        buttons.add(Buttons.blue("dream_offer_move_nexus", "Move Nexus Token"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " you may resolve _Liturgy_ unit ability now by placing or moving 1 nexus token.",
                buttons);
    }

    @ButtonHandler("dream_offer_add_nexus")
    public static void offerAddNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;

        ButtonHelper.deleteMessage(event);
        if (getNexusTokenTiles(game).size() >= 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " cannot add a nexus token because all 3 are already on the map.");
            return;
        }

        List<Tile> tilesWithUnits =
                hasLiturgyII(player, activeTile) ? getTilesContainingPlayersUnits(game, player) : List.of(activeTile);
        List<Button> buttons = new ArrayList<>();
        for (Tile tileWithUnits : tilesWithUnits) {
            buttons.add(Buttons.green(
                    "dream_add_nexus" + tileWithUnits.getPosition(),
                    "Place nexus in " + tileWithUnits.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.gray(LITURGY_MENU_BUTTON_ID, "Back"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose where to add a nexus token:",
                buttons);
    }

    @ButtonHandler("dream_offer_move_nexus")
    public static void offerMoveNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;
        ButtonHelper.deleteMessage(event);
        List<Tile> tilesWithUnits =
                hasLiturgyII(player, activeTile) ? getTilesContainingPlayersUnits(game, player) : List.of(activeTile);
        List<Tile> tilesWithNexusTokens = getNexusTokenTiles(game);
        List<Button> buttons = new ArrayList<>();
        for (Tile fromTile : tilesWithNexusTokens) {
            for (Tile toTile : tilesWithUnits) {
                if (fromTile.getPosition().equals(toTile.getPosition())) {
                    continue;
                }
                buttons.add(Buttons.blue(
                        "dream_move_nexus" + fromTile.getPosition() + "_to_" + toTile.getPosition(),
                        "Move from " + fromTile.getRepresentationForButtons(game, player) + " to "
                                + toTile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no valid nexus token moves available right now.");
            return;
        }
        buttons.add(Buttons.gray(LITURGY_MENU_BUTTON_ID, "Back"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose where to move a nexus token:",
                buttons);
    }

    @ButtonHandler("dream_add_nexus")
    public static void addNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("dream_add_nexus", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }
        addNexusTokenToTile(game, player, tile);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " added a nexus token to " + tile.getRepresentationForButtons(game, player)
                        + ".");
    }

    @ButtonHandler("dream_move_nexus")
    public static void moveNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String data = buttonID.replace("dream_move_nexus", "");
        String[] parts = data.split("_to_");
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse nexus move request.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        if (fromTile == null || toTile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find one of those systems.");
            return;
        }
        if (!moveNexusTokenBetweenTiles(game, player, fromTile, toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "The source system does not contain a nexus token.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " moved a nexus token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
    }

    private static Tile getActiveLiturgyTile(Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            return null;
        }
        String activeSystem = game.getActiveSystem();
        if (activeSystem == null || activeSystem.isBlank()) {
            return null;
        }
        Tile tile = game.getTileByPosition(activeSystem);
        if (tile == null) {
            return null;
        }
        if (!ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile)
                && !ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile)) {
            return null;
        }
        return tile;
    }

    static boolean hasLiturgyII(Player player, Tile tile) {
        return ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile)
                || (player.hasTech(LITURGY_II_TECH)
                        && ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile));
    }

    static List<Tile> getTilesContainingPlayersUnits(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.containsPlayersUnits(player))
                .toList();
    }

    // Shared nexus queries

    public static List<Tile> getNexusTokenTiles(Game game) {
        List<Tile> nexusTiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tileContainsNexusToken(game, tile, false)) {
                nexusTiles.add(tile);
            }
        }
        return nexusTiles;
    }

    public static int getDreamCommanderVoteCount(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tileContainsNexusToken(game, tile, true))
                .mapToInt(tile -> tile.getPlanetUnitHolders().size())
                .sum();
    }

    public static Set<String> getDreamNexusAdjacencies(Game game, Player player, String position) {
        Set<String> adjacentPositions = new HashSet<>();
        if (game == null || position == null) {
            return adjacentPositions;
        }
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !tileContainsNexusToken(game, tile, true)) {
            return adjacentPositions;
        }
        for (Tile nexusTile : game.getTileMap().values()) {
            if (tileContainsNexusToken(game, nexusTile, true)) {
                adjacentPositions.add(nexusTile.getPosition());
            }
        }
        adjacentPositions.remove(position);
        return adjacentPositions;
    }

    // Xal'thuun, the Dreaming Throne agent

    public static void offerDreamAgentButtons(Game game, Player activePlayer, Player dreamPlayer) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(
                "dream_agent_offer_" + activePlayer.getFaction() + "_" + dreamPlayer.getFaction(),
                "Use Dreaming Throne Agent",
                FactionEmojis.dream));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                dreamPlayer.getCardsInfoThread(),
                dreamPlayer.getRepresentation()
                        + " may exhaust **Xal'thuun**, the Dreaming Throne agent, to let "
                        + activePlayer.getRepresentationNoPing()
                        + " choose a non-home anomaly to ignore during this tactical action.",
                buttons);
    }

    @ButtonHandler("dream_agent_offer_")
    public static void offerDreamAgentChoice(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("dream_agent_offer_", "").split("_", 2);
        Player activePlayer = game.getPlayerFromColorOrFaction(parts[0]);
        Player dreamPlayer = parts.length > 1 ? game.getPlayerFromColorOrFaction(parts[1]) : player;
        Player dreamAgentOwner = dreamPlayer == null ? player : dreamPlayer;
        if (activePlayer == null) {
            activePlayer = game.getActivePlayer();
        }
        if (activePlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find the active player.");
            return;
        }
        List<Tile> anomalyTiles = getDreamAgentAnomalyTiles(game);
        if (anomalyTiles.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "There are no valid non-home anomalies to choose.");
            return;
        }

        dreamAgentOwner
                .getLeader("dreamagent")
                .ifPresent(agent -> ExhaustLeaderService.exhaustLeader(game, dreamAgentOwner, agent));
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : anomalyTiles) {
            buttons.add(Buttons.green(
                    "dream_agent_choose_" + activePlayer.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, activePlayer)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer.getCorrectChannel(),
                activePlayer.getRepresentation()
                        + " choose the anomaly whose movement effects you will ignore during this tactical action.",
                buttons);
    }

    @ButtonHandler("dream_agent_choose_")
    public static void chooseDreamAgentAnomaly(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String data = buttonID.replace("dream_agent_choose_", "");
        String[] parts = data.split("_", 2);
        if (parts.length != 2 || !player.getFaction().equalsIgnoreCase(parts[0])) {
            MessageHelper.sendMessageToEventChannel(event, "Only the chosen active player may choose that anomaly.");
            return;
        }
        Tile tile = game.getTileByPosition(parts[1]);
        if (tile == null || tile.isHomeSystem(game) || !tile.isAnomaly(game)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid non-home anomaly.");
            return;
        }
        game.setStoredValue(AGENT_IGNORED_ANOMALY_TILE_KEY, tile.getPosition());
        game.setStoredValue(AGENT_IGNORED_ANOMALY_PLAYER_KEY, player.getFaction());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " will ignore the movement effects of "
                        + tile.getRepresentationForButtons(game, player) + " during this tactical action.");
    }

    public static boolean playerIgnoresDreamAgentAnomaly(Game game, Player player, Tile tile) {
        if (game == null || player == null || tile == null) return false;
        return tile.getPosition().equalsIgnoreCase(game.getStoredValue(AGENT_IGNORED_ANOMALY_TILE_KEY))
                && player.getFaction().equalsIgnoreCase(game.getStoredValue(AGENT_IGNORED_ANOMALY_PLAYER_KEY));
    }

    public static void clearDreamAgentAnomaly(Game game) {
        game.removeStoredValue(AGENT_IGNORED_ANOMALY_TILE_KEY);
        game.removeStoredValue(AGENT_IGNORED_ANOMALY_PLAYER_KEY);
    }

    public static List<Tile> getDreamAgentAnomalyTiles(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> tile.isAnomaly(game))
                .toList();
    }

    // Reality Unbound - Unknown Geometries, the Dreaming Throne hero

    public static void postDreamHeroButtons(Game game, Player player) {
        game.setStoredValue(HERO_NEXUS_USES_KEY + player.getFaction(), "0");
        sendDreamHeroNexusMenu(game, player);
    }

    private static void sendDreamHeroNexusMenu(Game game, Player player) {
        int uses = getDreamHeroNexusUses(game, player);
        List<Button> buttons = new ArrayList<>();
        if (uses < 3) {
            if (getNexusTokenTiles(game).size() < 3
                    && !getDreamHeroNexusDestinations(game).isEmpty()) {
                buttons.add(Buttons.green("dream_hero_offer_add_nexus", "Place Nexus Token", FactionEmojis.dream));
            }
            if (!getNexusTokenTiles(game).isEmpty()) {
                buttons.add(Buttons.blue("dream_hero_offer_move_nexus", "Move Nexus Token", FactionEmojis.dream));
            }
        }
        buttons.add(Buttons.gray("dream_hero_offer_units", "Continue to Unit Placement", FactionEmojis.dream));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " resolve **Reality Unbound - Unknown Geometries**. You have used " + uses
                        + "/3 nexus token placements or moves.",
                buttons);
    }

    @ButtonHandler("dream_hero_offer_add_nexus")
    public static void offerDreamHeroAddNexus(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean pageButton = isHeroPageButton(buttonID);
        int page = getHeroPage(buttonID);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getDreamHeroNexusDestinations(game)) {
            if (tileContainsNexusToken(game, tile, false)) continue;
            buttons.add(Buttons.green(
                    "dream_hero_add_nexus_" + tile.getPosition(),
                    "Place in " + tile.getRepresentationForButtons(game, player)));
        }
        String message = player.getRepresentation() + " choose where to place a nexus token.";
        sendOrUpdateHeroPagedButtons(
                event,
                player,
                message,
                buttons,
                List.of(Buttons.gray("dream_hero_back_to_nexus", "Back")),
                "dream_hero_offer_add_nexus_",
                page,
                pageButton);
    }

    @ButtonHandler("dream_hero_add_nexus_")
    public static void dreamHeroAddNexus(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (getDreamHeroNexusUses(game, player) >= 3) {
            MessageHelper.sendMessageToEventChannel(event, "You have already placed or moved 3 nexus tokens.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        Tile tile = game.getTileByPosition(buttonID.replace("dream_hero_add_nexus_", ""));
        if (tile == null || !isDreamHeroNexusDestination(game, tile) || tileContainsNexusToken(game, tile, false)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid system for a nexus token.");
            return;
        }
        addNexusTokenToTile(game, player, tile);
        incrementDreamHeroNexusUses(game, player);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " placed a nexus token in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        sendDreamHeroNexusMenu(game, player);
    }

    @ButtonHandler("dream_hero_offer_move_nexus")
    public static void offerDreamHeroMoveNexus(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getNexusTokenTiles(game)) {
            buttons.add(Buttons.blue(
                    "dream_hero_move_nexus_from_" + tile.getPosition(),
                    "Move from " + tile.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.gray("dream_hero_back_to_nexus", "Back"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentation() + " choose a nexus token to move.", buttons);
    }

    @ButtonHandler("dream_hero_move_nexus_from_")
    public static void offerDreamHeroMoveNexusDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String fromPosition = buttonID.replace("dream_hero_move_nexus_from_", "");
        if (isHeroPageButton(buttonID)) {
            fromPosition = fromPosition.substring(0, fromPosition.lastIndexOf("_page"));
        }
        Tile fromTile = game.getTileByPosition(fromPosition);
        if (fromTile == null || !tileContainsNexusToken(game, fromTile, false)) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a nexus token.");
            return;
        }
        boolean pageButton = isHeroPageButton(buttonID);
        int page = getHeroPage(buttonID);
        List<Button> buttons = new ArrayList<>();
        for (Tile toTile : getDreamHeroNexusDestinations(game)) {
            if (fromPosition.equals(toTile.getPosition()) || tileContainsNexusToken(game, toTile, false)) continue;
            buttons.add(Buttons.blue(
                    "dream_hero_move_nexus_" + fromPosition + "_to_" + toTile.getPosition(),
                    "Move to " + toTile.getRepresentationForButtons(game, player)));
        }
        String message = player.getRepresentation() + " choose where to move that nexus token.";
        sendOrUpdateHeroPagedButtons(
                event,
                player,
                message,
                buttons,
                List.of(Buttons.gray("dream_hero_offer_move_nexus", "Back")),
                "dream_hero_move_nexus_from_" + fromPosition + "_",
                page,
                pageButton);
    }

    @ButtonHandler("dream_hero_move_nexus_")
    public static void dreamHeroMoveNexus(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (getDreamHeroNexusUses(game, player) >= 3) {
            MessageHelper.sendMessageToEventChannel(event, "You have already placed or moved 3 nexus tokens.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        String[] parts = buttonID.replace("dream_hero_move_nexus_", "").split("_to_");
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that nexus token move.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        if (fromTile == null
                || toTile == null
                || !tileContainsNexusToken(game, fromTile, false)
                || !isDreamHeroNexusDestination(game, toTile)
                || tileContainsNexusToken(game, toTile, false)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid nexus token move.");
            return;
        }
        if (!moveNexusTokenBetweenTiles(game, player, fromTile, toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from that system.");
            return;
        }
        incrementDreamHeroNexusUses(game, player);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " moved a nexus token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
        sendDreamHeroNexusMenu(game, player);
    }

    @ButtonHandler("dream_hero_back_to_nexus")
    public static void dreamHeroBackToNexus(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        sendDreamHeroNexusMenu(game, player);
    }

    @ButtonHandler("dream_hero_offer_units")
    public static void offerDreamHeroUnitSystems(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean pageButton = isHeroPageButton(buttonID);
        int page = getHeroPage(buttonID);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getDreamHeroUnitDestinations(game, player)) {
            buttons.add(Buttons.green(
                    "dream_hero_units_tile_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player),
                    FactionEmojis.dream));
        }
        String message = player.getRepresentation()
                + " may place your flagship and up to 3 destroyers in a system that contains a planet you control.";
        sendOrUpdateHeroPagedButtons(
                event,
                player,
                message,
                buttons,
                List.of(Buttons.red("dream_hero_skip_units", "Skip Unit Placement")),
                "dream_hero_offer_units_",
                page,
                pageButton);
    }

    @ButtonHandler("dream_hero_units_tile_")
    public static void offerDreamHeroDestroyerCounts(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("dream_hero_units_tile_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !getDreamHeroUnitDestinations(game, player).contains(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid system for the hero units.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        for (int destroyers = 0; destroyers <= 3; destroyers++) {
            String label = destroyers == 0 ? "Place Flagship Only" : "Place Flagship + " + destroyers + " Destroyers";
            buttons.add(Buttons.green("dream_hero_place_units_" + position + "_" + destroyers, label));
        }
        buttons.add(Buttons.gray("dream_hero_offer_units", "Back"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose how many destroyers to place with your flagship in "
                        + tile.getRepresentationForButtons(game, player) + ".",
                buttons);
    }

    @ButtonHandler("dream_hero_place_units_")
    public static void dreamHeroPlaceUnits(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("dream_hero_place_units_", "").split("_");
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that unit placement.");
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        int destroyers = Integer.parseInt(parts[1]);
        if (tile == null
                || destroyers < 0
                || destroyers > 3
                || !getDreamHeroUnitDestinations(game, player).contains(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid hero unit placement.");
            return;
        }
        String units = "fs" + (destroyers == 0 ? "" : ", " + destroyers + " destroyer");
        AddUnitService.addUnits(event, tile, game, player.getColor(), units);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " placed " + units + " in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        game.removeStoredValue(HERO_NEXUS_USES_KEY + player.getFaction());
        if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            StartCombatService.combatCheck(game, event, tile);
        }
    }

    @ButtonHandler("dream_hero_skip_units")
    public static void dreamHeroSkipUnits(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        game.removeStoredValue(HERO_NEXUS_USES_KEY + player.getFaction());
        MessageHelper.sendMessageToEventChannel(
                event, player.getRepresentation() + " skipped the hero unit placement.");
    }

    private static void sendOrUpdateHeroPagedButtons(
            ButtonInteractionEvent event,
            Player player,
            String message,
            List<Button> mainButtons,
            List<Button> persistentButtons,
            String pagePrefix,
            int page,
            boolean pageButton) {
        List<Button> buttons = getHeroPagedButtons(mainButtons, persistentButtons, pagePrefix, page);
        if (pageButton) {
            MessageHelper.editMessageWithButtons(event, message, buttons);
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static List<Button> getHeroPagedButtons(
            List<Button> mainButtons, List<Button> persistentButtons, String pagePrefix, int page) {
        List<Button> buttons = new ArrayList<>();
        int persistentCount = persistentButtons == null ? 0 : persistentButtons.size();
        if (mainButtons.size() + persistentCount <= HERO_BUTTON_LIMIT) {
            buttons.addAll(mainButtons);
            if (persistentButtons != null) buttons.addAll(persistentButtons);
            return buttons;
        }

        int pageSize = Math.max(1, HERO_BUTTON_LIMIT - persistentCount - 2);
        int maxPage = (mainButtons.size() - 1) / pageSize;
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int fromIndex = currentPage * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, mainButtons.size());

        if (currentPage > 0) {
            buttons.add(Buttons.blue(pagePrefix + "page" + (currentPage - 1), "Previous Page", "⏪"));
        }
        buttons.addAll(mainButtons.subList(fromIndex, toIndex));
        if (currentPage < maxPage) {
            buttons.add(Buttons.blue(pagePrefix + "page" + (currentPage + 1), "Next Page", "⏩"));
        }
        if (persistentButtons != null) buttons.addAll(persistentButtons);
        return buttons;
    }

    private static boolean isHeroPageButton(String buttonID) {
        return buttonID != null && buttonID.contains("_page");
    }

    private static int getHeroPage(String buttonID) {
        if (!isHeroPageButton(buttonID)) return 0;
        try {
            return Integer.parseInt(buttonID.substring(buttonID.lastIndexOf("_page") + 5));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int getDreamHeroNexusUses(Game game, Player player) {
        String value = game.getStoredValue(HERO_NEXUS_USES_KEY + player.getFaction());
        if (value.isBlank()) return 0;
        return Integer.parseInt(value);
    }

    private static void incrementDreamHeroNexusUses(Game game, Player player) {
        game.setStoredValue(
                HERO_NEXUS_USES_KEY + player.getFaction(), Integer.toString(getDreamHeroNexusUses(game, player) + 1));
    }

    private static List<Tile> getDreamHeroNexusDestinations(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> isDreamHeroNexusDestination(game, tile))
                .toList();
    }

    private static boolean isDreamHeroNexusDestination(Game game, Tile tile) {
        return tile != null && !tile.isHomeSystem(game) && !tile.isMecatol(game);
    }

    private static List<Tile> getDreamHeroUnitDestinations(Game game, Player player) {
        return getTilesWithPlanetsControlledBy(game, player);
    }

    // The Recurring, the Dreaming Throne mech

    public static void offerRecurringMechButtons(
            GenericInteractionCreateEvent event, Game game, Player player, int amount, String sourcePlanet) {
        if (amount < 1) return;
        String source = sourcePlanet == null || sourcePlanet.isBlank() || "space".equalsIgnoreCase(sourcePlanet)
                ? NO_SOURCE_PLANET
                : sourcePlanet;
        List<Button> placeButtons = getRecurringMechPlanetButtons(game, player, amount, source);
        if (placeButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no valid planet they control for _The Recurring_ in a system with a nexus token or flagship.");
            return;
        }

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        buttons.add(Buttons.green("dream_recurring_mech_paid_" + amount + "_from_" + source, "Done Spending"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " may spend 1 influence to place "
                        + (amount == 1 ? "_The Recurring_" : "1 of " + amount + " destroyed _Recurring_ mechs")
                        + " on another planet you control in a system that contains a nexus token or your flagship. After spending, click Done Spending to choose the destination.",
                buttons);
    }

    @ButtonHandler("dream_recurring_mech_paid_")
    public static void offerRecurringMechPlacementButtons(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("dream_recurring_mech_paid_", "").split("_from_", 2);
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that _Recurring_ payment confirmation.");
            return;
        }

        int remaining = Integer.parseInt(parts[0]);
        String source = parts[1];
        List<Button> buttons = getRecurringMechPlanetButtons(game, player, remaining, source);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "There are no valid planets for _The Recurring_ anymore.");
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose where to place _The Recurring_.",
                buttons);
    }

    @ButtonHandler("dream_recurring_mech_")
    public static void placeRecurringMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("dream_recurring_mech_", "").split("_to_", 2);
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that _Recurring_ placement.");
            return;
        }
        int remaining = Integer.parseInt(parts[0]);
        String[] planetParts = parts[1].split("_from_", 2);
        if (planetParts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that _Recurring_ destination.");
            return;
        }

        String planet = planetParts[0];
        String source = planetParts[1];
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || getRecurringMechPlanetButtons(game, player, remaining, source).stream()
                        .noneMatch(button -> button.getCustomId().equals(buttonID))) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid planet for _The Recurring_.");
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " placed _The Recurring_ on "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        if (remaining > 1) {
            offerRecurringMechButtons(event, game, player, remaining - 1, source);
        }
    }

    private static List<Button> getRecurringMechPlanetButtons(
            Game game, Player player, int remaining, String sourcePlanet) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tileContainsNexusToken(game, tile, true)) continue;
            for (var planet : tile.getPlanetUnitHolders()) {
                if (planet.getName().equalsIgnoreCase(sourcePlanet)) continue;
                if (!player.getPlanets().contains(planet.getName())) continue;
                buttons.add(Buttons.green(
                        "dream_recurring_mech_" + remaining + "_to_" + planet.getName() + "_from_" + sourcePlanet,
                        "Place on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }

    // Dream-Space Convergence, the Dreaming Throne breakthrough

    public static boolean hasDreamBtNexusMove(Game game, Player player) {
        return getNexusTokenTiles(game).stream().anyMatch(tile -> !getDreamBtNexusDestinations(game, player, tile)
                .isEmpty());
    }

    public static void postDreamBtMoveNexusButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Tile> sourceTiles = getNexusTokenTiles(game);
        if (sourceTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentation() + " has no nexus tokens to move.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : sourceTiles) {
            if (getDreamBtNexusDestinations(game, player, tile).isEmpty()) continue;
            buttons.add(Buttons.blue(
                    "dream_bt_move_nexus_from_" + tile.getPosition(),
                    "Move nexus from " + tile.getRepresentationForButtons(game, player)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no valid destination for _Dream-Space Convergence_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose a nexus token to move with _Dream-Space Convergence_.",
                buttons);
    }

    @ButtonHandler("dream_bt_move_nexus_from_")
    public static void offerDreamBtMoveNexusDestinations(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String fromPosition = buttonID.replace("dream_bt_move_nexus_from_", "");
        Tile fromTile = game.getTileByPosition(fromPosition);
        if (fromTile == null || !tileContainsNexusToken(game, fromTile, false)) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a movable nexus token.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        for (Tile toTile : getDreamBtNexusDestinations(game, player, fromTile)) {
            buttons.add(Buttons.green(
                    "dream_bt_move_nexus_" + fromPosition + "_to_" + toTile.getPosition(),
                    "Move to " + toTile.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose where to move that nexus token.",
                buttons);
    }

    @ButtonHandler("dream_bt_move_nexus_")
    public static void resolveDreamBtMoveNexus(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("dream_bt_move_nexus_", "").split("_to_", 2);
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that nexus move.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        if (fromTile == null
                || toTile == null
                || !getDreamBtNexusDestinations(game, player, fromTile).contains(toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid _Dream-Space Convergence_ move.");
            return;
        }
        if (!moveNexusTokenBetweenTiles(game, player, fromTile, toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "The source system does not contain a nexus token.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " moved a nexus token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
    }

    private static List<Tile> getDreamBtNexusDestinations(Game game, Player player, Tile fromTile) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getPosition().equals(fromTile.getPosition()))
                .filter(tile -> !tileContainsNexusToken(game, tile, false))
                .filter(tile -> containsPlanetControlledBy(tile, player))
                .toList();
    }

    public static void offerDreamBtRemoveCommandTokenButton(Game game, Player player, Tile tile, String msg) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(
                "dream_bt_remove_cc_" + tile.getPosition(), "Resolve Dream-Space Convergence", FactionEmojis.dream));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                msg
                        + ", a reminder that if you win this combat as the defender, you may remove your command token from the active system.",
                buttons);
    }

    @ButtonHandler("dream_bt_remove_cc_")
    public static void resolveDreamBtRemoveCommandToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.replace("dream_bt_remove_cc_", ""));
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }
        RemoveCommandCounterService.fromTile(player.getColor(), tile, game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation() + " removed their command token from "
                        + tile.getRepresentationForButtons(game, player) + " with _Dream-Space Convergence_.");
    }

    // The Waking and Visions promissory note

    @ButtonHandler("dream_remove_nexus_")
    public static void removeNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("dream_remove_nexus_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        // Validate that this tile contains both the player's ships and a nexus token
        boolean hasShips =
                FoWHelper.playerHasShipsInSystem(player, tile) || FoWHelper.playerHasActualShipsInSystem(player, tile);
        if (!hasShips || !tileContainsNexusToken(game, tile, false)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "You can only resolve _The Waking_ in a system that contains both your ships and a Dreaming Throne nexus token.");
            return;
        }

        if (!removeNexusTokenFromTile(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from that system.");
            return;
        }

        // Record that this player has used The Waking this round so they cannot remove another
        try {
            game.setStoredValue("theWakingRemovedFor" + player.getFaction() + "Round" + game.getRound(), "removed");
        } catch (Exception ignored) {
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " removed a nexus token from "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }

    public static void offerTheWakingButtons(Game game) {
        for (Player player : game.getRealPlayers()) {
            List<Tile> eligibleTiles = getTheWakingEligibleTiles(game, player);
            if (eligibleTiles.isEmpty()) continue;

            List<Button> buttons = new ArrayList<>();
            for (Tile tile : eligibleTiles) {
                buttons.add(Buttons.red(
                        "dream_remove_nexus_" + tile.getPosition(),
                        "Remove nexus from " + tile.getRepresentationForButtons(game, player)));
            }
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " may resolve _The Waking_ now: remove 1 nexus token from a system that contains both your ships and a nexus token.",
                    buttons);
        }
    }

    private static List<Tile> getTheWakingEligibleTiles(Game game, Player player) {
        if (player == null || DREAM_FACTION.equalsIgnoreCase(player.getFaction())) return List.of();
        String key = "theWakingRemovedFor" + player.getFaction() + "Round" + game.getRound();
        String val = game.getStoredValue(key);
        if (val != null && !val.isBlank()) return List.of();

        return game.getTileMap().values().stream()
                .filter(tile -> tileContainsNexusToken(game, tile, false))
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile)
                        || FoWHelper.playerHasActualShipsInSystem(player, tile))
                .toList();
    }

    /**
     * Offer the Visions promissory (bepndream) to the owner at the start of a tactical action.
     * The owner may return the card to the Dreaming Throne player to remove 1 nexus token from
     * a system that contains a planet they control.
     */
    public static void offerVisionsPromissoryAtTacticalStart(Game game, Player player) {
        List<Tile> eligible = game.getTileMap().values().stream()
                .filter(tile -> !tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(p -> player.getPlanets().contains(p.getName())))
                .filter(tile -> tileContainsNexusToken(game, tile, true))
                .toList();
        if (eligible.isEmpty()) return;

        List<Button> buttons = new ArrayList<>();
        for (Tile t : eligible) {
            buttons.add(Buttons.green(
                    "promissory_bepndream_return_" + t.getPosition(),
                    "Return Visions to Dream & remove nexus from " + t.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " may return Visions to the Dreaming Throne to remove 1 nexus token:",
                buttons);
    }

    @ButtonHandler("promissory_bepndream_return_")
    public static void resolveVisionsPromissory(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null) return;
        // Only non-Dream players who own the promissory may use it
        if (DREAM_FACTION.equalsIgnoreCase(player.getFaction())) {
            MessageHelper.sendMessageToEventChannel(event, "The Dreaming Throne may not resolve this promissory.");
            return;
        }
        if (!player.getPromissoryNotes().containsKey("bepndream")) {
            MessageHelper.sendMessageToEventChannel(event, "You do not own Visions.");
            return;
        }

        String pos = buttonID.replace("promissory_bepndream_return_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        if (!tile.getPlanetUnitHolders().stream()
                .anyMatch(p -> player.getPlanets().contains(p.getName()))) {
            MessageHelper.sendMessageToEventChannel(event, "You do not control a planet in that system.");
            return;
        }

        if (!tileContainsNexusToken(game, tile, true)) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a nexus token.");
            return;
        }

        // Find Dreaming Throne player (owner)
        Player dreamOwner = game.getPNOwner("bepndream");
        if (dreamOwner == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not find the Dreaming Throne player to return the card to.");
            return;
        }

        // Use canonical service to transfer the promissory (handles play-area -> owner and hand cases)
        SendPromissoryService.sendPromissoryToPlayer(event, game, player, dreamOwner, "bepndream");

        if (!removeNexusTokenFromTile(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from that system.");
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " returned Visions to " + dreamOwner.getRepresentation()
                        + " and removed a nexus token from " + tile.getRepresentationForButtons(game, player) + ".");
    }

    // Shared helpers

    public static boolean tileContainsNexusToken(Game game, Tile tile, boolean includeFlagship) {
        return getPhysicalNexusToken(tile) != null
                || (includeFlagship && getDreamFlagshipPlayerInTile(game, tile) != null);
    }

    private static String getPhysicalNexusToken(Tile tile) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN_ALIAS);
        for (String token : tile.getSpaceUnitHolder().getTokenList()) {
            if ((tokenId != null && tokenId.equalsIgnoreCase(token))
                    || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(token)
                    || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(Mapper.getTokenKey(token))) {
                return token;
            }
        }
        return null;
    }

    private static Player getDreamFlagshipPlayerInTile(Game game, Tile tile) {
        for (Player player : game.getRealPlayers()) {
            if (DREAM_FACTION.equalsIgnoreCase(player.getFaction())
                    && ButtonHelper.doesPlayerHaveFSHere(DREAM_FLAGSHIP_UNIT, player, tile)) {
                return player;
            }
        }
        return null;
    }

    private static boolean removeNexusTokenFromTile(Tile tile) {
        String token = getPhysicalNexusToken(tile);
        return token != null && tile.removeToken(token, "space");
    }

    private static void addNexusTokenToTile(Game game, Player player, Tile tile) {
        tile.addToken(Mapper.getTokenID(NEXUS_TOKEN_ALIAS), "space");
        CommanderUnlockCheckService.checkPlayer(player, DREAM_FACTION);
    }

    private static boolean moveNexusTokenBetweenTiles(Game game, Player player, Tile fromTile, Tile toTile) {
        if (!removeNexusTokenFromTile(fromTile)) return false;
        addNexusTokenToTile(game, player, toTile);
        return true;
    }

    private static List<Tile> getTilesWithPlanetsControlledBy(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> containsPlanetControlledBy(tile, player))
                .toList();
    }

    private static boolean containsPlanetControlledBy(Tile tile, Player player) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> player.getPlanets().contains(planet.getName()));
    }

    // Incomprehensible Form

    public static List<Button> getIncomprehensibleFormButtons(Game game, Player p1, Player p2, Tile tile) {
        return Stream.of(p1, p2)
                .filter(player -> player != null && player.hasAbility("incomprehensible_form"))
                .map(player -> Buttons.gray(
                        player.factionButtonChecker() + "incomprehensible_form_" + tile.getPosition(),
                        "Use Incomprehensible Form",
                        FactionEmojis.dream))
                .toList();
    }

    @ButtonHandler("incomprehensible_form_")
    public static void presentIncomprehensibleChoices(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.replace("incomprehensible_form_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        boolean hasToken = tileContainsNexusToken(game, tile, false);
        boolean hasFlagship = getDreamFlagshipPlayerInTile(game, tile) != null;

        if (!hasToken && !hasFlagship) {
            MessageHelper.sendMessageToEventChannel(event, "There is no nexus token or Dream flagship in that system.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (hasToken) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "incomprehensible_form_use_token_" + pos,
                    "Remove Nexus Token",
                    FactionEmojis.dream));
        }
        if (hasFlagship) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "incomprehensible_form_use_flagship_" + pos,
                    "Remove Dream Flagship",
                    FactionEmojis.dream));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + " choose whether to remove the nexus token or the Dream flagship:",
                buttons);
    }

    @ButtonHandler("incomprehensible_form_use_flagship_")
    @ButtonHandler("incomprehensible_form_use_token_")
    public static void useIncomprehensibleForm(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility("incomprehensible_form")) {
            MessageHelper.sendMessageToEventChannel(event, "Only a player with Incomprehensible Form may use this.");
            return;
        }
        boolean choiceFlagship = buttonID.contains("_use_flagship_");
        String pos = buttonID.replace("incomprehensible_form_use_flagship_", "")
                .replace("incomprehensible_form_use_token_", "")
                .replace("incomprehensible_form_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }

        boolean usedFlagship = false;
        if (choiceFlagship) {
            Player dreamPlayer = getDreamFlagshipPlayerInTile(game, tile);
            if (dreamPlayer == null) {
                MessageHelper.sendMessageToEventChannel(event, "There is no Dream flagship in that system to use.");
                return;
            }
            var removedFlagship = RemoveUnitService.removeUnit(
                    event, tile, game, dreamPlayer, tile.getSpaceUnitHolder(), UnitType.Flagship, 1);
            if (removedFlagship.isEmpty()) {
                MessageHelper.sendMessageToEventChannel(event, "Failed to remove the Dream flagship from that system.");
                return;
            }
            usedFlagship = true;
        } else {
            if (!removeNexusTokenFromTile(tile)) {
                MessageHelper.sendMessageToEventChannel(
                        event, "Failed to remove the nexus token from the active system.");
                return;
            }
        }

        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation()
                        + " used **Incomprehensible Form** in " + tile.getRepresentationForButtons(game, player)
                        + " to remove a nexus token from the active system instead of destroying a ship. If the Dreaming Throne player removed their flagship, the hit produced is assigned by the Dreaming Throne player.");

        if (usedFlagship) {
            String playersInCombat = game.getStoredValue("factionsInCombat");
            if (!playersInCombat.isBlank() && playersInCombat.contains(player.getFaction())) {
                for (Player opponent : game.getRealPlayersExcludingThis(player)) {
                    if (playersInCombat.contains(opponent.getFaction())) {
                        CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, 1);
                        break;
                    }
                }
            }
        }
    }
}
