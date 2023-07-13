package ti4.commands.tech;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TechExhaust extends TechAddRemove {
    public TechExhaust() {
        super(Constants.TECH_EXHAUST, "Exhaust Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        player.exhaustTech(techID);
        if(techID.equalsIgnoreCase("mi")){
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "getACFrom",null);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select who you would like to mageon.", buttons);
        }
        
        sendMessage(Helper.getPlayerRepresentation(player, getActiveMap()) + " exhausted tech: " + Helper.getTechRepresentation(techID));
    }
}
