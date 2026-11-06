package ti4.discord.interactions.commands.homebrew.lost_legacies;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperExplore;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

class ViewPurgedStonebornFragments extends GameStateSubcommand {
    ViewPurgedStonebornFragments() {
        super("purged_stoneborn_frags", "View purged Stoneborn relic fragments", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!getPlayer().hasAbility("trovantous")) {
            MessageHelper.sendMessageToChannel(getPlayer().getCardsInfoThread(), "You do not have Trovantous.");
            return;
        }
        List<String> fragments = ButtonHelperExplore.getPurgedFragments(getGame()).stream()
                .filter(fragment -> fragment.startsWith("stoneborn"))
                .toList();
        String message = fragments.isEmpty()
                ? getPlayer().getRepresentationNoPing() + " has no purged Stoneborn relic fragments."
                : getPlayer().getRepresentationNoPing() + " has these purged Stoneborn relic fragments:\n"
                        + fragments.stream()
                                .map(Mapper::getExplore)
                                .map(fragment -> "- " + fragment.getName())
                                .reduce((first, second) -> first + "\n" + second)
                                .orElse("");
        MessageHelper.sendMessageToChannel(getPlayer().getCardsInfoThread(), message);
    }
}
