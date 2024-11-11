package ti4.commands.capture;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;

public class CaptureCommand implements Command {

    private final Collection<GameStateSubcommand> subcommands = List.of(
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
}
