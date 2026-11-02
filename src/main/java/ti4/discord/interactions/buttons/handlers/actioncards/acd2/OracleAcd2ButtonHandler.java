package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.CardEmojis;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
class OracleAcd2ButtonHandler {

    private static final String CARD = "_Oracle_";
    private static final String NO_CARD = "none";
    private static final int ADDITIONAL_CARDS = 2;

    private enum Deck {
        ACTIONCARD("action card", true),
        SECRETOBJECTIVE("secret objective", true),
        CULTURAL("cultural exploration", false),
        INDUSTRIAL("industrial exploration", false),
        HAZARDOUS("hazardous exploration", false),
        FRONTIER("frontier exploration", false),
        RELIC("relic", false);

        private final String label;
        private final boolean heldInHand;

        Deck(String label, boolean heldInHand) {
            this.label = label;
            this.heldInHand = heldInHand;
        }
    }

    @ButtonHandler("resolveOracle")
    public static void resolveOracle(Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Deck deck : Deck.values()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "oracleDeck_" + deck.name(),
                    "Draw from the " + deck.label + " deck"));
        }
        buttons.add(Buttons.CANCEL_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which deck you are drawing from with " + CARD + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("oracleDeck_")
    public static void chooseDeck(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Deck deck = parseDeck(buttonID.replace("oracleDeck_", ""));
        ButtonHelper.deleteMessage(event);
        if (deck == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }
        if (deck.heldInHand) {
            askForDrawnCard(player, deck);
        } else {
            offerTopCards(player, game, deck);
        }
    }

    @ButtonHandler("oracleTop_")
    public static void keepTopCard(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("oracleTop_", "").split("_", 2);
        Deck deck = parts.length < 2 ? null : parseDeck(parts[0]);
        ButtonHelper.deleteMessage(event);
        if (deck == null || !topCards(game, deck).contains(parts[1])) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }

        List<String> purged = new ArrayList<>(topCards(game, deck));
        purged.remove(parts[1]);
        purged.forEach(cardId -> purgeFromDeck(game, deck, cardId));
        placeOnTop(game, deck, parts[1]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " resolved " + CARD + " on the " + deck.label + " deck, purging "
                        + StringHelper.pluralize(purged.size(), "card")
                        + ". The card they kept is now on top of that deck, so draw it as normal.");
    }

    private static void askForDrawnCard(Player player, Deck deck) {
        List<Button> buttons = handButtons(player, deck, player.factionButtonChecker() + "oracleDrawn_" + deck.name());
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "oracleDrawn_" + deck.name() + "_" + NO_CARD, "I Did Not Draw a Card"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose the " + deck.label
                        + " you drew, which counts as 1 of the cards " + CARD + " lets you choose between.",
                buttons);
    }

    private static void offerTopCards(Player player, Game game, Deck deck) {
        List<String> cards = topCards(game, deck);
        if (cards.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", the " + deck.label + " deck is empty.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();
        for (String cardId : cards) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "oracleTop_" + deck.name() + "_" + cardId,
                    "Keep " + topCardName(deck, cardId)));
            embeds.add(topCardEmbed(deck, cardId));
        }
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", these are the top " + cards.size() + " cards of the "
                        + deck.label + " deck, which are the cards you draw with " + CARD
                        + ". Choose the card to keep; the others are purged.",
                embeds,
                buttons);
    }

    @ButtonHandler("oracleDrawn_")
    public static void chooseDrawnCard(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("oracleDrawn_", "").split("_", 2);
        Deck deck = parts.length < 2 ? null : parseDeck(parts[0]);
        ButtonHelper.deleteMessage(event);
        if (deck == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }

        List<Integer> candidates = new ArrayList<>(drawAdditionalCards(game, player, deck));
        if (!NO_CARD.equals(parts[1])) {
            candidates.add(Integer.parseInt(parts[1]));
        }
        candidates.removeIf(index -> !hand(player, deck).containsValue(index));
        if (candidates.size() < 2) {
            finish(game, player, deck, 0);
            return;
        }

        String candidateIds = candidates.stream().map(String::valueOf).collect(Collectors.joining("-"));
        List<Button> buttons = new ArrayList<>();
        for (Integer index : candidates) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "oracleKeep_" + deck.name() + "_" + index + "_" + candidateIds,
                    "Keep " + cardName(player, deck, index)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", you drew " + StringHelper.pluralize(ADDITIONAL_CARDS, "card")
                        + " with " + CARD + ". Choose the " + deck.label + " to keep; the others are purged.",
                buttons);
    }

    @ButtonHandler("oracleKeep_")
    public static void keepCard(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("oracleKeep_", "").split("_", 3);
        Deck deck = parts.length < 3 ? null : parseDeck(parts[0]);
        ButtonHelper.deleteMessage(event);
        if (deck == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }

        Integer kept = Integer.valueOf(parts[1]);
        List<Integer> toPurge = Arrays.stream(parts[2].split("-"))
                .map(Integer::valueOf)
                .filter(index -> !index.equals(kept))
                .filter(index -> hand(player, deck).containsValue(index))
                .toList();
        toPurge.forEach(index -> purge(game, player, deck, index));
        finish(game, player, deck, toPurge.size());
    }

    private static void finish(Game game, Player player, Deck deck, int purged) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " resolved " + CARD + " on the " + deck.label + " deck, purging "
                        + StringHelper.pluralize(purged, "card") + ".");
        switch (deck) {
            case ACTIONCARD -> ActionCardHelper.sendActionCardInfo(game, player);
            case SECRETOBJECTIVE -> SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
            default -> {}
        }
    }

    private static List<Integer> drawAdditionalCards(Game game, Player player, Deck deck) {
        Set<Integer> before = new HashSet<>(hand(player, deck).values());
        switch (deck) {
            case ACTIONCARD -> game.drawActionCard(player.getUserID(), ADDITIONAL_CARDS);
            case SECRETOBJECTIVE -> {
                for (int i = 0;
                        i < ADDITIONAL_CARDS && !game.getSecretObjectives().isEmpty();
                        i++) {
                    game.drawSpecificSecretObjective(game.getSecretObjectives().getFirst(), player.getUserID());
                }
            }
            default -> {}
        }
        return hand(player, deck).values().stream()
                .filter(index -> !before.contains(index))
                .toList();
    }

    private static void purge(Game game, Player player, Deck deck, Integer index) {
        switch (deck) {
            case ACTIONCARD -> game.purgedActionCard(player.getUserID(), index);
            case SECRETOBJECTIVE -> player.removeSecret(index);
            default -> {}
        }
    }

    private static Map<String, Integer> hand(Player player, Deck deck) {
        return switch (deck) {
            case ACTIONCARD -> player.getActionCards();
            case SECRETOBJECTIVE -> player.getSecretsUnscored();
            default -> Map.of();
        };
    }

    private static List<Button> handButtons(Player player, Deck deck, String idPrefix) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> card : hand(player, deck).entrySet()) {
            buttons.add(Buttons.green(
                    idPrefix + "_" + card.getValue(), cardName(player, deck, card.getValue()), cardEmoji(deck)));
        }
        return buttons;
    }

    private static String cardName(Player player, Deck deck, Integer index) {
        return hand(player, deck).entrySet().stream()
                .filter(card -> card.getValue().equals(index))
                .map(card -> switch (deck) {
                    case ACTIONCARD -> Mapper.getActionCard(card.getKey()).getName();
                    case SECRETOBJECTIVE ->
                        Mapper.getSecretObjective(card.getKey()).getName();
                    default -> "Unknown Card";
                })
                .findFirst()
                .orElse("Unknown Card");
    }

    private static String cardEmoji(Deck deck) {
        return switch (deck) {
            case ACTIONCARD -> CardEmojis.ActionCard.toString();
            case SECRETOBJECTIVE -> CardEmojis.SecretObjective.toString();
            default -> "";
        };
    }

    private static List<String> topCards(Game game, Deck deck) {
        int count = ADDITIONAL_CARDS + 1;
        if (deck == Deck.RELIC) {
            return game.getAllRelics().stream().limit(count).toList();
        }
        return game.peekExploreTop(deck.name().toLowerCase(), count);
    }

    private static void placeOnTop(Game game, Deck deck, String cardId) {
        if (deck == Deck.RELIC) {
            game.getAllRelics().remove(cardId);
            game.getAllRelics().addFirst(cardId);
        } else {
            game.placeExploreOnTop(cardId);
        }
    }

    private static void purgeFromDeck(Game game, Deck deck, String cardId) {
        if (deck == Deck.RELIC) {
            game.getAllRelics().remove(cardId);
        } else {
            game.purgeExplore(cardId);
        }
    }

    private static String topCardName(Deck deck, String cardId) {
        return deck == Deck.RELIC
                ? Mapper.getRelic(cardId).getName()
                : Mapper.getExplore(cardId).getName();
    }

    private static MessageEmbed topCardEmbed(Deck deck, String cardId) {
        return deck == Deck.RELIC
                ? Mapper.getRelic(cardId).getRepresentationEmbed(false, true)
                : Mapper.getExplore(cardId).getRepresentationEmbed(false, true);
    }

    private static Deck parseDeck(String name) {
        try {
            return Deck.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
