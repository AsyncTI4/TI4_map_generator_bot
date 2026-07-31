package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
public class KairnAbilityHandler {
    private static final String COLONY_OUTPOSTS = "colony_outposts";
    private static final String USE_COLONY_OUTPOSTS = "useColonyOutposts";
    private static final String SELECT_COLONY_OUTPOSTS_PLANET = "selectColonyOutpostsPlanet_";
    private static final String USE_EXPEDITIONARY_CACHE = "useExpeditionaryCache";
    private static final String PLACE_EXPEDITION_TOKEN = "placeExpeditionToken_";
    private static final String SHARED_DISCOVERIES = "shared_discoveries";
    private static final String USE_SHARED_DISCOVERIES = "useSharedDiscoveries";
    private static final String REMOVE_EXPEDITION_TOKEN = "removeExpeditionToken_";
    private static final String EXPEDITION_TOKEN = "token_theodisi_kairnexpedition.png";
    private static final int MAX_EXPEDITION_TOKENS = 5;

    // Colony Outposts
    public static Button offerColonyOutposts(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_COLONY_OUTPOSTS, "Use Colony Outposts", FactionEmojis.kairn);
    }

    @ButtonHandler(USE_COLONY_OUTPOSTS)
    public static void startColonyOutposts(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasAbility(COLONY_OUTPOSTS)
                || player.getStrategicCC() < 1
                || !player.getUserID().equals(game.getActivePlayerID())
                || game.getStoredValue(ButtonHelperTacticalAction.TACTICAL_ACTION_LOGGED)
                        .isEmpty()
                || game.getStoredValue(player.getFaction() + "planetsExplored").isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Set<String> exploredPlanets = new LinkedHashSet<>(Arrays.asList(
                game.getStoredValue(player.getFaction() + "planetsExplored").split("\\*")));
        List<Button> buttons = new ArrayList<>();
        for (String planetName : exploredPlanets) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null) {
                continue;
            }
            for (String trait : planet.getPlanetTypes()) {
                if (!hasAttachmentInExploreDeck(game, trait)) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SELECT_COLONY_OUTPOSTS_PLANET + planetName + "|" + trait,
                        StringUtils.capitalize(trait) + " " + Helper.getPlanetRepresentation(planetName, game)));
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has no explored planet with an attachment remaining in its exploration deck.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose an exploration deck of a planet you explored.",
                buttons);
    }

    @ButtonHandler(SELECT_COLONY_OUTPOSTS_PLANET)
    public static void resolveColonyOutposts(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String buttonInfo = buttonID.substring(SELECT_COLONY_OUTPOSTS_PLANET.length());
        int separatorIndex = buttonInfo.lastIndexOf('|');
        if (separatorIndex < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planetName = buttonInfo.substring(0, separatorIndex);
        String trait = buttonInfo.substring(separatorIndex + 1);
        Planet planet = game.getPlanetsInfo().get(planetName);
        String exploredPlanets = game.getStoredValue(player.getFaction() + "planetsExplored");
        if (!player.hasAbility(COLONY_OUTPOSTS)
                || player.getStrategicCC() < 1
                || !player.getUserID().equals(game.getActivePlayerID())
                || game.getStoredValue(ButtonHelperTacticalAction.TACTICAL_ACTION_LOGGED)
                        .isEmpty()
                || planet == null
                || !exploredPlanets.contains(planetName + "*")
                || !planet.getPlanetTypes().contains(trait)
                || !hasAttachmentInExploreDeck(game, trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setStrategicCC(player.getStrategicCC() - 1);
        List<String> revealedCards = new ArrayList<>();
        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" spent 1 strategy token for **Colony Outposts** and revealed from the ")
                .append(trait)
                .append(" exploration deck for ")
                .append(Helper.getPlanetRepresentation(planetName, game))
                .append(':');

        while (true) {
            if (game.getExploreDeck(trait).isEmpty()) {
                for (String cardID : new ArrayList<>(game.getExploreDiscard(trait))) {
                    game.addExplore(cardID);
                }
            }

            String cardID = game.getExploreDeck(trait).getFirst();
            game.discardExplore(cardID);
            revealedCards.add(cardID);
            ExploreModel explore = Mapper.getExplore(cardID);
            if (explore == null) {
                continue;
            }

            message.append("\n> Revealed ").append(explore.getNameRepresentation());
            if (!"attach".equalsIgnoreCase(explore.getResolution())) {
                continue;
            }

            message.append(" and found an attachment.");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
            ExploreService.resolveExplore(
                    event,
                    cardID,
                    tile,
                    planetName,
                    player.getRepresentation() + " resolved an attachment with _Colony Outposts_ on "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game)
                            + ":",
                    player,
                    game);

            for (String revealedCard : revealedCards) {
                if (!revealedCard.equals(cardID)
                        && game.getExploreDiscard(trait).contains(revealedCard)) {
                    game.addExplore(revealedCard);
                }
            }
            ButtonHelper.deleteMessage(event);
            return;
        }
    }

    private static boolean hasAttachmentInExploreDeck(Game game, String trait) {
        for (String cardID : game.getExploreDeck(trait)) {
            ExploreModel explore = Mapper.getExplore(cardID);
            if (explore != null && "attach".equalsIgnoreCase(explore.getResolution())) {
                return true;
            }
        }
        for (String cardID : game.getExploreDiscard(trait)) {
            ExploreModel explore = Mapper.getExplore(cardID);
            if (explore != null && "attach".equalsIgnoreCase(explore.getResolution())) {
                return true;
            }
        }
        return false;
    }

    // Expeditionary Cache
    public static List<Button> getExpeditionaryCacheButtons(Player player, Game game) {
        if (player == null || player.getCommodities() < 1 || getAvailableExpeditionTokens(game) < 1) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_EXPEDITIONARY_CACHE,
                "Use Expeditionary Cache",
                FactionEmojis.kairn));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        return buttons;
    }

    @ButtonHandler(USE_EXPEDITIONARY_CACHE)
    public static void getExpeditionaryCachePlanets(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasAbility("expeditionary_cache")
                || player.getCommodities() == 0
                || getAvailableExpeditionTokens(game) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> planets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getTokenList().contains(EXPEDITION_TOKEN)) {
                    continue;
                }
                planets.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_EXPEDITION_TOKEN + planet.getName(),
                        "Place on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        String prefix = player.factionButtonChecker() + PLACE_EXPEDITION_TOKEN;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Done"));
        List<Button> displayedButtons = NewStuffHelper.buttonPagination(planets, extraButtons, prefix, 25, 0, false);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose the planets on which to place expedition tokens using **Expeditionary Cache**. You may place "
                        + getExpeditionTokensToPlace(player, game) + " more token"
                        + (getExpeditionTokensToPlace(player, game) == 1 ? "." : "s."),
                displayedButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_EXPEDITION_TOKEN)
    public static void placeExpeditionTokenOnPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasAbility("expeditionary_cache")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> planets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getTokenList().contains(EXPEDITION_TOKEN)) {
                    continue;
                }
                planets.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_EXPEDITION_TOKEN + planet.getName(),
                        "Place on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }

        String message = player.getRepresentation()
                + ", please choose the planets on which to place expedition tokens using **Expeditionary Cache**. You may place "
                + getExpeditionTokensToPlace(player, game) + " more token"
                + (getExpeditionTokensToPlace(player, game) == 1 ? "." : "s.");
        String prefix = player.factionButtonChecker() + PLACE_EXPEDITION_TOKEN;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Done"));

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), planets, extraButtons, message, prefix, buttonID)) {
            return;
        }

        String planetName = buttonID.substring(PLACE_EXPEDITION_TOKEN.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve planet name.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (player.getCommodities() < 1
                || getAvailableExpeditionTokens(game) < 1
                || planet.getTokenList().contains(EXPEDITION_TOKEN)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        tile.addToken(EXPEDITION_TOKEN, planetName);
        player.setCommodities(player.getCommodities() - 1);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed an expedition token on " + planet.getRepresentation(game)
                        + " using **Expeditionary Cache**. Their commodities are now (" + player.getCommodities() + "/"
                        + player.getCommoditiesTotal()
                        + ").");

        ButtonHelper.deleteMessage(event);
        getExpeditionaryCachePlanets(event, game, player);
    }

    // Shared Discoveries
    public static Button getSharedDiscoveriesButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_SHARED_DISCOVERIES, "Remove Expedition Token", FactionEmojis.kairn);
    }

    @ButtonHandler(USE_SHARED_DISCOVERIES)
    public static void getSharedDiscoveriesPlanets(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(SHARED_DISCOVERIES)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> planets = getSharedDiscoveriesPlanetButtons(game, player);
        if (planets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no expedition tokens on planets in the active system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String prefix = player.factionButtonChecker() + REMOVE_EXPEDITION_TOKEN;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose an expedition token to remove using **Shared Discoveries**.",
                NewStuffHelper.buttonPagination(planets, prefix, 0));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }

    @ButtonHandler(REMOVE_EXPEDITION_TOKEN)
    public static void resolveSharedDiscoveries(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasAbility(SHARED_DISCOVERIES)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> planets = getSharedDiscoveriesPlanetButtons(game, player);
        String message = player.getRepresentation()
                + ", please choose an expedition token to remove using **Shared Discoveries**.";
        String prefix = player.factionButtonChecker() + REMOVE_EXPEDITION_TOKEN;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), planets, message, prefix, buttonID)) {
            return;
        }

        String planetName = buttonID.substring(REMOVE_EXPEDITION_TOKEN.length());
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        Planet planet = activeSystem == null ? null : activeSystem.getUnitHolderFromPlanet(planetName);
        Player planetOwner = game.getPlayerThatControlsPlanet(planetName);
        if (planet == null
                || planetOwner == null
                || !planetOwner.getUserID().equals(game.getActivePlayerID())
                || !planet.getTokenList().contains(EXPEDITION_TOKEN)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        planet.removeToken(EXPEDITION_TOKEN);
        ButtonHelper.deleteMessage(event);

        List<Button> exploreButtons = new ArrayList<>();
        for (String trait : List.of("cultural", "hazardous", "industrial")) {
            exploreButtons.add(Buttons.gray(
                    planetOwner.factionButtonChecker() + "movedNExplored_filler_" + planetName + "_" + trait,
                    "Explore " + Helper.getPlanetRepresentation(planetName, game) + " As "
                            + StringUtils.capitalize(trait),
                    ExploreEmojis.getTraitEmoji(trait)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                planetOwner.getCorrectChannel(),
                planetOwner.getRepresentation() + ", an expedition token was removed from "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " due to **Shared Discoveries**. Please choose its exploration trait.",
                exploreButtons);
    }

    private static List<Button> getSharedDiscoveriesPlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return buttons;
        }
        for (Planet planet : activeSystem.getPlanetUnitHolders()) {
            Player planetOwner = game.getPlayerThatControlsPlanet(planet.getName());
            if (planet.getTokenList().contains(EXPEDITION_TOKEN)
                    && planetOwner != null
                    && planetOwner.getUserID().equals(game.getActivePlayerID())) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + REMOVE_EXPEDITION_TOKEN + planet.getName(),
                        "Remove From " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }

    public static int getAvailableExpeditionTokens(Game game) {
        if (game == null) {
            return 0;
        }
        int placed = (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> planet.getTokenList().contains(EXPEDITION_TOKEN))
                .count();
        return Math.max(0, MAX_EXPEDITION_TOKENS - placed);
    }

    private static int getExpeditionTokensToPlace(Player player, Game game) {
        return Math.min(player.getCommodities(), getAvailableExpeditionTokens(game));
    }
}
