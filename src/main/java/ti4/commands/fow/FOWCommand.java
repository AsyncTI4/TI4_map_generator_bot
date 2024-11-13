package ti4.commands.fow;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class FOWCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new AddCustomAdjacentTile(),
                    new AddAdjacencyOverride(),
                    new AddAdjacencyOverrideList(),
                    new AddFogTile(),
                    new CheckChannels(),
                    new PingActivePlayer(),
                    new PingSystem(),
                    new RemoveAdjacencyOverride(),
                    new RemoveAllAdjacencyOverrides(),
                    new RemoveFogTile(),
                    new RemoveCustomAdjacentTile(),
                    new RemoveAllCustomAdjacentTiles(),
                    new SetFogFilter(),
                    new Whisper(),
                    new Announce(),
                    new FOWOptions(),
                    new ShowGameAsPlayer())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.FOW;
    }

    public String getDescription() {
        return "Fog of War";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
                CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
