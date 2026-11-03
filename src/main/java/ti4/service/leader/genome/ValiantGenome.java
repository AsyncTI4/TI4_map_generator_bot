package ti4.service.leader.genome;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperActionCards;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public class ValiantGenome extends Genome<Player> {

    static final String ID = "valiantagent";
    private static final int TWILIGHTS_FALL_UNITS_TO_DESTROY = 1;

    private final int unitsToDestroy;

    public ValiantGenome() {
        this(ID, "Valiant Genome", TWILIGHTS_FALL_UNITS_TO_DESTROY);
    }

    public ValiantGenome(String leaderId, String genomeName, int unitsToDestroy) {
        super(leaderId, genomeName);
        this.unitsToDestroy = unitsToDestroy;
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", please choose the recently deceased unit that you wish to use the genome on."
                                + destructionReminder(),
                        ButtonHelperActionCards.getCourageousOptions(target, use.game(), true, "courageous")),
                use.noteToUser(target, " has no recently destroyed units to use the genome on."));
    }

    private String destructionReminder() {
        if (unitsToDestroy == TWILIGHTS_FALL_UNITS_TO_DESTROY) {
            return "";
        }
        return " If the roll hits, their opponent must choose and destroy " + unitsToDestroy + " of their units.";
    }
}
