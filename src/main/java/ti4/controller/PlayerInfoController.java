package ti4.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ti4.controller.validator.GameNameValidator;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;

@RestController
@RequestMapping("/api/game")
public class PlayerInfoController {

    private static final String DISCORD_API_BASE = "https://discord.com/api";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

        @GetMapping("/{gameName}/playerInfo")
    public ResponseEntity<PlayerInfoResponse> getPlayerInfo(
            @PathVariable String gameName,
            @RequestHeader("Authorization") String authorization) {

                try {
            // Validate game name
            GameNameValidator.validate(gameName);

            // Extract bearer token
            if (!authorization.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().build();
            }

            // Validate Discord token and get user info
            String discordUserId = validateDiscordToken(authorization);
            if (discordUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get game
            var managedGame = GameManager.getManagedGame(gameName);
            if (managedGame == null) {
                return ResponseEntity.notFound().build();
            }
            Game game = managedGame.getGame();

            // Get player
            // Player player = game.getPlayer(discordUserId);
            // During testing
            Player player = game.getPlayer("148195684644814848");
            if (player == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Get action card IDs
            Set<String> actionCardIds = player.getActionCards().keySet();
            List<String> actionCards = List.copyOf(actionCardIds);

            return ResponseEntity.ok(new PlayerInfoResponse(actionCards));

        } catch (Exception e) {
            BotLogger.error("Error getting player info for game " + gameName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

            private String validateDiscordToken(String authorization) {
        try {
            String token = authorization.substring(7);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DISCORD_API_BASE + "/users/@me"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                BotLogger.warning("Discord API returned status " + response.statusCode() + " for token validation");
                return null;
            }

            JsonNode userNode = objectMapper.readTree(response.body());
            return userNode.get("id").asText();

        } catch (Exception e) {
            BotLogger.error("Error validating Discord token", e);
            return null;
        }
    }

    public static class PlayerInfoResponse {
        private final List<String> actionCards;

        public PlayerInfoResponse(List<String> actionCards) {
            this.actionCards = actionCards;
        }

        public List<String> getActionCards() {
            return actionCards;
        }
    }
}