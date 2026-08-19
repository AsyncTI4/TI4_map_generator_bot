package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.turn.EndTurnService;

@UtilityClass
public class BorrowedTimeLLButtonHandler {
    private static final String RESOLVE = "resolveBorrowedTime";
    private static final String SPEND = "borrowedTimeSpend_";
    private static final String STATE = "borrowedTimeSkips_";

    @ButtonHandler(RESOLVE)
    public static void resolveBorrowedTime(ButtonInteractionEvent event, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(player.factionButtonChecker() + SPEND + "0", "Spend 0 Trade Goods"));
        if (player.getTg() >= 1) {
            buttons.add(Buttons.green(player.factionButtonChecker() + SPEND + "1", "Spend 1 Trade Good"));
        }
        if (player.getTg() >= 2) {
            buttons.add(Buttons.green(player.factionButtonChecker() + SPEND + "2", "Spend 2 Trade Goods"));
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose how many trade goods to spend for _Borrowed Time_. You will skip that many next turns.",
                buttons);
    }

    @ButtonHandler(SPEND)
    public static void spendBorrowedTime(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int amount;
        try {
            amount = Integer.parseInt(buttonID.substring(SPEND.length()));
        } catch (NumberFormatException e) {
            return;
        }
        if (amount < 0 || amount > 2 || player.getTg() < amount) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That trade-good payment is no longer available.");
            return;
        }
        if (amount > 0) {
            player.gainTG(-amount);
            int existing = Integer.parseInt(
                    game.getStoredValue(STATE + player.getFaction()).isEmpty()
                            ? "0"
                            : game.getStoredValue(STATE + player.getFaction()));
            game.setStoredValue(STATE + player.getFaction(), Integer.toString(existing + amount));
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " spent " + amount
                        + " trade good" + (amount == 1 ? "" : "s") + " for _Borrowed Time_ and will skip " + amount
                        + " upcoming turn" + (amount == 1 ? "" : "s") + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean skipTurnIfNecessary(GenericInteractionCreateEvent event, Game game, Player player) {
        String key = STATE + player.getFaction();
        int remaining;
        try {
            remaining = Integer.parseInt(game.getStoredValue(key));
        } catch (NumberFormatException e) {
            return false;
        }
        if (remaining < 1) return false;
        game.setStoredValue(key, Integer.toString(remaining - 1));
        game.updateActivePlayer(player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " skipped this turn with _Borrowed Time_. " + (remaining - 1) + " skipped turn"
                        + (remaining == 2 ? " remains." : " remain."));
        EndTurnService.pingNextPlayer(event, game, player);
        return true;
    }
}
