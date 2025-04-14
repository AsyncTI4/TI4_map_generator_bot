package ti4.commands.omegaphase;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class OmegaPhaseCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
        new AssignPlayerPriority(),
        new ClearPriorityTrack(),
        new PrintPriorityTrack(),
        new ElectVoiceOfTheCouncil(),
        new ResetVoiceOfTheCouncil()).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.OMEGA_PHASE_COMMAND;
    }

    @Override
    public String getDescription() {
        return "Omega Phase homebrew commands";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}