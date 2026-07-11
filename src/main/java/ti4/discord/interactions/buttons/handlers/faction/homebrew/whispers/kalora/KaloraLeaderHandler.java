package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class KaloraLeaderHandler {

    @ButtonHandler("exhaustAgent_kaloraagent")
    public static void exhaustKaloraAgent(ButtonInteractionEvent event, Game game, Player player) {
        var leader = player.getLeader("kaloraagent").orElse(null);
        if (leader == null) return;
        ExhaustLeaderService.exhaustLeader(game, player, leader);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.toString() + " has exhausted Valzor, the Kalora agent.");
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please use the buttons to gain 1 command token.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void offerKaloraAgentButtons(Player player, String msg) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "exhaustAgent_kaloraagent",
                "Use Valzor, the Kalora Agent",
                FactionEmojis.kalora));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                msg
                        + ", a reminder that at the end of this space combat, you may exhaust the Kalora agent to allow a participating player to gain 1 command token.",
                buttons);
    }

    public static void addCommanderBombardmentUnits(
            Player player, Tile tile, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        var game = player.getGame();
        if (game == null) return;
        String guardKey = "kaloraCommanderBombardResolving_" + player.getFaction();
        if (!game.getStoredValue(guardKey).isEmpty()) return;
        game.setStoredValue(guardKey, "true");
        try {
            for (String adjacentPos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false)) {
                Tile adjacentTile = game.getTileByPosition(adjacentPos);
                if (adjacentTile == null) continue;
                Map<Pair<UnitModel, UnitHolder>, Integer> adjacentUnits =
                        CombatRollService.getUnitsInBombardment(adjacentTile, player, null);
                adjacentUnits.forEach((pair, count) -> units.merge(pair, count, Integer::sum));
            }
        } finally {
            game.setStoredValue(guardKey, "");
        }
    }
}
