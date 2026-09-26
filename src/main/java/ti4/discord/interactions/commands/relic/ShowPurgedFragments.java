package ti4.discord.interactions.commands.relic;

import java.util.Comparator;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperExplore;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class ShowPurgedFragments extends GameStateSubcommand {

    ShowPurgedFragments() {
        super(Constants.SHOW_PURGED_FRAGMENTS, "Show relic fragment purged list", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder message = new StringBuilder("__Relic Fragment Purge List__:\n");
        int index = 1;
        for (String fragmentId : ButtonHelperExplore.getPurgedFragments(getGame()).stream()
                .sorted(Comparator.comparing(
                        fragmentId -> Mapper.getExplore(fragmentId).getName()))
                .toList()) {
            message.append('`')
                    .append(index++)
                    .append(".` - ")
                    .append(Mapper.getExplore(fragmentId).getNameRepresentation())
                    .append(" (`")
                    .append(fragmentId)
                    .append("`)")
                    .append('\n');
        }
        if (index == 1) {
            message.append("No relic fragments have been purged.");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
    }
}
