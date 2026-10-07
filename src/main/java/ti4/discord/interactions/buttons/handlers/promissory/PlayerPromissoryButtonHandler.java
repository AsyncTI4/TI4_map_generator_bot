package ti4.discord.interactions.buttons.handlers.promissory;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.metadata.TechSummariesMetadataManager;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class PlayerPromissoryButtonHandler {

    @ButtonHandler("resolvePNPlay_")
    public static void resolvePNPlay(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pnID = buttonID.replace("resolvePNPlay_", "");
        String tech = null;
        if (pnID.contains("ra_")) {
            tech = AliasHandler.resolveTech(pnID.replace("ra_", ""));
            pnID = pnID.replace("_" + tech, "");
        }

        if (!canPlayFromHand(player, pnID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "You can't play this promissory note: it has already been played, is no longer in your hand,"
                            + " or is your own.");
            return;
        }

        if (tech != null) {
            TechnologyModel techModel = Mapper.getTech(tech);
            String message = player.getRepresentationNoPing() + " acquired the technology "
                    + techModel.getRepresentation(false) + " via _Research Agreement_.";
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

        var possibleCombatMod = CombatTempModHelper.getPossibleTempModifier(
                Constants.PROMISSORY_NOTES, pnID, player.getNumberOfTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "Combat modifier will be applied next time you push the \"Combat Roll\" button.");
        }
    }

    private static boolean canPlayFromHand(Player player, String pnID) {
        String cardID = "bmfNotHand".equalsIgnoreCase(pnID) ? "bmf" : pnID;
        return player.hasPlayablePromissoryInHand(cardID)
                && !player.getPromissoryNotesInPlayArea().contains(cardID);
    }
}
