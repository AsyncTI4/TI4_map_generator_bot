package ti4.discord.interactions.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class CaptureCommand extends AbstractParentCommand {

    public CaptureCommand() {
        super(Constants.CAPTURE, "Capture units", CaptureUnits.add(), CaptureUnits.fixRemove(), CaptureUnits.remove());
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
