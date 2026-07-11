package ti4.game;

import javax.annotation.Nullable;

/** A planet together with its containing tile and current controller, if any. */
public record PlanetLocation(
        Planet planet, Tile tile, @Nullable Player owner) {}
