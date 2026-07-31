package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class OblivionAbilityHandler {
    private static final String IGNO_DISCO = "ignorant_discoveries";
    private static final String START_IGNO_DISCO = "useIgnorantDiscoveries";
    private static final String PURGE_TECH = "purgeTechForIgnorantDiscoveries";
    private static final String REFLECTIONS_OF_THE_VOID = "reflections_of_the_void";
    private static final String PLACE_REFLECTION = "placeOblivionReflection_";
    private static final String PURGE_REFLECTION = "purgeOblivionReflection_";
    private static final String REFLECTION_COUNT = "oblivionReflectionCount_";
    private static final String PURGED_REFLECTION_COUNT = "oblivionPurgedReflectionCount";
    private static final String SHOW_REFLECTIONS = "showOblivionReflections";
    private static final int MAX_REFLECTION_TOKENS = 3;

    public static List<Button> getIgnorantDiscoveriesButtons(GenericInteractionCreateEvent event, Player player) {
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + START_IGNO_DISCO,
                        "Use Ignorant Discoveries",
                        FactionEmojis.oblivion),
                Buttons.red("deleteButtons", "Decline"));

        return buttons;
    }

    @ButtonHandler(START_IGNO_DISCO)
    public static void startIgnorantDiscoveries(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(IGNO_DISCO) || player.getStrategicCC() < 1) {
            return;
        }

        player.setStrategicCC(player.getStrategicCC() - 1);

        List<Button> buttons = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .map(tech -> Buttons.gray(player.factionButtonChecker() + PURGE_TECH + tech.getID(), tech.getName()))
                .toList();

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please choose the technology to purge.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PURGE_TECH)
    public static void purgeTechAndResearchTwoMore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || game == null || !player.hasAbility(IGNO_DISCO)) {
            return;
        }

        String techID = buttonID.replace(PURGE_TECH, "");
        if (!player.hasTech(techID)) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Unable to find a controlled technology.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        PlayerTechService.purgeTech(event, player, techID);
        Button researchTech = Buttons.green(
                player.factionButtonChecker() + "getAllTechOfType_allTechResearchable_noPay", "Research a Technology");
        String message =
                player.getRepresentationUnfogged() + ", please research 2 technologies for **Ignorant Discoveries**.";
        MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, researchTech);
        MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, researchTech);

        ButtonHelper.deleteMessage(event);
    }

    public static void offerReflectionPlacement(
            ButtonInteractionEvent event, Game game, Player player, Tile activeTile) {
        if (game == null
                || player == null
                || activeTile == null
                || !player.hasAbility(REFLECTIONS_OF_THE_VOID)
                || !activeTile.getPlanetUnitHolders().isEmpty()
                || getAvailableReflectionTokens(game) < 1) {
            return;
        }

        List<Button> buttons = game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .map(Planet::getName)
                        .anyMatch(player.getPlanets()::contains))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + PLACE_REFLECTION + tile.getPosition(),
                        "Place Reflection in " + tile.getRepresentationForButtons(game, player),
                        FactionEmojis.oblivion))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }

        String message = player.getRepresentation()
                + ", you may use **Reflections of the Void** to place a reflection token in a system containing a planet you control.";
        List<Button> extraButtons = List.of(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        String buttonPrefix = player.factionButtonChecker() + PLACE_REFLECTION;
        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(buttons, extraButtons, buttonPrefix, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, displayedButtons);
    }

    @ButtonHandler(PLACE_REFLECTION)
    public static void placeReflection(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace(PLACE_REFLECTION, "");
        List<Button> buttons = game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .map(Planet::getName)
                        .anyMatch(player.getPlanets()::contains))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + PLACE_REFLECTION + tile.getPosition(),
                        "Place Reflection in " + tile.getRepresentationForButtons(game, player),
                        FactionEmojis.oblivion))
                .toList();
        String message = player.getRepresentation()
                + ", you may use **Reflections of the Void** to place a reflection token in a system containing a planet you control.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_REFLECTION;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                List.of(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")),
                message,
                buttonPrefix,
                buttonID)) {
            return;
        }

        Tile tile = game.getTileByPosition(position);
        if (!player.hasAbility(REFLECTIONS_OF_THE_VOID)
                || tile == null
                || tile.getPlanetUnitHolders().stream().map(Planet::getName).noneMatch(player.getPlanets()::contains)
                || getAvailableReflectionTokens(game) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        addReflection(game, tile);
        int reflectionCount = getReflectionCount(game, tile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed a reflection token in "
                        + tile.getRepresentationForButtons(game, player)
                        + ". This system now contains " + reflectionCount + " reflection token"
                        + (reflectionCount == 1 ? "." : "s.")
                        + " Multiple reflection tokens use a single map marker with their count displayed on it.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerReflectionExplore(ButtonInteractionEvent event, Game game) {
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (activeTile == null || getReflectionCount(game, activeTile) < 1) {
            return;
        }

        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility(REFLECTIONS_OF_THE_VOID)) {
                continue;
            }
            Button explore = Buttons.green(
                    player.factionButtonChecker() + PURGE_REFLECTION + activeTile.getPosition(),
                    "Use Reflections of the Void",
                    FactionEmojis.oblivion);
            Button decline = Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline");
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", the active system contains " + getReflectionCount(game, activeTile)
                            + " reflection token" + (getReflectionCount(game, activeTile) == 1 ? "" : "s")
                            + ". You may use **Reflections of the Void** to purge 1 and explore the frontier deck there.",
                    List.of(explore, decline));
        }
    }

    @ButtonHandler(PURGE_REFLECTION)
    public static void purgeReflectionAndExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.replace(PURGE_REFLECTION, ""));
        if (!player.hasAbility(REFLECTIONS_OF_THE_VOID) || tile == null || getReflectionCount(game, tile) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        removeReflection(game, tile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " purged a reflection token in "
                        + tile.getRepresentationForButtons(game, player)
                        + " using **Reflections of the Void** to explore the frontier deck.");
        ExploreService.expFront(event, tile, game, player, true);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean addReflection(Game game, Tile tile) {
        if (getAvailableReflectionTokens(game) < 1) {
            return false;
        }
        int currentCount = getReflectionCount(game, tile);
        if (currentCount == 0) {
            tile.addToken(Mapper.getTokenID("oblivionreflection"), Constants.SPACE);
        }
        game.setStoredValue(REFLECTION_COUNT + tile.getPosition(), Integer.toString(currentCount + 1));
        return true;
    }

    private static void removeReflection(Game game, Tile tile) {
        int remaining = getReflectionCount(game, tile) - 1;
        if (remaining < 1) {
            tile.removeToken(Mapper.getTokenID("oblivionreflection"), Constants.SPACE);
            game.removeStoredValue(REFLECTION_COUNT + tile.getPosition());
        } else {
            game.setStoredValue(REFLECTION_COUNT + tile.getPosition(), Integer.toString(remaining));
        }
        int purged;
        try {
            purged = Integer.parseInt(game.getStoredValue(PURGED_REFLECTION_COUNT));
        } catch (NumberFormatException e) {
            purged = 0;
        }
        game.setStoredValue(PURGED_REFLECTION_COUNT, Integer.toString(purged + 1));
    }

    public static int getReflectionCount(Game game, Tile tile) {
        if (!tile.getSpaceUnitHolder().getTokenList().contains(Mapper.getTokenID("oblivionreflection"))) {
            return 0;
        }
        try {
            return Math.max(1, Integer.parseInt(game.getStoredValue(REFLECTION_COUNT + tile.getPosition())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static boolean hasReflections(Game game) {
        return game != null && game.getTileMap().values().stream().anyMatch(tile -> getReflectionCount(game, tile) > 0);
    }

    public static int getAvailableReflectionTokens(Game game) {
        if (game == null) {
            return 0;
        }
        int reflectionsOnMap = game.getTileMap().values().stream()
                .mapToInt(tile -> getReflectionCount(game, tile))
                .sum();
        int purged;
        try {
            purged = Integer.parseInt(game.getStoredValue(PURGED_REFLECTION_COUNT));
        } catch (NumberFormatException e) {
            purged = 0;
        }
        return Math.max(0, MAX_REFLECTION_TOKENS - reflectionsOnMap - purged);
    }

    public static Button getReflectionLedgerButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SHOW_REFLECTIONS, "View Reflection Tokens", FactionEmojis.oblivion);
    }

    @ButtonHandler(SHOW_REFLECTIONS)
    public static void showReflections(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasAbility(REFLECTIONS_OF_THE_VOID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> reflectedSystems = game.getTileMap().values().stream()
                .filter(tile -> getReflectionCount(game, tile) > 0)
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> "- " + tile.getRepresentationForButtons(game, player) + " (" + tile.getPosition() + "): **"
                        + getReflectionCount(game, tile) + " reflection token"
                        + (getReflectionCount(game, tile) == 1 ? "" : "s") + "**")
                .toList();
        String message = reflectedSystems.isEmpty()
                ? player.getRepresentationUnfogged() + ", there are no reflection tokens on the map."
                : player.getRepresentationUnfogged() + ", your **Reflections of the Void** tokens:\n"
                        + String.join("\n", reflectedSystems)
                        + "\n-# Each system shows one reflection marker, even when it contains multiple reflection tokens.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }
}
