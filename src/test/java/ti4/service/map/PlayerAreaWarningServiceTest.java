package ti4.service.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.map.MapFrame;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class PlayerAreaWarningServiceTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private List<Tile> tiles;
    private MockedStatic<MessageHelper> messages;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("player-area-warnings-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        tiles = new ArrayList<>(game.getTileMap().values());
        player = game.addPlayer("p1", "sol");
        player.setFaction(game, "sol");
        player.setColor("blue");
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction("sol").getUnits()));
        player.addAbility("incursion");
        messages = mockStatic(MessageHelper.class, Mockito.CALLS_REAL_METHODS);
        messages.when(() -> MessageHelper.sendMessageToChannel(Mockito.<MessageChannel>any(), anyString()))
                .then(invocation -> null);
        messages.when(() -> MessageHelper.sendMessageToChannelWithButtons(
                        Mockito.<MessageChannel>any(), anyString(), Mockito.<List<Button>>any()))
                .then(invocation -> null);
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void placeBreaches(int count) {
        for (int i = 0; i < tiles.size(); i++) {
            if (i < count) {
                tiles.get(i).getSpaceUnitHolder().addToken(Constants.TOKEN_BREACH_ACTIVE);
            } else {
                tiles.get(i).getSpaceUnitHolder().removeToken(Constants.TOKEN_BREACH_ACTIVE);
            }
        }
    }

    private void placeDreadnoughts(int count) {
        tiles.getFirst().addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Dreadnought, player.getColorID()), count);
    }

    private void assertBreachWarningsSent(int expected) {
        messages.verify(
                () -> MessageHelper.sendMessageToChannelWithButtons(
                        any(), Mockito.contains("too many Breach tokens"), any()),
                times(expected));
    }

    private void assertPlasticWarningsSent(int expected) {
        messages.verify(
                () -> MessageHelper.sendMessageToChannelWithButtons(
                        any(), Mockito.contains("exceeding unit plastic or cardboard limits"), any()),
                times(expected));
    }

    @Test
    void warnsAPlayerWithTooManyTokensAndOffersToShowTheSystems() {
        placeBreaches(8);

        PlayerAreaWarningService.warnPlayer(game, player);

        ArgumentCaptor<List<Button>> buttons = ArgumentCaptor.captor();
        messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                any(), Mockito.contains("too many Breach tokens"), buttons.capture()));
        assertEquals(1, buttons.getValue().size());
        assertEquals(
                "showWarningSystems_blue_breach", buttons.getValue().getFirst().getCustomId());
        assertEquals("Show Systems", buttons.getValue().getFirst().getLabel());
    }

    @Test
    void warnsAgainOnEveryCallWhileTheProblemRemains() {
        placeBreaches(8);

        PlayerAreaWarningService.warnPlayer(game, player);
        PlayerAreaWarningService.warnPlayer(game, player);

        assertBreachWarningsSent(2);
    }

    @Test
    void doesNotWarnWithinTheLimitOrAfterItIsResolved() {
        placeBreaches(7);
        PlayerAreaWarningService.warnPlayer(game, player);
        placeBreaches(8);
        placeBreaches(3);
        PlayerAreaWarningService.warnPlayer(game, player);

        assertBreachWarningsSent(0);
    }

    @Test
    void doesNotWriteAnythingToTheGame() {
        placeBreaches(8);
        var storedBefore = new java.util.HashMap<>(game.getStoredValueMap());

        PlayerAreaWarningService.warnAllPlayers(game);

        assertEquals(storedBefore, game.getStoredValueMap());
    }

    @Test
    void warnAllPlayersWarnsEveryRealPlayerWhoCanBeWarned() {
        placeBreaches(8);

        PlayerAreaWarningService.warnAllPlayers(game);

        assertBreachWarningsSent(1);
    }

    @Test
    void warnAllPlayersExceptSkipsThatPlayer() {
        placeBreaches(8);

        PlayerAreaWarningService.warnAllPlayersExcept(game, player);

        assertBreachWarningsSent(0);
    }

    @Test
    void plasticOverTheLimitWarnsWithShowSystemsAndRemoveButtons() {
        game.setCcNPlasticLimit(true);
        placeDreadnoughts(6);

        PlayerAreaWarningService.warnPlayer(game, player);

        ArgumentCaptor<List<Button>> buttons = ArgumentCaptor.captor();
        messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                any(), Mockito.contains("exceeding unit plastic or cardboard limits"), buttons.capture()));
        assertEquals(
                "showWarningSystems_blue_plastic_dn",
                buttons.getValue().getFirst().getCustomId());
    }

    @Test
    void plasticLimitIsIgnoredWhenTheGameDoesNotEnforceIt() {
        game.setCcNPlasticLimit(false);
        placeDreadnoughts(6);

        PlayerAreaWarningService.warnPlayer(game, player);

        assertPlasticWarningsSent(0);
    }

    @Test
    void showSystemsFindsTheSystemsHoldingTheOffendingBreachTokens() {
        placeBreaches(2);

        var affected = PlayerAreaWarningService.affectedSystems(game, "showWarningSystems_blue_breach");

        assertEquals(
                List.of(tiles.get(0).getPosition(), tiles.get(1).getPosition()).stream()
                        .sorted()
                        .toList(),
                affected.positions());
    }

    @Test
    void showSystemsFindsTheSystemsHoldingTheOffendingPlastic() {
        placeDreadnoughts(2);

        var affected = PlayerAreaWarningService.affectedSystems(game, "showWarningSystems_blue_plastic_dn");

        assertEquals(List.of(tiles.getFirst().getPosition()), affected.positions());
    }

    @Test
    void showSystemsIgnoresUnknownButtons() {
        assertNull(PlayerAreaWarningService.affectedSystems(game, "showWarningSystems_blue_bogus"));
        assertNull(PlayerAreaWarningService.affectedSystems(game, "showWarningSystems_nobody_breach"));
    }
}
