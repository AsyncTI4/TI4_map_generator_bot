package ti4.service.leader;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.service.franken.FrankenAlternateTextService;

@UtilityClass
public class PurgeHeroService {

    public static void purgeHeroPreamble(
            ButtonInteractionEvent event, Player player, Game game, String heroId, String heroTitle) {
        if (heroId.contains("redcreuss") && player.hasLeaderUnlocked("crimsonhero")) {
            heroId = "crimsonhero";
        }
        Leader playerLeader = player.unsafeGetLeader(heroId);
        LeaderModel leaderModel = playerLeader.getLeaderModel().orElse(null);
        boolean showFlavourText = Constants.VERBOSITY_VERBOSE.equals(game.getOutputVerbosity());
        if (leaderModel != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.toString() + " is playing " + heroTitle + ".",
                    FrankenAlternateTextService.getLeaderEmbed(
                            game, leaderModel, false, true, true, showFlavourText, game.isTwilightsFallMode()));
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " is playing " + heroTitle + ".\n"
                            + Helper.getLeaderFullRepresentation(playerLeader));
        }
        boolean purged = PlayHeroService.removeLeader(game, player, playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), heroTitle + " has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), heroTitle + " was not purged - something went wrong.");
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
