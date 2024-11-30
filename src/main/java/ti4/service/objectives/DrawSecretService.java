package ti4.service.objectives;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
public class DrawSecretService {

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player) {
        drawSO(event, game, player, 1, true);
    }

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player, int count, boolean useTnelis) {
        String output = "Drew " + count + " Secret Objective" + (count > 1 ? "s" : "");
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            output = "Used Plausible Deniablity to draw [" + count + " + 1 = " + (count + 1) + "] Secret Objectives";
            count++;
        }
        for (int i = 0; i < count; i++) {
            game.drawSecretObjective(player.getUserID());
        }
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " " + output);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            SecretObjectiveHelper.sendSODiscardButtons(player);
        }
    }

    public static void dealSOToAll(GenericInteractionCreateEvent event, int count, Game game) {
        if (count > 0) {
            for (Player player : game.getRealPlayers()) {
                for (int i = 0; i < count; i++) {
                    game.drawSecretObjective(player.getUserID());
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " due to Plausible Deniability, you were dealt an extra SO. You must also discard an extra SO.");
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), count + Emojis.SecretObjective + " dealt to all players. Check your Cards-Info threads.");
        if (game.getRound() == 1) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startOfGameObjReveal", "Reveal Objectives and Start Strategy Phase"));
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "Press this button after everyone has discarded", buttons);
            Player speaker = null;
            if (game.getPlayer(game.getSpeakerUserID()) != null) {
                speaker = game.getPlayers().get(game.getSpeakerUserID());
            }
            if (speaker == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Speaker is not yet assigned. Secrets have been dealt, but please assign speaker soon (command is `/player speaker`)");
            }
            Helper.setOrder(game);
        }
    }
}
