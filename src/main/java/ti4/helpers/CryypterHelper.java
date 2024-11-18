package ti4.helpers;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.leaders.UnlockLeader;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class CryypterHelper {

    public static List<Button> getCryypterSC3Buttons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawCards = Buttons.gray("cryypterSC3Draw", "Draw Action Cards", Emojis.ActionCard);
        return List.of(drawCards, followButton, noFollowButton);
    }

    @ButtonHandler("cryypterSC3Draw")
    public static void resolveCryypterSC3Draw(ButtonInteractionEvent event, Game game, Player player) {
        drawXPickYActionCards(game, player, 3, 1, true);
    }

    private static void drawXPickYActionCards(Game game, Player player, int draw, int discard, boolean addScheming) {
        if (draw > 10) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " Drew " + draw + " AC";
        if (addScheming && player.hasAbility("scheming")) {
            message = "Drew [" + draw + "+1=" + ++draw + "] AC (Scheming)";
        }

        for (int i = 0; i < draw; i++) {
            game.drawActionCard(player.getUserID());
        }
        ActionCardHelper.sendActionCardInfo(game, player);

        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " use buttons to discard 1 of the " + draw + " cards just drawn.",
            ActionCardHelper.getDiscardActionCardButtons(player, false));

        ButtonHelper.checkACLimit(game, player);
        if (addScheming && player.hasAbility("scheming")) ActionCardHelper.sendDiscardActionCardButtons(player, false);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            CommanderUnlockCheck.checkPlayer(player, "yssaril");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }

    public static void checkEnvoyUnlocks(Game game) {
        if (!game.isVotcMode()) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            Leader envoy = player.getLeaderByType("envoy").orElse(null);
            if (envoy != null && envoy.isLocked()) {
                UnlockLeader.unlockLeader(envoy.getId(), game, player);
            }
        }
    }

}
