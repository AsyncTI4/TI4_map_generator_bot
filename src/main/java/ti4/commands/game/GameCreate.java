package ti4.commands.game;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class GameCreate extends GameSubcommandData {

    public GameCreate() {
        super(Constants.CREATE_GAME, "Create a new game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        Member member = event.getMember();

        String regex = "^[a-zA-Z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mapName);
        if (!matcher.matches()){
            MessageHelper.replyToMessage(event, "Game name can only contain a-z 0-9 symbols");
            return;
        }
        if (MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Game with such name exist already, choose different name");
            return;
        }

        createNewGame(event, mapName, member);
        MessageHelper.replyToMessage(event, "Game created with name: " + mapName);
    }

    public static Map createNewGame(SlashCommandInteractionEvent event, String mapName, Member gameOwner) {
        Map newMap = new Map();
        String ownerID = gameOwner.getId();
        newMap.setOwnerID(ownerID);
        newMap.setOwnerName(gameOwner.getEffectiveName());
        newMap.setName(mapName);

        MapManager mapManager = MapManager.getInstance();
        mapManager.addMap(newMap);
        boolean setMapSuccessful = mapManager.setMapForUser(ownerID, mapName);
        newMap.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        if (!setMapSuccessful) {
            MessageHelper.replyToMessage(event, "Could not assign active Game " + mapName);
        }
        MapSaveLoadManager.saveMap(newMap, event);
        return newMap;
    }
    
}
