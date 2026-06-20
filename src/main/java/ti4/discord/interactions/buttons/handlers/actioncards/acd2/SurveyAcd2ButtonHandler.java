package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
class SurveyAcd2ButtonHandler {

    private static final List<String> TRAITS = List.of("cultural", "industrial", "hazardous");

    @ButtonHandler("resolveSurvey")
    public static void resolveSurvey(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null) {
                continue;
            }
            for (String trait : TRAITS) {
                if (planet.getPlanetTypes().contains(trait)) {
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + "surveySelect_" + planetName + "_" + trait,
                            "Explore " + Helper.getPlanetRepresentation(planetName, game) + " ("
                                    + StringUtils.capitalize(trait) + ")",
                            ExploreEmojis.getTraitEmoji(trait)));
                }
            }
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you do not control a planet you can explore for _Survey_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose 1 planet you control to explore for _Survey_.",
                buttons);
    }

    @ButtonHandler("surveySelect_")
    public static void surveySelect(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("surveySelect_", "").split("_");
        if (parts.length < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planetName = parts[0];
        String trait = parts[1];
        if (!player.getPlanetsAllianceMode().contains(planetName)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "You no longer control that planet for _Survey_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        // Reveal (draw) the top card of the matching deck. drawExplore already moves it to the discard pile.
        String cardId = game.drawExplore(trait);
        if (cardId == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", the " + trait + " exploration deck is empty for _Survey_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExploreModel card = Mapper.getExplore(cardId);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "surveyResolveRevealed_" + cardId + "_" + planetName + "_" + trait,
                "Resolve " + card.getName()));
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + "surveyDiscardOptions_" + planetName + "_" + trait,
                "Resolve from " + StringUtils.capitalize(trait) + " Discard Instead"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " revealed _" + card.getName() + "_ exploring "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " for _Survey_. Resolve this card, or resolve any card from the " + trait
                        + " discard pile instead.",
                List.of(card.getRepresentationEmbed()),
                buttons);
    }

    @ButtonHandler("surveyResolveRevealed_")
    public static void surveyResolveRevealed(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("surveyResolveRevealed_", "").split("_");
        if (parts.length < 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        resolveCard(player, game, event, parts[0], parts[1]);
    }

    @ButtonHandler("surveyDiscardOptions_")
    public static void surveyDiscardOptions(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("surveyDiscardOptions_", "").split("_");
        if (parts.length < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planetName = parts[0];
        String trait = parts[1];

        // One button per distinct card name in the discard pile of this trait.
        Map<String, String> distinctByName = new LinkedHashMap<>();
        for (String cardId : game.getExploreDiscard(trait)) {
            ExploreModel card = Mapper.getExplore(cardId);
            if (card != null) {
                distinctByName.putIfAbsent(card.getName(), cardId);
            }
        }

        ButtonHelper.deleteMessage(event);
        if (distinctByName.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", the " + trait
                            + " discard pile is empty, so there is nothing to resolve for _Survey_.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, String> entry : distinctByName.entrySet()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "surveyResolveDiscard_" + entry.getValue() + "_" + planetName + "_"
                            + trait,
                    "Resolve " + entry.getKey(),
                    ExploreEmojis.getTraitEmoji(trait)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which card from the " + trait
                        + " discard pile to resolve for _Survey_.",
                buttons);
    }

    @ButtonHandler("surveyResolveDiscard_")
    public static void surveyResolveDiscard(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("surveyResolveDiscard_", "").split("_");
        if (parts.length < 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String cardId = parts[0];
        if (!game.getExploreDiscard(parts[2]).contains(cardId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "That card is no longer in the discard pile for _Survey_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        resolveCard(player, game, event, cardId, parts[1]);
    }

    private static void resolveCard(
            Player player, Game game, ButtonInteractionEvent event, String cardId, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        String messageText = player.getRepresentation() + " resolved _"
                + Mapper.getExplore(cardId).getName() + "_ on " + Helper.getPlanetRepresentation(planetName, game)
                + " for _Survey_:";
        ExploreService.resolveExplore(event, cardId, tile, planetName, messageText, player, game);
        ButtonHelper.deleteMessage(event);
    }
}
