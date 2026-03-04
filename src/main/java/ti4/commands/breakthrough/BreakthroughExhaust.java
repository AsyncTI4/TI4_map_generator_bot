package ti4.commands.breakthrough;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

class BreakthroughExhaust extends GameStateSubcommand {

    BreakthroughExhaust() {
        super(Constants.BREAKTHROUGH_EXHAUST, "Exhaust breakthrough", true, true);
        addOption(OptionType.STRING, Constants.BREAKTHROUGH, "Which breakthrough to exhaust", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction to exhaust their breakthrough", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> ids = BreakthroughCommandHelper.getBreakthroughsFromEvent(event, getPlayer());
        BreakthroughCommandHelper.exhaustBreakthroughs(getPlayer(), ids);
    }
}
