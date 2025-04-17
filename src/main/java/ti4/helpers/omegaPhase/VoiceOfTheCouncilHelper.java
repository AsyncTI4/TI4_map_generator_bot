package ti4.helpers.omegaPhase;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class VoiceOfTheCouncilHelper {
    public static void ResetVoiceOfTheCouncil(Game game) {
        //Clean everything in case it got put somewhere weird
        game.getLaws().remove(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getLawsInfo().remove(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getDiscardAgendas().remove(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getAgendas().remove(Constants.VOICE_OF_THE_COUNCIL_ID);

        game.addLaw(Constants.VOICE_OF_THE_COUNCIL_ID, null);

        if (game.getCustomPublicVP().containsKey(Constants.VOICE_OF_THE_COUNCIL_ID)) {
            game.removeCustomPO(Constants.VOICE_OF_THE_COUNCIL_ID);
        }

        game.addCustomPO(Constants.VOICE_OF_THE_COUNCIL_PO, 1);
    }

    public static void ElectVoiceOfTheCouncil(Game game, Player player) {
        var lawID = game.getLaws().get(Constants.VOICE_OF_THE_COUNCIL_ID);
        var poID = game.getRevealedPublicObjectives().get(Constants.VOICE_OF_THE_COUNCIL_PO);
        if (lawID == null || poID == null) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Cannot elect Voice of the Council; missing Agenda or custom Public Ojbective. Consider running \"/omegaphase reset_voice_of_the_council\".");
            return;
        }

        var previousElectee = game.getLawsInfo().get(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getScoredPublicObjectives().remove(Constants.VOICE_OF_THE_COUNCIL_PO);
        game.reviseLaw(lawID, player.getFaction());
        game.scorePublicObjective(player.getUserID(), poID);

        StringBuilder sb = new StringBuilder();
        sb.append("**Voice of the Council**\n");
        if (previousElectee != null) {
            var previousPlayer = game.getPlayerFromColorOrFaction(previousElectee);
            sb.append(previousPlayer.getRepresentation()).append(" is no longer Voice of the Council.\n");
        }
        sb.append(player.getRepresentation()).append(" has been elected as Voice of the Council.");
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb.toString());
    }

    public static void RevealVoiceOfTheCouncil(Game game, GenericInteractionCreateEvent event) {
        if (!game.removeLaw(Constants.VOICE_OF_THE_COUNCIL_ID)) {
            game.revealAgenda(Constants.VOICE_OF_THE_COUNCIL_ID, true);
        }
        game.getScoredPublicObjectives().remove(Constants.VOICE_OF_THE_COUNCIL_PO);

        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.putAgendaBackIntoDeckOnTop(uniqueID);
        AgendaHelper.revealAgenda(event, false, game, event.getMessageChannel());
    }
}
