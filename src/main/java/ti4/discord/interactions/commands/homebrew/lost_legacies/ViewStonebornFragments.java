package ti4.discord.interactions.commands.homebrew.lost_legacies;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornAbilityHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class ViewStonebornFragments extends GameStateSubcommand {
    ViewStonebornFragments() {
        super("view_stoneborn_frags", "View the remaining Stoneborn relic fragments", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!getPlayer().hasAbility("trovantous")) {
            MessageHelper.sendMessageToChannel(getPlayer().getCardsInfoThread(), "You do not have Trovantous.");
            return;
        }
        List<String> fragments = StonebornAbilityHandler.getRemainingStonebornFragments(getGame());
        String message = fragments.isEmpty()
                ? getPlayer().getRepresentationNoPing() + " has no Stoneborn relic fragments remaining."
                : getPlayer().getRepresentationNoPing() + " has these Stoneborn relic fragments remaining:\n"
                        + fragments.stream()
                                .map(Mapper::getExplore)
                                .map(fragment -> "- " + fragment.getName())
                                .reduce((first, second) -> first + "\n" + second)
                                .orElse("");
        MessageHelper.sendMessageToChannel(getPlayer().getCardsInfoThread(), message);
    }
}
