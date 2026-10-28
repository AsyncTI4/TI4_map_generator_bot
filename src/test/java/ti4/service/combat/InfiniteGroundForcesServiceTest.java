package ti4.service.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class InfiniteGroundForcesServiceTest extends BaseTi4Test {

    private Game game;
    private Player letnev;
    private Player hacan;
    private Tile tile;
    private UnitHolder mecatol;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("infinite-ground-combat-test");
        tile = new Tile("18", "000");
        game.setTile(tile);
        letnev = seat("111", "letnev", "black");
        hacan = seat("222", "hacan", "yellow");
        mecatol = tile.getUnitHolders().get("mr");
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    @Test
    void anInfiniteStackProducesInfiniteHitsWithoutRollingDice() {
        mecatol.makeInfinite(Units.getUnitKey(UnitType.Infantry, letnev.getColorID()));
        mecatol.addUnit(Units.getUnitKey(UnitType.Infantry, hacan.getColorID()), 3);
        mecatol.addUnit(Units.getUnitKey(UnitType.Mech, hacan.getColorID()), 1);
        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class, RETURNS_DEEP_STUBS);
        List<String> messages = new ArrayList<>();

        int hits;
        try (MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class);
                MockedStatic<DiceHelper> dice = mockStatic(DiceHelper.class)) {
            messageHelper
                    .when(() -> MessageHelper.sendMessageToEventChannel(any(), anyString()))
                    .thenAnswer(call -> messages.add(call.getArgument(1)));
            messageHelper
                    .when(() -> MessageHelper.sendMessageToChannelWithButtons(
                            any(MessageChannel.class), anyString(), anyList()))
                    .thenAnswer(call -> messages.add(call.getArgument(1)));

            hits = CombatRollService.secondHalfOfCombatRoll(
                    letnev, game, event, tile, "mr", CombatRollType.combatround, false);

            dice.verifyNoInteractions();
        }

        // Three infantry plus a mech that needs two hits to get past SUSTAIN DAMAGE.
        assertThat(hits).isEqualTo(5);
        assertThat(messages).anyMatch(message -> message.contains("∞ hits"));
        assertThat(messages).anyMatch(message -> message.contains("please assign"));
    }

    @Test
    void anOrdinaryStackStillRolls() {
        mecatol.addUnit(Units.getUnitKey(UnitType.Infantry, letnev.getColorID()), 3);

        assertThat(InfiniteGroundForcesService.rollsInfinitely(letnev, mecatol, CombatRollType.combatround))
                .isFalse();
    }

    @Test
    void onlyGroundCombatRoundsRollInfinitely() {
        mecatol.makeInfinite(Units.getUnitKey(UnitType.Infantry, letnev.getColorID()));

        assertThat(InfiniteGroundForcesService.rollsInfinitely(letnev, mecatol, CombatRollType.combatround))
                .isTrue();
        assertThat(InfiniteGroundForcesService.rollsInfinitely(letnev, mecatol, CombatRollType.bombardment))
                .isFalse();
        assertThat(InfiniteGroundForcesService.rollsInfinitely(
                        letnev, tile.getSpaceUnitHolder(), CombatRollType.combatround))
                .isFalse();
    }
}
