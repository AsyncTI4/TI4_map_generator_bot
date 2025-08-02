package ti4.spring.service;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ti4.map.Player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = HandService.class)
class HandServiceTest {

    @Autowired
    private HandService service;

    @Test
    void returnsActionCards() {
        Player player = Mockito.mock(Player.class);
        when(player.getActionCards()).thenReturn(Map.of("a", 1, "b", 2));
        assertThat(service.getActionCards(player)).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void returnsSecretObjectives() {
        Player player = Mockito.mock(Player.class);
        when(player.getSecrets()).thenReturn(Map.of("s", 3));
        assertThat(service.getSecretObjectives(player)).containsExactly("s");
    }

    @Test
    void returnsPromissoryNotes() {
        Player player = Mockito.mock(Player.class);
        when(player.getPromissoryNotes()).thenReturn(Map.of("pn", 4));
        assertThat(service.getPromissoryNotes(player)).containsExactly("pn");
    }
}
