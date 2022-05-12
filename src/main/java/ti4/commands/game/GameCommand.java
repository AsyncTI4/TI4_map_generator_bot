package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.NotNull;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class GameCommand implements Command {

    private final Collection<GameSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.GAME;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        String commandExecuted = "User: " + userName + " executed command.\n" +
                event.getName() + " " + event.getInteraction().getSubcommandName() + " " + event.getOptions().stream()
                .map(option -> option.getName() + ":" + getOptionValue(option))
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    private String getOptionValue(OptionMapping option) {
        if (option.getType().equals(OptionType.USER)){
            return option.getAsUser().getName();
        }
        return option.getAsString();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean undoCommand = false;
        String subcommandName = event.getInteraction().getSubcommandName();
        for (GameSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                if (subcommandName.equals(Constants.UNDO)){
                    undoCommand = true;
                }
            }
        }
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        if (!undoCommand) {
            MapSaveLoadManager.saveMap(activeMap);
        }
        File file = GenerateMap.getInstance().saveImage(activeMap);
        MessageHelper.replyToMessage(event, file);
    }

    protected String getActionDescription() {
        return "Game";
    }

    private Collection<GameSubcommandData> getSubcommands() {
        Collection<GameSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new Info());
        subcommands.add(new SetStatus());
        subcommands.add(new Join());
        subcommands.add(new Leave());
        subcommands.add(new Add());
        subcommands.add(new Remove());
        subcommands.add(new SetOrder());
        subcommands.add(new Undo());
        subcommands.add(new SCCount());
        subcommands.add(new Setup());
        subcommands.add(new Replace());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
