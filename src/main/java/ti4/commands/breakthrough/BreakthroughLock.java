package ti4.commands.breakthrough;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

class BreakthroughLock extends GameStateSubcommand {

    BreakthroughLock() {
        super(Constants.BREAKTHROUGH_LOCK, "Lock breakthrough", true, true);
        addOption(OptionType.STRING, Constants.BREAKTHROUGH, "Which breakthrough to lock", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction to lock their breakthrough", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> ids = BreakthroughCommandHelper.getBreakthroughsFromEvent(event, getPlayer());
        BreakthroughCommandHelper.lockBreakthroughs(getPlayer(), ids);
    }
}
