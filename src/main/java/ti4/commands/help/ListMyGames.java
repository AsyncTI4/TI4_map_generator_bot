package ti4.commands.help;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListMyGames extends HelpSubcommandData {
    public ListMyGames() {
        super(Constants.LIST_MY_GAMES, "List all of your games you are currently in");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_MY_TURN, "True to only show games where it is your turn"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show ended games as well (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean onlyMyTurn = event.getOption(Constants.IS_MY_TURN, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);
        User user = event.getUser();
        String userID = user.getId();

        Predicate<Map> onlyMyTurnFilter = onlyMyTurn ? m -> m.getActivePlayer().equals(userID) : m -> true;
        Predicate<Map> endedGamesFilter = includeEndedGames ? m -> !m.isFoWMode() && m.getPlayerIDs().contains(userID) : m -> !m.isHasEnded() && !m.isFoWMode() && m.getPlayerIDs().contains(userID);

        Comparator<Map> mapSort = new Comparator<Map>() {
            @Override
            public int compare(Map map1, Map map2) {
                return map1.getName().compareTo(map2.getName());
            }
        };

        List<Map> maps = MapManager.getInstance().getMapList().values().stream().filter(onlyMyTurnFilter).filter(endedGamesFilter).sorted(mapSort).toList();

        int index = 1;
        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (Map playerMap : maps) {
            sb.append(Helper.rightpad("`" + index + ".`", 6)).append(getPlayerMapListRepresentation(playerMap, userID, event)).append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), user.getName() + "'s Game List", sb.toString());
    }

    private String getPlayerMapListRepresentation(Map playerMap, String userID, GenericInteractionCreateEvent event) {
        Player player = playerMap.getPlayer(userID);
        if (player == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(playerMap.getName()).append("**:  ");
        sb.append(Helper.getFactionIconFromDiscord(player.getFaction())).append(Helper.getColourAsMention(event.getGuild(), player.getColor())).append(" ").append(playerMap.getActionsChannel() == null ? "" : playerMap.getActionsChannel().getAsMention());
        if (playerMap.getActivePlayer() == null ? false : playerMap.getActivePlayer().equals(userID)) sb.append(" - **__IT IS YOUR TURN__**");
        if (playerMap.isHasEnded()) sb.append(" - GAME HAS ENDED");
        return sb.toString();
    }

}
