package ti4.discord.interactions.buttons.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.AnnotationHandlerTestAccess;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ComponentIdEnvelope;

class ButtonIdCodecsTest {

    @Test
    void pillageFormatsTheLegacyWireShape() {
        // Buttons already posted in Discord use these exact strings, so the format must not drift.
        assertThat(PillageButtonIds.format("red", PillageButtonIds.Stage.UNCHECKED))
                .isEqualTo("pillage_red_unchecked");
        assertThat(PillageButtonIds.format("red", PillageButtonIds.Stage.TRADE_GOOD))
                .isEqualTo("pillage_red_checked");
        assertThat(PillageButtonIds.format("red", PillageButtonIds.Stage.COMMODITY))
                .isEqualTo("pillage_red_checkedcomm");
        assertThat(PillageButtonIds.formatDecline("red")).isEqualTo("declinePillage_red");
    }

    @Test
    void pillageRoundTripsEveryStage() {
        for (PillageButtonIds.Stage stage : PillageButtonIds.Stage.values()) {
            PillageButtonIds.Parsed parsed = PillageButtonIds.parse(PillageButtonIds.format("lightgray", stage));

            assertThat(parsed.targetColor()).isEqualTo("lightgray");
            assertThat(parsed.stage()).isEqualTo(stage);
        }
        assertThat(PillageButtonIds.parseDeclinedColor(PillageButtonIds.formatDecline("lightgray")))
                .isEqualTo("lightgray");
    }

    @Test
    void pillageParsesTheIdTheHandlerReceivesAfterEnvelopeDecoding() {
        String raw = ComponentIdEnvelope.ownedBy("mentak")
                + PillageButtonIds.format("blue", PillageButtonIds.Stage.COMMODITY)
                + "deleteThisMessage";

        PillageButtonIds.Parsed parsed =
                PillageButtonIds.parse(ComponentIdEnvelope.decode(raw).handlerId());

        assertThat(parsed.targetColor()).isEqualTo("blue");
        assertThat(parsed.stage()).isEqualTo(PillageButtonIds.Stage.COMMODITY);
    }

    @Test
    void autoAssignGroundHitsFormatsTheLegacyWireShapeAndRoundTrips() {
        assertThat(AutoAssignGroundHitsButtonIds.format("mecatolrex", 3))
                .isEqualTo("autoAssignGroundHits_mecatolrex_3");

        AutoAssignGroundHitsButtonIds.Parsed parsed =
                AutoAssignGroundHitsButtonIds.parse(AutoAssignGroundHitsButtonIds.format("mecatolrex", 3));

        assertThat(parsed.planetName()).isEqualTo("mecatolrex");
        assertThat(parsed.hits()).isEqualTo(3);
    }

    @Test
    void codecsRejectIdsTheyDoNotOwn() {
        assertThatThrownBy(() -> PillageButtonIds.parse("declinePillage_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AutoAssignGroundHitsButtonIds.parse("autoAssignGroundHits_mecatolrex"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyCodecPrefixIsRegisteredAsAButtonHandler() {
        Set<String> registered = new HashSet<>();
        for (Class<?> klass : AnnotationHandlerTestAccess.allHandlerClasses()) {
            for (Method method : klass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                for (ButtonHandler handler : method.getAnnotationsByType(ButtonHandler.class)) {
                    registered.add(handler.value());
                }
            }
        }

        assertThat(registered)
                .contains(
                        PillageButtonIds.PREFIX, PillageButtonIds.DECLINE_PREFIX, AutoAssignGroundHitsButtonIds.PREFIX);
    }
}
