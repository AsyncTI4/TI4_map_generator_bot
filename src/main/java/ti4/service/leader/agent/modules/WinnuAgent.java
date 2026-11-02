package ti4.service.leader.agent.modules;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
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

    public static Button button(Player winnu) {
        return Buttons.red(ownedButtonId(winnu, ID), useLabel(winnu, ID, "Winnu Agent"), FactionEmojis.Winnu);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
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
