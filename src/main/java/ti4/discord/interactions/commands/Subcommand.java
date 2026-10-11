package ti4.discord.interactions.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

public abstract class Subcommand extends SubcommandData implements Command<SlashCommandInteractionEvent> {

    private boolean suspicious;

    protected Subcommand(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    protected void markSuspicious() {
        suspicious = true;
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return suspicious;
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return getName().equals(event.getInteraction().getSubcommandName());
    }

    public abstract void execute(SlashCommandInteractionEvent event);
}
