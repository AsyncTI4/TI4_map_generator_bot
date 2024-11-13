package ti4.commands.planet;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class PlanetCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new PlanetAdd(),
            new PlanetRemove(),
            new PlanetExhaust(),
            new PlanetRefresh(),
            new PlanetExhaustAbility(),
            new PlanetRefreshAbility(),
            new PlanetRefreshAll(),
            new PlanetExhaustAll(),
            new PlanetInfo()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.PLANET;
    }

    public String getDescription() {
        return "Add/remove/exhaust/ready/spend planets";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
