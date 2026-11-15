package ti4.discord.interactions.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.listeners.context.ButtonContext;

class PanelHandlerGuardTest {

    // Panels whose every handler is admin/GM-only. The router runs the class's guard before each handler,
    // so a panel class without @GuardedBy would let anyone press its buttons.
    private static boolean isGuardedPanel(Class<?> klass) {
        return "ti4.service.tournament.panel".equals(klass.getPackageName())
                || ("ti4.service.fow.setup".equals(klass.getPackageName())
                        && klass.getSimpleName().startsWith("FowSetupPanel"));
    }

    private static boolean hasHandlers(Class<?> klass) {
        return Arrays.stream(klass.getDeclaredMethods()).anyMatch(PanelHandlerGuardTest::isHandler);
    }

    private static boolean isHandler(Method method) {
        return method.getAnnotationsByType(ButtonHandler.class).length > 0
                || method.getAnnotationsByType(ModalHandler.class).length > 0
                || method.getAnnotationsByType(SelectionHandler.class).length > 0;
    }

    @Test
    void everyPanelClassWithHandlersIsGuarded() {
        List<Class<?>> panelClasses = AnnotationHandler.getAllClasses().stream()
                .filter(PanelHandlerGuardTest::isGuardedPanel)
                .filter(PanelHandlerGuardTest::hasHandlers)
                .toList();

        assertThat(panelClasses).isNotEmpty();
        assertThat(panelClasses)
                .allSatisfy(klass -> assertThat(klass.getAnnotation(GuardedBy.class))
                        .as("%s needs @GuardedBy", klass.getName())
                        .isNotNull());
    }

    @Test
    void everyGuardOnARealHandlerClassCanBeCreated() {
        // A guard the router cannot create makes all of that class's buttons refuse every press.
        List<Class<?>> guarded = AnnotationHandler.getAllClasses().stream()
                .filter(klass -> klass.getAnnotation(GuardedBy.class) != null)
                .filter(PanelHandlerGuardTest::hasHandlers)
                .toList();

        assertThat(guarded).isNotEmpty();
        assertThat(guarded)
                .allSatisfy(klass -> assertThat(AnnotationHandler.guardOf(klass))
                        .as("guard of %s", klass.getName())
                        .isNotSameAs(AnnotationHandler.MISCONFIGURED)
                        .isNotSameAs(AnnotationHandler.ALLOW_ALL));
    }

    static int handlerCalls;

    static void recordingHandler() {
        handlerCalls++;
    }

    @Test
    void routerRunsTheGuardBeforeTheHandler() throws NoSuchMethodException {
        Method handler = PanelHandlerGuardTest.class.getDeclaredMethod("recordingHandler");
        ButtonContext context = mock(ButtonContext.class);
        handlerCalls = 0;

        AnnotationHandler.<ButtonContext>buildConsumer(handler, ctx -> List.of(), false, (event, game) -> false)
                .accept(context);
        assertThat(handlerCalls).as("refused press must not reach the handler").isZero();

        AnnotationHandler.<ButtonContext>buildConsumer(handler, ctx -> List.of(), false, (event, game) -> true)
                .accept(context);
        assertThat(handlerCalls).isEqualTo(1);
    }

    @Test
    void unguardedClassLetsEveryoneThrough() {
        assertThat(AnnotationHandler.guardOf(PanelHandlerGuardTest.class)).isSameAs(AnnotationHandler.ALLOW_ALL);
    }

    @Test
    void guardThatCannotBeCreatedFailsClosedForItsClassOnly() {
        // Throwing here would abort the whole handler scan and leave every button in the bot unrouted.
        HandlerGuard guard = AnnotationHandler.guardOf(NeedsArgs.class);

        assertThat(guard).isSameAs(AnnotationHandler.MISCONFIGURED);
        assertThat(guard.allows(null, null)).isFalse();
    }

    static final class ArgGuard implements HandlerGuard {
        ArgGuard(String unused) {}

        @Override
        public boolean allows(
                net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent event, ti4.game.Game game) {
            return true;
        }
    }

    @GuardedBy(ArgGuard.class)
    static final class NeedsArgs {}
}
