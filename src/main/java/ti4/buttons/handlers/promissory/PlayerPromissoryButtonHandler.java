package ti4.buttons.handlers.promissory;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.metadata.TechSummariesMetadataManager;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class PlayerPromissoryButtonHandler {

    @ButtonHandler("resolvePNPlay_")
    public static void resolvePNPlay(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pnID = buttonID.replace("resolvePNPlay_", "");

        if (pnID.contains("ra_")) {
            String tech = AliasHandler.resolveTech(pnID.replace("ra_", ""));
            TechnologyModel techModel = Mapper.getTech(tech);
            pnID = pnID.replace("_" + tech, "");
            String message = player.getFactionEmojiOrColor() + " Acquired The Tech " + techModel.getRepresentation(false) + " via Research Agreement";
            player.addTech(tech);
            TechSummariesMetadataManager.addTech(game, player, tech, true);
            ButtonHelperCommanders.resolveNekroCommanderCheck(player, tech, game);
            CommanderUnlockCheckService.checkPlayer(player, "jolnar", "nekro", "mirveda", "dihmohn");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
        PromissoryNoteHelper.resolvePNPlay(pnID, player, game, event);
        if (!"bmfNotHand".equalsIgnoreCase(pnID)) {
            ButtonHelper.deleteMessage(event);
        }

        var possibleCombatMod = CombatTempModHelper.getPossibleTempModifier(Constants.PROMISSORY_NOTES, pnID, player.getNumberTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Combat modifier will be applied next time you push the combat roll button.");
        }
    }
}
