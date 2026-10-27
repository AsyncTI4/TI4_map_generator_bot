package ti4.service.leader.agent.modules;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SpendAgent;

public final class WinnuAgent extends SpendAgent {

    static final String ID = "winnuagent";

    public WinnuAgent() {
        super(ID, "Berekar Berekon, the Winnu");
    }

    public static Button button() {
        return Buttons.red(AgentButtonIds.format(ID), "Use Winnu Agent", FactionEmojis.Winnu);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(user);
        }
        return AgentTargets.player(game, payload);
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
