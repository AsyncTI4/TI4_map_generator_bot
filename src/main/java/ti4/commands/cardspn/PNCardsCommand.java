package ti4.commands.cardspn;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

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
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Game userActiveGame = gameManager.getUserActiveGame(userID);
            if (userActiveGame.isCommunityMode()){
                Player player = Helper.getGamePlayer(userActiveGame, null, event, userID);
                if (player == null || !userActiveGame.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(MapGenerator.userID)) {
                    MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                    return false;
                }
            } else if (!userActiveGame.getPlayerIDs().contains(userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
            if (!event.getChannel().getName().startsWith(userActiveGame.getName() + "-")) {
                MessageHelper.replyToMessage(event, "Commands can be executed only in game specific channels");
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        Game userActiveGame = GameManager.getInstance().getUserActiveGame(user.getId());
        String activeGame = "";
        if (userActiveGame != null) {
            activeGame = "Active map: " + userActiveGame.getName();
        }
        String commandExecuted = "User: " + userName + " executed command. " + activeGame + "\n" +
                event.getName() + " " + event.getInteraction().getSubcommandName() + " " + event.getOptions().stream()
                .map(option -> option.getName() + ":" + getOptionValue(option))
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    private String getOptionValue(OptionMapping option) {
        if (option.getName().equals(Constants.PLAYER)) {
            return option.getAsUser().getName();
        }
        return option.getAsString();
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
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
       // GenerateMap.getInstance().saveImage(activeMap, event);
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
