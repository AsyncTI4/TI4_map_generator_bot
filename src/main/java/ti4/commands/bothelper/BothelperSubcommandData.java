package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

public abstract class BothelperSubcommandData extends SubcommandData {

    public BothelperSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    abstract public void execute(SlashCommandInteractionEvent event);
}
