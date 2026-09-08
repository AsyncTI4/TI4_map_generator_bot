package ti4.service.tech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Space;
import ti4.game.Tile;

class EntropicScarServiceTest {

    private Game game;
    private Player player;
    private Tile scar;

    @BeforeEach
    void setUp() {
        game = mock(Game.class);
        player = mock(Player.class);
        scar = mock(Tile.class);
        Space space = mock(Space.class);

        when(game.getTileMap()).thenReturn(Map.of("210", scar));
        when(scar.isScar()).thenReturn(true);
        when(scar.getSpaceUnitHolder()).thenReturn(space);
        when(space.getUnitKeys()).thenReturn(Set.of());
        when(scar.containsPlayersUnitsWithModelCondition(eq(player), any())).thenReturn(true);
        when(player.getFactionTechs()).thenReturn(new ArrayList<>(List.of("ff2")));
        when(player.getTechs()).thenReturn(new ArrayList<>());
        when(player.getStrategicCC()).thenReturn(1);
    }

    @Test
    void offersUnownedFactionTechnologyWhenPlayerCanPay() {
        assertThat(EntropicScarService.getAvailableTechnologies(game, player)).contains("ff2");
        assertThat(EntropicScarService.hasPendingTechnologyChoice(game, player)).isTrue();
    }

    @Test
    void readyScepterCanPayInsteadOfStrategyToken() {
        when(player.getStrategicCC()).thenReturn(0);
        when(player.hasRelicReady("emelpar")).thenReturn(true);

        assertThat(EntropicScarService.hasPendingTechnologyChoice(game, player)).isTrue();
    }

    @Test
    void noPaymentSourceLeavesNoPendingChoice() {
        when(player.getStrategicCC()).thenReturn(0);

        assertThat(EntropicScarService.hasPendingTechnologyChoice(game, player)).isFalse();
    }

    @Test
    void ownedFactionTechnologiesLeaveNoPendingChoice() {
        when(player.getTechs()).thenReturn(new ArrayList<>(List.of("ff2")));

        assertThat(EntropicScarService.getAvailableTechnologies(game, player)).isEmpty();
        assertThat(EntropicScarService.hasPendingTechnologyChoice(game, player)).isFalse();
    }

    @Test
    void noShipInScarLeavesNoPendingChoice() {
        when(scar.containsPlayersUnitsWithModelCondition(eq(player), any())).thenReturn(false);

        assertThat(EntropicScarService.hasPendingTechnologyChoice(game, player)).isFalse();
    }
}
