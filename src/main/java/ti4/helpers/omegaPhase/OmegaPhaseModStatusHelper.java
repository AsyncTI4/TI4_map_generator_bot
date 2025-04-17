package ti4.helpers.omegaPhase;

import ti4.map.Game;
import ti4.message.MessageHelper;

public class OmegaPhaseModStatusHelper {
    public static void PrintGreeting(Game game) {
        String greeting = "### " + game.getPing() + " **Omega Phase v0.1**\n\n" +
            "Omega Phase has been enabled! This overhaul will apply several changes to the game, including:\n" +
            "- A Priority Track which is used to determine the non-Initiative order of play\n" +
            "- A new Agenda \"Voice of the Council\" which the Speaker can choose to resolve after the second agenda is resolved\n" +
            "- Everyone must exhaust a planet to cast votes if they have any available during every agenda\n" +
            "- Everyone but the Speaker votes simultaneously, without knowing how others will vote. Then the Speaker votes and proceeds to resolve the Agenda.\n" +
            "- Scoring happens at the end of each round, after the Status and Agenda phases\n" +
            "- Objectives have been revamped, and are all worth 1 VP. The Speaker peeks at the next Objective when a new one is revealed after scoring.\n\n" +
            "This mod is in alpha, and you'll have to use some workarounds for the time being.\n" +
            "- Voting is currently skipped, skipping straight to buttons to resolve the Agenda. Players should privately message the Speaker which planets they will exhaust, and the Speaker will reveal all player's exhausted planets together when all voting players have done so. At this time, players may use relevant abilities that apply when voting. Players should sum their planets' votes and then note their actual final vote tally after all effects are accounted for. Then the Speaker votes, and uses the appropriate button to resolve the correct outcome.\n"
            +
            "- After electing a new Voice of the Council, use `/omegaphase elect_voice_of_the_council` to elect them and update the points.\n";
        MessageHelper.sendMessageToChannelAndPin(game.getActionsChannel(), greeting);
    }
}
