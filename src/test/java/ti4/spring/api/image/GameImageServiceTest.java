package ti4.spring.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.persistence.GameManager;

class GameImageServiceTest {

    @Test
    void saveDiscordMessagePersistsAttachmentUrl() {
        MapImageDataRepository mapImageDataRepository = mock(MapImageDataRepository.class);
        PlayerMapImageDataRepository playerMapImageDataRepository = mock(PlayerMapImageDataRepository.class);
        GameImageService service = new GameImageService(mapImageDataRepository, playerMapImageDataRepository);
        Game game = new Game();
        game.setName("pbd11223");

        Message message = mock(Message.class);
        Attachment attachment = mock(Attachment.class);
        when(message.getIdLong()).thenReturn(11L);
        when(message.getGuild()).thenReturn(mock(net.dv8tion.jda.api.entities.Guild.class));
        when(message.getChannel()).thenReturn(mock(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel.class));
        when(message.getGuild().getIdLong()).thenReturn(22L);
        when(message.getChannel().getIdLong()).thenReturn(33L);
        when(message.getAttachments()).thenReturn(List.of(attachment));
        when(attachment.getUrl()).thenReturn("https://cdn.discordapp.com/example.png");
        when(mapImageDataRepository.findById("pbd11223")).thenReturn(Optional.empty());
        when(mapImageDataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd11223")).thenReturn(true);

            service.saveDiscordMessage(game, message);
        }

        ArgumentCaptor<MapImageData> dataCaptor = ArgumentCaptor.forClass(MapImageData.class);
        org.mockito.Mockito.verify(mapImageDataRepository).save(dataCaptor.capture());
        assertThat(dataCaptor.getValue().getLatestDiscordMessageId()).isEqualTo(11L);
        assertThat(dataCaptor.getValue().getLatestDiscordGuildId()).isEqualTo(22L);
        assertThat(dataCaptor.getValue().getLatestDiscordChannelId()).isEqualTo(33L);
        assertThat(dataCaptor.getValue().getLatestDiscordAttachmentUrl())
                .isEqualTo("https://cdn.discordapp.com/example.png");
    }
}
