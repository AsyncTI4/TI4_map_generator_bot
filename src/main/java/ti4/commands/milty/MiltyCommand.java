package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
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

public class MiltyCommand implements Command {

    private final Collection<MiltySubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.MILTY;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            String userID = event.getUser().getId();
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
        //MiltySubcommandData executedCommand = null;
        for (MiltySubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                //executedCommand = subcommand;
                break;
            }
        }
        reply(event);
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(game, event);
    }

    protected String getActionDescription() {
        return "Milty draft";
    }

    private Collection<MiltySubcommandData> getSubcommands() {
        Collection<MiltySubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DebugMilty());
        subcommands.add(new ForcePick());
        subcommands.add(new SetupMilty());
        subcommands.add(new StartMilty());
        subcommands.add(new ShowMilty());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}
