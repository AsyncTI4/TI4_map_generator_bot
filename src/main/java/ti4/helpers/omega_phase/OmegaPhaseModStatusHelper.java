package ti4.helpers.omega_phase;

import ti4.map.Game;
import ti4.message.MessageHelper;

public class OmegaPhaseModStatusHelper {
    public static void PrintGreeting(Game game) {
        String greeting = "### " + game.getPing() + " **Omega Phase v1**\n\n" +
            "Omega Phase has been enabled! This overhaul will apply several changes to the game, including:\n" +
            "- When not in Strategy Card order, your turn happens in the order you last passed in, which is shown on the Priority Track.\n" +
            "- The Priority Track is also used for scoring order.\n" +
            "- A new Agenda \"Voice of the Council\" starts in the common play area. After the first 2 agendas fully resolve:\n" +
            "  - A. if VotC is neutral, it must be voted on now like a third agenda.\n" +
            "  - B. if VotC is held by a player, the Speaker may choose to initiate a re-vote on it.\n" +
            "- During every agenda, everyone MUST vote if they have any unexhausted voting planets left.\n" +
            "- Everyone but the Speaker votes simultaneously, without knowing how others will vote. Use the pre-vote buttons to do this.\n" +
            "- Scoring happens at the end of each round, after the other Status and Agenda steps\n" +
            "- Objectives have been revamped, and are all worth 1 VP. The Speaker peeks at the next Objective when a new one is revealed after scoring.";
        MessageHelper.sendMessageToChannelAndPin(game.getActionsChannel(), greeting);
    }
}
