package ti4.discord.interactions.commands.homebrew;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.SubcommandGroup;
import ti4.discord.interactions.commands.homebrew.lost_legacies.LostLegaciesGroup;
import ti4.discord.interactions.commands.homebrew.monuments.MonumentsGroup;
import ti4.discord.interactions.commands.homebrew.omega_phase.OmegaPhaseGroup;

public class HomebrewCommand implements ParentCommand {

    private final Map<String, SubcommandGroup> subcommandGroups = Stream.of(
                    new LostLegaciesGroup(), new MonumentsGroup(), new OmegaPhaseGroup())
            .collect(Collectors.toMap(SubcommandGroup::getName, subcommandGroup -> subcommandGroup));

    @Override
    public String getName() {
        return "homebrew";
    }

    @Override
    public String getDescription() {
        return "Homebrew game mode commands";
    }

    @Override
    public Map<String, SubcommandGroup> getSubcommandGroups() {
        return subcommandGroups;
    }
}
