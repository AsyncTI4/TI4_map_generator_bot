package ti4.commands.capture;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class CaptureCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new AddCaptureUnits(),
            new RemoveCaptureUnits());

    @Override
    public String getActionId() {
        return Constants.CAPTURE;
    }

    public String getActionDescription() {
        return "Capture units";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
