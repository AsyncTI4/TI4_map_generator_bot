package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Freelancers lets the explorer produce one unit in the system, paying with influence as if it were resources. A ship
// built in a system with no dock of the seat's saves the trip from home, so that is where it pays off.
class FreelancersRulesTest extends BaseTi4Test {

    private static final String UNIT_PREFIX = "FFCC_nekro_placeOneNDone_dontskipfreelancers_";

    private AiTestGame test;
    private Tile forward;
    private String position;

    // Nekro holds Tequran (2 resources) and Torkan (3 influence) in a system away from its dock, besides its home.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        forward = test.place("28", position);
        test.nekro.addPlanet("tequran");
        test.nekro.addPlanet("torkan");
        test.units(forward, "tequran", test.nekro, UnitType.Infantry, 1);
        test.units(forward, "torkan", test.nekro, UnitType.Infantry, 1);
        test.game.setRound(8);
        test.aiIsActive("action");
    }

    // With money to spare, the best ship for the system is a carrier (the AI wants two), and the extra system it
    // saves a trip to makes it worth building. It builds the carrier and pays for it.
    @Test
    void buildsAShipInAForwardSystem() {
        assertThat(pressedId(ExplorationRules.next(test.context(offer())).orElseThrow()))
                .isEqualTo("freelancersBuild_tequran");

        AiPrompt units = units();
        assertThat(pressedId(ExplorationRules.next(test.context(units)).orElseThrow()))
                .isEqualTo(UNIT_PREFIX + "carrier_" + position);
        assertThat(PaymentRules.isPending(test.context())).isTrue();
    }

    // Influence pays as resources, so the 3-influence Torkan covers the carrier at a lower price than a resource
    // planet: that is the planet the AI exhausts.
    @Test
    void paysWithTheSparestInfluencePlanet() {
        ExplorationRules.next(test.context(offer()));
        ExplorationRules.next(test.context(units()));

        AiPrompt payment = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW + 10,
                List.of(
                        "spend_tequran_freelancers",
                        "spend_torkan_freelancers",
                        "spend_mordaiii_freelancers",
                        "deleteButtons_placeOneNDone_dontskipfreelancers_carrier_" + position),
                List.of("Tequran", "Torkan", "Mordai II", "Done Exhausting Planets"));
        assertThat(pressedId(PaymentRules.pay(test.context(payment)).orElseThrow()))
                .isEqualTo("spend_torkan_freelancers");
    }

    // With every planet spent and no trade goods there is nothing to pay with, so the offer is declined.
    @Test
    void declinesWhenBroke() {
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);

        assertThat(pressedId(ExplorationRules.next(test.context(offer())).orElseThrow()))
                .isEqualTo("decline_explore");
    }

    // Three trade goods are all the seat has, and a carrier is worth more than that in a forward system.
    @Test
    void paysWithTradeGoodsWhenEveryPlanetIsSpent() {
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);
        test.nekro.setTg(3);

        assertThat(pressedId(ExplorationRules.next(test.context(offer())).orElseThrow()))
                .isEqualTo("freelancersBuild_tequran");
        ExplorationRules.next(test.context(units()));

        AiPrompt payment = AiTestGame.withContent(
                prompt(
                        "payment",
                        PromptSource.PUBLIC,
                        NOW + 10,
                        List.of(
                                "reduceTG_1_freelancers",
                                "deleteButtons_placeOneNDone_dontskipfreelancers_carrier_" + position),
                        List.of("Spend 1 Trade Good", "Done Exhausting Planets")),
                test.nekro.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust.");
        assertThat(pressedId(PaymentRules.pay(test.context(payment)).orElseThrow()))
                .isEqualTo("reduceTG_1_freelancers");
    }

    // In the home system a ship would be built at the dock anyway, so Freelancers must beat what the dock builds: a
    // plain destroyer for a resource is not worth the exhausted planet, and the offer is declined.
    @Test
    void declinesAPlainShipWhereThereIsADock() {
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Carrier, 2);
        AiPrompt homeOffer = AiTestGame.withContent(
                prompt(
                        "offer",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("freelancersBuild_mordaiii", "decline_explore"),
                        List.of("Produce 1 Unit", "Decline Exploration")),
                test.nekro.getRepresentation() + ", please resolve _Freelancers_.");
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);
        test.nekro.refreshPlanet("mordaiii");

        assertThat(pressedId(ExplorationRules.next(test.context(homeOffer)).orElseThrow()))
                .isEqualTo("decline_explore");
    }

    // Units cannot be produced on a planet with the Demilitarized Zone, so only the other planet offers ground forces.
    @Test
    void offersNoGroundForcesOnADemilitarizedPlanet() {
        forward.getUnitHolderFromPlanet("tequran").addToken("attachment_dmz.png");

        List<String> handlers = FreelancersRules.choices(test.context(), forward).stream()
                .map(choice -> choice.option().handler())
                .toList();

        assertThat(handlers).contains("infantry_torkan").doesNotContain("infantry_tequran", "mech_tequran");
    }

    private AiPrompt offer() {
        return AiTestGame.withContent(
                prompt(
                        "offer",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("freelancersBuild_tequran", "decline_explore"),
                        List.of("Produce 1 Unit", "Decline Exploration")),
                test.nekro.getRepresentation() + ", please resolve _Freelancers_.");
    }

    private AiPrompt units() {
        return prompt(
                "units",
                PromptSource.PUBLIC,
                NOW + 5,
                List.of(
                        UNIT_PREFIX + "carrier_" + position,
                        UNIT_PREFIX + "destroyer_" + position,
                        UNIT_PREFIX + "fighter_" + position,
                        UNIT_PREFIX + "infantry_tequran",
                        UNIT_PREFIX + "mech_tequran"),
                List.of());
    }
}
