package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.jotunn;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.combat.CancelGroundHitsButtonId;
import ti4.discord.interactions.buttons.handlers.combat.CombatButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.adjacency.Adjacency;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class JotunnTechHandler {
    public static Button getWardstoneLatticeButton(Player player, String position, String unitHolder, int hits) {
        return Buttons.green(
                player.factionButtonChecker() + "cancelWardstoneHits_" + position + "|" + unitHolder + "|" + hits,
                "Use Wardstone Lattice",
                FactionEmojis.jotunn);
    }

    @ButtonHandler("cancelWardstoneHits_")
    public static void cancelWardstoneHits(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring("cancelWardstoneHits_".length()).split("\\|", 3);
        Tile tile = parts.length == 3 ? game.getTileByPosition(parts[0]) : null;
        if (tile == null
                || !player.hasTechReady("aojotunnwl")
                || !JotunnAbilityHandler.hasJotunnStructure(player, game, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        int hits = Integer.parseInt(parts[2]);
        int cancelled = Math.min(hits, getAdjacentStructureCount(game, player, tile));
        if (cancelled == 0) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        PlayerTechService.exhaustTechAndResolve(event, game, player, "aojotunnwl_" + String.join("|", parts));
    }

    public static void resolveWardstoneLattice(
            GenericInteractionCreateEvent event, Game game, Player player, String context) {
        String[] parts = context.split("\\|", 3);
        Tile tile = parts.length == 3 ? game.getTileByPosition(parts[0]) : null;
        if (tile == null || !JotunnAbilityHandler.hasJotunnStructure(player, game, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        int hits = Integer.parseInt(parts[2]);
        int cancelled = Math.min(hits, getAdjacentStructureCount(game, player, tile));
        if (cancelled == 0) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (!(event instanceof ButtonInteractionEvent buttonEvent)) {
            return;
        }
        int remainingHits = hits - cancelled;
        if ("space".equals(parts[1])) {
            CombatButtonHandler.cancelSpaceHits(
                    buttonEvent, player, "cancelSpaceHits_" + tile.getPosition() + "_" + (remainingHits + 1), game);
            return;
        }
        CombatButtonHandler.cancelGroundHits(
                buttonEvent,
                player,
                CancelGroundHitsButtonId.of(tile.getPosition(), remainingHits + 1, parts[1]),
                game);
    }

    public static int getAdjacentStructureCount(Game game, Player player, Tile tile) {
        return (int) Adjacency.of(game, player).forEffects().either(tile.getPosition()).stream()
                .map(game::getTileByPosition)
                .filter(adjacentTile ->
                        adjacentTile != null && JotunnAbilityHandler.hasJotunnStructure(player, game, adjacentTile))
                .limit(3)
                .count();
    }
}
