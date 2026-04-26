package ti4.spring.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.game.persistence.GameManager;

class GameImageServiceTest {

    @Test
    void saveDiscordMessagePersistsDiscordIds() {
        MapImageDataRepository mapImageDataRepository = mock(MapImageDataRepository.class);
        PlayerMapImageDataRepository playerMapImageDataRepository = mock(PlayerMapImageDataRepository.class);
        GameImageService service = new GameImageService(mapImageDataRepository, playerMapImageDataRepository);

        Message message = mock(Message.class);
        net.dv8tion.jda.api.entities.Guild guild = mock(net.dv8tion.jda.api.entities.Guild.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        when(message.getIdLong()).thenReturn(11L);
        when(message.getGuild()).thenReturn(guild);
        when(message.getChannel()).thenReturn(channel);
        when(guild.getIdLong()).thenReturn(22L);
        when(channel.getIdLong()).thenReturn(33L);
        when(mapImageDataRepository.findById("pbd11223")).thenReturn(Optional.empty());
        when(mapImageDataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd11223")).thenReturn(true);

            service.saveDiscordMessage("pbd11223", message);
        }

        ArgumentCaptor<MapImageData> dataCaptor = ArgumentCaptor.forClass(MapImageData.class);
        verify(mapImageDataRepository).save(dataCaptor.capture());
        assertThat(dataCaptor.getValue().getLatestDiscordMessageId()).isEqualTo(11L);
        assertThat(dataCaptor.getValue().getLatestDiscordGuildId()).isEqualTo(22L);
        assertThat(dataCaptor.getValue().getLatestDiscordChannelId()).isEqualTo(33L);
    }
}
