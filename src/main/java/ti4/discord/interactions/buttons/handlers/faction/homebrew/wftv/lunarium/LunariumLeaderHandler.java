package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.lunarium;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.objectives.DrawSecretService;

@UtilityClass
public class LunariumLeaderHandler {

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " draws 1 secret objective due to " + FactionEmojis.lunarium
                        + " **Lunarium Commander**.");
        DrawSecretService.drawSO(event, game, player);
    }

    public static boolean isHeroActive(Player player) {
        return player.getLeader("lunariumhero").map(Leader::isActive).orElse(false);
    }

    public static void offerDoneScoringSecretsButton(Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may score another secret objective this status phase via "
                        + FactionEmojis.lunarium
                        + " **Anima Scrutator**. Press the button below once you are done scoring secrets so the"
                        + " game can move on to the next player.",
                List.of(Buttons.green(
                        player.factionButtonChecker() + "lunariumHeroDoneScoring", "Done Scoring Secrets")));
    }

    @ButtonHandler("lunariumHeroDoneScoring")
    public static void lunariumHeroDoneScoring(ButtonInteractionEvent event, Game game, Player player) {
        String key3 = "potentialScoreSOBlockers";
        String key3b = "potentialScorePOBlockers";
        game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
        if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
            Helper.resolvePOScoringQueue(game, event);
        }
        ButtonHelper.deleteMessage(event);
    }
}
