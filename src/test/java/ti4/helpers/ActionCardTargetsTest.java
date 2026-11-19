package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.GameStats.ActionCardTarget;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Action card targets record who aimed a card at whom. Cards resolved through a "choose a player" button record the
// chosen player there; combat cards have no such step, so they record the combat opponent when played. Combat only
// happens in the active system between the active player and one other player, which is what decides the opponent.
class ActionCardTargetsTest extends BaseTi4Test {

    private static final String SYSTEM = "304";

    private Game game;
    private Player sol;
    private Player hacan;
    private Player xxcha;
    private Tile tile;
    private MockedStatic<MessageHelper> messages;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("ac-targets-test");
        sol = seat("200000000000000001", "sol", "blue");
        hacan = seat("200000000000000002", "hacan", "yellow");
        xxcha = seat("200000000000000003", "xxcha", "green");
        tile = new Tile("26", SYSTEM);
        game.setTile(tile);
        game.setActivePlayerID(sol.getUserID());
        game.setActiveSystem(SYSTEM);
        messages = mockStatic(MessageHelper.class);
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }

    private void ships(Player player) {
        tile.addUnit("space", Mapper.getUnitKey("cv", player.getColorID()), 1);
    }

    private List<ActionCardTarget> targets() {
        return game.getGameStats().getActionCardTargets();
    }

    private static ButtonInteractionEvent event() {
        return mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void theActivePlayersCombatOpponentIsTheOnlyOtherPlayerInTheActiveSystem() {
        ships(sol);
        ships(hacan);

        assertThat(ActionCardTargets.combatOpponent(game, sol)).contains(hacan);
    }

    @Test
    void theDefendersCombatOpponentIsTheActivePlayer() {
        assertThat(ActionCardTargets.combatOpponent(game, hacan)).contains(sol);
    }

    // With two other players in the system the engine cannot tell which one the card was aimed at, so it records none.
    @Test
    void noOpponentWhenTwoOtherPlayersShareTheActiveSystem() {
        ships(sol);
        ships(hacan);
        ships(xxcha);

        assertThat(ActionCardTargets.combatOpponent(game, sol)).isEmpty();
    }

    @Test
    void aCombatCardRecordsTheOpponentWhenPlayed() {
        ships(sol);
        ships(hacan);

        ActionCardTargets.recordOnPlay(game, hacan, Mapper.getActionCard("intercept"));

        assertThat(targets()).singleElement().satisfies(target -> {
            assertThat(target.getActionCard()).isEqualTo("Intercept");
            assertThat(target.getPlayerId()).isEqualTo(hacan.getUserID());
            assertThat(target.getTargetId()).isEqualTo(sol.getUserID());
        });
    }

    // Cards that name their target on a later button record it there, not when played.
    @Test
    void aCardWithoutACombatOpponentRecordsNothingWhenPlayed() {
        ActionCardTargets.recordOnPlay(game, hacan, Mapper.getActionCard("spy"));

        assertThat(targets()).isEmpty();
    }

    @Test
    void ancientBurialSitesRecordsTheChosenPlayer() {
        ButtonHelperActionCards.resolveABSStep2(sol, game, event(), "absStep2_" + hacan.getFaction());

        assertThat(targets())
                .extracting(ActionCardTarget::getActionCard, ActionCardTarget::getTargetId)
                .containsExactly(tuple("Ancient Burial Sites", hacan.getUserID()));
    }

    // Tactical Bombardment exhausts every planet other players control in the system; each player counts once.
    @Test
    void tacticalBombardmentRecordsEachPlayerWithPlanetsInTheSystemOnce() {
        game.setTile(new Tile("27", "305"));
        hacan.addPlanet("newalbion");
        hacan.addPlanet("starpoint");
        xxcha.addPlanet("lodor");

        ButtonHelperActionCards.resolveTacticalBombardmentStep2(sol, game, event(), "tacticalBombardmentStep2_305");

        assertThat(targets()).extracting(ActionCardTarget::getTargetId).containsExactly(hacan.getUserID());
    }
}
