package ti4.commands.planet;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class PlanetCommand implements Command {

    private final Collection<PlanetSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.PLANET;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            String userID = event.getUser().getId();
            MapManager mapManager = MapManager.getInstance();
            if (!mapManager.isUserWithActiveMap(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Map userActiveMap = mapManager.getUserActiveMap(userID);
            if (!userActiveMap.getPlayerIDs().contains(userID) && !userActiveMap.isCommunityMode()) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
            return true;
        }
        return false;
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
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap, event);
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

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
 
}
