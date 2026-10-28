package ti4.service.leader.genome;

import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.GhostAgent;

public final class GhostGenome extends LeaningGenome<Player> {

    static final String ID = "ghostagent";

    public GhostGenome() {
        super(ID, "Enigmatic Genome", new GhostAgent());
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        if (game.isFowMode() && activatedSystemHasAWormholeAndIsNotAHome(game)) {
            game.setStoredValue(GhostAgent.ACTIVE_KEY, game.getActiveSystem());
        }
        return AgentOutcome.none();
    }

    private static boolean activatedSystemHasAWormholeAndIsNotAHome(Game game) {
        Set<String> wormholes = FoWHelper.getTileWHs(game, game.getActiveSystem());
        Tile active = game.getTileByPosition(game.getActiveSystem());
        return !wormholes.isEmpty() && (active == null || !active.isHomeSystem(game));
    }
}
