package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.function.BiPredicate;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.TI4Emoji;
import ti4.service.game.MonumentsService;

record MonumentCommandTokenPool(
        String monumentId,
        String storageKey,
        BiPredicate<Game, Player> ownsMonument,
        String name,
        String formattedName,
        String checkButtonId,
        TI4Emoji emoji) {

    int count(Game game, Player player) {
        if (game == null || player == null || !game.isMonumentsMode() || !ownsMonument.test(game, player)) {
            return 0;
        }
        String storedCount = game.getStoredValue(storageKey + player.getFaction());
        return storedCount.isBlank() ? 0 : Integer.parseInt(storedCount);
    }

    Button statusButton(Game game, Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + checkButtonId,
                name + ": " + StringHelper.pluralize(count(game, player), "Command Token"),
                emoji);
    }

    boolean canPlace(Game game, Player player) {
        return game.isMonumentsMode()
                && ownsMonument.test(game, player)
                && Helper.getCCCount(game, player.getColor()) < player.getCommandTokenLimit();
    }

    boolean canSpendIn(Game game, Player player, Tile tile) {
        return count(game, player) > 0
                && MonumentsService.isInOrAdjacentToMonumentSystem(game, player, monumentId, tile);
    }

    void spend(Game game, Player player) {
        int remainingTokens = count(game, player) - 1;
        if (remainingTokens == 0) {
            game.removeStoredValue(storageKey + player.getFaction());
        } else {
            game.setStoredValue(storageKey + player.getFaction(), Integer.toString(remainingTokens));
        }
    }

    void place(ButtonInteractionEvent event, Game game, Player player) {
        if (!canPlace(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, name + " cannot take a command token right now.");
            return;
        }
        int tokens = count(game, player) + 1;
        game.setStoredValue(storageKey + player.getFaction(), Integer.toString(tokens));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a command token on " + formattedName + " (" + tokens
                        + ").");
    }

    void check(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendEphemeralMessageToEventChannel(
                event,
                formattedName + " has " + StringHelper.pluralize(count(game, player), "command token") + " on it.");
    }
}
