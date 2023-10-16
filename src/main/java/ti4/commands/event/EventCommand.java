package ti4.commands.event;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.cardsac.ACCardsCommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class EventCommand implements Command {

    private final Collection<EventSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.EVENT;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ACCardsCommand.acceptEvent(event, getActionID());
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
        String subcommandName = event.getInteraction().getSubcommandName();
        EventSubcommandData executedCommand = null;
        for (EventSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }

        Game activeGame = GameManager.getInstance().getUserActiveGame(event.getUser().getId());
        if (activeGame != null) {
            GameSaveLoadManager.saveMap(activeGame, event);
        }
        if (executedCommand != null) {
            // MessageHelper.replyToMessage(event, "Executed action: " + executedCommand.getActionID());
        } else {
            MessageHelper.replyToMessage(event, "No Action executed");
        }
    }

    protected String getActionDescription() {
        return "Event handling";
    }

    private Collection<EventSubcommandData> getSubcommands() {
        Collection<EventSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DrawEvent());
        subcommands.add(new PutEventTop());
        subcommands.add(new PutEventBottom());
        subcommands.add(new LookAtTopEvent());
        subcommands.add(new LookAtBottomEvent());
        subcommands.add(new RevealEvent());
        subcommands.add(new RevealSpecificEvent());
        subcommands.add(new AddPermanentEvent());
        subcommands.add(new RemoveEvent());
        subcommands.add(new ReviseEvent());
        subcommands.add(new ShowDiscardedEvents());
        subcommands.add(new ListVoteCount());
        subcommands.add(new ShuffleEvents());
        subcommands.add(new ResetEvents());
        subcommands.add(new ResetDrawStateEvents());
        subcommands.add(new PutDiscardBackIntoDeckEvents());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
