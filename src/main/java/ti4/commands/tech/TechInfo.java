package ti4.commands.tech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

public class TechInfo extends TechSubcommandData {
    public TechInfo() {
        super(Constants.INFO, "Send tech information to your Cards Info channel");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        sendTechInfo(activeGame, player, event);
    }

    public static void sendTechInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendTechInfo(activeGame, player);
    }

    public static void sendTechInfo(Game activeGame, Player player) {
        //TECH INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getTechInfoText(player));

        //BUTTONS
        String exhaustTechMsg = "_ _\nClick a button below to exhaust an Action Card";
        List<Button> techButtons = getTechButtons(activeGame, player);
        if (techButtons != null && !techButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(exhaustTechMsg, techButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeGame);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    }

    private static List<Button> getTechButtons(Game activeGame, Player player) {
        return null;
    }

    private static String getTechInfoText(Player player) {
        List<String> playerTechs = player.getTechs();
        StringBuilder sb = new StringBuilder("__**Tech Info**__\n");
        if (playerTechs == null || playerTechs.isEmpty()) {
            sb.append("> No Techs");
            return sb.toString();
        }

        HashMap<String, TechnologyModel> techInfo = Mapper.getTechs();
        java.util.Map<String, List<String>> techsFiltered = new HashMap<>();
        for (String tech : playerTechs) {
            String techType = Mapper.getTechType(tech).toString().toLowerCase();
            List<String> techList = techsFiltered.get(techType);
            if (techList == null) {
                techList = new ArrayList<>();
            }
            techList.add(tech);
            techsFiltered.put(techType, techList);
        }

        for (java.util.Map.Entry<String, List<String>> entry : techsFiltered.entrySet()) {
            List<String> list = entry.getValue();
            list.sort((tech1, tech2) -> {
                TechnologyModel tech1Info = techInfo.get(tech1);
                TechnologyModel tech2Info = techInfo.get(tech2);
                return TechnologyModel.sortTechsByRequirements(tech1Info, tech2Info);
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
