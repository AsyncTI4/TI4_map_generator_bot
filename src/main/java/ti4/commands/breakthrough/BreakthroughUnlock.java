package ti4.commands.breakthrough;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

class BreakthroughUnlock extends GameStateSubcommand {

    BreakthroughUnlock() {
        super(Constants.BREAKTHROUGH_UNLOCK, "Unlock breakthrough", true, true);
        addOption(OptionType.STRING, Constants.BREAKTHROUGH, "Which breakthrough to unlock", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction to unlock their breakthrough", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> ids = BreakthroughCommandHelper.getBreakthroughsFromEvent(event, getPlayer());
        BreakthroughCommandHelper.unlockBreakthroughs(getGame(), getPlayer(), ids);
    }
}
