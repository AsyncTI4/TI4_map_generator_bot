package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

public abstract class Subcommand extends SubcommandData implements Command {

    protected Subcommand(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return getName().equals(event.getInteraction().getSubcommandName());
    }

    public abstract void execute(SlashCommandInteractionEvent event);
}
