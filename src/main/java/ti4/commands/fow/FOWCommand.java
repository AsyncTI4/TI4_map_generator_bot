package ti4.commands.fow;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class FOWCommand implements Command {

    private final Collection<FOWSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.FOW;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            User user = event.getUser();
            String userID = user.getId();
            GameManager gameManager = GameManager.getInstance();
            if (!gameManager.isUserWithActiveGame(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Game userActiveGame = gameManager.getUserActiveGame(userID);
            if (!userActiveGame.getPlayerIDs().contains(userID) && !userActiveGame.isCommunityMode()) {
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
        FOWSubcommandData executedCommand = null;
        for (FOWSubcommandData subcommand : subcommandData) {
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
        GameSaveLoadManager.saveMap(game, event);
        MessageHelper.replyToMessage(event, "Executed command. Use /show_game to check map");
    }

    protected String getActionDescription() {
        return "Fog of War";
    }

    private Collection<FOWSubcommandData> getSubcommands() {
        Collection<FOWSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new AddCustomAdjacentTile());
        subcommands.add(new AddAdjacencyOverride());
        subcommands.add(new AddAdjacencyOverrideList());
        subcommands.add(new AddFogTile());
        subcommands.add(new CheckChannels());
        subcommands.add(new PingActivePlayer());
        subcommands.add(new PingSystem());
        subcommands.add(new RemoveAdjacencyOverride());
        subcommands.add(new RemoveAllAdjacencyOverrides());
        subcommands.add(new RemoveFogTile());
        subcommands.add(new RemoveCustomAdjacentTile());
        subcommands.add(new RemoveAllCustomAdjacentTiles());
        subcommands.add(new SetFogFilter());
        subcommands.add(new Whisper());
        subcommands.add(new Announce());
        subcommands.add(new FOWOptions());
        subcommands.add(new ShowGameAsPlayer());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        SlashCommandData list = Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands());
        commands.addCommands(list);
    }
}
