package ti4.discord.interactions.commands.bothelper;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

public class BothelperCommand2 implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new CreateFOWGameChannels(), new DeleteFOWCommThreads())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.bothelperRoles);
    }

    @Override
    public String getName() {
        return Constants.BOTHELPER + 2;
    }

    @Override
    public String getDescription() {
        return "Bothelper Fog of War tools: create FoW game channels, delete FoW communication threads";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
