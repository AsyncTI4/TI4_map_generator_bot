package ti4.spring.service.gameevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ti4.game.Game;
import ti4.game.Player;

class GameEventServiceTest {

    @Test
    void commitsPreSerializedEventAndOmitsUnchangedMapState() {
        GameEventRepository repository = mock(GameEventRepository.class);
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        when(game.getName()).thenReturn("pbd1");
        when(game.getEventSequenceCounter()).thenReturn(4L);
        when(game.getRound()).thenReturn(2);
        when(game.getPhaseOfGame()).thenReturn("action");
        when(player.getFaction()).thenReturn("arborec");
        GameEventEntity previousEvent = mock(GameEventEntity.class);
        when(previousEvent.getMapState()).thenReturn("same-map");
        when(repository.findFirstByGameNameAndSeqLessThanEqualAndMapStateIsNotNullOrderBySeqDesc("pbd1", 4L))
                .thenReturn(Optional.of(previousEvent));

        new GameEventService(repository)
                .commitEvent(game, "turn", player, "{\"passed\":false}", "same-map", "movement");

        verify(repository).deleteByGameNameAndSeqGreaterThan("pbd1", 4L);
        verify(game).setEventSequenceCounter(5L);

        ArgumentCaptor<GameEventEntity> eventCaptor = ArgumentCaptor.forClass(GameEventEntity.class);
        verify(repository).save(eventCaptor.capture());
        GameEventEntity event = eventCaptor.getValue();
        assertThat(event.getSeq()).isEqualTo(5L);
        assertThat(event.getPayload()).isEqualTo("{\"passed\":false}");
        assertThat(event.getMapState()).isNull();
        assertThat(event.getMovementState()).isEqualTo("movement");
    }
}
