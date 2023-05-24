package ti4.commands.cardsso;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class SOCardsCommand implements Command {

    private final Collection<SOCardsSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.CARDS_SO;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    public static boolean acceptEvent(SlashCommandInteractionEvent event, String actionID) {
        if (event.getName().equals(actionID)) {
            String userID = event.getUser().getId();
            MapManager mapManager = MapManager.getInstance();
            if (!mapManager.isUserWithActiveMap(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Member member = event.getMember();
            if (member != null) {
                java.util.List<Role> roles = member.getRoles();
                for (Role role : MapGenerator.adminRoles) {
                    if (roles.contains(role)) {
                        return true;
                    }
                }
            }
            Map userActiveMap = mapManager.getUserActiveMap(userID);
            if (userActiveMap.isCommunityMode()){
                Player player = Helper.getGamePlayer(userActiveMap, null, event, userID);
                if (player == null || !userActiveMap.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(MapGenerator.userID)) {
                    MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                    return false;
                }
            } else if (!userActiveMap.getPlayerIDs().contains(userID) && !event.getUser().getId().equals(MapGenerator.userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
            if (!event.getChannel().getName().startsWith(userActiveMap.getName()+"-")){
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
        SOCardsSubcommandData subCommandExecuted = null;
        for (SOCardsSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subCommandExecuted = subcommand;
                break;
            }
        }
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap, event);
        // MessageHelper.replyToMessage(event, "Card action executed: " + (subCommandExecuted != null ? subCommandExecuted.getName() : ""));
    }


    protected String getActionDescription() {
        return "Secret Objectives";
    }

    private Collection<SOCardsSubcommandData> getSubcommands() {
        Collection<SOCardsSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DrawSO());
        subcommands.add(new DiscardSO());
        subcommands.add(new SOInfo());
        subcommands.add(new ShowSO());
        subcommands.add(new ShowSOToAll());
        subcommands.add(new ScoreSO());
        subcommands.add(new DealSO());
        subcommands.add(new UnscoreSO());
        subcommands.add(new ShowAllSO());
        subcommands.add(new ShowAllSOToAll());
        subcommands.add(new DealSOToAll());
        subcommands.add(new DrawSpecificSO());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
