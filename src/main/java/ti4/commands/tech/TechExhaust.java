package ti4.commands.tech;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TemporaryCombatModifierModel;

public class TechExhaust extends TechAddRemove {
    public TechExhaust() {
        super(Constants.TECH_EXHAUST, "Exhaust Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        exhaustTechAndResolve(event, getActiveGame(), player, techID);
        checkAndApplyCombatMods(event, player, techID);
    }

    private void exhaustTechAndResolve(GenericInteractionCreateEvent event, Game game, Player player, String techID) {
        player.exhaustTech(techID);
        if ("mi".equalsIgnoreCase(techID)) {
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "getACFrom", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select who you would like to Mageon.", buttons);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " exhausted tech: " + Mapper.getTech(techID).getRepresentation(false));
    }

    private void checkAndApplyCombatMods(GenericInteractionCreateEvent event, Player player, String techID) {
        TemporaryCombatModifierModel possibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.TECH, techID, player.getNumberTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Combat modifier will be applied next time you push the combat roll button.");
        }
    }
}
