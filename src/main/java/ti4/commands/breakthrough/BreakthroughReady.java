package ti4.commands.breakthrough;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

class BreakthroughReady extends GameStateSubcommand {

    BreakthroughReady() {
        super(Constants.BREAKTHROUGH_READY, "Ready breakthrough", true, true);
        addOption(OptionType.STRING, Constants.BREAKTHROUGH, "Which breakthrough to ready", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction to ready their breakthrough", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> ids = BreakthroughCommandHelper.getBreakthroughsFromEvent(event, getPlayer());
        BreakthroughCommandHelper.readyBreakthroughs(getPlayer(), ids);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return false;
    }
}
