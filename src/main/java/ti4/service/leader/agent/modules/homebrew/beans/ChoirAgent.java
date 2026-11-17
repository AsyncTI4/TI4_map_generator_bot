package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.Optional;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir.ChoirLeadersHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class ChoirAgent extends AgentModule<Player> {

    public static final String ID = "choiragent";

    public ChoirAgent() {
        super(ID, "Lyra of the Crypt");
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (!ChoirLeadersHandler.hasDamagedShips(game, target)) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no damaged ships to repair");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        ChoirLeadersHandler.offerLyraDamagedShipSelection(use.game(), use.user(), use.payload());
        return AgentOutcome.none();
    }
}
