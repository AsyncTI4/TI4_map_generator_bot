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
import ti4.message.MessageHelper;

@UtilityClass
class ArtifactResearchAcd2ButtonHandler {

    @ButtonHandler("resolveArtifactResearch")
    public static void resolveArtifactResearch(Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        if (player.getFragments().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has no relic fragments to discard for _Artifact Research_.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String fragId : player.getFragments()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "artifactResearchDiscard_" + fragId,
                    "Discard " + HeistAcd2ButtonHandler.fragmentLabel(fragId),
                    HeistAcd2ButtonHandler.fragmentEmoji(fragId)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the relic fragment to discard for _Artifact Research_."
                        + " You will ignore 1 of the researched technology's prerequisites and gain 3 trade goods.",
                buttons);
    }

    @ButtonHandler("artifactResearchDiscard_")
    public static void artifactResearchDiscard(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String fragId = buttonID.replace("artifactResearchDiscard_", "");
        if (!player.getFragments().contains(fragId)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Artifact Research_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        player.removeFragment(fragId);
        game.discardExplore(fragId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " discarded a " + HeistAcd2ButtonHandler.fragmentEmoji(fragId)
                        + HeistAcd2ButtonHandler.fragmentLabel(fragId)
                        + " for _Artifact Research_, ignoring 1 of that technology's prerequisites.");
        ButtonHelperStats.gainTGs(event, game, player, 3, false);
    }
}
