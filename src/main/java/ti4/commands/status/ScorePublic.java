package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class ScorePublic extends StatusSubcommandData {
    public ScorePublic() {
        super(Constants.SCORE_OBJECTIVE, "Score Public Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.PO_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Public Objective to score");
            return;
        }

        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        int poID = option.getAsInt();
        boolean scored = activeMap.scorePublicObjective(player.getUserID(), poID);
        if (!scored) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Public Objective ID found, please retry");
        } else {
            LinkedHashMap<String, Integer> revealedPublicObjectives = activeMap.getRevealedPublicObjectives();
            String id = "";
            for (java.util.Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
                if (po.getValue().equals(poID)) {
                    id = po.getKey();
                    break;
                }
            }
            HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesState1();
            HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesState2();
            String poName1 = publicObjectivesState1.get(id);
            String poName2 = publicObjectivesState2.get(id);
            String poName = id;
            if (poName1 != null) {
                poName = poName1;
            } else if (poName2 != null){
                poName = poName2;
            }
            String message = Helper.getPlayerPing(event, player) + "(" + player.getColor() + ") scored: " + poName;
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap);
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
