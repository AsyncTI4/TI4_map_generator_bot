package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class SyndicateAcd2ButtonHandler {

    private static final String SYNDICATE_CARDS_KEY = "syndicateRevealedCards";
    private static final String SYNDICATE_PLAYERS_KEY = "syndicateRemainingPlayers";

    @ButtonHandler("resolveSyndicate")
    public static void resolveSyndicate(Player player, Game game, ButtonInteractionEvent event) {
        List<Player> realPlayers = game.getRealPlayers();
        int playerCount = realPlayers.size();

        List<String> revealedCards = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            String cardId = drawSyndicateCard(game, player);
            if (cardId == null) break; // deck and discard pile are both empty
            revealedCards.add(cardId);
        }

        if (revealedCards.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + ", the action card deck is empty; _Syndicate_ cannot be resolved.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> playerFactions =
                realPlayers.stream().map(Player::getFaction).toList();
        game.setStoredValue(SYNDICATE_CARDS_KEY, String.join(",", revealedCards));
        game.setStoredValue(SYNDICATE_PLAYERS_KEY, String.join(",", playerFactions));

        List<MessageEmbed> embeds = revealedCards.stream()
                .map(id -> Mapper.getActionCard(id).getRepresentationEmbed(false, true, game))
                .toList();
        MessageHelper.sendMessageToChannelWithEmbeds(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " revealed " + revealedCards.size()
                        + " action cards for _Syndicate_:",
                embeds);

        ButtonHelper.deleteMessage(event);
        sendSyndicateAssignButtons(player, game, revealedCards, playerFactions.getFirst());
    }

    @ButtonHandler("syndicateAssign_")
    public static void resolveSyndicateAssign(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("syndicateAssign_", "");
        int separator = payload.indexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Syndicate_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String targetFaction = payload.substring(0, separator);
        String cardId = payload.substring(separator + 1);

        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find the target player for _Syndicate_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> revealedCards = getSyndicateStoredCards(game);
        if (!revealedCards.remove(cardId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "That card is no longer available for _Syndicate_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> remainingPlayers = getSyndicateStoredPlayers(game);
        remainingPlayers.remove(targetFaction);

        target.setActionCard(cardId);
        ActionCardHelper.sendActionCardInfo(game, target);
        ButtonHelper.checkACLimit(game, target);

        String targetDisplay = game.isFowMode() ? "another player" : target.getFactionEmojiOrColor();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " gave an action card to " + targetDisplay + " via _Syndicate_.");

        game.setStoredValue(SYNDICATE_CARDS_KEY, String.join(",", revealedCards));
        game.setStoredValue(SYNDICATE_PLAYERS_KEY, String.join(",", remainingPlayers));

        ButtonHelper.deleteMessage(event);

        if (remainingPlayers.isEmpty()) {
            game.setStoredValue(SYNDICATE_CARDS_KEY, "");
            game.setStoredValue(SYNDICATE_PLAYERS_KEY, "");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "_Syndicate_ has been fully resolved.");
        } else {
            sendSyndicateAssignButtons(player, game, revealedCards, remainingPlayers.getFirst());
        }
    }

    private static void sendSyndicateAssignButtons(
            Player player, Game game, List<String> revealedCards, String targetFaction) {
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) return;

        List<Button> buttons = revealedCards.stream()
                .map(cardId -> Buttons.green(
                        player.factionButtonChecker() + "syndicateAssign_" + targetFaction + "_" + cardId,
                        Mapper.getActionCard(cardId).getName()))
                .toList();

        String targetDisplay = game.isFowMode() ? target.getColor() : target.getFactionEmojiOrColor();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which action card to give to " + targetDisplay
                        + " for _Syndicate_.",
                buttons);
    }

    // Draws the top action card into the resolving player's hand (reshuffling the discard pile if the deck is empty),
    // then pulls it back out so it can be held aside for redistribution. Returns the drawn card ID, or null if the
    // deck and discard pile are both empty.
    private static String drawSyndicateCard(Game game, Player player) {
        Set<String> before = new HashSet<>(player.getActionCards().keySet());
        game.drawActionCard(player.getUserID());
        Map<String, Integer> after = player.getActionCards();
        String drawn = after.keySet().stream()
                .filter(id -> !before.contains(id))
                .findFirst()
                .orElse(null);
        if (drawn != null) {
            player.removeActionCard(after.get(drawn));
        }
        return drawn;
    }

    private static List<String> getSyndicateStoredCards(Game game) {
        String stored = game.getStoredValue(SYNDICATE_CARDS_KEY);
        if (stored.isBlank()) return new ArrayList<>();
        return new ArrayList<>(List.of(stored.split(",")));
    }

    private static List<String> getSyndicateStoredPlayers(Game game) {
        String stored = game.getStoredValue(SYNDICATE_PLAYERS_KEY);
        if (stored.isBlank()) return new ArrayList<>();
        return new ArrayList<>(List.of(stored.split(",")));
    }
}
