package ti4.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class PrePlayService {
    private static final String PREFIX = "PrePlay_";

    private enum Timing {
        NEVER,
        STRAT_PHASE_START,
        STRAT_PHASE_END; // Telepathic, ...

        static Timing getDefault(String card) {
            card = card.toLowerCase();
            if (card.contains("telepathic")) {
                return STRAT_PHASE_END;
            }
            return NEVER;
        }
    }

    private static String getStoredValue(Game game, Timing timing) {
        return game.getStoredValue(PREFIX + timing);
    }

    private static void setStoredValue(Game game, Timing timing, String value) {
        game.setStoredValue(PREFIX + timing, value);
    }

    private static Stream<String> getAssignments(Game game, Timing timing) {
        return Arrays.stream(getStoredValue(game, timing).split("_"));
    }

    private static boolean isAssigned(Game game, Timing timing, String card) {
        return getAssignments(game, timing).anyMatch(card::equals);
    }

    public static boolean isAssigned(Game game, String card) {
        return isAssigned(game, Timing.getDefault(card), card);
    }

    private static void assign(Game game, Timing timing, String card) {
        setStoredValue(game, timing, getStoredValue(game, timing) + card + "_");
    }

    private static void assign(Game game, String card) {
        assign(game, Timing.getDefault(card), card);
    }

    private static void unassign(Game game, Timing timing, String card) {
        setStoredValue(game, timing, getStoredValue(game, timing).replace(card + "_", ""));
    }

    public static void unassign(Game game, String card) {
        unassign(game, Timing.getDefault(card), card);
    }

    public static void sendPrePlayButtons(Player player, String card, String msg, String buttonLabel) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(PREFIX + card, buttonLabel));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler(PREFIX)
    public static void prePlayButton(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String card = buttonID.split("_")[1];
        assign(game, card);
        ButtonHelper.deleteMessage(event);
    }
}
