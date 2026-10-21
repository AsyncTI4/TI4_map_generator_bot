package ti4.image.playerarea;

import java.awt.Graphics2D;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.image.draw.WebsiteOverlays;

public final class PlayerAreaContext {

    private final Graphics2D g2;
    private final Game game;
    private final boolean fogPrivate;
    private final Player viewer;
    private final WebsiteOverlays overlays;
    private final NeighbourMap neighbours;
    private final ReinforcementCounts reinforcementCounts = new ReinforcementCounts();
    private final PlayerAreaWarnings warnings = PlayerAreaWarnings.none();

    private PlayerAreaContext(
            Graphics2D g2,
            Game game,
            boolean fogPrivate,
            @Nullable Player viewer,
            WebsiteOverlays overlays,
            NeighbourMap neighbours) {
        this.g2 = g2;
        this.game = game;
        this.fogPrivate = fogPrivate;
        this.viewer = viewer;
        this.overlays = overlays;
        this.neighbours = neighbours;
    }

    public static PlayerAreaContext of(
            Graphics2D g2,
            Game game,
            boolean fogPrivate,
            @Nullable Player viewer,
            WebsiteOverlays overlays,
            NeighbourMap neighbours) {
        return new PlayerAreaContext(g2, game, fogPrivate, viewer, overlays, neighbours);
    }

    public static PlayerAreaContext publicView(Graphics2D g2, Game game, WebsiteOverlays overlays) {
        return of(g2, game, false, null, overlays, new NeighbourMap(game));
    }

    Graphics2D g2() {
        return g2;
    }

    Game game() {
        return game;
    }

    boolean fogPrivate() {
        return fogPrivate;
    }

    @Nullable
    Player viewer() {
        return viewer;
    }

    WebsiteOverlays overlays() {
        return overlays;
    }

    NeighbourMap neighbours() {
        return neighbours;
    }

    ReinforcementCounts reinforcementCounts() {
        return reinforcementCounts;
    }

    public PlayerAreaWarnings warnings() {
        return warnings;
    }

    boolean hidesStatsOf(Player player) {
        return fogPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, viewer);
    }
}
