package ti4.service.combat;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.model.UnitModel;

public final class CombatRollPipelineState {
    final Player player;
    final Game game;
    final GenericInteractionCreateEvent event;
    final Tile tile;
    final String unitHolderName;
    final CombatRollType rollType;
    final boolean automated;
    UnitHolder combatOnHolder;
    Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits;
    Player opponent;
    CombatRollModifiers modifiers;
    String bombardPlanet = "";
    CombatRollResult rollResult;
    String message;
    CombatRollPayload payload;
    int opponentRound;
    int playerRound;
    int hits;
    CombatRollStatus stoppedStatus;

    public CombatRollPipelineState(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated) {
        this.player = player;
        this.game = game;
        this.event = event;
        this.tile = tile;
        this.unitHolderName = unitHolderName;
        this.rollType = rollType;
        this.automated = automated;
    }

    public void setCombatOnHolder(UnitHolder combatOnHolder) {
        this.combatOnHolder = combatOnHolder;
    }

    public void setPlayerUnits(Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits) {
        this.playerUnits = playerUnits;
    }

    public void setPlayerUnitsByModel(Map<UnitModel, Integer> playerUnits, UnitHolder unitHolder) {
        this.playerUnits = new java.util.HashMap<>();
        playerUnits.forEach((model, count) -> this.playerUnits.put(Pair.of(model, unitHolder), count));
    }

    public void setOpponent(Player opponent) {
        this.opponent = opponent;
    }

    public void setModifiers(CombatRollModifiers modifiers) {
        this.modifiers = modifiers;
    }

    void stop(CombatRollStatus status) {
        stoppedStatus = status;
    }

    boolean isStopped() {
        return stoppedStatus != null;
    }
}
