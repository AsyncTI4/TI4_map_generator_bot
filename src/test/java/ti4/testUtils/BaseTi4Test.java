package ti4.testUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.managers.Presence;
import org.junit.jupiter.api.BeforeAll;
import ti4.discord.JdaService;
import ti4.discord.interactions.selections.SelectionManager;
import ti4.game.persistence.GameManager;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.service.emoji.ApplicationEmojiService;

/**
 * Base test class for all Ti4 tests. Allows for proper global config.
 */
public class BaseTi4Test {

    private static final int GLOBAL_BEFORE_ALL_WAIT_THRESHOLD_SECONDS = 30;
    private static final CountDownLatch setupCountDownLatch = new CountDownLatch(1);
    private static final AtomicBoolean setupStarted = new AtomicBoolean(false);
    private static final JDA jda = mock(JDA.class);

    @BeforeAll
    public static void beforeAll() throws InterruptedException {
        if (setupStarted.compareAndSet(false, true)) {
            globalBeforeAll();
        }
        if (!setupCountDownLatch.await(GLOBAL_BEFORE_ALL_WAIT_THRESHOLD_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Setup timed out");
        }
    }

    private static void globalBeforeAll() {
        // Use this to turn off random chance things that may impact testing
        // and reroute all logging to the console
        JdaService.testingMode = true;
        JdaService.jda = jda;
        when(jda.getPresence()).thenReturn(mock(Presence.class));

        // This is set when running tests within docker. However, this must be manually
        // set when running tests within vs code for resources to be loaded properly.
        if (System.getenv(Storage.ENV_VAR_RESOURCE_PATH) == null) {
            Storage.setResourcePath("./src/main/resources");
        }

        // Init base static data
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
        SelectionManager.init();
        ApplicationEmojiService.spoofEmojis();

        GameManager.warmup();
        setupCountDownLatch.countDown();
    }
}
