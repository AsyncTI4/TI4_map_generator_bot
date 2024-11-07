package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.SlashCommandAcceptanceHelper;

public interface InfoThreadCommand {
    String getActionID();

    default boolean acceptEvent(SlashCommandInteractionEvent event, String actionID) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getActionID(), event);
    }
}
