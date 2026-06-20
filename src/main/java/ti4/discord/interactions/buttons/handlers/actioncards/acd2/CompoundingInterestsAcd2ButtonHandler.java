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
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.CommandCounterHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
class CompoundingInterestsAcd2ButtonHandler {

    @ButtonHandler("resolveCompoundingInterests")
    public static void resolveCompoundingInterests(Player player, Game game, ButtonInteractionEvent event) {
        int tokenCount = (int) game.getTileMap().values().stream()
                .filter(tile -> CommandCounterHelper.hasCC(player, tile))
                .count();
        ButtonHelper.deleteMessage(event);
        if (tokenCount == 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no command tokens on the board; _Compounding Interests_ has no effect.");
            return;
        }
        sendChoiceButtons(player, tokenCount);
    }

    @ButtonHandler("compoundingInterestsStep2_")
    public static void resolveCompoundingInterestsStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("compoundingInterestsStep2_", "");
        int lastUnderscore = payload.lastIndexOf('_');
        if (lastUnderscore < 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String choice = payload.substring(0, lastUnderscore);
        int remaining;
        try {
            remaining = Integer.parseInt(payload.substring(lastUnderscore + 1));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if ("gain".equals(choice)) {
            ButtonHelperStats.gainComms(event, game, player, 1, true);
        } else {
            ButtonHelperStats.convertComms(event, game, player, 1, true);
        }

        if (remaining > 1) {
            sendChoiceButtons(player, remaining - 1);
        }
    }

    private static void sendChoiceButtons(Player player, int remaining) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("compoundingInterestsStep2_gain_" + remaining, "Gain 1 Commodity", MiscEmojis.comm));
        buttons.add(Buttons.blue(
                "compoundingInterestsStep2_convert_" + remaining, "Convert 1 Commodity to TG", MiscEmojis.tg));
        buttons.add(Buttons.red("deleteButtons", "Done"));
        String remainingText = remaining == 1 ? "1 choice remaining" : remaining + " choices remaining";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose an option for _Compounding Interests_ (" + remainingText
                        + ").",
                buttons);
    }
}
