package ti4.ai.runtime;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;

@UtilityClass
class AiProgress {

    static int fingerprint(Game game, Player seat) {
        return Objects.hash(
                game.getRound(),
                game.getPhaseOfGame(),
                game.getActivePlayerID(),
                game.getPlayedSCs(),
                new TreeMap<>(game.getStoredValueMap()),
                game.getTacticalActionDisplacement(),
                unitPositions(game, seat),
                seat.getTg(),
                seat.getCommodities(),
                seat.getTacticalCC(),
                seat.getFleetCC(),
                seat.getStrategicCC(),
                seat.getSCs(),
                seat.getFollowedSCs(),
                seat.isPassed(),
                seat.getSecretsUnscored().keySet(),
                seat.getActionCards().keySet(),
                seat.getPromissoryNotes().keySet(),
                seat.getTechs(),
                seat.getPlanets(),
                seat.getExhaustedPlanets(),
                seat.getCurrentProducedUnits(),
                List.copyOf(seat.getTransactionItems()));
    }

    private static int unitPositions(Game game, Player seat) {
        int hash = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey key : holder.getUnitKeysForPlayer(seat)) {
                    hash += Objects.hash(tile.getPosition(), holder.getName(), key, holder.getUnitCount(key));
                }
            }
        }
        return hash;
    }
}
