package ti4.commands.tech;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class TechCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new TechAdd(),
            new TechRemove(),
            new TechExhaust(),
            new TechRefresh(),
            new TechInfo(),
            new GetTechButton(),
            new TechChangeType(),
            new TechShowDeck()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.TECH;
    }

    @Override
    public String getDescription() {
        return "Add/remove/exhaust/ready Technologies";
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
