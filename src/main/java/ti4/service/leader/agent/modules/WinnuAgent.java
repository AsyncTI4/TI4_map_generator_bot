package ti4.service.leader.agent.modules;

import java.util.Optional;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class WinnuAgent implements AgentModule<Player> {

    public static final String ID = "winnuagent";

    public static String buttonId() {
        return AgentButtonIds.format(ID);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Berekar Berekon, the Winnu";
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrSelf(game, user, payload);
    }

    @Override
    public String exhaustAnnouncement(AgentUse<Player> use) {
        return use.user().getRepresentation() + " has exhausted " + use.agentName() + " to use on "
                + use.payload().getRepresentationNoPing() + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player spender = use.payload();
        spender.addSpentThing(ID);
        if (spender != use.user()) {
            return AgentOutcome.none();
        }
        return AgentOutcome.none().withPressedMessageEdit(Helper.buildSpentThingsMessage(spender, use.game(), "res"));
    }
}
