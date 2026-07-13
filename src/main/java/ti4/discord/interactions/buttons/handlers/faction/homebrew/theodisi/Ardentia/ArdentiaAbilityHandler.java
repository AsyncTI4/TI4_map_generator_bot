package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ardentia;

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
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ArdentiaAbilityHandler {
    private static final String BORROWED_AUTHORITY = "borrowed_authority";
    private static final String SEIZE_COMMAND = "seize_command";
    private static final String SEIZE_COMMAND_POOL = "Seize Command";
    private static final String USE_SEIZE = "ardentiaSeizeCommand";
    private static final String ADD_SEIZE_TOKEN = "ardentiaSeizeAddToken_";
    private static final String USE_BORROWED_AUTHORITY = "ardentiaBorrowedAuthority";
    private static final String BA_SELECT_COLOR = "ardentiaBorrowedAuthorityColor_";

    // Seize Command
    public static Button getSeizeCommandButton(Player player) {
        if (player == null || !player.hasAbility(SEIZE_COMMAND)) {
            return null;
        }

        return Buttons.green(player.factionButtonChecker() + USE_SEIZE, "Use Seize Command", FactionEmojis.ardentia);
    }

    @ButtonHandler(USE_SEIZE)
    public static void useSeizeCommand(ButtonInteractionEvent event, Player player, Game game) {
        if (player == null || !player.hasAbility(SEIZE_COMMAND)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player otherPlayer : game.getRealPlayers()) {
            if (player.getDebtTokenCount(otherPlayer.getColor(), SEIZE_COMMAND_POOL) >= 1) {
                continue;
            }

            if (otherPlayer != player) {
                buttons.add(Buttons.green(player.factionButtonChecker() + ADD_SEIZE_TOKEN + otherPlayer.getColor(), otherPlayer.getFactionNameOrColor(), otherPlayer.getFactionEmojiOrColor()));
            }
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "There are no players to use _Seize Command_ on.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
            player.getCardsInfoThread(),
            player.getRepresentation()
                + ", you may use the buttons below to use _Seize Command_. This will add that player's control token to your **Seize Command** debt pool.\n\n**REMINDER**: This is _INSTEAD OF_ gaining 1 of your purchased command tokens.",
            buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(ADD_SEIZE_TOKEN)
    public static void resolveAddSeizeToken(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null || !player.hasAbility(SEIZE_COMMAND)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.replace(ADD_SEIZE_TOKEN, "");
        Player otherPlayer = game.getPlayerFromColorOrFaction(payload);

        if (otherPlayer == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find player.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addDebtTokens(otherPlayer.getColor(), 1, SEIZE_COMMAND_POOL);

        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
            game.getActionsChannel(),
            player.getRepresentation()
                + " has used _Seize Command_ to add a control token from "
                + otherPlayer.getFactionNameOrColor()
                + " to their **Seize Command** debt pool.");
    }

    // Borrowed Authority
    public static boolean canUseBorrowedAuthority(Player player, Game game) {
        if (player == null || game == null || !player.hasAbility(BORROWED_AUTHORITY)) {
            return false;
        }
        return !getBorrowedAuthorityTargets(player, game).isEmpty();
    }

    @ButtonHandler(USE_BORROWED_AUTHORITY)
    public static void startBorrowedAuthority(ButtonInteractionEvent event, Player player, Game game) {
        if (!canUseBorrowedAuthority(player, game)) {
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player otherPlayer : getBorrowedAuthorityTargets(player, game)) {
            if (getBorrowedAuthorityValidTiles(player, game, otherPlayer.getColor()).isEmpty()) {
                continue;
            }
            buttons.add(Buttons.green(
                player.factionButtonChecker() + BA_SELECT_COLOR + otherPlayer.getColor(),
                otherPlayer.getFactionNameOrColor(),
                otherPlayer.getFactionEmojiOrColor()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + ", there are no valid systems for _Borrowed Authority_.");
            return;
        }
        buttons.add(Buttons.red("finishComponentAction", "Cancel"));

        MessageHelper.sendMessageToChannelWithButtons(
            event.getMessageChannel(),
            player.getRepresentation()
                + ", choose whose command token you are returning from your **Seize Command** debt pool.",
            buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(BA_SELECT_COLOR)
    public static void resolveBorrowedAuthorityColor(
        ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String color = buttonID.replace(BA_SELECT_COLOR, "");
        Player otherPlayer = game.getPlayerFromColorOrFaction(color);

        if (otherPlayer == null || player.getDebtTokenCount(otherPlayer.getColor(), SEIZE_COMMAND_POOL) < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find a valid borrowed command token.");
            return;
        }

        if (getBorrowedAuthorityValidTiles(player, game, otherPlayer.getColor()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + ", there are no valid systems for _Borrowed Authority_.");
            return;
        }

        ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        game.removeStoredValue("fortuneSeekers");
        game.setComponentAction(true);
        game.setStoredValue("borrowedAuthorityColor", otherPlayer.getColor());
        ButtonHelperTacticalAction.beginTacticalAction(game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Player> getBorrowedAuthorityTargets(Player player, Game game) {
        List<Player> targets = new ArrayList<>();
        for (Player otherPlayer : game.getRealPlayers()) {
            if (otherPlayer != player && player.getDebtTokenCount(otherPlayer.getColor(), SEIZE_COMMAND_POOL) > 0) {
                targets.add(otherPlayer);
            }
        }
        return targets;
    }

    private static List<Tile> getBorrowedAuthorityValidTiles(Player player, Game game, String borrowedColor) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (isValidBorrowedAuthorityTile(player, game, tile, borrowedColor)) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private static boolean isValidBorrowedAuthorityTile(Player player, Game game, Tile tile, String borrowedColor) {
        if (!ButtonHelper.canActivateTile(game, player, tile)
            || tile.isHomeSystem(game)
            || tile.isMecatol(game)
            || FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
            return false;
        }
        String borrowedCc = tile.getCCPath(ti4.image.Mapper.getCCID(borrowedColor));
        return borrowedCc != null && !tile.hasCC(ti4.image.Mapper.getCCID(borrowedColor));
    }

}
