package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
public class EcologicalSurveyLLButtonHandler {
    private static final String RESOLVE = "resolveEcologicalSurvey";
    private static final String PLANET = "ecologicalSurveyPlanet_";
    private static final String EXPLORE = "ecologicalSurveyExplore_";
    private static final String STATE = "ecologicalSurvey_";
    private static final List<String> TRAITS = List.of("cultural", "industrial", "hazardous", "frontier");

    @ButtonHandler(RESOLVE)
    public static void resolveEcologicalSurvey(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(STATE + player.getFaction());
        List<Button> buttons = getPlanetButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no planet they can explore for _Ecological Survey_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message =
                player.getRepresentationNoPing() + ", choose the planet you would explore for _Ecological Survey_.";
        MessageHelper.editMessageWithButtons(
                event, message, NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + PLANET, 0));
    }

    @ButtonHandler(PLANET)
    public static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getPlanetButtons(game, player);
        String message =
                player.getRepresentationNoPing() + ", choose the planet you would explore for _Ecological Survey_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + PLANET, buttonID)) {
            return;
        }

        String planetName = buttonID.substring(PLANET.length());
        if (!player.getPlanetsAllianceMode().contains(planetName) || game.getUnitHolderFromPlanet(planetName) == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible to explore.");
            return;
        }

        List<String> drawnCards = new ArrayList<>();
        List<String> drawnTraits = new ArrayList<>();
        for (String trait : TRAITS) {
            String cardID = game.drawExplore(trait);
            if (cardID != null && Mapper.getExplore(cardID) != null) {
                drawnTraits.add(trait);
                drawnCards.add(cardID);
            }
        }
        if (drawnCards.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "No planet-trait exploration cards are available for _Ecological Survey_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> exploreButtons = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();
        StringBuilder state = new StringBuilder(planetName);
        for (int index = 0; index < drawnCards.size(); index++) {
            String trait = drawnTraits.get(index);
            String cardID = drawnCards.get(index);
            ExploreModel card = Mapper.getExplore(cardID);
            state.append('|').append(trait).append('|').append(cardID);
            exploreButtons.add(Buttons.green(
                    player.factionButtonChecker() + EXPLORE + index,
                    "Resolve " + card.getName(),
                    ExploreEmojis.getTraitEmoji(trait)));
            embeds.add(card.getRepresentationEmbed());
        }
        game.setStoredValue(STATE + player.getFaction(), state.toString());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose 1 exploration card to resolve on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " for _Ecological Survey_. The other revealed cards will be discarded.",
                embeds,
                exploreButtons);
    }

    @ButtonHandler(EXPLORE)
    public static void resolveExplore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = game.getStoredValue(STATE + player.getFaction()).split("\\|");
        if (payload.length < 3 || (payload.length - 1) % 2 != 0) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That exploration selection is no longer valid.");
            return;
        }
        String planetName = payload[0];
        int selectedIndex;
        try {
            selectedIndex = Integer.parseInt(buttonID.substring(EXPLORE.length()));
        } catch (NumberFormatException e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That exploration selection is no longer valid.");
            return;
        }
        int numberOfCards = (payload.length - 1) / 2;
        if (!player.getPlanetsAllianceMode().contains(planetName)
                || game.getUnitHolderFromPlanet(planetName) == null
                || selectedIndex < 0
                || selectedIndex >= numberOfCards) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That exploration selection is no longer valid.");
            return;
        }

        String selectedCard = null;
        for (int index = 0; index < numberOfCards; index++) {
            String trait = payload[1 + index * 2];
            String cardID = payload[2 + index * 2];
            if (Mapper.getExplore(cardID) == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "That exploration selection is no longer valid.");
                return;
            }
            // drawExplore reshuffles a just-emptied deck. Return only cards that were reshuffled to the discard.
            if (game.getExploreDeck(trait).contains(cardID)) {
                game.discardExplore(cardID);
            }
            if (index == selectedIndex) {
                selectedCard = cardID;
            }
        }
        Tile tile = game.getTileFromPlanet(planetName);
        String message = player.getRepresentationNoPing() + " chose to resolve _"
                + Mapper.getExplore(selectedCard).getName() + "_ on "
                + Helper.getPlanetRepresentation(planetName, game) + " for _Ecological Survey_.";
        game.removeStoredValue(STATE + player.getFaction());
        ExploreService.resolveExplore(event, selectedCard, tile, planetName, message, player, game);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getPlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (game.getUnitHolderFromPlanet(planetName) != null) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + PLANET + planetName,
                        "Explore " + Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        return buttons;
    }
}
