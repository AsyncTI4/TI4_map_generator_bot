package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class HijackedSignalAcd2ButtonHandler {

    private static final String CARD = "_Hijacked Signal_";

    private record Token(Player owner, Tile tile) {}

    @ButtonHandler("resolveHijackedSignal")
    public static void resolveHijackedSignal(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Token token : swappableTokens(game)) {
            if (hasSwapPartner(game, token)) {
                buttons.add(tokenButton(player, "hijackedSignalFirst_", token, game));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there are no 2 command tokens that can be swapped for "
                            + CARD + ".");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the first command token to swap for " + CARD + ".",
                buttons);
    }

    @ButtonHandler("hijackedSignalFirst_")
    public static void chooseFirst(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Token first = parseToken(game, buttonID.replace("hijackedSignalFirst_", ""));
        ButtonHelper.deleteMessage(event);
        if (first == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Token second : swappableTokens(game)) {
            if (canSwap(first, second)) {
                buttons.add(tokenButton(player, "hijackedSignalSecond_" + encode(first) + "_", second, game));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", that command token has nothing to swap with for " + CARD
                            + ".");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the command token to swap with the one in "
                        + first.tile().getRepresentationForButtons(game, player) + " for " + CARD + ".",
                buttons);
    }

    @ButtonHandler("hijackedSignalSecond_")
    public static void chooseSecond(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("hijackedSignalSecond_", "").split("_", 3);
        ButtonHelper.deleteMessage(event);
        Token first = parts.length < 3 ? null : parseToken(game, parts[0] + "_" + parts[1]);
        Token second = parts.length < 3 ? null : parseToken(game, parts[2]);
        if (first == null || second == null || !canSwap(first, second)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve " + CARD + ".");
            return;
        }
        swap(game, first, second);
        announceSwap(player, game, first, second);
    }

    private static void swap(Game game, Token first, Token second) {
        moveToken(game, first.owner(), first.tile(), second.tile());
        moveToken(game, second.owner(), second.tile(), first.tile());
    }

    private static void moveToken(Game game, Player owner, Tile from, Tile to) {
        String ccID = Mapper.getCCID(owner.getColor());
        from.removeCC(ccID);
        to.addCC(ccID);
        if (game.isFowMode()) {
            FoWHelper.pingSystem(game, from.getPosition(), "A command token has been removed from the system.");
            FoWHelper.pingSystem(game, to.getPosition(), "A command token has been placed in the system.");
        }
    }

    private static void announceSwap(Player player, Game game, Token first, Token second) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " swapped " + first.owner().getRepresentationNoPing()
                        + "'s command token in " + first.tile().getRepresentationForButtons(game, player) + " with "
                        + second.owner().getRepresentationNoPing() + "'s command token in "
                        + second.tile().getRepresentationForButtons(game, player) + " using " + CARD + ".");
        if (!game.isFowMode()) {
            return;
        }
        notifyAffected(game, player, first, second);
        notifyAffected(game, player, second, first);
    }

    private static void notifyAffected(Game game, Player actor, Token affected, Token other) {
        if (affected.owner() == actor) {
            return;
        }
        MessageHelper.sendMessageToChannel(
                affected.owner().getCorrectChannel(),
                affected.owner().getRepresentationUnfogged() + ", your command token in "
                        + affected.tile().getRepresentationForButtons(game, affected.owner())
                        + " was swapped with another player's command token from "
                        + other.tile().getRepresentationForButtons(game, affected.owner()) + " by " + CARD + ".");
    }

    private static List<Token> swappableTokens(Game game) {
        List<Token> tokens = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile == null || tile.isHomeSystem(game)) {
                continue;
            }
            for (Player owner : game.getRealPlayers()) {
                if (CommandCounterHelper.hasCC(owner, tile)) {
                    tokens.add(new Token(owner, tile));
                }
            }
        }
        return tokens;
    }

    private static boolean hasSwapPartner(Game game, Token token) {
        return swappableTokens(game).stream().anyMatch(other -> canSwap(token, other));
    }

    private static boolean canSwap(Token first, Token second) {
        return first.owner() != second.owner()
                && first.tile() != second.tile()
                && !CommandCounterHelper.hasCC(first.owner(), second.tile())
                && !CommandCounterHelper.hasCC(second.owner(), first.tile());
    }

    private static Button tokenButton(Player viewer, String idPrefix, Token token, Game game) {
        return Buttons.gray(
                viewer.factionButtonChecker() + idPrefix + encode(token),
                token.tile().getRepresentationForButtons(game, viewer),
                token.owner().fogSafeEmoji());
    }

    private static String encode(Token token) {
        return token.owner().getColor() + "_" + token.tile().getPosition();
    }

    private static Token parseToken(Game game, String encoded) {
        String[] parts = encoded.split("_", 2);
        if (parts.length < 2) {
            return null;
        }
        Player owner = game.getPlayerFromColorOrFaction(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        if (owner == null || tile == null || !CommandCounterHelper.hasCC(owner, tile)) {
            return null;
        }
        return new Token(owner, tile);
    }
}
