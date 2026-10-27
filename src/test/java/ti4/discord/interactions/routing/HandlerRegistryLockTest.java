package ti4.discord.interactions.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.listeners.context.ButtonContext;
import ti4.testUtils.BaseTi4Test;

/**
 * The executor picks a READ or WRITE game lock from {@link HandlerRegistry#resolve} before the interaction context
 * strips the faction-ownership prefix. These tests pin that the lock decision sees the same handler id that dispatch
 * does, so {@code @ButtonHandler(save = false)} keeps its read lock when the button is faction-prefixed.
 */
class HandlerRegistryLockTest extends BaseTi4Test {

    @Test
    void factionPrefixedIdUsesTheHandlersSaveFlag() {
        HandlerRegistry<ButtonContext> registry = new HandlerRegistry<>();
        registry.register("readOnly_", context -> {}, false);
        registry.register("writes_", context -> {}, true);

        assertThat(registry.resolve("FFCC_hacan_readOnly_1").shouldSave()).isFalse();
        assertThat(registry.resolve("FFCC_pi_hacan_readOnly_1").shouldSave()).isFalse();
        assertThat(registry.resolve("dummyPlayerSpoofpi_hacan_readOnly_1").shouldSave())
                .isFalse();
        assertThat(registry.resolve("FFCC_hacan_readOnly_1deleteThisMessage").shouldSave())
                .isFalse();
        assertThat(registry.resolve("FFCC_hacan_writes_1").shouldSave()).isTrue();
    }

    @Test
    void unknownIdStillTakesTheWriteLock() {
        HandlerRegistry<ButtonContext> registry = new HandlerRegistry<>();
        registry.register("readOnly_", context -> {}, false);

        assertThat(registry.resolve("FFCC_hacan_somethingElse").shouldSave()).isTrue();
    }

    @Test
    void everyReadOnlyButtonHandlerKeepsItsReadLockWhenFactionPrefixed() {
        HandlerRegistry<ButtonContext> registry =
                AnnotationHandler.buildHandlerRegistry(ButtonContext.class, ButtonHandler.class);

        List<String> wronglyWriteLocked = new ArrayList<>();
        for (String key : readOnlyButtonHandlerKeys()) {
            for (String prefix : List.of("FFCC_hacan_", "FFCC_pi_hacan_", "dummyPlayerSpoofnekro_")) {
                if (registry.resolve(prefix + key).shouldSave()) wronglyWriteLocked.add(prefix + key);
            }
        }

        assertThat(readOnlyButtonHandlerKeys()).isNotEmpty();
        assertThat(wronglyWriteLocked).isEmpty();
    }

    // These used to be switch cases in ButtonProcessor; with no registered route, a pure view defaulted to the
    // WRITE lock and a full game save.
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
                "checkUnlocked",
                "getSwapButtons_");

        assertThat(viewButtons).noneMatch(id -> registry.resolve(id).shouldSave());
    }

    // The last startsWith branches and switch cases in ButtonProcessor always saved. Now that they are
    // @ButtonHandlers, each sample id must reach the key that replaced its branch and keep the WRITE lock.
    @Test
    void formerLegacyStateChangingButtonsRouteToTheirHandlerAndTakeTheWriteLock() {
        HandlerRegistry<ButtonContext> registry =
                AnnotationHandler.buildHandlerRegistry(ButtonContext.class, ButtonHandler.class);

        Map<String, String> expectedKeyById = new LinkedHashMap<>();
        expectedKeyById.put("so_score_hand_12", "so_score_hand_");
        expectedKeyById.put("FFCC_hacan_po_scoring_3", "po_scoring_");
        expectedKeyById.put("generic_button_id_1234567890", "generic_button_id_");
        expectedKeyById.put("FFCC_hacan_strategicAction_4", "strategicAction_");
        for (String id : List.of(
                "gain_1_comms",
                "gain_2_comms",
                "gain_3_comms",
                "gain_4_comms",
                "gain_1_comms_stay",
                "gain_2_comms_stay",
                "gain_3_comms_stay",
                "gain_4_comms_stay",
                "convert_1_comms",
                "convert_2_comms",
                "convert_3_comms",
                "convert_4_comms",
                "convert_2_comms_stay",
                "play_when",
                "gain_1_tg",
                "gain1tgFromLetnevCommander",
                "gain1tgFromMuaatCommander",
                "gain1tgFromCommander",
                "resolveHarness",
                "pass_on_abilities")) {
            expectedKeyById.put(id, id);
        }

        expectedKeyById.forEach((id, expectedKey) -> {
            HandlerRegistry.Route<ButtonContext> route = registry.resolve(id);
            assertThat(route.key()).as(id).isEqualTo(expectedKey);
            assertThat(route.shouldSave()).as(id).isTrue();
        });
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
