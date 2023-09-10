package ti4.commands.map;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class MapCommand implements Command {
    private final Collection<MapSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.MAP;
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
        if (option.getType() == OptionType.USER) {
            return option.getAsUser().getName();
        }
        return option.getAsString();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (MapSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
            }
        }
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        if (activeGame == null) return;
        File file = GenerateMap.getInstance().saveImage(activeGame, event);
        MessageHelper.replyToMessage(event, file);
    }

    protected String getActionDescription() {
        return "Game";
    }

    private Collection<MapSubcommandData> getSubcommands() {
        Collection<MapSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new AddTile());
        subcommands.add(new AddTileList());
        subcommands.add(new RemoveTile());
        subcommands.add(new AddBorderAnomaly());
        subcommands.add(new RemoveBorderAnomaly());
        subcommands.add(new InitTspmap());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}