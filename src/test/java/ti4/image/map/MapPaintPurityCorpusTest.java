package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.helpers.RenderRandom;
import ti4.image.FakeImageNetwork;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.FogEvents;
import ti4.testUtils.GameStateSnapshot;

/**
 * Runs the paint purity check on real game saves. Skipped unless -Dgolden.saves points at a folder of saves, so CI and
 * a clean checkout never need them and none are committed. -Dgolden.purity.games=a,b overrides the games checked.
 */
@EnabledIfSystemProperty(named = "golden.saves", matches = ".+")
class MapPaintPurityCorpusTest extends BaseTi4Test {

    private static final List<String> DEFAULT_GAMES = List.of("fow593", "fow241b", "pbd26808", "pbd4206", "pbd28368");
    private static final List<DisplayType> DISPLAY_TYPES = List.of(DisplayType.all, DisplayType.stats);

    @Test
    void paintingSavedGamesChangesNothingAndSendsNothing() throws Exception {
        Path saves = Path.of(System.getProperty("golden.saves"));
        String configured = System.getProperty("golden.purity.games");
        List<String> names = configured == null ? DEFAULT_GAMES : List.of(configured.split(","));
        int checked = 0;
        try (FakeImageNetwork network = FakeImageNetwork.notFound()) {
            for (String name : names) {
                File save = saves.resolve(name + ".txt").toFile();
                if (!Files.exists(save.toPath())) continue;
                checked++;
                for (DisplayType type : DISPLAY_TYPES) {
                    assertPaintIsPure(save, type, false);
                    assertPaintIsPure(save, type, true);
                }
            }
        }
        assertTrue(checked > 0, "none of the requested games exist in golden.saves");
    }

    private static void assertPaintIsPure(File save, DisplayType type, boolean privateView) throws Exception {
        Game game = load(save);
        GenericInteractionCreateEvent event = null;
        if (privateView) {
            if (!game.isFowMode() || game.getRealPlayers().isEmpty()) return;
            String userId = game.getRealPlayers().getFirst().getUserID();
            event = FogEvents.eventIn(userId, "fog-" + userId + "-private");
        }
        try (AutoCloseable mode = RenderRandom.neutral()) {
            RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, type, event, null));
            settleFactionEmoji(game);
            List<String> stateBefore = GameStateSnapshot.saveText(game);
            String fogBefore = GameStateSnapshot.fogFlags(game);

            try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class, Mockito.CALLS_REAL_METHODS)) {
                MapRenderer.paint(context, RenderTimings.disabled());

                messages.verifyNoInteractions();
            }

            String label = game.getName() + " " + type + (privateView ? " private" : " public");
            assertEquals(List.of(), GameStateSnapshot.changes(stateBefore, GameStateSnapshot.saveText(game)), label);
            assertEquals(fogBefore, GameStateSnapshot.fogFlags(game), label);
        }
    }

    // Without application emoji ids a replaced custom faction emoji is not parseable, so only a second normalization
    // reaches the value production reaches after one.
    private static void settleFactionEmoji(Game game) {
        game.getPlayers().values().forEach(Player::normalizeFactionEmoji);
        game.getPlayers().values().forEach(Player::normalizeFactionEmoji);
    }

    private static Game load(File save) throws Exception {
        Class<?> loader = Class.forName("ti4.game.persistence.GameLoadService");
        Method readGame = loader.getDeclaredMethod("readGame", File.class);
        readGame.setAccessible(true);
        return (Game) readGame.invoke(null, save);
    }
}
