package ti4.commands.status;

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

public class StatusCommand implements Command {

    private final Collection<StatusSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.STATUS;
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
        StatusSubcommandData executedCommand = null;
        for (StatusSubcommandData subcommand : subcommandData) {
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
        MapSaveLoadManager.saveMap(activeMap);

        File file = GenerateMap.getInstance().saveImage(activeMap);
        MessageHelper.replyToMessage(event, file);
    }


    protected String getActionDescription() {
        return "Status phase";
    }

    private Collection<StatusSubcommandData> getSubcommands() {
        Collection<StatusSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new Cleanup());
        subcommands.add(new RevealStage1());
        subcommands.add(new RevealStage2());
        subcommands.add(new ShufflePublicBack());
        subcommands.add(new ScorePublic());
        subcommands.add(new UnscorePublic());
        subcommands.add(new AddCustomPO());
        subcommands.add(new RemoveCustomPO());
        subcommands.add(new SCTradeGoods());
        subcommands.add(new ListTurnOrder());
        subcommands.add(new ListVoteCount());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
