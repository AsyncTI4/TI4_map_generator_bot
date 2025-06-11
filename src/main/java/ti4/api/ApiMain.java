package ti4.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import ti4.map.Game;

import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.service.movement.MovementExecutionService;
import ti4.service.movement.MovementExecutionService.MovementExecutionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiMain {
    public static void start() {
        Javalin app = Javalin.create().start(7070);
        ObjectMapper objectMapper = new ObjectMapper();

        app.post("/api/{gameId}/move-units", ctx -> {
            String gameId = ctx.pathParam("gameId");
            Game game = GameManager.get(gameId);
            if (game == null) {
                ctx.status(404).result("Game not found");
                return;
            }

            try {
                Map<String, Map<String, Integer>> receivedUnits = objectMapper.readValue(ctx.body(), new TypeReference<>() {});
                Player player = game.getActivePlayer();
                if (player == null) {
                    ctx.status(400).result("No active player");
                    return;
                }
                Tile tile = game.getTileByPosition(game.getActiveSystem());
                if (tile == null) {
                    ctx.status(400).result("No active system");
                    return;
                }

                // Translate incoming JSON to displacement map
                Map<String, Map<UnitKey, List<Integer>>> displacementMap = new HashMap<>();
                for (Map.Entry<String, Map<String, Integer>> systemEntry : receivedUnits.entrySet()) {
                    String tilePosition = systemEntry.getKey();
                    Tile fromTile = game.getTileByPosition(tilePosition);
                    if (fromTile == null) continue;

                    for (Map.Entry<String, Integer> unitEntry : systemEntry.getValue().entrySet()) {
                        String unitId = unitEntry.getKey();
                        Integer count = unitEntry.getValue();

                        for (UnitHolder uh : fromTile.getUnitHolders().values()) {
                            UnitKey unitKey = Units.getUnitKey(unitId, player.getColor());
                             if(uh.getUnits().containsKey(unitKey)){
                                String uhKey = fromTile.getPosition() + "-" + uh.getName();
                                Map<UnitKey, List<Integer>> movement = displacementMap.getOrDefault(uhKey, new HashMap<>());
                                List<Integer> states = uh.removeUnit(unitKey, count);
                                movement.put(unitKey, states);
                                displacementMap.put(uhKey, movement);
                                break;
                             }
                        }
                    }
                }
                game.setTacticalActionDisplacement(displacementMap);

                MovementExecutionResult result = MovementExecutionService.executeMovement(null, game, player, tile);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), result.getMessage(), result.getButtons());
                ctx.status(200).result("Movement executed");

            } catch (Exception e) {
                ctx.status(500).result("Error executing movement: " + e.getMessage());
            }
        });
    }
}