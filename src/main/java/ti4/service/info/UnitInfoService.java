package ti4.service.info;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class UnitInfoService {

    public static void sendUnitInfo(Game game, Player player, GenericInteractionCreateEvent event, boolean showAllUnits) {
        String headerText = "Somebody" + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendUnitInfo(player, showAllUnits);
    }

    public static void sendUnitInfo(Player player, boolean showAllUnits) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
            player.getCardsInfoThread(),
            "__**Unit Info:**__",
            getUnitMessageEmbeds(player, showAllUnits),
            getUnitInfoButtons());
    }

    private static List<Button> getUnitInfoButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_UNIT_INFO);
        buttons.add(Buttons.REFRESH_ALL_UNIT_INFO);
        return buttons;
    }

    public static List<MessageEmbed> getUnitMessageEmbeds(Player player, boolean includeAllUnits) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        List<String> unitList = new ArrayList<>();
        if (includeAllUnits) {
            unitList.addAll(player.getUnitsOwned());
        } else {
            unitList.addAll(player.getSpecialUnitsOwned());
        }
        for (UnitModel unitModel : unitList.stream().sorted().map(Mapper::getUnit).toList()) {
            MessageEmbed unitRepresentationEmbed = unitModel.getRepresentationEmbed(false);
            messageEmbeds.add(unitRepresentationEmbed);
        }
        return messageEmbeds;
    }
}
