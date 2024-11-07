package ti4.commands.planet;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class PlanetCommand implements Command {

    private final Collection<PlanetSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.PLANET;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionID(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        PlanetSubcommandData executedCommand = null;
        for (PlanetSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }
        if (executedCommand == null) {
            reply(event);
        } else {
            executedCommand.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);

        MapRenderPipeline.renderToWebsiteOnly(game, event);
    }

    protected String getActionDescription() {
        return "Add/remove/exhaust/ready/spend planets";
    }

    private Collection<PlanetSubcommandData> getSubcommands() {
        Collection<PlanetSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new PlanetAdd());
        subcommands.add(new PlanetRemove());
        subcommands.add(new PlanetExhaust());
        subcommands.add(new PlanetRefresh());
        subcommands.add(new PlanetExhaustAbility());
        subcommands.add(new PlanetRefreshAbility());
        subcommands.add(new PlanetRefreshAll());
        subcommands.add(new PlanetExhaustAll());
        subcommands.add(new PlanetInfo());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }

}
