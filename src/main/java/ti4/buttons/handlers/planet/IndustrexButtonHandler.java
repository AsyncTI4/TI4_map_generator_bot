package ti4.buttons.handlers.planet;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.planet.IndustrexService;
import ti4.service.regex.RegexService;

@UtilityClass
public class IndustrexButtonHandler {

    @ButtonHandler("industrexPickType_")
    public static void pickUnitType(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Pattern regex = Pattern.compile("industrexPickType_" + RegexHelper.unitTypeRegex());
        RegexService.runMatcher(regex, buttonID, matcher -> {
            UnitType type = Units.findUnitType(matcher.group("unittype"));

            String msg = "Choose a tile in which to place your " + type.getUnitTypeEmoji();
            List<Button> buttons = IndustrexService.getIndustrexButtonsPart2(game, player, type);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            ButtonHelper.deleteMessage(event);
        });
    }
}
