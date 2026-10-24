package ti4.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha.HydromorphicCardHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha.MorphaUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.HydromorphicCardModel;
import ti4.model.Mapper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class HydromorphicHelper {
    private static final String CHOICE_PREFIX = "hydromorphicKeep_";
    private static final String PLAY_PREFIX = "playHydromorphic_";
    private static final String DISCARD_PREFIX = "hydromorphicDiscard_";
    private static final String CONFIRM_PREFIX = "hydromorphicConfirm";
    private static final String CANCEL_PREFIX = "hydromorphicCancel";
    private static final String DRAWN_CARDS_KEY = "hydromorphicDrawn_";
    private static final String PENDING_CARD_KEY = "hydromorphicPendingCard_";

    private record PendingCard(
            String cardId, int identifier, int discardedActionCards, List<String> discardedActionCardIds) {}

    public static boolean shouldShowInPlayerArea(Player player) {
        return !player.getHydromorphicCards().isEmpty()
                || player.hasAbility("hydromorphic")
                || player.hasTech("thmorphag")
                || player.getPromissoryNotesInPlayArea().contains("thpnmorpha");
    }

    public static boolean isDeckAvailable(Game game) {
        return game.getRealPlayers().stream().anyMatch(HydromorphicHelper::shouldShowInPlayerArea);
    }

    @ButtonHandler(value = "showDeck_hydromorphic", save = false)
    public static void showUnplayedDeck(Game game, GenericInteractionCreateEvent event) {
        showUnplayedDeck(game, event, false);
    }

    @ButtonHandler(value = "hydromorphicShowUnplayedFullText", save = false)
    public static void showUnplayedDeckFullText(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        showUnplayedDeck(game, event, true);
    }

    private static void showUnplayedDeck(Game game, GenericInteractionCreateEvent event, boolean showFullText) {
        List<HydromorphicCardModel> cards = Mapper.getHydromorphicCards().values().stream()
                .sorted(Comparator.comparing(HydromorphicCardModel::getName)
                        .thenComparing(HydromorphicCardModel::getAlias))
                .toList();
        StringBuilder message = new StringBuilder("### __Hydromorphic Cards (20)__\n");
        int index = 1;
        for (HydromorphicCardModel card : cards) {
            message.append(index++)
                    .append("\\. ")
                    .append(CardEmojis.HydromorphicCard)
                    .append(" _")
                    .append(card.getName())
                    .append("_ `")
                    .append(card.getAlias())
                    .append('`');
            if (showFullText) {
                message.append("\n> ").append(card.getWindow()).append(": ").append(card.getText());
            }
            message.append('\n');
        }
        MessageHelper.sendMessageToChannelWithButton(
                event.getMessageChannel(),
                message.toString(),
                showFullText ? null : Buttons.green("hydromorphicShowUnplayedFullText", "Show Full Text"));
    }

    public static boolean hasPendingCard(Game game, Player player) {
        return getPendingCard(game, player) != null;
    }

    public static void draw(Game game, Player player, int count) {
        List<String> drawn = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String cardId = game.drawHydromorphicCard(player);
            if (cardId != null) {
                drawn.add(cardId);
            }
        }
        if (drawn.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "No Hydromorphic cards remain in the deck.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " drew " + StringHelper.pluralize(drawn.size(), "Hydromorphic card")
                        + ".");
        if (drawn.size() == 1) {
            sendHydromorphicInfo(game, player);
            return;
        }
        game.setStoredValue(DRAWN_CARDS_KEY + player.getFaction(), String.join(",", drawn));
        List<Button> buttons = drawn.stream()
                .map(cardId -> Buttons.green(
                        CHOICE_PREFIX + cardId,
                        "Keep " + Mapper.getHydromorphicCard(cardId).getName()))
                .toList();
        List<MessageEmbed> embeds = drawn.stream()
                .map(Mapper::getHydromorphicCard)
                .map(HydromorphicCardModel::getRepresentationEmbed)
                .toList();
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose 1 Hydromorphic card to keep. The other will be shuffled back.",
                embeds,
                buttons);
    }

    @ButtonHandler(CHOICE_PREFIX)
    public static void keepDrawnCard(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String keptCard = buttonID.replace(CHOICE_PREFIX, "");
        List<String> drawnCards = new ArrayList<>(List.of(
                game.getStoredValue(DRAWN_CARDS_KEY + player.getFaction()).split(",")));
        if (!drawnCards.contains(keptCard) || !player.getHydromorphicCards().containsKey(keptCard)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Hydromorphic card is no longer available.");
            return;
        }
        List<String> returnedCards = new ArrayList<>(drawnCards);
        returnedCards.remove(keptCard);
        for (String cardId : returnedCards) {
            Integer identifier = player.getHydromorphicCards().get(cardId);
            if (identifier != null) {
                player.removeHydromorphicCard(identifier);
                game.returnHydromorphicCard(cardId);
            }
        }
        game.setStoredValue(DRAWN_CARDS_KEY + player.getFaction(), "");
        ButtonHelper.deleteMessage(event);
        sendHydromorphicInfo(game, player);
    }

    public static void sendHydromorphicInfo(Game game, Player player) {
        if (player.getHydromorphicCards().isEmpty()) {
            return;
        }
        PendingCard pendingCard = getPendingCard(game, player);
        StringBuilder message = new StringBuilder("### __Hydromorphic Cards__\n");
        int[] index = {1};
        player.getHydromorphicCards().entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .forEach(entry -> {
                    HydromorphicCardModel card = Mapper.getHydromorphicCard(entry.getKey());
                    message.append(index[0]).append("\\. ");
                    index[0]++;
                    message.append(CardEmojis.HydromorphicCard)
                            .append(" _")
                            .append(card.getName())
                            .append("_ `")
                            .append(Helper.leftpad("(" + entry.getValue(), 3))
                            .append(")`\n> ")
                            .append(card.getWindow())
                            .append(": ")
                            .append(card.getText())
                            .append(
                                    pendingCard != null && pendingCard.cardId().equals(entry.getKey())
                                            ? " (resolving)"
                                            : "")
                            .append('\n');
                });
        List<Button> buttons = player.getHydromorphicCards().entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .filter(entry -> pendingCard == null || !pendingCard.cardId().equals(entry.getKey()))
                .map(entry -> Buttons.red(
                        PLAY_PREFIX + entry.getValue(),
                        "(" + entry.getValue() + ") "
                                + Mapper.getHydromorphicCard(entry.getKey()).getName()))
                .toList();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message.toString(), buttons);
    }

    @ButtonHandler(PLAY_PREFIX)
    public static void playCard(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        int identifier;
        try {
            identifier = Integer.parseInt(buttonID.replace(PLAY_PREFIX, ""));
        } catch (NumberFormatException e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Hydromorphic card could not be found.");
            return;
        }
        if (!beginCardResolution(game, player, identifier)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Finish or cancel your pending Hydromorphic card first.");
            return;
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    public static boolean beginCardResolution(Game game, Player player, int identifier) {
        if (getPendingCard(game, player) != null) {
            return false;
        }
        String cardId = player.getHydromorphicCards().entrySet().stream()
                .filter(entry -> entry.getValue().equals(identifier))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (cardId == null) {
            return false;
        }
        HydromorphicCardModel card = Mapper.getHydromorphicCard(cardId);
        setPendingCard(game, player, new PendingCard(cardId, identifier, 0, List.of()));
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " is resolving "
                        + card.getNameRepresentation()
                        + ".",
                card.getRepresentationEmbed());
        sendPendingDiscardPrompt(game, player);
        return true;
    }

    @ButtonHandler(DISCARD_PREFIX)
    public static void discardActionCard(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        PendingCard pendingCard = getPendingCard(game, player);
        if (pendingCard == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There is no pending Hydromorphic card.");
            return;
        }
        HydromorphicCardModel card = Mapper.getHydromorphicCard(pendingCard.cardId());
        if (card.getActionCardDiscardMax() != null
                && pendingCard.discardedActionCards() >= card.getActionCardDiscardMax()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have already discarded the maximum amount.");
            return;
        }
        int identifier;
        try {
            identifier = Integer.parseInt(buttonID.replace(DISCARD_PREFIX, ""));
        } catch (NumberFormatException e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Action Card could not be found.");
            return;
        }
        String actionCardId = player.getActionCards().entrySet().stream()
                .filter(entry -> entry.getValue().equals(identifier))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (actionCardId == null || pendingCard.discardedActionCardIds().contains(actionCardId)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Action Card is no longer in your hand.");
            return;
        }
        List<String> discardedActionCardIds = new ArrayList<>(pendingCard.discardedActionCardIds());
        discardedActionCardIds.add(actionCardId);
        setPendingCard(
                game,
                player,
                new PendingCard(
                        pendingCard.cardId(),
                        pendingCard.identifier(),
                        pendingCard.discardedActionCards() + 1,
                        discardedActionCardIds));
        refreshPendingDiscardPrompt(event, game, player);
    }

    @ButtonHandler(CONFIRM_PREFIX)
    public static void confirmCard(ButtonInteractionEvent event, Game game, Player player) {
        PendingCard pendingCard = getPendingCard(game, player);
        if (pendingCard == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There is no pending Hydromorphic card.");
            return;
        }
        HydromorphicCardModel card = Mapper.getHydromorphicCard(pendingCard.cardId());
        if (pendingCard.discardedActionCards() < card.getActionCardDiscardMin()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Discard the required Action Cards first.");
            return;
        }
        Map<String, Integer> selectedActionCards = new java.util.LinkedHashMap<>();
        for (String actionCardId : pendingCard.discardedActionCardIds()) {
            Integer actionCardIdentifier = player.getActionCards().get(actionCardId);
            if (actionCardIdentifier == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "A selected Action Card is no longer in your hand.");
                return;
            }
            selectedActionCards.put(actionCardId, actionCardIdentifier);
        }
        String cardId = player.removeHydromorphicCard(pendingCard.identifier());
        if (!pendingCard.cardId().equals(cardId)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That Hydromorphic card is no longer in your hand.");
            return;
        }
        for (Integer actionCardIdentifier : selectedActionCards.values()) {
            game.discardActionCard(player.getUserID(), actionCardIdentifier);
        }
        clearPendingCard(game, player);
        game.returnHydromorphicCard(cardId);
        MorphaUnitHandler.recordCytoplasmicTransportDiscards(game, player, pendingCard.discardedActionCards());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " discarded the Hydromorphic card _" + card.getName() + "_.\n"
                        + card.getRepresentation());
        if (!selectedActionCards.isEmpty()) {
            MessageChannel channel = game.isFowMode() ? player.getPrivateChannel() : game.getMainGameChannel();
            String discardedCards = selectedActionCards.keySet().stream()
                    .map(actionCardId -> Mapper.getActionCard(actionCardId).getRepresentation(game))
                    .collect(java.util.stream.Collectors.joining("\n"));
            MessageHelper.sendMessageToChannel(
                    channel == null ? event.getMessageChannel() : channel,
                    player.getRepresentationNoPing() + " discarded the following "
                            + StringHelper.pluralize(selectedActionCards.size(), "Action Card")
                            + " as the cost of _"
                            + card.getName()
                            + "_:\n"
                            + discardedCards);
        }
        HydromorphicCardHandler.resolve(event, game, player, card, pendingCard.discardedActionCards());
        ButtonHelper.deleteMessage(event);
        ActionCardHelper.sendActionCardInfo(game, player);
        AgendaWhensAftersHelper.resumeHydromorphicWhen(event, game, player);
    }

    @ButtonHandler(CANCEL_PREFIX)
    public static void cancelCard(ButtonInteractionEvent event, Game game, Player player) {
        PendingCard pendingCard = getPendingCard(game, player);
        if (pendingCard == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There is no pending Hydromorphic card.");
            return;
        }
        clearPendingCard(game, player);
        ButtonHelper.deleteMessage(event);
        ActionCardHelper.sendActionCardInfo(game, player);
        AgendaWhensAftersHelper.resumeHydromorphicWhen(event, game, player);
    }

    private static void sendPendingDiscardPrompt(Game game, Player player) {
        PendingCard pendingCard = getPendingCard(game, player);
        if (pendingCard == null) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                pendingDiscardPromptText(player, pendingCard),
                pendingDiscardButtons(player, pendingCard));
    }

    private static void refreshPendingDiscardPrompt(ButtonInteractionEvent event, Game game, Player player) {
        PendingCard pendingCard = getPendingCard(game, player);
        if (pendingCard == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event, pendingDiscardPromptText(player, pendingCard), pendingDiscardButtons(player, pendingCard));
    }

    private static String pendingDiscardPromptText(Player player, PendingCard pendingCard) {
        HydromorphicCardModel card = Mapper.getHydromorphicCard(pendingCard.cardId());
        return player.getRepresentationUnfogged() + ", resolve " + card.getNameRepresentation() + ".\n"
                + "Action Cards selected: " + pendingCard.discardedActionCards() + discardRequirementText(card);
    }

    private static List<Button> pendingDiscardButtons(Player player, PendingCard pendingCard) {
        HydromorphicCardModel card = Mapper.getHydromorphicCard(pendingCard.cardId());
        List<Button> buttons = new ArrayList<>();
        boolean canDiscardMore = card.getActionCardDiscardMax() == null
                || pendingCard.discardedActionCards() < card.getActionCardDiscardMax();
        if (canDiscardMore) {
            player.getActionCards().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .filter(entry -> !pendingCard.discardedActionCardIds().contains(entry.getKey()))
                    .forEach(entry -> buttons.add(Buttons.blue(
                            DISCARD_PREFIX + entry.getValue(),
                            "(" + entry.getValue() + ") "
                                    + Mapper.getActionCard(entry.getKey()).getName())));
        }
        if (pendingCard.discardedActionCards() >= card.getActionCardDiscardMin()) {
            buttons.add(Buttons.green(CONFIRM_PREFIX, "Resolve " + card.getName()));
        }
        buttons.add(Buttons.red(CANCEL_PREFIX, "Cancel"));
        return buttons;
    }

    private static String discardRequirementText(HydromorphicCardModel card) {
        if (card.getActionCardDiscardMax() == null) {
            return " (minimum " + card.getActionCardDiscardMin() + ")";
        }
        if (card.getActionCardDiscardMin().equals(card.getActionCardDiscardMax())) {
            return " / " + card.getActionCardDiscardMin() + " required";
        }
        return " / " + card.getActionCardDiscardMin() + "-" + card.getActionCardDiscardMax() + " allowed";
    }

    private static PendingCard getPendingCard(Game game, Player player) {
        String value = game.getStoredValue(PENDING_CARD_KEY + player.getFaction());
        if (value.isBlank()) {
            return null;
        }
        String[] values = value.split("\\|", -1);
        if (values.length != 3 && values.length != 4) {
            clearPendingCard(game, player);
            return null;
        }
        try {
            List<String> discardedActionCardIds =
                    values.length == 4 && !values[3].isBlank() ? List.of(values[3].split(",")) : List.of();
            return new PendingCard(
                    values[0], Integer.parseInt(values[1]), Integer.parseInt(values[2]), discardedActionCardIds);
        } catch (NumberFormatException e) {
            clearPendingCard(game, player);
            return null;
        }
    }

    private static void setPendingCard(Game game, Player player, PendingCard pendingCard) {
        game.setStoredValue(
                PENDING_CARD_KEY + player.getFaction(),
                pendingCard.cardId()
                        + "|"
                        + pendingCard.identifier()
                        + "|"
                        + pendingCard.discardedActionCards()
                        + "|"
                        + String.join(",", pendingCard.discardedActionCardIds()));
    }

    private static void clearPendingCard(Game game, Player player) {
        game.setStoredValue(PENDING_CARD_KEY + player.getFaction(), "");
    }
}
