package ti4.spring.api.image;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.spring.context.RequestContext;
import ti4.spring.context.SetupRequestContext;
import ti4.spring.jda.JdaService;

@RequiredArgsConstructor
@RestController
// TODO: this should be /image
@RequestMapping("/api/public/game/{gameName}")
public class GameImageController {

    private final GameImageService gameImageService;

    // TODO: once the above is /image, this doesn't need to specify anything
    @SetupRequestContext(false)
    @GetMapping("/image")
    public ResponseEntity<String> get(@PathVariable String gameName) {
        return gameImageService
                .getLatestMapImageData(gameName)
                .map(MapImageData::getLatestMapImageName)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @SetupRequestContext(false)
    @GetMapping("/image/attachment-url")
    public ResponseEntity<String> getAttachmentUrl(@PathVariable String gameName) {
        MapImageData mapImageData =
                gameImageService.getLatestMapImageData(gameName).orElse(null);
        if (mapImageData == null) return ResponseEntity.notFound().build();

        Long messageId = mapImageData.getLatestDiscordMessageId();
        Long channelId = mapImageData.getLatestDiscordChannelId();

        if (messageId == null || messageId == 0 || channelId == null || channelId == 0) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Fetch the message from Discord to get the attachment URL using the channel.
            // The channel could be a TextChannel or ThreadChannel.
            Message message = JdaService.jda
                    .getChannelById(MessageChannel.class, channelId)
                    .retrieveMessageById(messageId)
                    .complete();
            if (message == null || message.getAttachments().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String attachmentUrl = message.getAttachments().getFirst().getUrl();
            return ResponseEntity.ok(attachmentUrl);
        } catch (Exception e) {
            BotLogger.error(
                    "Failed to fetch message " + messageId + " from channel " + channelId + " for game " + gameName, e);
            return ResponseEntity.notFound().build();
        }
    }

    @SetupRequestContext(save = false)
    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@PathVariable String gameName) {
        Game game = RequestContext.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        MapRenderPipeline.queue(game, null, DisplayType.all, null);
        return ResponseEntity.ok("Queued");
    }
}
