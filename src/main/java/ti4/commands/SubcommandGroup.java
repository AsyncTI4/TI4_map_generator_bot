package ti4.commands;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.jetbrains.annotations.NotNull;
import ti4.commands.SuspicionLevel;

public abstract class SubcommandGroup extends SubcommandGroupData implements Command {

    protected SubcommandGroup(@NotNull String name, @NotNull String description) {
        super(name, description);

        Map<String, Subcommand> subcommands = getGroupSubcommands();
        if (subcommands == null) {
            throw new IllegalStateException("Subcommand group " + name + " returned null subcommands map");
        }
        addSubcommands(subcommands.values());
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        if (!getName().equals(event.getInteraction().getSubcommandGroup())) return false;

        return getGroupSubcommands().containsKey(event.getInteraction().getSubcommandName());
    }

    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        Subcommand subcommand = getGroupSubcommands().get(subcommandName);
        subcommand.preExecute(event);
        subcommand.execute(event);
        subcommand.postExecute(event);
    }

    @Override
    public SuspicionLevel getSuspicionLevel(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        Subcommand subcommand = getGroupSubcommands().get(subcommandName);
        return subcommand != null ? subcommand.getSuspicionLevel(event) : Command.super.getSuspicionLevel(event);
    }

    public abstract Map<String, Subcommand> getGroupSubcommands();
}
