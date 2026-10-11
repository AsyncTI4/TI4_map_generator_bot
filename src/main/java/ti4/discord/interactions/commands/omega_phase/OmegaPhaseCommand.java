package ti4.discord.interactions.commands.omega_phase;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class OmegaPhaseCommand extends AbstractParentCommand {

    public OmegaPhaseCommand() {
        super(
                Constants.OMEGA_PHASE_COMMAND,
                "Omega Phase homebrew commands",
                new AssignPlayerPriority(),
                new ClearPriorityTrack(),
                new PrintPriorityTrack(),
                new ElectVoiceOfTheCouncil(),
                new ResetVoiceOfTheCouncil(),
                new ResetObjectives());
    }
}
