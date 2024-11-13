package ti4.commands.fow;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class FOWCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
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
            new ShowGameAsPlayer());


    @Override
    public String getActionId() {
        return Constants.FOW;
    }

    public String getActionDescription() {
        return "Fog of War";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
