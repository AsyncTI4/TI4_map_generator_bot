package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.cards.CardsCommand;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class AgendaCommand implements Command {

    private final Collection<AgendaSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.AGENDA;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return CardsCommand.acceptEvent(event, getActionID());
    }

    @Override
    public void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        Map userActiveMap = MapManager.getInstance().getUserActiveMap(user.getId());
        String activeMap = "";
        if (userActiveMap != null) {
            activeMap = "Active map: " + userActiveMap.getName();
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
        AgendaSubcommandData executedCommand = null;
        for (AgendaSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }

        Map map = MapManager.getInstance().getUserActiveMap(event.getUser().getId());
        if (map != null) {
            MapSaveLoadManager.saveMap(map);
        }
        if (executedCommand != null) {
            // MessageHelper.replyToMessage(event, "Executed action: " + executedCommand.getActionID());
        } else {
            MessageHelper.replyToMessage(event, "No Action executed");
        }
    }

    protected String getActionDescription() {
        return "Agenda handling";
    }

    private Collection<AgendaSubcommandData> getSubcommands() {
        Collection<AgendaSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DrawAgenda());
        subcommands.add(new PutAgendaTop());
        subcommands.add(new PutAgendaBottom());
        subcommands.add(new LookAtTopAgenda());
        subcommands.add(new LookAtBottomAgenda());
        subcommands.add(new RevealAgenda());
        subcommands.add(new AddLaw());
        subcommands.add(new RemoveLaw());
        subcommands.add(new ShowDiscardedAgendas());
        subcommands.add(new ListVoteCount());
        subcommands.add(new ShuffleAgendas());
        subcommands.add(new ResetAgendas());
        subcommands.add(new Cleanup());
        subcommands.add(new ResetDrawStateAgendas());
        subcommands.add(new ShuffleDiscardBackIntoDeckAgendas());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
