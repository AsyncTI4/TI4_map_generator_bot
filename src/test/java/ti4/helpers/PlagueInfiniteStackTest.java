package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class PlagueInfiniteStackTest extends BaseTi4Test {

    private Game game;
    private Player letnev;
    private Player hacan;
    private UnitHolder mecatol;
    private UnitKey hacanInfantry;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("plague-infinite-stack-test");
        Tile tile = new Tile("18", "000");
        game.setTile(tile);
        letnev = seat("111", "letnev", "black");
        hacan = seat("222", "hacan", "yellow");
        hacan.addPlanet("mr");
        mecatol = tile.getUnitHolders().get("mr");
        hacanInfantry = Units.getUnitKey(UnitType.Infantry, hacan.getColorID());
        mecatol.makeInfinite(hacanInfantry);
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    @Test
    void plagueRollsNoDiceAndDoesNothingToAnInfiniteStack() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        List<String> messages = new ArrayList<>();

        try (MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class);
                MockedConstruction<Die> dice = mockConstruction(Die.class)) {
            messageHelper
                    .when(() -> MessageHelper.sendMessageToChannel(any(), anyString()))
                    .thenAnswer(call -> messages.add(call.getArgument(1)));

            ButtonHelperActionCards.resolvePlagueStep3(letnev, game, event, "plagueStep3_hacan_mr");

            assertThat(dice.constructed()).isEmpty();
        }

        assertThat(mecatol.isInfinite(hacanInfantry)).isTrue();
        assertThat(messages).anyMatch(message -> message.contains("nothing to the infinite stack"));
    }
}
