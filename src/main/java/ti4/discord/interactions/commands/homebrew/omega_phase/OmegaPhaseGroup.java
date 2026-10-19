package ti4.discord.interactions.commands.homebrew.omega_phase;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;
import ti4.helpers.Constants;

public class OmegaPhaseGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new AssignPlayerPriority(),
                    new ClearPriorityTrack(),
                    new PrintPriorityTrack(),
                    new ElectVoiceOfTheCouncil(),
                    new ResetVoiceOfTheCouncil(),
                    new ResetObjectives())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public OmegaPhaseGroup() {
        super(Constants.OMEGA_PHASE_COMMAND, "Omega Phase commands");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }
}
