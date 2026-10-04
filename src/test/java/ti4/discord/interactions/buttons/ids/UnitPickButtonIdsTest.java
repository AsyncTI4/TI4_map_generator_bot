package ti4.discord.interactions.buttons.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ti4.discord.interactions.buttons.ids.UnitPickButtonIds.ASSIGN_DAMAGE;
import static ti4.discord.interactions.buttons.ids.UnitPickButtonIds.ASSIGN_HITS;
import static ti4.discord.interactions.buttons.ids.UnitPickButtonIds.REPAIR_DAMAGE;
import static ti4.discord.interactions.buttons.ids.UnitPickButtonIds.TACTICAL_MOVE;
import static ti4.discord.interactions.buttons.ids.UnitPickButtonIds.TACTICAL_REMOVE;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.ids.UnitPickButtonIds.BulkCommand;
import ti4.discord.interactions.buttons.ids.UnitPickButtonIds.Parsed;
import ti4.discord.interactions.buttons.ids.UnitPickButtonIds.ParsedBulk;
import ti4.discord.interactions.routing.AnnotationHandlerTestAccess;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class UnitPickButtonIdsTest extends BaseTi4Test {

    private static UnitKey key(UnitType type, String color) {
        return Units.getUnitKey(type, color);
    }

    @Test
    void formatsTheLegacyWireShapeForEveryAction() {
        // Buttons already posted in Discord use these exact strings, so the format must not drift.
        UnitKey redDestroyer = key(UnitType.Destroyer, "red");
        assertThat(UnitPickButtonIds.format(ASSIGN_HITS, "101", 2, redDestroyer, UnitState.none, null, false))
                .isEqualTo("assignHits_101_2_dd_red");
        assertThat(UnitPickButtonIds.format(ASSIGN_DAMAGE, "101", 1, redDestroyer, UnitState.none, null, false))
                .isEqualTo("assignDamage_101_1_dd_red");
        assertThat(UnitPickButtonIds.format(REPAIR_DAMAGE, "101", 1, redDestroyer, UnitState.none, null, false))
                .isEqualTo("repairDamage_101_1_dd_red");
        assertThat(UnitPickButtonIds.format(TACTICAL_MOVE, "101", 1, redDestroyer, UnitState.none, null, false))
                .isEqualTo("unitTacticalMove_101_1_dd_red");
        assertThat(UnitPickButtonIds.format(TACTICAL_REMOVE, "101", 1, redDestroyer, UnitState.none, null, false))
                .isEqualTo("unitTacticalRemove_101_1_dd_red");
    }

    @Test
    void formatsEveryOptionalSegmentCombinationInLegacyOrder() {
        // Order: action, position, amount, unit, [state], [planet], color, [reverse].
        UnitKey orangeMech = key(UnitType.Mech, "orange");
        assertThat(UnitPickButtonIds.format(ASSIGN_DAMAGE, "tl", 1, orangeMech, UnitState.dmg, null, false))
                .isEqualTo("assignDamage_tl_1_mf_dmg_orange");
        assertThat(UnitPickButtonIds.format(ASSIGN_HITS, "tl", 1, orangeMech, UnitState.none, "jord", false))
                .isEqualTo("assignHits_tl_1_mf_jord_orange");
        assertThat(UnitPickButtonIds.format(REPAIR_DAMAGE, "tl", 1, orangeMech, UnitState.glv, "jord", false))
                .isEqualTo("repairDamage_tl_1_mf_glv_jord_orange");
        assertThat(UnitPickButtonIds.format(TACTICAL_MOVE, "tl", 2, orangeMech, UnitState.dmg_glv, "jord", true))
                .isEqualTo("unitTacticalMove_tl_2_mf_dmg_glv_jord_orange_reverse");
        assertThat(UnitPickButtonIds.format(TACTICAL_MOVE, "tl", 1, orangeMech, UnitState.none, null, true))
                .isEqualTo("unitTacticalMove_tl_1_mf_orange_reverse");
    }

    @Test
    void formatWritesTheColorNameNotTheColorAlias() {
        // "org" is orange's colour id; posted ids have always carried the resolved colour name.
        UnitKey orangeFighter = key(UnitType.Fighter, "org");
        assertThat(UnitPickButtonIds.format(ASSIGN_HITS, "101", 1, orangeFighter, UnitState.none, null, false))
                .isEqualTo("assignHits_101_1_ff_orange");
    }

    @Test
    void formatsTheLegacyBulkWireShapes() {
        assertThat(UnitPickButtonIds.formatBulk(ASSIGN_HITS, "101", BulkCommand.ALL))
                .isEqualTo("assignHits_101_All");
        assertThat(UnitPickButtonIds.formatBulk(ASSIGN_HITS, "101", BulkCommand.ALL_SHIPS))
                .isEqualTo("assignHits_101_AllShips");
        assertThat(UnitPickButtonIds.formatBulk(TACTICAL_MOVE, "101", BulkCommand.MOVE_ALL))
                .isEqualTo("unitTacticalMove_101_moveAll");
        assertThat(UnitPickButtonIds.formatBulk(TACTICAL_MOVE, "101", BulkCommand.REVERSE_ALL))
                .isEqualTo("unitTacticalMove_101_reverseAll");
        assertThat(UnitPickButtonIds.formatBulk(TACTICAL_REMOVE, "101", BulkCommand.REMOVE_ALL))
                .isEqualTo("unitTacticalRemove_101_removeAll");
        assertThat(UnitPickButtonIds.formatBulk(TACTICAL_REMOVE, "101", BulkCommand.REMOVE_ALL_SHIPS))
                .isEqualTo("unitTacticalRemove_101_removeAllShips");
    }

    @Test
    void bulkCommandsAreTiedToTheirActions() {
        assertThatThrownBy(() -> UnitPickButtonIds.formatBulk(ASSIGN_HITS, "101", BulkCommand.MOVE_ALL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.formatBulk(TACTICAL_MOVE, "101", BulkCommand.ALL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(UnitPickButtonIds.tryParseBulk(ASSIGN_HITS, "assignHits_101_moveAll"))
                .isEmpty();
        assertThat(UnitPickButtonIds.tryParseBulk(TACTICAL_REMOVE, "unitTacticalRemove_101_All"))
                .isEmpty();
    }

    @Test
    void roundTripsEveryActionUnitStatePlanetAndSuffixCombination() {
        String[] planets = {null, "jord", "my_custom_planet"};
        for (String action : UnitPickButtonIds.ACTIONS) {
            for (UnitType type : UnitType.values()) {
                for (UnitState state : UnitState.values()) {
                    for (String planet : planets) {
                        for (boolean reverse : new boolean[] {false, true}) {
                            UnitKey unitKey = key(type, "lightgray");
                            String id = UnitPickButtonIds.format(action, "305", 2, unitKey, state, planet, reverse);

                            Parsed parsed = UnitPickButtonIds.parse(action, id);

                            assertThat(parsed.action()).as(id).isEqualTo(action);
                            assertThat(parsed.position()).as(id).isEqualTo("305");
                            assertThat(parsed.amount()).as(id).isEqualTo(2);
                            assertThat(parsed.unitType()).as(id).isEqualTo(type);
                            assertThat(parsed.state()).as(id).isEqualTo(state);
                            assertThat(parsed.planetName()).as(id).isEqualTo(planet);
                            assertThat(parsed.onPlanet()).as(id).isEqualTo(planet != null);
                            assertThat(parsed.color()).as(id).isEqualTo("lightgray");
                            assertThat(parsed.reverse()).as(id).isEqualTo(reverse);
                        }
                    }
                }
            }
        }
    }

    @Test
    void roundTripsEveryBulkCommand() {
        for (String action : UnitPickButtonIds.ACTIONS) {
            for (BulkCommand command : BulkCommand.values()) {
                if (!command.appliesTo(action)) continue;

                ParsedBulk parsed =
                        UnitPickButtonIds.parseBulk(action, UnitPickButtonIds.formatBulk(action, "br", command));

                assertThat(parsed.action()).isEqualTo(action);
                assertThat(parsed.position()).isEqualTo("br");
                assertThat(parsed.command()).isEqualTo(command);
            }
        }
    }

    @Test
    void parsesTheIdTheHandlerReceivesAfterEnvelopeDecoding() {
        // Project Pi factions contain an underscore, so the envelope must strip "FFCC_pi_hacan_" whole.
        String payload = UnitPickButtonIds.format(
                ASSIGN_HITS, "201", 1, key(UnitType.Infantry, "blue"), UnitState.none, "jord", false);
        String raw = ComponentIdEnvelope.ownedBy("pi_hacan") + payload + "deleteThisButton";

        Parsed parsed = UnitPickButtonIds.parse(
                ASSIGN_HITS, ComponentIdEnvelope.decode(raw).handlerId());

        assertThat(parsed.position()).isEqualTo("201");
        assertThat(parsed.unitType()).isEqualTo(UnitType.Infantry);
        assertThat(parsed.planetName()).isEqualTo("jord");
        assertThat(parsed.color()).isEqualTo("blue");
    }

    @Test
    void parsesDummyPlayerSpoofedIdsAfterEnvelopeDecoding() {
        String payload = UnitPickButtonIds.format(
                TACTICAL_MOVE, "102", 1, key(UnitType.Carrier, "green"), UnitState.dmg, null, true);
        String raw = ComponentIdEnvelope.spoofedAs("pi_letnev") + payload;

        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode(raw);
        Parsed parsed = UnitPickButtonIds.parse(TACTICAL_MOVE, envelope.handlerId());

        assertThat(envelope.spoofedFaction()).isEqualTo("pi_letnev");
        assertThat(parsed.unitType()).isEqualTo(UnitType.Carrier);
        assertThat(parsed.state()).isEqualTo(UnitState.dmg);
        assertThat(parsed.reverse()).isTrue();
    }

    @Test
    void parsesBulkIdsAfterEnvelopeDecoding() {
        String raw = ComponentIdEnvelope.ownedBy("pi_mentak")
                + UnitPickButtonIds.formatBulk(ASSIGN_HITS, "101", BulkCommand.ALL_SHIPS);

        ParsedBulk parsed = UnitPickButtonIds.parseBulk(
                ASSIGN_HITS, ComponentIdEnvelope.decode(raw).handlerId());

        assertThat(parsed.position()).isEqualTo("101");
        assertThat(parsed.command()).isEqualTo(BulkCommand.ALL_SHIPS);
    }

    @Test
    void acceptsIdsPostedBeforeTheColorSegmentWasAdded() {
        // Before June 2025 the id ended at the unit, state or planet; the old regex made colour optional.
        Parsed bare = UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_2_dd");
        assertThat(bare.unitType()).isEqualTo(UnitType.Destroyer);
        assertThat(bare.color()).isNull();
        assertThat(bare.planetName()).isNull();

        Parsed onPlanet = UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_1_gf_jord");
        assertThat(onPlanet.planetName()).isEqualTo("jord");
        assertThat(onPlanet.color()).isNull();

        Parsed damaged = UnitPickButtonIds.parse(REPAIR_DAMAGE, "repairDamage_101_1_dn_dmg_glv");
        assertThat(damaged.state()).isEqualTo(UnitState.dmg_glv);
        assertThat(damaged.color()).isNull();

        Parsed reversed = UnitPickButtonIds.parse(TACTICAL_MOVE, "unitTacticalMove_101_1_dd_reverse");
        assertThat(reversed.reverse()).isTrue();
        assertThat(reversed.color()).isNull();
    }

    @Test
    void acceptsTheUnitSpellingsTheOldRegexAccepted() {
        // The old regex accepted each unit's async id, its plain name, and "csd".
        assertThat(UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_1_destroyer_red")
                        .unitType())
                .isEqualTo(UnitType.Destroyer);
        assertThat(UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_1_csd_jord_red")
                        .unitType())
                .isEqualTo(UnitType.Spacedock);
        assertThat(UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_+1_ff_red")
                        .amount())
                .isEqualTo(1);
    }

    @Test
    void stateDefaultsToNoneWhenAbsent() {
        Parsed parsed = UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_1_dd_red");

        assertThat(parsed.state()).isEqualTo(UnitState.none);
        assertThat(parsed.hasState()).isFalse();
    }

    @Test
    void rejectsIdsItDoesNotOwn() {
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, "assignDamage_101_1_dd_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(TACTICAL_MOVE, "unitTacticalRemove_101_1_dd_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, "assignHitsX_101_1_dd_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, "FFCC_hacan_assignHits_101_1_dd_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_All"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_two_dd_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101_1_notaunit_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, "assignHits_101__dd_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parse(ASSIGN_HITS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parseBulk(ASSIGN_HITS, "assignHits_101_1_dd_red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitPickButtonIds.parseBulk(ASSIGN_HITS, "autoAssignSpaceHits_101_3"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hitAssignmentPositionReadsOnlyAssignHitsAndAssignDamageIds() {
        assertThat(UnitPickButtonIds.hitAssignmentPosition("assignHits_101_1_dd_red"))
                .contains("101");
        assertThat(UnitPickButtonIds.hitAssignmentPosition("assignHits_tl_All")).contains("tl");
        assertThat(UnitPickButtonIds.hitAssignmentPosition("assignDamage_305_1_dn_dmg_red"))
                .contains("305");
        assertThat(UnitPickButtonIds.hitAssignmentPosition("repairDamage_101_1_dn_red"))
                .isEmpty();
        assertThat(UnitPickButtonIds.hitAssignmentPosition("unitTacticalMove_101_1_dd_red"))
                .isEmpty();
        assertThat(UnitPickButtonIds.hitAssignmentPosition("autoAssignSpaceHits_101_3"))
                .isEmpty();
    }

    @Test
    void everyActionIsRoutedToARegisteredButtonHandler() {
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
                .contains("assignHits_", "assignDamage_", "repairDamage_", "unitTacticalMove", "unitTacticalRemove");
        for (String action : UnitPickButtonIds.ACTIONS) {
            assertThat(registered.stream().anyMatch(key -> (action + "_").startsWith(key)))
                    .as(action)
                    .isTrue();
        }
        assertThat(UnitPickButtonIds.ACTIONS)
                .containsExactlyInAnyOrder(ASSIGN_HITS, ASSIGN_DAMAGE, REPAIR_DAMAGE, TACTICAL_MOVE, TACTICAL_REMOVE);
    }
}
