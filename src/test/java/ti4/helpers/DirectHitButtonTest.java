package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class DirectHitButtonTest extends BaseTi4Test {

    private static final String POSITION = "304";

    private AiTestGame test;
    private Tile tile;
    private ButtonInteractionEvent event;
    private MockedStatic<MessageHelper> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        tile = test.place("26", POSITION);
        test.game.setActiveSystem(POSITION);
        event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        messages = mockStatic(MessageHelper.class);
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private UnitKey solUnit(UnitType type) {
        return Units.getUnitKey(type, test.sol.getColor());
    }

    private void solShip(UnitType type, int count, int damaged) {
        test.units(tile, "space", test.sol, type, count);
        if (damaged > 0) tile.addUnitDamage("space", solUnit(type), damaged);
    }

    private List<Button> offeredButtons() {
        ButtonHelperActionCards.resolveDirectHit(test.nekro, test.game, event);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Button>> buttons = ArgumentCaptor.forClass(List.class);
        messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(any(), anyString(), buttons.capture()));
        return buttons.getValue();
    }

    private String idFor(UnitType type) {
        UnitKey key = solUnit(type);
        return test.nekro.factionButtonChecker() + "directHitOn_" + POSITION + "_" + key.unitName() + "_"
                + key.getColor();
    }

    private void destroyViaButton(UnitType type) {
        ButtonHelperActionCards.resolveDirectHitDestruction(
                test.nekro,
                test.game,
                event,
                idFor(type).substring(test.nekro.factionButtonChecker().length()));
    }

    private int count(UnitType type) {
        return tile.getUnitHolders().get("space").getUnitCount(solUnit(type));
    }

    @Test
    void offersEveryDamagedEnemyShipThatCanBeDirectHit() {
        solShip(UnitType.Flagship, 1, 1);
        solShip(UnitType.Dreadnought, 2, 1);
        solShip(UnitType.Cruiser, 1, 0);

        List<String> ids = offeredButtons().stream().map(Button::getCustomId).toList();

        assertThat(ids).containsExactlyInAnyOrder(idFor(UnitType.Flagship), idFor(UnitType.Dreadnought));
    }

    @Test
    void doesNotOfferAnUndamagedShip() {
        solShip(UnitType.Flagship, 1, 0);
        solShip(UnitType.Dreadnought, 1, 1);

        List<String> ids = offeredButtons().stream().map(Button::getCustomId).toList();

        assertThat(ids).containsExactly(idFor(UnitType.Dreadnought));
    }

    @Test
    void doesNotOfferADreadnoughtII() {
        test.sol.removeOwnedUnitByID("dreadnought");
        test.sol.addOwnedUnitByID("dreadnought2");
        solShip(UnitType.Dreadnought, 1, 1);

        ButtonHelperActionCards.resolveDirectHit(test.nekro, test.game, event);

        messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(any(), anyString(), anyList()), never());
    }

    @Test
    void doesNotOfferTheOwnersOwnShips() {
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 1);
        tile.addUnitDamage("space", Units.getUnitKey(UnitType.Dreadnought, test.nekro.getColor()), 1);

        ButtonHelperActionCards.resolveDirectHit(test.nekro, test.game, event);

        messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(any(), anyString(), anyList()), never());
    }

    @Test
    void theButtonDestroysTheChosenDamagedShip() {
        solShip(UnitType.Flagship, 1, 1);
        solShip(UnitType.Dreadnought, 2, 1);

        destroyViaButton(UnitType.Dreadnought);

        assertThat(count(UnitType.Dreadnought)).isEqualTo(1);
        assertThat(tile.getUnitHolders().get("space").getDamagedUnitCount(solUnit(UnitType.Dreadnought)))
                .isZero();
        assertThat(count(UnitType.Flagship)).isEqualTo(1);
    }

    @Test
    void destroysTheFlagshipAndLeavesTheRestOfTheFleet() {
        solShip(UnitType.Flagship, 1, 1);
        solShip(UnitType.Destroyer, 2, 0);

        destroyViaButton(UnitType.Flagship);

        assertThat(count(UnitType.Flagship)).isZero();
        assertThat(count(UnitType.Destroyer)).isEqualTo(2);
    }

    @Test
    void doesNothingToAShipThatIsNoLongerDamaged() {
        solShip(UnitType.Flagship, 1, 0);

        destroyViaButton(UnitType.Flagship);

        assertThat(count(UnitType.Flagship)).isEqualTo(1);
    }

    @Test
    void doesNothingToADreadnoughtII() {
        test.sol.removeOwnedUnitByID("dreadnought");
        test.sol.addOwnedUnitByID("dreadnought2");
        solShip(UnitType.Dreadnought, 1, 1);

        destroyViaButton(UnitType.Dreadnought);

        assertThat(count(UnitType.Dreadnought)).isEqualTo(1);
        verify(event, atLeastOnce()).getChannel();
    }
}
