package ti4.image.playerarea;

import java.awt.Graphics2D;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.image.NeighbourMap;
import ti4.image.WebsiteOverlays;

public final class PlayerAreaContext {

    private final Graphics2D g2;
    private final Game game;
    private final boolean fogPrivate;
    private final Player viewer;
    private final WebsiteOverlays overlays;
    private final NeighbourMap neighbours;
    private final int scoreTokenSpacing;
    private final ReinforcementCounts reinforcementCounts = new ReinforcementCounts();

    private PlayerAreaContext(
            Graphics2D g2,
            Game game,
            boolean fogPrivate,
            @Nullable Player viewer,
            WebsiteOverlays overlays,
            NeighbourMap neighbours,
            int scoreTokenSpacing) {
        this.g2 = g2;
        this.game = game;
        this.fogPrivate = fogPrivate;
        this.viewer = viewer;
        this.overlays = overlays;
        this.neighbours = neighbours;
        this.scoreTokenSpacing = scoreTokenSpacing;
    }

    public static PlayerAreaContext of(
            Graphics2D g2,
            Game game,
            boolean fogPrivate,
            @Nullable Player viewer,
            WebsiteOverlays overlays,
            NeighbourMap neighbours,
            int scoreTokenSpacing) {
        return new PlayerAreaContext(g2, game, fogPrivate, viewer, overlays, neighbours, scoreTokenSpacing);
    }

    public static PlayerAreaContext publicView(Graphics2D g2, Game game, WebsiteOverlays overlays) {
        return of(g2, game, false, null, overlays, new NeighbourMap(game), 54);
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

    int scoreTokenSpacing() {
        return scoreTokenSpacing;
    }

    ReinforcementCounts reinforcementCounts() {
        return reinforcementCounts;
    }

    boolean hidesStatsOf(Player player) {
        return fogPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, viewer);
    }
}
