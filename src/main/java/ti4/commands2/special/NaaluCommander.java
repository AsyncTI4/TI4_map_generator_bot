package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.leader.NaaluCommanderService;

class NaaluCommander extends GameStateSubcommand {

    public NaaluCommander() {
        super(Constants.NAALU_COMMANDER, "Look at your neighbours' promissory notes and the top and bottom of the agenda deck.", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        NaaluCommanderService.secondHalfOfNaaluCommander(event, getGame(), getPlayer());
    }
}
