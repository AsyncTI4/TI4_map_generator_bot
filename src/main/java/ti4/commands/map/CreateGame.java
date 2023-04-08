package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateGame implements Command {


    @Override
    public String getActionID() {
        return Constants.CREATE_GAME;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        String regex = "^[a-zA-Z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mapName);
        if (!matcher.matches()){
            MessageHelper.replyToMessage(event, "Game name can only contain a-z 0-9 symbols");
            return false;
        }
        if (MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Game with such name exist already, choose different name");
            return false;
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        Member member = event.getMember();
        createNewGame(event, mapName, member);
        MessageHelper.replyToMessage(event, "Game created with name: " + mapName);
    }

    public Map createNewGame(SlashCommandInteractionEvent event, String mapName, Member gameOwner) {
        Map map = new Map();
        String ownerID = gameOwner.getId();
        map.setOwnerID(ownerID);
        map.setOwnerName(gameOwner.getEffectiveName());
        map.setName(mapName);

        MapManager mapManager = MapManager.getInstance();
        mapManager.addMap(map);
        boolean setMapSuccessful = mapManager.setMapForUser(ownerID, mapName);
        map.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        if (!setMapSuccessful) {
            MessageHelper.replyToMessage(event, "Could not assign active Game " + mapName);
        }
        MapSaveLoadManager.saveMap(map);
        return map;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Shows selected Game")
                        .addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setRequired(true))
        );
    }
}
