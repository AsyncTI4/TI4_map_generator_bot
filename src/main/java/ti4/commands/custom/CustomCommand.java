package ti4.commands.custom;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
import java.util.stream.Collectors;

public class CustomCommand implements Command {

    private final Collection<CustomSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.CUSTOM;
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
    public void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        Game userActiveGame = GameManager.getInstance().getUserActiveGame(user.getId());
        String activeMap = "";
        if (userActiveGame != null) {
            activeMap = "Active map: " + userActiveGame.getName();
        }
        String commandExecuted = "User: " + userName + " executed command. " + activeMap + "\n" +
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
        String subcommandName = event.getInteraction().getSubcommandName();
        CustomSubcommandData executedCommand = null;
        for (CustomSubcommandData subcommand : subcommandData) {
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
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
    }


    protected String getActionDescription() {
        return "Custom";
    }

    private Collection<CustomSubcommandData> getSubcommands() {
        Collection<CustomSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new SoRemoveFromGame());
        subcommands.add(new SoAddToGame());
        subcommands.add(new AgendaRemoveFromGame());
        subcommands.add(new ACRemoveFromGame());
        subcommands.add(new SCAddToGame());
        subcommands.add(new SCRemoveFromGame());
        subcommands.add(new PoRemoveFromGame());
        subcommands.add(new DiscardSpecificAgenda());
        subcommands.add(new FixSODeck());
        subcommands.add(new SetThreadName());
        subcommands.add(new PeakAtStage1());
        subcommands.add(new PeakAtStage2());
        subcommands.add(new SetUpPeakableObjectives());
        subcommands.add(new SwapStage1());
        subcommands.add(new SwapStage2());
        subcommands.add(new RevealSpecificStage1());
        subcommands.add(new RevealSpecificStage2());
        subcommands.add(new SpinTilesInFirstThreeRings());
        subcommands.add(new ChangeToBaseGame());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
