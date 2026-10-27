package ti4.service.leader.agent.modules;

import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class GhostAgent extends UntargetedAgent {

    static final String ID = "ghostagent";
    static final String ACTIVE_KEY = "ghostagent_active";

    public GhostAgent() {
        super(ID, "Emissary Taivra, the Creuss");
    }

    public static Button button(Player creuss) {
        return Buttons.gray(ownedButtonId(creuss, ID), useLabel(creuss, ID, "Creuss Agent"), FactionEmojis.Ghost);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        if (game.isFowMode()) {
            Set<String> wormholes = FoWHelper.getTileWHs(game, game.getActiveSystem());
            if (!wormholes.isEmpty() && !wormholes.contains(Constants.DELTA)) {
                game.setStoredValue(ACTIVE_KEY, game.getActiveSystem());
            }
        }
        return AgentOutcome.none();
    }
}
