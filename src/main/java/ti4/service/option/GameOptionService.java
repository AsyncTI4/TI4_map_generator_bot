package ti4.service.option;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.message.MessageHelper;

@UtilityClass
public class GameOptionService {

    public static void offerGameOptionButtons(MessageChannel channel) {
        List<Button> factionReactButtons = new ArrayList<>();
        factionReactButtons.add(Buttons.green("enableAidReacts", "Enable Faction Reactions"));
        factionReactButtons.add(Buttons.red("disableAidReacts", "No Faction Reactions"));
        MessageHelper.sendMessageToChannel(channel, "Enable to have the bot react to player messages with their faction emoji.", factionReactButtons);

        List<Button> hexBorderButtons = new ArrayList<>();
        hexBorderButtons.add(Buttons.green("showHexBorders_dash", "Dashed line"));
        hexBorderButtons.add(Buttons.blue("showHexBorders_solid", "Solid line"));
        hexBorderButtons.add(Buttons.red("showHexBorders_off", "Off (default)"));
        MessageHelper.sendMessageToChannel(channel, "Show borders around systems with player's ships.", hexBorderButtons);
    }
}
