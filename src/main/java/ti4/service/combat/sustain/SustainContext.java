package ti4.service.combat.sustain;

import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.model.UnitModel;

public record SustainContext(Game game, Player owner, Tile tile, UnitHolder holder, HitSource hitSource) {

    public static SustainContext of(Game game, Player owner, Tile tile, UnitHolder holder, HitSource hitSource) {
        UnitHolder resolvedHolder = holder == null && tile != null ? tile.getSpaceUnitHolder() : holder;
        return new SustainContext(game, owner, tile, resolvedHolder, hitSource == null ? HitSource.ABILITY : hitSource);
    }

    public boolean inSpace() {
        return holder == null || Constants.SPACE.equals(holder.getName());
    }

    public UnitKey unitKeyOf(UnitModel unit) {
        return Units.getUnitKey(unit.getUnitType(), owner.getColorID());
    }
}
