package ti4.service.leader.agent.modules.twilightsfall;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.JolNarAgent;

public final class ExperimentalGenomeAgent extends AgentModule<Player> {

    static final String ID = "experimentalagent";

    public ExperimentalGenomeAgent() {
        super(ID, "Experimental Genome");
    }

    public static Button button(Player player) {
        return Buttons.gray(
                ownedButtonId(player, ID), useLabel(player, ID, "Experimental Genome"), FactionEmojis.Jolnar);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(JolNarAgent.offerInfantryRemoval(use.game(), use.payload()));
    }
}
