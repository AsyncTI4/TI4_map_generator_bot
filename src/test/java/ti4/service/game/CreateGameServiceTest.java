package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;

class CreateGameServiceTest {

    private boolean originalTestingMode;

    @BeforeEach
    void routeLogsToConsole() {
        originalTestingMode = JdaService.testingMode;
        JdaService.testingMode = true;
    }

    @AfterEach
    void restoreTestingMode() {
        JdaService.testingMode = originalTestingMode;
    }

    @Test
    void awaitRoleAssignmentsReturnsMembersWhoseRoleAddFailedInsteadOfThrowing() {
        Game game = new Game();
        game.setName("pbd1");
        Member assigned = mock(Member.class);
        Member rejected = mock(Member.class);
        when(rejected.getEffectiveName()).thenReturn("rejected");

        Map<Member, CompletableFuture<Void>> assignments = new LinkedHashMap<>();
        assignments.put(assigned, CompletableFuture.completedFuture(null));
        assignments.put(rejected, CompletableFuture.failedFuture(new IllegalStateException("Missing Permissions")));

        // A failed role add must not abort launch: the channels and threads already exist by now.
        assertThat(CreateGameService.awaitRoleAssignments(assignments, game)).containsExactly(rejected);
    }

    @Test
    void awaitRoleAssignmentsWaitsForRoleAddsStillInFlight() {
        Game game = new Game();
        game.setName("pbd1");
        CompletableFuture<Void> inFlight = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> inFlight.complete(null));

        Map<Member, CompletableFuture<Void>> assignments = new LinkedHashMap<>();
        assignments.put(mock(Member.class), inFlight);

        // Launch pings players right after this returns, so every role add must have landed first.
        assertThat(CreateGameService.awaitRoleAssignments(assignments, game)).isEmpty();
        assertThat(inFlight).isDone();
    }
}
