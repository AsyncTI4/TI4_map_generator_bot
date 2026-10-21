package ti4.image.playerarea;

import java.awt.Graphics2D;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.WebsiteOverlays;
import ti4.service.fow.FogViewer;

public final class PlayerAreaContext {

    private final Graphics2D g2;
    private final Game game;
    private final FogViewer fogViewer;
    private final WebsiteOverlays overlays;
    private final NeighbourMap neighbours;
    private final ReinforcementCounts reinforcementCounts = new ReinforcementCounts();
    private final PlayerAreaWarnings warnings = PlayerAreaWarnings.none();

    private PlayerAreaContext(
            Graphics2D g2, Game game, FogViewer fogViewer, WebsiteOverlays overlays, NeighbourMap neighbours) {
        this.g2 = g2;
        this.game = game;
        this.fogViewer = fogViewer;
        this.overlays = overlays;
        this.neighbours = neighbours;
    }

    public static PlayerAreaContext of(
            Graphics2D g2, Game game, FogViewer fogViewer, WebsiteOverlays overlays, NeighbourMap neighbours) {
        return new PlayerAreaContext(g2, game, fogViewer, overlays, neighbours);
    }

    public static PlayerAreaContext publicView(Graphics2D g2, Game game, WebsiteOverlays overlays) {
        return of(g2, game, FogViewer.unfogged(game), overlays, new NeighbourMap(game));
    }

    Graphics2D g2() {
        return g2;
    }

    Game game() {
        return game;
    }

    boolean fogPrivate() {
        return fogViewer.privateView();
    }

    @Nullable
    Player viewer() {
        return fogViewer.privateView() ? fogViewer.player() : null;
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
        return fogViewer.privateView() && !fogViewer.canSeeStats(player);
    }
}
