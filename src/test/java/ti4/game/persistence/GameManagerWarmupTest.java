package ti4.game.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

class GameManagerWarmupTest {

    private boolean originalTestingMode;

    @BeforeEach
    void beforeEach() throws Exception {
        originalTestingMode = JdaService.testingMode;
        resetState();
        JdaService.testingMode = false;
    }

    @AfterEach
    void afterEach() throws Exception {
        resetState();
        JdaService.testingMode = originalTestingMode;
    }

    @Test
    void initializeDoesNotStartWarmupWithoutLease() throws Exception {
        ActiveLeaseService activeLeaseService = mock(ActiveLeaseService.class);
        when(activeLeaseService.mayMutate()).thenReturn(false);

        try (MockedStatic<GameLoadService> gameLoadService = Mockito.mockStatic(GameLoadService.class);
                MockedStatic<SpringContext> springContext = Mockito.mockStatic(SpringContext.class);
                MockedStatic<ExecutorServiceManager> executorServiceManager =
                        Mockito.mockStatic(ExecutorServiceManager.class)) {
            gameLoadService.when(GameLoadService::loadGameNames).thenReturn(List.of("pbd1000"));
            springContext
                    .when(() -> SpringContext.getBean(ActiveLeaseService.class))
                    .thenReturn(activeLeaseService);

            GameManager.initialize();

            executorServiceManager.verify(
                    () -> ExecutorServiceManager.runAsync(
                            ArgumentMatchers.anyString(), ArgumentMatchers.any(Runnable.class)),
                    never());
        }
    }

    @SuppressWarnings("unchecked")
    private void resetState() throws Exception {
        ((Set<String>) getField("validGameNames")).clear();
        ((Map<?, ?>) getField("gameNameToManagedGame")).clear();
        ((Map<?, ?>) getField("userIdToManagedPlayer")).clear();
        ((AtomicBoolean) getField("GAME_NAMES_INDEXED")).set(false);
        ((AtomicBoolean) getField("WARMUP_STARTED")).set(false);
    }

    private Object getField(String fieldName) throws Exception {
        Field field = GameManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }
}
