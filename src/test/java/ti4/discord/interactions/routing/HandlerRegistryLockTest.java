package ti4.discord.interactions.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.listeners.context.ButtonContext;
import ti4.testUtils.BaseTi4Test;

/**
 * The executor picks a READ or WRITE game lock from {@link HandlerRegistry#isSave} before the interaction context
 * strips the faction-ownership prefix. These tests pin that the lock decision sees the same handler id that dispatch
 * does, so {@code @ButtonHandler(save = false)} keeps its read lock when the button is faction-prefixed.
 */
class HandlerRegistryLockTest extends BaseTi4Test {

    @Test
    void factionPrefixedIdUsesTheHandlersSaveFlag() {
        HandlerRegistry<ButtonContext> registry = new HandlerRegistry<>();
        registry.register("readOnly_", context -> {}, false);
        registry.register("writes_", context -> {}, true);

        assertThat(registry.isSave("FFCC_hacan_readOnly_1")).isFalse();
        assertThat(registry.isSave("FFCC_pi_hacan_readOnly_1")).isFalse();
        assertThat(registry.isSave("dummyPlayerSpoofpi_hacan_readOnly_1")).isFalse();
        assertThat(registry.isSave("FFCC_hacan_readOnly_1deleteThisMessage")).isFalse();
        assertThat(registry.isSave("FFCC_hacan_writes_1")).isTrue();
    }

    @Test
    void unknownIdStillTakesTheWriteLock() {
        HandlerRegistry<ButtonContext> registry = new HandlerRegistry<>();
        registry.register("readOnly_", context -> {}, false);

        assertThat(registry.isSave("FFCC_hacan_somethingElse")).isTrue();
    }

    @Test
    void everyReadOnlyButtonHandlerKeepsItsReadLockWhenFactionPrefixed() {
        HandlerRegistry<ButtonContext> registry =
                AnnotationHandler.buildHandlerRegistry(ButtonContext.class, ButtonHandler.class);

        List<String> wronglyWriteLocked = new ArrayList<>();
        for (String key : readOnlyButtonHandlerKeys()) {
            for (String prefix : List.of("FFCC_hacan_", "FFCC_pi_hacan_", "dummyPlayerSpoofnekro_")) {
                if (registry.isSave(prefix + key)) wronglyWriteLocked.add(prefix + key);
            }
        }

        assertThat(readOnlyButtonHandlerKeys()).isNotEmpty();
        assertThat(wronglyWriteLocked).isEmpty();
    }

    // These used to be switch cases in ButtonProcessor; with no registered route, isSave defaulted to true and
    // a pure view took the WRITE lock and a full game save.
    @Test
    void formerLegacyViewButtonsTakeTheReadLock() {
        HandlerRegistry<ButtonContext> registry =
                AnnotationHandler.buildHandlerRegistry(ButtonContext.class, ButtonHandler.class);

        List<String> viewButtons = List.of(
                "searchMyGames",
                "refreshInfoButtons",
                "checkWHView",
                "checkAnomView",
                "checkLegendView",
                "checkEmptyView",
                "checkAetherView",
                "checkCannonView",
                "checkTraitView",
                "checkTechSkipView",
                "checkAttachmView",
                "checkShiplessView",
                "checkUnlocked");

        assertThat(viewButtons).noneMatch(registry::isSave);
    }

    private static List<String> readOnlyButtonHandlerKeys() {
        List<String> keys = new ArrayList<>();
        for (Class<?> klass : AnnotationHandler.getAllClasses()) {
            for (Method method : klass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                for (ButtonHandler handler : method.getAnnotationsByType(ButtonHandler.class)) {
                    if (!handler.save()) keys.add(handler.value());
                }
            }
        }
        return keys;
    }
}
