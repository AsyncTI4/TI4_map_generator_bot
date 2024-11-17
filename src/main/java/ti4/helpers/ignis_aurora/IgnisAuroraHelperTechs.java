package ti4.helpers.ignis_aurora;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddCC;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Emojis;
import ti4.helpers.SecretObjectiveHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class IgnisAuroraHelperTechs {
    public static void handleExhaustIgnisAuroraTech(GenericInteractionCreateEvent event, Game game, Player player, String tech) {
        boolean deleteMsg = true, deleteButton = true;
        switch (tech) {
            case "baldrick_nm" -> ActionCardHelper.drawActionCards(game, player, 1, true);
            case "baldrick_hm" -> ButtonHelperStats.sendGainCCButtons(game, player, false);
            case "baldrick_lwd" -> deleteMsg = false;
            case "baldrick_gd" -> {
                deleteMsg = false;
                game.setStoredValue("baldrickGDboost", "1");
            }
            case "fabrilerealignment" -> {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.red("fibrileRealign_AC", "Discard/draw Action Card", Emojis.ActionCard));
                buttons.add(Buttons.red("fibrileRealign_SO", "Discard/draw Secret Objective", Emojis.SecretObjective));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Use buttons to resolve:", buttons);
            }
            case "stellarcorridors" -> postStellarCorridors(event, game, player);
            default -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "> This tech is not automated. Please resolve manually.");
            }
        }
        if (deleteMsg) {
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    @ButtonHandler("fibrileRealign_AC")
    public static void handleFibrileAC(ButtonInteractionEvent event, Game game, Player player) {
        ActionCardHelper.sendDiscardAndDrawActionCardButtons(player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("fibrileRealign_SO")
    public static void handleFibrileSO(ButtonInteractionEvent event, Game game, Player player) {
        SecretObjectiveHelper.sendSODiscardButtons(player, "redraw");
        ButtonHelper.deleteMessage(event);
    }

    private static void postStellarCorridors(GenericInteractionCreateEvent event, Game game, Player player) {
        // At the start of your turn you may exhaust this card to remove 1 of your command tokens
        // from a system that does not contain a planet owned by another player or another player's units
        Predicate<Tile> pred = tile -> {
            if (!AddCC.hasCC(event, player.getColor(), tile)) return false;
            for (Player p : game.getRealPlayers()) {
                if (p == player) continue;
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    if (p.hasPlanet(planet.getName())) return false;
                }
                if (tile.containsPlayersUnits(p)) return false;
            }
            return true;
        };
        String action = "removeCCFromBoard_stellarcorridors";
        List<Button> buttons = ButtonHelper.getTilesWithPredicateForAction(player, game, action, pred, false);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Use buttons to remove one of your CCs:", buttons);
    }
}
