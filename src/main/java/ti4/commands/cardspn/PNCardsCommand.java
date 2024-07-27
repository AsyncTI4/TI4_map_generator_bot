package ti4.commands.cardspn;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PNCardsCommand implements Command {

    private final Collection<PNCardsSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.CARDS_PN;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    public static boolean acceptEvent(SlashCommandInteractionEvent event, String actionID) {
        if (event.getName().equals(actionID)) {
            String userID = event.getUser().getId();
            GameManager gameManager = GameManager.getInstance();
            if (!gameManager.isUserWithActiveGame(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: `/set_game gameName`.");
                return false;
            }
            Game userActiveGame = gameManager.getUserActiveGame(userID);
            if (userActiveGame.isCommunityMode()) {
                Player player = Helper.getGamePlayer(userActiveGame, null, event, userID);
                if (player == null || !userActiveGame.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                    MessageHelper.replyToMessage(event, "You're not a player of the game, please call function `/join gameName`.");
                    return false;
                }
            } else if (!userActiveGame.getPlayerIDs().contains(userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function `/join gameName`.");
                return false;
            }
            if (!event.getChannel().getName().startsWith(userActiveGame.getName() + "-")) {
                MessageHelper.replyToMessage(event, "Commands may be executed only in game specific channels.");
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        PNCardsSubcommandData subCommandExecuted = null;
        for (PNCardsSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subCommandExecuted = subcommand;
                break;
            }
        }
        if (subCommandExecuted == null) {
            reply(event);
        } else {
            subCommandExecuted.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(game, event);
        // new GenerateMap().saveImage(activeMap, event);
    }

    protected String getActionDescription() {
        return "Promissory Notes";
    }

    private Collection<PNCardsSubcommandData> getSubcommands() {
        Collection<PNCardsSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new ShowPN());
        subcommands.add(new ShowAllPN());
        subcommands.add(new ShowPNToAll());
        subcommands.add(new PlayPN());
        subcommands.add(new SendPN());
        subcommands.add(new PurgePN());
        subcommands.add(new PNInfo());
        subcommands.add(new PNReset());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
