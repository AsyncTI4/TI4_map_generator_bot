package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ObjectiveHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

public class TfForetellResolver implements EdictResolver {
    @Getter
    public String edict = "tf-foretell";

    private static List<Button> buttons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (int loc = 1; loc <= game.getPublicObjectives1Peekable().size(); loc++) {
            String id = player.finChecker() + "foretellPeak_1_" + loc;
            String label = "Stage 1, Position " + loc;
            buttons.add(Buttons.green(id, label, CardEmojis.Public1alt));
        }
        for (int loc = 1; loc <= game.getPublicObjectives2Peekable().size(); loc++) {
            String id = player.finChecker() + "foretellPeak_2_" + loc;
            String label = "Stage 2, Position " + loc;
            buttons.add(Buttons.blue(id, label, CardEmojis.Public2alt));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("Done Peeking"));
        return buttons;
    }

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), playerPing(player), buttons(game, player));
    }

    @ButtonHandler("foretellPeak_")
    private static void foretellPeak(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String stage = buttonID.split("_")[1];
        int location = Integer.parseInt(buttonID.split("_")[2]);
        if ("1".equalsIgnoreCase(stage)) {
            ObjectiveHelper.secondHalfOfPeakStage1(game, player, location);
        } else {
            ObjectiveHelper.secondHalfOfPeakStage2(game, player, location);
        }

        String peaks = game.getStoredValue("foretellPeaks") + "x";
        game.setStoredValue("foretellPeaks", peaks);
        if (peaks.length() >= 3) {
            game.removeStoredValue("foretellPeaks");
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }
}
