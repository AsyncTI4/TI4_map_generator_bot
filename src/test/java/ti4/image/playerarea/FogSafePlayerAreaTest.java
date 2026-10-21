package ti4.image.playerarea;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.map.MapFrame;
import ti4.model.Mapper;
import ti4.service.fow.FogViewer;
import ti4.testUtils.BaseTi4Test;
import ti4.website.model.WebsiteOverlay;

class FogSafePlayerAreaTest extends BaseTi4Test {

    private Game game;
    private Player holder;
    private Player owner;
    private String factionNote;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("fog-safe-player-area-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        holder = seat("p1", "mahact", "blue");
        owner = seat("p2", "hacan", "yellow");
        factionNote = Mapper.getFaction("hacan").getPromissoryNotes().getFirst();
        owner.addOwnedPromissoryNoteByID(factionNote);
        holder.addPromissoryNoteToPlayArea(factionNote);
        holder.addMahactCC(owner.getColor());
    }

    private Player seat(String userID, String faction, String color) {
        Player player = game.addPlayer(userID, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    private List<WebsiteOverlay> drawHolderArea(boolean fogPrivate) {
        BufferedImage image = new BufferedImage(3000, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        WebsiteOverlays overlays = new WebsiteOverlays();
        PlayerAreaContext ctx = PlayerAreaContext.of(
                graphics, game, new FogViewer(game, fogPrivate, holder), overlays, new NeighbourMap(game));
        PlayerAreas.drawSingle(ctx, holder, new Point(5, 5), 3000);
        graphics.dispose();
        return overlays.asList();
    }

    // The holder's own area is drawn because the viewer is the holder; only the note's owner is out of sight.
    private List<WebsiteOverlay> drawHolderAreaWithOwnerHidden() {
        try (MockedStatic<FoWHelper> fog = mockStatic(FoWHelper.class, Mockito.CALLS_REAL_METHODS)) {
            fog.when(() -> FoWHelper.canSeeStatsOfPlayerInCurrentFog(any(), eq(owner), any()))
                    .thenReturn(false);
            return drawHolderArea(true);
        }
    }

    private static boolean hasModelOverlay(List<WebsiteOverlay> overlays, String modelID) {
        return overlays.stream().anyMatch(overlay -> modelID.equals(overlay.getDataModelID()));
    }

    private static boolean hasTitledOverlay(List<WebsiteOverlay> overlays, String title) {
        return overlays.stream().anyMatch(overlay -> title.equals(overlay.getTitle()));
    }

    @Test
    void publicViewNamesTheFactionNoteAndBorrowedCommander() {
        List<WebsiteOverlay> overlays = drawHolderArea(false);

        assertTrue(hasModelOverlay(overlays, factionNote));
        assertTrue(hasModelOverlay(overlays, "hacancommander"));
    }

    @Test
    void hiddenOwnerKeepsFactionNoteAnonymous() {
        List<WebsiteOverlay> overlays = drawHolderAreaWithOwnerHidden();

        assertFalse(hasModelOverlay(overlays, factionNote));
        assertTrue(hasTitledOverlay(overlays, "Promissory Note"));
    }

    @Test
    void hiddenOwnerKeepsImperiaCommanderAnonymous() {
        List<WebsiteOverlay> overlays = drawHolderAreaWithOwnerHidden();

        assertFalse(hasModelOverlay(overlays, "hacancommander"));
        assertTrue(hasTitledOverlay(overlays, "Commander"));
    }
}
