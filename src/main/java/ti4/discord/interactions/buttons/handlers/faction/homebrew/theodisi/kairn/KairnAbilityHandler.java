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
    private static final String SHARED_DISCOVERIES = "shared_discoveries";
    private static final String USE_SHARED_DISCOVERIES = "useSharedDiscoveries";
    private static final String USE_SHARED_DISCOVERIES_PROMPT = "useSharedDiscoveriesPrompt";

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
                || (player.getStrategicCC() < 1 && player.getFleetCC() < 1 && player.getTacticalCC() < 1)
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

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please remove 1 command token from any pool.",
                ButtonHelper.getLoseCCButtons(player));
        List<String> revealedCards = new ArrayList<>();
        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" spent 1 command token for **Colony Outposts** and revealed from the ")
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

    // Shared Discoveries
    public static Button getSharedDiscoveriesButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_SHARED_DISCOVERIES, "Use Shared Discoveries", FactionEmojis.kairn);
    }

    public static void offerSharedDiscoveries(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasAbility(SHARED_DISCOVERIES)
                || player.getCommodities() < 1
                || getSharedDiscoveriesExploreButtons(game, player).isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may spend 1 commodity to use **Shared Discoveries** to explore a planet in the active system as any trait.",
                List.of(
                        Buttons.gray(
                                player.factionButtonChecker() + USE_SHARED_DISCOVERIES_PROMPT,
                                "Use Shared Discoveries",
                                FactionEmojis.kairn),
                        Buttons.red("deleteButtons", "Decline")));
    }

    @ButtonHandler(USE_SHARED_DISCOVERIES)
    @ButtonHandler(USE_SHARED_DISCOVERIES_PROMPT)
    public static void getSharedDiscoveriesPlanets(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean isPrompt = buttonID.startsWith(USE_SHARED_DISCOVERIES_PROMPT);
        Player activePlayer = game == null ? null : game.getPlayer(game.getActivePlayerID());
        if (player == null
                || !player.hasAbility(SHARED_DISCOVERIES)
                || player.getCommodities() < 1
                || activePlayer == null) {
            if (isPrompt) {
                ButtonHelper.deleteMessage(event);
            } else {
                ButtonHelper.deleteTheOneButton(event);
            }
            return;
        }
        List<Button> exploreButtons = getSharedDiscoveriesExploreButtons(game, activePlayer);
        if (exploreButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "The active player controls no planets in the active system to explore.");
            if (isPrompt) {
                ButtonHelper.deleteMessage(event);
            } else {
                ButtonHelper.deleteTheOneButton(event);
            }
            return;
        }

        player.setCommodities(player.getCommodities() - 1);
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer.getCorrectChannel(),
                player.getRepresentationNoPing() + " spent 1 commodity to use **Shared Discoveries**. "
                        + activePlayer.getRepresentation()
                        + ", choose a planet in the active system and the trait to explore it as.",
                exploreButtons);
        if (isPrompt) {
            ButtonHelper.deleteMessage(event);
        }
    }

    private static List<Button> getSharedDiscoveriesExploreButtons(Game game, Player player) {
        Tile activeSystem = game == null ? null : game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null || player == null) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : activeSystem.getPlanetUnitHolders()) {
            if (!player.getPlanets().contains(planet.getName())) {
                continue;
            }
            for (String trait : List.of("cultural", "hazardous", "industrial")) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "movedNExplored_filler_" + planet.getName() + "_" + trait,
                        "Explore " + Helper.getPlanetRepresentation(planet.getName(), game) + " As "
                                + StringUtils.capitalize(trait),
                        ExploreEmojis.getTraitEmoji(trait)));
            }
        }
        return buttons;
    }

    public static boolean canOfferSharedDiscoveriesCardsInfoButton(Game game, Player player) {
        Player activePlayer = game == null ? null : game.getPlayer(game.getActivePlayerID());
        return player != null
                && player.hasAbility(SHARED_DISCOVERIES)
                && activePlayer != null
                && activePlayer != player;
    }
}
