package ti4.commands.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TechInfo extends PlayerSubcommandData {
    public TechInfo() {
		super(Constants.TECH_INFO, "Send tech information to your Cards Info channel");
	}

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, Helper.getPlayerRepresentation(event, player));
        sendTechInfo(activeMap, player);
    }

    public static void sendTechInfo(Map activeMap, Player player) {
        //TECH INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getTechInfoText(player));

        //BUTTONS
        String exhaustTechMsg = "_ _\nClick a button below to exhaust an Action Card";
        List<Button> techButtons = getTechButtons(activeMap, player);
        if (techButtons != null && !techButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(exhaustTechMsg, techButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeMap);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    } 

    private static List<Button> getTechButtons(Map activeMap, Player player) {
        return null;
    }

    private static String getTechInfoText(Player player) {
        List<String> playerTechs = player.getTechs();
        StringBuilder sb = new StringBuilder("__**Tech Info**__\n");
        if (playerTechs == null || playerTechs.isEmpty()) {
            sb.append("> No Techs");
            return sb.toString();
        }

        HashMap<String, String[]> techInfo = Mapper.getTechsInfo();
        java.util.Map<String, List<String>> techsFiltered = new HashMap<>();
        for (String tech : playerTechs) {
            String techType = Mapper.getTechType(tech);
            List<String> techList = techsFiltered.get(techType);
            if (techList == null) {
                techList = new ArrayList<>();
            }
            techList.add(tech);
            techsFiltered.put(techType, techList);
        }
        for (java.util.Map.Entry<String, List<String>> entry : techsFiltered.entrySet()) {
            List<String> list = entry.getValue();
            list.sort(new Comparator<String>() {
                @Override
                public int compare(String tech1, String tech2) {
                    String[] tech1Info = techInfo.get(tech1);
                    String[] tech2Info = techInfo.get(tech2);
                    try {
                        int t1 = tech1Info.length >= 3 ? tech1Info[2].length() : 0;
                        int t2 = tech2Info.length >= 3 ? tech2Info[2].length() : 0;
                        return (t1 < t2) ? -1 : ((t1 == t2) ? (tech1Info[0].compareTo(tech2Info[0])) : 1);
                    } catch (Exception e) {
                        //do nothing
                    }
                    return 0;
                }
            });
        }

        for (List<String> techList : techsFiltered.values()) {
            for (String techID : techList) {
                sb.append(Helper.getTechRepresentationLong(techID));
            }
        }
        
        return sb.toString();
    }
}
