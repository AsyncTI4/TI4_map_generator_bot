package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Info extends GameSubcommandData{
    public static final String NEW_LINE = "\n";

    public Info() {
        super(Constants.INFO, "Game information:");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game Name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping gameOption = event.getOption(Constants.GAME_NAME);
        MapManager mapManager = MapManager.getInstance();
        Map activeMap = mapManager.getUserActiveMap(event.getUser().getId());
        StringBuilder sb = getGameInfo(gameOption, mapManager, activeMap);
        MessageHelper.replyToMessage(event, sb.toString());
    }

    public static StringBuilder getGameInfo(OptionMapping gameOption, MapManager mapManager, Map map) {
        StringBuilder sb = new StringBuilder();
        if (map == null && gameOption == null){
            sb.append("Game not specified");
            return sb;
        } else if (map == null ){
            map = mapManager.getMap(gameOption.getAsString());
        }
        sb.append("Game Info:").append(NEW_LINE);
        sb.append("Game name: " + map.getName()).append(NEW_LINE);

        sb.append("Game owner: " + map.getOwnerName()).append(NEW_LINE);
        sb.append("Game status: " + map.getMapStatus()).append(NEW_LINE);
        sb.append("Game player count: " + map.getPlayerCountForMap()).append(NEW_LINE);
        sb.append("Game Display type count: " + (map.getDisplayTypeForced() != null ? map.getDisplayTypeForced().getValue() : DisplayType.all.getValue())).append(NEW_LINE);
        sb.append("Players: ").append(NEW_LINE);
        HashMap<String, Player> players = map.getPlayers();
        int index = 1;
        ArrayList<Player> playerNames = new ArrayList<>(players.values());
        for (Player value : playerNames) {
            sb.append(index).append(". ").append(value.getUserName()).append(NEW_LINE);
            index++;
        }
        return sb;
    }
}
