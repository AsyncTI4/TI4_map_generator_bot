package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class DreamLeadersHandler {

    private static final String HERO_NEXUS_USES_KEY = "dreamHeroNexusUses";
    private static final int HERO_BUTTON_LIMIT = 25;
    private static final Pattern TO_ = Pattern.compile("_to_");

    public static int getDreamCommanderVoteCount(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> DreamAbilitiesHandler.hasNexusTokenOrDreamFlagship(game, tile))
                .mapToInt(tile -> tile.getPlanetUnitHolders().size())
                .sum();
    }

    // Xal'thuun, the Dreaming Throne agent

    // Reality Unbound - Unknown Geometries, the Dreaming Throne hero

    public static void postDreamHeroButtons(Game game, Player player) {
        game.setStoredValue(HERO_NEXUS_USES_KEY + player.getFaction(), "0");
        sendDreamHeroNexusMenu(game, player);
    }

    private static void sendDreamHeroNexusMenu(Game game, Player player) {
        int uses = getDreamHeroNexusUses(game, player);
        List<Button> buttons = new ArrayList<>();
        if (uses < 3) {
            if (DreamUnitsHandler.getNexusTokenCount(game) < 3
                    && !getDreamHeroNexusDestinations(game).isEmpty()) {
                buttons.add(Buttons.green("dream_hero_offer_add_nexus", "Place Nexus Token", FactionEmojis.dream));
            }
            if (!DreamUnitsHandler.getNexusTokenTiles(game).isEmpty()) {
                buttons.add(Buttons.blue("dream_hero_offer_move_nexus", "Move Nexus Token", FactionEmojis.dream));
            }
        }
        buttons.add(Buttons.gray("dream_hero_offer_units", "Continue to Unit Placement", FactionEmojis.dream));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", resolve **Reality Unbound - Unknown Geometries**. You have used " + uses
                        + "/3 nexus token placements or moves.",
                buttons);
    }

    @ButtonHandler("dream_hero_offer_add_nexus")
    public static void offerDreamHeroAddNexus(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isDreamHeroActive(game, player)) return;
        boolean pageButton = isHeroPageButton(buttonID);
        int page = getHeroPage(buttonID);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getDreamHeroNexusDestinations(game)) {
            if (DreamAbilitiesHandler.hasNexusToken(tile)) continue;
            buttons.add(Buttons.green(
                    "dream_hero_add_nexus_" + tile.getPosition(),
                    "Place in " + tile.getRepresentationForButtons(game, player)));
        }
        String message = player.getRepresentation() + ", choose where to place a nexus token.";
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
        if (!isDreamHeroActive(game, player)) return;
        if (getDreamHeroNexusUses(game, player) >= 3) {
            MessageHelper.sendMessageToEventChannel(event, "You have already placed or moved 3 nexus tokens.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        Tile tile = game.getTileByPosition(buttonID.replace("dream_hero_add_nexus_", ""));
        if (!isDreamHeroNexusDestination(game, tile) || DreamAbilitiesHandler.hasNexusToken(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid system for a nexus token.");
            return;
        }
        if (!DreamUnitsHandler.addNexusTokenToTile(game, tile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid system for a nexus token.");
            return;
        }
        incrementDreamHeroNexusUses(game, player);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", placed a nexus token in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        sendDreamHeroNexusMenu(game, player);
    }

    @ButtonHandler("dream_hero_offer_move_nexus")
    public static void offerDreamHeroMoveNexus(ButtonInteractionEvent event, Game game, Player player) {
        if (!isDreamHeroActive(game, player)) return;
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : DreamUnitsHandler.getNexusTokenTiles(game)) {
            buttons.add(Buttons.blue(
                    "dream_hero_move_nexus_from_" + tile.getPosition(),
                    "Move from " + tile.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.gray("dream_hero_back_to_nexus", "Back"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentation() + ", choose a nexus token to move.", buttons);
    }

    @ButtonHandler("dream_hero_move_nexus_from_")
    public static void offerDreamHeroMoveNexusDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isDreamHeroActive(game, player)) return;
        String fromPosition = buttonID.replace("dream_hero_move_nexus_from_", "");
        if (isHeroPageButton(buttonID)) {
            fromPosition = fromPosition.substring(0, fromPosition.lastIndexOf("_page"));
        }
        Tile fromTile = game.getTileByPosition(fromPosition);
        if (!DreamAbilitiesHandler.hasNexusToken(fromTile)) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a nexus token.");
            return;
        }
        boolean pageButton = isHeroPageButton(buttonID);
        int page = getHeroPage(buttonID);
        List<Button> buttons = new ArrayList<>();
        for (Tile toTile : getDreamHeroNexusDestinations(game)) {
            if (fromPosition.equals(toTile.getPosition()) || DreamAbilitiesHandler.hasNexusToken(toTile)) continue;
            buttons.add(Buttons.blue(
                    "dream_hero_move_nexus_" + fromPosition + "_to_" + toTile.getPosition(),
                    "Move to " + toTile.getRepresentationForButtons(game, player)));
        }
        String message = player.getRepresentation() + ", choose where to move that nexus token.";
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
        if (!isDreamHeroActive(game, player)) return;
        if (getDreamHeroNexusUses(game, player) >= 3) {
            MessageHelper.sendMessageToEventChannel(event, "You have already placed or moved 3 nexus tokens.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        String[] parts = TO_.split(buttonID.replace("dream_hero_move_nexus_", ""));
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that nexus token move.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        if (!DreamAbilitiesHandler.hasNexusToken(fromTile)
                || !isDreamHeroNexusDestination(game, toTile)
                || DreamAbilitiesHandler.hasNexusToken(toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid nexus token move.");
            return;
        }
        if (!DreamUnitsHandler.moveNexusTokenBetweenTiles(player, fromTile, toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to remove the nexus token from that system.");
            return;
        }
        incrementDreamHeroNexusUses(game, player);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", moved a nexus token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
        sendDreamHeroNexusMenu(game, player);
    }

    @ButtonHandler("dream_hero_back_to_nexus")
    public static void dreamHeroBackToNexus(ButtonInteractionEvent event, Game game, Player player) {
        if (!isDreamHeroActive(game, player)) return;
        ButtonHelper.deleteMessage(event);
        sendDreamHeroNexusMenu(game, player);
    }

    @ButtonHandler("dream_hero_offer_units")
    public static void offerDreamHeroUnitSystems(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isDreamHeroActive(game, player)) return;
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
                + ", may place your flagship and up to 3 destroyers in a system that contains a planet you control.";
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
        if (!isDreamHeroActive(game, player)) return;
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
                player.getRepresentation() + ", choose how many destroyers to place with your flagship in "
                        + tile.getRepresentationForButtons(game, player) + ".",
                buttons);
    }

    @ButtonHandler("dream_hero_place_units_")
    public static void dreamHeroPlaceUnits(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!isDreamHeroActive(game, player)) return;
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
                player.getRepresentation() + ", placed " + units + " in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        game.removeStoredValue(HERO_NEXUS_USES_KEY + player.getFaction());
        if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            StartCombatService.combatCheck(game, event, tile);
        }
    }

    @ButtonHandler("dream_hero_skip_units")
    public static void dreamHeroSkipUnits(ButtonInteractionEvent event, Game game, Player player) {
        if (!isDreamHeroActive(game, player)) return;
        ButtonHelper.deleteMessage(event);
        game.removeStoredValue(HERO_NEXUS_USES_KEY + player.getFaction());
        MessageHelper.sendMessageToEventChannel(
                event, player.getRepresentation() + ", skipped the hero unit placement.");
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
        int currentPage = Math.clamp(page, 0, maxPage);
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

    private static boolean isDreamHeroActive(Game game, Player player) {
        return game != null
                && player != null
                && !game.getStoredValue(HERO_NEXUS_USES_KEY + player.getFaction())
                        .isBlank();
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
        return tile != null && !tile.isHomeSystem(game);
    }

    private static List<Tile> getDreamHeroUnitDestinations(Game game, Player player) {
        return getTilesWithPlanetsControlledBy(game, player);
    }

    private static List<Tile> getTilesWithPlanetsControlledBy(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> player.getPlanets().contains(planet.getName())))
                .toList();
    }
}
