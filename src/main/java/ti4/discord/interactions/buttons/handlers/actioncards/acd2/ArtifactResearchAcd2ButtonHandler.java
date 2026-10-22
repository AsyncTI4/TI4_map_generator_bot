package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StringHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class ArtifactResearchAcd2ButtonHandler {

    @ButtonHandler("resolveArtifactResearch")
    public static void resolveArtifactResearch(Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        int ignoredPrerequisites = distinctFragmentTypes(player).size();
        if (ignoredPrerequisites == 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " owns no relic fragments, so _Artifact Research_ lets that player ignore no"
                            + " prerequisites.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " owns " + StringHelper.pluralize(ignoredPrerequisites, "type")
                        + " of relic fragment, so with _Artifact Research_ the researching player ignores "
                        + StringHelper.pluralize(ignoredPrerequisites, "prerequisite")
                        + " of that technology. The bot does not check prerequisites, so use the \"Get Other"
                        + " Technology\" button if the technology is not shown.");
    }

    private static Set<String> distinctFragmentTypes(Player player) {
        return player.getFragments().stream()
                .map(fragId -> Mapper.getExplore(fragId).getType().toLowerCase())
                .collect(Collectors.toSet());
    }
}
