package ti4.commands.cardsac;

import java.util.List;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class ACCardsCommand implements Command {

    private final Collection<ACCardsSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.CARDS_AC;
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
            Member member = event.getMember();
            if (member != null) {
                List<Role> roles = member.getRoles();
                for (Role role : AsyncTI4DiscordBot.adminRoles) {
                    if (roles.contains(role)) {
                        return true;
                    }
                }
            }
            Game userActiveGame = gameManager.getUserActiveGame(userID);
            if (userActiveGame.isCommunityMode()){
                Player player = Helper.getGamePlayer(userActiveGame, null, event, userID);
                if (player == null || !userActiveGame.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                    MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                    return false;
                }
            } else if (!userActiveGame.getPlayerIDs().contains(userID) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
            if (!event.getChannel().getName().startsWith(userActiveGame.getName()+"-")){
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
                event.getName() + " " +  event.getInteraction().getSubcommandName() + " " + event.getOptions().stream()
                .map(option -> option.getName() + ":" + getOptionValue(option))
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    private String getOptionValue(OptionMapping option) {
        if (option.getName().equals(Constants.PLAYER)){
            return option.getAsUser().getName();
        }
        return option.getAsString();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ACCardsSubcommandData subCommandExecuted = null;
        String subcommandName = event.getInteraction().getSubcommandName();
        for (ACCardsSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subCommandExecuted = subcommand;
                break;
            }
        }
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
        MessageHelper.replyToMessage(event, "Card action executed: " + (subCommandExecuted != null ? subCommandExecuted.getName() : ""));
    }


    protected String getActionDescription() {
        return "Action Cards";
    }

    private Collection<ACCardsSubcommandData> getSubcommands() {
        Collection<ACCardsSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new ACInfo_Legacy());
        subcommands.add(new ACInfo());
        subcommands.add(new DrawAC());
        subcommands.add(new DiscardAC());
        subcommands.add(new DiscardACRandom());
        subcommands.add(new ShowAC());
        subcommands.add(new ShowACToAll());
        subcommands.add(new PlayAC());
        subcommands.add(new ShuffleACDeck());
        subcommands.add(new ShowAllAC());
        subcommands.add(new ShowACRemainingCardCount());
        subcommands.add(new PickACFromDiscard());
        subcommands.add(new ShowDiscardActionCards());
        subcommands.add(new ShuffleACBackIntoDeck());
        subcommands.add(new RevealAndPutACIntoDiscard());
        subcommands.add(new SentAC());
        subcommands.add(new SentACRandom());
        subcommands.add(new DrawSpecificAC());
        subcommands.add(new MakeCopiesOfACs());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
