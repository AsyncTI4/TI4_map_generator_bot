package ti4.spring.api.image;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.DisplayType;
import ti4.helpers.discord.DiscordHelper;
import ti4.image.MapRenderPipeline;
import ti4.logging.BotLogger;
import ti4.spring.context.RequestContext;
import ti4.spring.context.SetupRequestContext;

@RequiredArgsConstructor
@RestController
// TODO: this should be /image
@RequestMapping("/api/public/game/{gameName}")
public class GameImageController {

    private final GameImageService gameImageService;
    private final GameAttachmentUrlRefreshService gameAttachmentUrlRefreshService;

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
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null) {
            return ResponseEntity.notFound().build();
        }

        // Non-FoW game: return full map to anyone
        if (!managedGame.isFowMode()) {
            return getFullMapUrl(managedGame.getGame());
        }

        // FoW game: check for authentication
        String userId = getOptionalUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required to view Fog of War maps");
        }

        // FoW game: GM (owner) gets full map
        Game game = managedGame.getGame();
        if (game != null && userId.equals(game.getOwnerID())) {
            return getFullMapUrl(game);
        }

        // FoW game: Check if user is a GM via Discord role
        if (game != null) {
            boolean isGm = game.getPlayersWithGMRole().stream().anyMatch(p -> userId.equals(p.getUserID()));
            if (isGm) {
                return getFullMapUrl(game);
            }
        }

        // FoW game: Player gets their FoW map
        if (managedGame.hasPlayer(userId)) {
            return getPlayerFowMapUrl(gameName, userId);
        }

        // FoW game: Not a participant
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        "To see this Fog of War map, please make sure you are logged in and are participating in this game");
    }

    @SetupRequestContext(false)
    @PostMapping("/image/attachment-url/refresh")
    public ResponseEntity<String> refreshAttachmentUrl(@PathVariable String gameName) {
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null) {
            return ResponseEntity.notFound().build();
        }
        if (managedGame.isFowMode()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Refresh is unavailable for Fog of War games");
        }

        Game game = managedGame.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        return refreshAttachmentUrlResponse(game);
    }

    /**
     * Get the user ID if authenticated, null otherwise.
     */
    private String getOptionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return null;
    }

    /**
     * Get the full (non-FoW) map URL for a game.
     */
    private ResponseEntity<String> getFullMapUrl(Game game) {
        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        String attachmentUrl =
                gameImageService.getLatestAttachmentUrl(game.getName()).orElse(null);
        if (attachmentUrl != null && !attachmentUrl.isBlank()) {
            return ResponseEntity.ok(attachmentUrl);
        }

        MapImageData mapImageData =
                gameImageService.getLatestMapImageData(game.getName()).orElse(null);
        if (mapImageData == null) {
            if (game.isFowMode()) {
                return notFound();
            }
            return refreshAttachmentUrlResponse(game);
        }

        Long messageId = mapImageData.getLatestDiscordMessageId();
        Long channelId = mapImageData.getLatestDiscordChannelId();

        if (messageId == null || messageId == 0 || channelId == null || channelId == 0) {
            if (game.isFowMode()) {
                return notFound();
            }
            return refreshAttachmentUrlResponse(game);
        }

        ResponseEntity<String> response = fetchDiscordAttachmentUrl(messageId, channelId, game);
        if (!HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
            return response;
        }

        if (game.isFowMode()) {
            return response;
        }

        return refreshAttachmentUrlResponse(game);
    }

    /**
     * Get the player-specific FoW map URL.
     */
    private ResponseEntity<String> getPlayerFowMapUrl(String gameName, String playerId) {
        PlayerMapImageData playerData =
                gameImageService.getPlayerMapImageData(gameName, playerId).orElse(null);
        if (playerData == null) {
            return ResponseEntity.notFound().build();
        }

        Long messageId = playerData.getDiscordMessageId();
        Long channelId = playerData.getDiscordChannelId();

        if (messageId == null || messageId == 0 || channelId == null || channelId == 0) {
            return ResponseEntity.notFound().build();
        }

        return fetchDiscordAttachmentUrl(messageId, channelId, null);
    }

    /**
     * Fetch the attachment URL from a Discord message.
     */
    private ResponseEntity<String> fetchDiscordAttachmentUrl(Long messageId, Long channelId, Game game) {
        MessageChannel channel = JdaService.jda.getChannelById(MessageChannel.class, channelId);
        if (channel == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Fetch the message from Discord to get the attachment URL using the channel.
            // The channel could be a TextChannel or ThreadChannel.
            Message message = channel.retrieveMessageById(messageId).complete();
            if (message == null || message.getAttachments().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String attachmentUrl = message.getAttachments().getFirst().getUrl();
            if (game != null && !game.isFowMode()) {
                gameImageService.saveDiscordMessage(game, message);
            }
            return ResponseEntity.ok(attachmentUrl);
        } catch (Exception e) {
            if (!DiscordHelper.isUnknownMessageError(e)) {
                BotLogger.error(
                        "Failed to fetch message " + messageId + " from channel " + channelId + " for game "
                                + (game == null ? "unknown" : game.getName()),
                        e);
            }
            return ResponseEntity.notFound().build();
        }
    }

    private java.util.Optional<String> refreshAttachmentUrl(Game game) {
        return gameAttachmentUrlRefreshService.refreshAttachmentUrl(game);
    }

    private ResponseEntity<String> refreshAttachmentUrlResponse(Game game) {
        return refreshAttachmentUrl(game).map(ResponseEntity::ok).orElseGet(this::notFound);
    }

    private ResponseEntity<String> notFound() {
        return ResponseEntity.notFound().build();
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
