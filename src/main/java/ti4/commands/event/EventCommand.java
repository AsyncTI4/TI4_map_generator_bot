package ti4.commands.event;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class EventCommand implements Command {

    private final Collection<EventSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.EVENT;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getActionId(), event);
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

        Game game = UserGameContextManager.getContextGame(event.getUser().getId());
        if (game != null) {
            GameSaveLoadManager.saveGame(game, event);
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
        subcommands.add(new AddEvent());
        subcommands.add(new RemoveEvent());
        subcommands.add(new ShowDiscardedEvents());
        subcommands.add(new ShuffleEvents());
        subcommands.add(new ResetEvents());
        subcommands.add(new PutDiscardBackIntoDeckEvents());
        subcommands.add(new EventInfo());
        subcommands.add(new PlayEvent());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
