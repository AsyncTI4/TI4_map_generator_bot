package ti4.commands.player;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class UnitInfo extends PlayerSubcommandData {
    public UnitInfo() {
		super(Constants.UNIT_INFO, "Send unit information to your Cards Info channel");
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
        sendUnitInfo(activeGame, player, event);
    }

    public static void sendUnitInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendUnitInfo(activeGame, player);
    }

    public static void sendUnitInfo(Game activeGame, Player player, ButtonInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame) + " pressed `" + event.getButton().getId() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendUnitInfo(activeGame, player);
    }

    public static void sendUnitInfo(Game activeGame, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, getUnitMessageEmbeds(player));
    }

    private static List<MessageEmbed> getUnitMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        for (UnitModel unitModel : player.getUnitsOwned().stream().sorted().map(Mapper::getUnit).toList()) { // Mapper.getUnits().values().stream().sorted(Comparator.comparing(UnitModel::getId)).toList()) {
            MessageEmbed unitRepresentationEmbed = unitModel.getUnitRepresentationEmbed(false);
            messageEmbeds.add(unitRepresentationEmbed);
        }
        return messageEmbeds;
    }

}
