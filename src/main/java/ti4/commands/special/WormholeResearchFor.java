package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;

public class WormholeResearchFor extends GameStateSubcommand {

    public WormholeResearchFor() {
        super(Constants.WORMHOLE_RESEARCH_FOR, "Destroy all ships in alpha/beta", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AgendaHelper.doResearch(event, getGame());
    }
}
