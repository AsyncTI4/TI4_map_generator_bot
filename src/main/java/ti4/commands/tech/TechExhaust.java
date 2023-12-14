package ti4.commands.tech;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.AgendaHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TechExhaust extends TechAddRemove {
    public TechExhaust() {
        super(Constants.TECH_EXHAUST, "Exhaust Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        player.exhaustTech(techID);
        if ("mi".equalsIgnoreCase(techID)) {
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "getACFrom", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Select who you would like to mageon.", buttons);
        }

        var posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.TECH, techID,
                player.getNumberTurns());
        if (posssibleCombatMod != null) {
            player.addNewTempCombatMod(posssibleCombatMod);
            sendMessage("Combat modifier will be applied next time you push the combat roll button.");
        }

        sendMessage(player.getRepresentation() + " exhausted tech: " + Helper.getTechRepresentation(techID));
    }
}
