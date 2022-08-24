package ti4.commands.player;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.status.StatusSubcommandData;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
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

public class PlayerCommand implements Command {

    private final Collection<PlayerSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.PLAYER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            String userID = event.getUser().getId();
            MapManager mapManager = MapManager.getInstance();
            if (!mapManager.isUserWithActiveMap(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Map userActiveMap = mapManager.getUserActiveMap(userID);
            if (!userActiveMap.getPlayerIDs().contains(userID) && !userActiveMap.isCommunityMode()) {
                MessageHelper.replyToMessage(event, "Your not a player of the game, please call function /join gameName");
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
        } else if (option.getName().equals(Constants.TECH)){
            return Mapper.getTechs().get(option.getAsString());
        }
        return option.getAsString();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        PlayerSubcommandData executedCommand = null;
        for (PlayerSubcommandData subcommand : subcommandData) {
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
        return "Player";
    }

    private Collection<PlayerSubcommandData> getSubcommands() {
        Collection<PlayerSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new Stats());
        subcommands.add(new Planets());
        subcommands.add(new Setup());
        subcommands.add(new SCPlay());
        subcommands.add(new Pass());
        subcommands.add(new TechAdd());
        subcommands.add(new TechRemove());
        subcommands.add(new TechExhaust());
        subcommands.add(new TechRefresh());
        subcommands.add(new Turn());
        subcommands.add(new SCPick());
        subcommands.add(new PlanetAdd());
        subcommands.add(new PlanetRemove());
        subcommands.add(new PlanetExhaust());
        subcommands.add(new PlanetRefresh());
        subcommands.add(new PlanetExhaustAbility());
        subcommands.add(new PlanetRefreshAbility());
        subcommands.add(new PlanetRefreshAll());
        subcommands.add(new PlanetExhaustAll());
        subcommands.add(new Speaker());
        subcommands.add(new SendTG());
        subcommands.add(new SendCommodities());
        subcommands.add(new SCFollow());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
