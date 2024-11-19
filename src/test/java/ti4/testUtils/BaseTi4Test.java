package ti4.testUtils;

import org.junit.jupiter.api.BeforeAll;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;

/**
 * Base test class for all Ti4 tests. Allows for proper global config.
 */
public class BaseTi4Test {
    private static boolean isFirstRun = true;

    /**
     * Logic which is ran once at the start of the entire test suit (before any test class is ran).
     */
    private static void globalBeforeAll() {
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
    }

    /**
     * Logic which is ran before each individual test class.
     */
    @BeforeAll
    public static void beforeAll() {
        if (isFirstRun) {
            // Not safe if we ever run tests in parallel but we prob never will.
            isFirstRun = false;
            globalBeforeAll();
        }
    }
}
