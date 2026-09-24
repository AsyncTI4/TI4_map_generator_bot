package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.FoWHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
class ConcordAcd2ButtonHandler {

    private static final String SEND_PN_PREFIX = "concordSendPN_";
    private static final String GIVE_PREFIX = "concordGive_";
    private static final String ACTION_CARD = "ac";
    private static final String PROMISSORY_NOTE = "pn";
    private static final String SECRET_OBJECTIVE = "so";

    @ButtonHandler("resolveConcord")
    public static void resolveConcord(Player player, Game game, ButtonInteractionEvent event) {
        List<Player> playersOwingPromissoryNotes = ButtonHelper.sendForcedPNSendButtonsToOtherPlayers(
                game,
                player,
                "_Concord_ has been played and you must give "
                        + FoWHelper.factionEmojiOrAnon(game, player, "another player")
                        + " 1 promissory note from your hand. Please choose the promissory note you wish to send.",
                SEND_PN_PREFIX + player.getFaction() + "_");
        ButtonHelper.deleteMessage(event);

        if (playersOwingPromissoryNotes.isEmpty()) {
            sendAllGiveButtons(game, player);
            return;
        }
        game.setStoredValue(
                pendingSendersKey(player),
                String.join(
                        ",",
                        playersOwingPromissoryNotes.stream()
                                .map(Player::getFaction)
                                .toList()));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is resolving _Concord_. Each other player must give them 1 promissory note from their hand;"
                        + " buttons have been sent to each player's `#cards-info` thread.");
    }

    @ButtonHandler(SEND_PN_PREFIX)
    public static void concordSendPromissoryNote(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace(SEND_PN_PREFIX, "");
        int separator = payload.indexOf('_');
        Player concordPlayer = game.getPlayerFromColorOrFaction(payload.substring(0, Math.max(separator, 0)));
        String naaluSendId = "naaluHeroSend_" + payload.substring(separator + 1);
        if (concordPlayer == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not resolve _Concord_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int promissoryNotesBefore = player.getPromissoryNotes().size();
        ButtonHelperHeroes.resolveNaaluHeroSend(player, game, naaluSendId, event);
        if (player.getPromissoryNotes().size() < promissoryNotesBefore) {
            markPromissoryNoteSent(game, concordPlayer, player);
        }
    }

    private static void markPromissoryNoteSent(Game game, Player concordPlayer, Player sender) {
        String pendingSenders = game.getStoredValue(pendingSendersKey(concordPlayer));
        if (pendingSenders.isEmpty()) {
            return;
        }
        List<String> stillPending = new ArrayList<>(List.of(pendingSenders.split(",")));
        stillPending.remove(sender.getFaction());
        if (!stillPending.isEmpty()) {
            game.setStoredValue(pendingSendersKey(concordPlayer), String.join(",", stillPending));
            return;
        }
        game.removeStoredValue(pendingSendersKey(concordPlayer));
        sendAllGiveButtons(game, concordPlayer);
    }

    private static String pendingSendersKey(Player concordPlayer) {
        return "concordPendingSenders_" + concordPlayer.getFaction();
    }

    private static void sendAllGiveButtons(Game game, Player concordPlayer) {
        MessageHelper.sendMessageToChannel(
                concordPlayer.getCorrectChannel(),
                concordPlayer.getRepresentation()
                        + " has received a promissory note from each other player who could give one for _Concord_,"
                        + " and must now give each other player 1 card.");
        for (Player receiver : game.getRealPlayers()) {
            if (receiver != concordPlayer) {
                sendGiveButtons(game, concordPlayer, receiver);
            }
        }
    }

    private static void sendGiveButtons(Game game, Player giver, Player receiver) {
        List<Button> buttons = getGiveButtons(game, giver, receiver);
        String receiverName = receiver.getColorIfCanSeeStats(giver);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    giver.getCardsInfoThread(),
                    giver.getRepresentationUnfogged() + ", you have no cards to give to " + receiverName
                            + " for _Concord_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                giver.getCardsInfoThread(),
                giver.getRepresentationUnfogged() + ", choose 1 card from your hand or 1 promissory note from your"
                        + " play area to give to " + receiverName + " for _Concord_.",
                buttons);
    }

    private static List<Button> getGiveButtons(Game game, Player giver, Player receiver) {
        String idPrefix = giver.factionButtonChecker() + GIVE_PREFIX + receiver.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        for (Map.Entry<String, Integer> actionCard : giver.getActionCards().entrySet()) {
            ActionCardModel model = Mapper.getActionCard(actionCard.getKey());
            if (model != null) {
                buttons.add(Buttons.green(
                        idPrefix + ACTION_CARD + "_" + actionCard.getValue(),
                        model.getName(),
                        CardEmojis.getACEmoji(giver)));
            }
        }

        for (Map.Entry<String, Integer> promissoryNote :
                giver.getPromissoryNotes().entrySet()) {
            Button button = getPromissoryNoteButton(game, giver, receiver, idPrefix, promissoryNote);
            if (button != null) {
                buttons.add(button);
            }
        }

        for (Map.Entry<String, Integer> secret : giver.getSecretsUnscored().entrySet()) {
            SecretObjectiveModel model = Mapper.getSecretObjective(secret.getKey());
            if (model != null) {
                buttons.add(Buttons.gray(
                        idPrefix + SECRET_OBJECTIVE + "_" + secret.getValue(),
                        model.getName(),
                        CardEmojis.SecretObjective));
            }
        }
        return buttons;
    }

    private static Button getPromissoryNoteButton(
            Game game, Player giver, Player receiver, String idPrefix, Map.Entry<String, Integer> promissoryNote) {
        String pnId = promissoryNote.getKey();
        PromissoryNoteModel model = Mapper.getPromissoryNote(pnId);
        Player owner = game.getPNOwner(pnId);
        if (model == null || owner == null || (receiver.hasAbility("hubris") && pnId.endsWith("_an"))) {
            return null;
        }

        String location = giver.getPromissoryNotesInPlayArea().contains(pnId) ? " (Play Area)" : "";
        String buttonId = idPrefix + PROMISSORY_NOTE + "_" + promissoryNote.getValue();
        if (game.isFowMode()) {
            return Buttons.blue(
                    buttonId, PromissoryNoteHelper.ownerColorPrefix(owner, pnId) + model.getName() + location);
        }
        return Buttons.blue(buttonId, model.getName() + location, owner.getFactionEmoji());
    }

    @ButtonHandler(GIVE_PREFIX)
    public static void concordGive(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace(GIVE_PREFIX, "");
        int identifierSeparator = payload.lastIndexOf('_');
        int typeSeparator = payload.lastIndexOf('_', identifierSeparator - 1);
        if (typeSeparator < 0) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not resolve _Concord_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player receiver = game.getPlayerFromColorOrFaction(payload.substring(0, typeSeparator));
        String cardType = payload.substring(typeSeparator + 1, identifierSeparator);
        int identifier = Integer.parseInt(payload.substring(identifierSeparator + 1));
        if (receiver == null || receiver == player) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not resolve _Concord_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        switch (cardType) {
            case ACTION_CARD -> giveActionCard(game, player, receiver, identifier, event);
            case PROMISSORY_NOTE ->
                ButtonHelperHeroes.resolveNaaluHeroSend(
                        player, game, "naaluHeroSend_" + receiver.getFaction() + "_" + identifier, event);
            case SECRET_OBJECTIVE -> giveSecretObjective(game, player, receiver, identifier, event);
            default -> {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not resolve _Concord_.");
                ButtonHelper.deleteMessage(event);
            }
        }
    }

    private static void giveActionCard(
            Game game, Player giver, Player receiver, int identifier, ButtonInteractionEvent event) {
        String actionCardId = findCardId(giver.getActionCards(), identifier);
        if (actionCardId == null) {
            reportCardUnavailable(giver);
            return;
        }

        giver.removeActionCard(identifier);
        receiver.setActionCard(actionCardId);
        ActionCardHelper.sendActionCardInfo(game, giver);
        ActionCardHelper.sendActionCardInfo(game, receiver);
        ButtonHelper.checkACLimit(game, receiver);

        String cardName = Mapper.getActionCard(actionCardId).getName();
        announceGift(game, giver, receiver, "an action card", cardName);
        ButtonHelper.deleteMessage(event);
    }

    private static void giveSecretObjective(
            Game game, Player giver, Player receiver, int identifier, ButtonInteractionEvent event) {
        String secretId = findCardId(giver.getSecretsUnscored(), identifier);
        if (secretId == null) {
            reportCardUnavailable(giver);
            return;
        }

        giver.removeSecret(identifier);
        receiver.setSecret(secretId);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, giver);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, receiver);
        game.checkSOLimit(receiver);

        String cardName = Mapper.getSecretObjective(secretId).getName();
        announceGift(game, giver, receiver, "a secret objective", cardName);
        ButtonHelper.deleteMessage(event);
    }

    private static void announceGift(Game game, Player giver, Player receiver, String cardKind, String cardName) {
        MessageHelper.sendMessageToChannel(
                giver.getCorrectChannel(),
                giver.getRepresentationNoPing() + " gave " + cardKind + " to "
                        + FoWHelper.actorOrAnon(game, receiver, "another player") + " via _Concord_.");
        MessageHelper.sendMessageToChannel(
                giver.getCardsInfoThread(),
                giver.getRepresentationUnfogged() + ", you gave _" + cardName + "_ to "
                        + receiver.getColorIfCanSeeStats(giver) + " via _Concord_.");
        MessageHelper.sendMessageToChannel(
                receiver.getCardsInfoThread(),
                receiver.getRepresentationUnfogged() + ", you received _" + cardName + "_ from "
                        + giver.getColorIfCanSeeStats(receiver) + " via _Concord_.");
    }

    private static void reportCardUnavailable(Player giver) {
        MessageHelper.sendMessageToChannel(
                giver.getCardsInfoThread(), "That card is no longer in your hand, so it could not be given.");
    }

    private static String findCardId(Map<String, Integer> cards, int identifier) {
        for (Map.Entry<String, Integer> card : cards.entrySet()) {
            if (card.getValue() == identifier) {
                return card.getKey();
            }
        }
        return null;
    }
}
