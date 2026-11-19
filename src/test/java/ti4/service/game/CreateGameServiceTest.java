package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.settings.users.UserSettings;
import ti4.spring.service.usersettings.UserSettingsService;

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

    @Test
    void checkingTheCreationLockDoesNotStartTheCooldown() {
        Map<String, UserSettings> stored = new HashMap<>();
        UserSettingsService service = mock(UserSettingsService.class);
        when(service.find(anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0, String.class))));
        doAnswer(call -> {
                    UserSettings settings = call.getArgument(0);
                    stored.put(settings.getUserId(), settings);
                    return null;
                })
                .when(service)
                .save(any(UserSettings.class));
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn("creation-lock-user");
        when(event.getUser()).thenReturn(user);

        try (MockedStatic<UserSettingsService> serviceBean = mockStatic(UserSettingsService.class)) {
            serviceBean.when(UserSettingsService::getBean).thenReturn(service);

            // A press that fails validation (e.g. a player at their game limit) only checks the lock.
            assertThat(CreateGameService.isLockedFromCreatingGames(event)).isFalse();
            assertThat(CreateGameService.isLockedFromCreatingGames(event)).isFalse();

            // Only a launch that went through starts the 10 minute cooldown.
            CreateGameService.lockFromCreatingGames(event);
            assertThat(CreateGameService.isLockedFromCreatingGames(event)).isTrue();
        }
    }
}
