package ti4.discord.interactions.slashcommands.units;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;

public class CaptureCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new AddCaptureUnits(), new FixRemoveCaptureUnits(), new RemoveCaptureUnits())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.CAPTURE;
    }

    public String getDescription() {
        return "Capture units";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
