package ti4.service.leader.agent.modules;

import java.util.Optional;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class AugersAgent implements AgentModule<Player> {

    public static final String ID = "augersagent";
    private static final int TRADE_GOODS_GAINED = 2;

    public static String buttonId(Player explorer) {
        return AgentButtonIds.format(ID, explorer.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Clodho, the Ilyxum";
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        int oldTg = target.getTg();
        target.setTg(oldTg + TRADE_GOODS_GAINED);

        AgentOutcome outcome = AgentOutcome.of(Message.to(
                target,
                target.getFactionEmojiOrColor() + " gained " + TRADE_GOODS_GAINED + " trade goods from "
                        + use.agentName() + ", being used (" + oldTg + "->" + target.getTg() + ")."));
        if (use.game().isFowMode()) {
            outcome = outcome.and(Message.to(
                    use.user(),
                    target.getFactionEmojiOrColor() + " gained " + TRADE_GOODS_GAINED
                            + " trade goods due to agent usage."));
        }

        ButtonHelperAbilities.pillageCheck(target, use.game());
        ButtonHelperAgents.resolveArtunoCheck(target, TRADE_GOODS_GAINED);
        return outcome;
    }
}
