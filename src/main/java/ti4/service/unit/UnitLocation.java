package ti4.service.unit;

import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;

/** A positive quantity of one unit key at a particular board location. */
public record UnitLocation(Tile tile, UnitHolder unitHolder, UnitKey unitKey, int count) {}
