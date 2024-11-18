package ti4.commands.map;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands2.uncategorized.ShowGame;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;

public class MapCommand implements Command {
    private final Collection<MapSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.MAP;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (MapSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
            }
        }
        String userID = event.getUser().getId();
        Game game = GameManager.getUserActiveGame(userID);
        if (game == null) return;
        ShowGameService.simpleShowGame(game, event);
    }

    protected String getActionDescription() {
        return "Game";
    }

    private Collection<MapSubcommandData> getSubcommands() {
        Collection<MapSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new AddTile());
        subcommands.add(new AddTileList());
        subcommands.add(new RemoveTile());
        subcommands.add(new AddBorderAnomaly());
        subcommands.add(new RemoveBorderAnomaly());
        //subcommands.add(new InteractiveBuilder());
        subcommands.add(new Preset());
        subcommands.add(new ShowMapSetup());
        subcommands.add(new ShowMapString());
        subcommands.add(new SetMapTemplate());
        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getName(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}
