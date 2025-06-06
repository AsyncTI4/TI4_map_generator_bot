package ti4.commands.omega_phase;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.omega_phase.PriorityTrackHelper;

class ClearPriorityTrack extends GameStateSubcommand {
    public ClearPriorityTrack() {
        super(Constants.CLEAR_PRIORITY_TRACK, "Clear all players off of the Priority Track", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PriorityTrackHelper.ClearPriorityTrack(getGame());
    }
}
