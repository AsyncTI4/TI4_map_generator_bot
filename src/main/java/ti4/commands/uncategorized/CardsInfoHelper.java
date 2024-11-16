package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public class CardsInfoHelper {
    private CardsInfoHelper() {
        // private constructor to hide the implicit public one
    }

    public static String getHeaderText(GenericInteractionCreateEvent event) {
        if (event instanceof SlashCommandInteractionEvent) {
            return " used `" + ((SlashCommandInteractionEvent) event).getCommandString() + "`";
        }
        if (event instanceof ButtonInteractionEvent) {
            return " pressed `" + ((ButtonInteractionEvent) event).getButton().getId() + "`";
        }
        return " used the force";
    }
}
