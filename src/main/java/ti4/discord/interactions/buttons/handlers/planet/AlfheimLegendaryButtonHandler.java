package ti4.discord.interactions.buttons.handlers.planet;

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
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class AlfheimLegendaryButtonHandler {
    private static final String STATE_PREFIX = "alfheimLegendary_";
    private static final String CHOOSE_DECK = "alfheimChooseDeck_";
    private static final String CHOOSE_ORDER = "alfheimChooseOrder_";

    public static List<Button> getDeckButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        addDeckButton(buttons, player, game, "CULTURAL", "Cultural", ExploreEmojis.Cultural);
        addDeckButton(buttons, player, game, "INDUSTRIAL", "Industrial", ExploreEmojis.Industrial);
        addDeckButton(buttons, player, game, "HAZARDOUS", "Hazardous", ExploreEmojis.Hazardous);
        if (!game.getAllRelics().isEmpty()) {
            buttons.add(
                    Buttons.green(player.factionButtonChecker() + CHOOSE_DECK + "RELIC", "Relic", ExploreEmojis.Relic));
        }
        return buttons;
    }

    @ButtonHandler(CHOOSE_DECK)
    public static void chooseDeck(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String deck = buttonID.substring(CHOOSE_DECK.length());
        List<String> cards = getTopCards(game, deck);
        if (cards.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(getStateKey(player), deck + "|" + String.join(",", cards) + "|");
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose the order for these cards, starting with the top card.",
                getCardEmbeds(deck, cards),
                getOrderButtons(player, deck, cards));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_ORDER)
    public static void chooseOrder(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(getStateKey(player)).split("\\|", -1);
        if (state.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> remaining = state[1].isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(state[1].split(",")));
        List<String> ordered = state[2].isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(state[2].split(",")));
        int selectedIndex;
        try {
            selectedIndex = Integer.parseInt(buttonID.substring(CHOOSE_ORDER.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= remaining.size()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ordered.add(remaining.remove(selectedIndex));
        if (remaining.isEmpty()) {
            reorderDeck(game, state[0], ordered);
            game.removeStoredValue(getStateKey(player));
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationNoPing() + " reordered the top cards of the " + getDeckName(state[0])
                            + ".");
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(
                getStateKey(player), state[0] + "|" + String.join(",", remaining) + "|" + String.join(",", ordered));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose the next card in the order.",
                getOrderButtons(player, state[0], remaining));
    }

    private static void addDeckButton(
            List<Button> buttons, Player player, Game game, String deck, String label, ExploreEmojis emoji) {
        if (!game.getExploreDeck(deck).isEmpty()) {
            buttons.add(Buttons.green(player.factionButtonChecker() + CHOOSE_DECK + deck, label, emoji));
        }
    }

    private static List<String> getTopCards(Game game, String deck) {
        if ("RELIC".equals(deck)) {
            return game.getAllRelics().stream().limit(3).toList();
        }
        return game.getExploreDeck(deck).stream().limit(3).toList();
    }

    private static List<Button> getOrderButtons(Player player, String deck, List<String> cards) {
        List<Button> buttons = new ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_ORDER + index,
                    "Put " + getCardName(deck, cards.get(index)) + " next"));
        }
        return buttons;
    }

    private static List<MessageEmbed> getCardEmbeds(String deck, List<String> cards) {
        return cards.stream()
                .map(card -> "RELIC".equals(deck)
                        ? Mapper.getRelic(card.replace("extra1", "").replace("extra2", ""))
                        : Mapper.getExplore(card))
                .map(model -> {
                    if (model instanceof RelicModel relic) return relic.getRepresentationEmbed();
                    if (model instanceof ExploreModel explore) return explore.getRepresentationEmbed();
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String getCardName(String deck, String card) {
        if ("RELIC".equals(deck)) {
            RelicModel relic = Mapper.getRelic(card.replace("extra1", "").replace("extra2", ""));
            return relic == null ? card : relic.getName();
        }
        ExploreModel explore = Mapper.getExplore(card);
        return explore == null ? card : explore.getName();
    }

    private static void reorderDeck(Game game, String deck, List<String> ordered) {
        List<String> cards = "RELIC".equals(deck) ? game.getAllRelics() : game.getAllExplores();
        int insertAt = ordered.stream()
                .mapToInt(cards::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(cards.size());
        cards.removeAll(ordered);
        cards.addAll(Math.max(0, insertAt), ordered);
    }

    private static String getDeckName(String deck) {
        return "RELIC".equals(deck) ? "relic deck" : deck.toLowerCase() + " exploration deck";
    }

    private static String getStateKey(Player player) {
        return STATE_PREFIX + player.getFaction();
    }
}
