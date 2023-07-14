package ti4.commands.player;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class UnitInfo extends PlayerSubcommandData {
    public UnitInfo() {
		super(Constants.UNIT_INFO, "Send unit information to your Cards Info channel");
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
        sendUnitInfo(activeMap, player, event);
    }

    public static void sendUnitInfo(Map activeMap, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeMap) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sendUnitInfo(activeMap, player);
    }

    public static void sendUnitInfo(Map activeMap, Player player, ButtonInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeMap) + " pressed `" + event.getButton().getId() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sendUnitInfo(activeMap, player);
    }

    public static void sendUnitInfo(Map activeMap, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeMap, player, getUnitMessageEmbeds(player));
    }

    private static List<MessageEmbed> getUnitMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<MessageEmbed>();

        for (UnitModel unitModel : player.getUnitsOwned().stream().sorted().map(unitID -> Mapper.getUnit(unitID)).toList()) { // Mapper.getUnits().values().stream().sorted(Comparator.comparing(UnitModel::getId)).toList()) {
            MessageEmbed unitRepresentationEmbed = unitModel.getUnitRepresentationEmbed(false);
            messageEmbeds.add(unitRepresentationEmbed);
        }
        return messageEmbeds;
    }

}
