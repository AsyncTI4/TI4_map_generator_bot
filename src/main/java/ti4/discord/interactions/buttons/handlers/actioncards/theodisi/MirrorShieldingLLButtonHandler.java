package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.StartCombatService;

@UtilityClass
public class MirrorShieldingLLButtonHandler {
    private static final String RESOLVE_MIRROR_SHIELDING = "resolveMirrorShielding";
    private static final String CANCELLED_HITS = "mirrorShieldingCancelledHits_";

    @ButtonHandler(RESOLVE_MIRROR_SHIELDING)
    public static void resolveMirrorShielding(ButtonInteractionEvent event, Game game, Player player) {
        CombatContext context = getCombatContext(game, player);
        int canceledHits = getCancelledHits(game, player, context);

        if (context == null || canceledHits < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has not canceled any hits this combat round for _Mirror Shielding_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(context.tilePosition());
        List<Player> opponents = new ArrayList<>(game.getRealPlayers());
        if (game.getNeutral() != null) opponents.add(game.getNeutral());
        Player opponent = opponents.stream()
                .filter(other -> other != player && FoWHelper.playerHasActualShipsInSystem(other, tile))
                .findFirst()
                .orElse(null);
        if (opponent == null || tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(CANCELLED_HITS + player.getFaction());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Mirror Shielding_ against "
                        + opponent.getRepresentationNoPing() + "."
                        + CombatMessageHelper.displayHitResults(canceledHits).stripTrailing());

        if (Constants.SPACE.equals(context.unitHolderName())) {
            CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, canceledHits);
        } else {
            sendGroundAssignHitsButtons(event, game, opponent, tile, context.unitHolderName(), canceledHits);
        }

        ButtonHelper.deleteMessage(event);
    }

    public static void recordCancelledHits(Game game, Player player, Tile tile, int hits) {
        CombatContext context = getCombatContext(game, player);
        if (context == null
                || tile == null
                || hits < 1
                || !context.tilePosition().equals(tile.getPosition())) {
            return;
        }

        String key = CANCELLED_HITS + player.getFaction();
        String[] stored = game.getStoredValue(key).split("\\|", 4);
        int existingHits = 0;

        if (stored.length == 4
                && stored[0].equals(context.tilePosition())
                && stored[1].equals(context.unitHolderName())
                && stored[2].equals(Integer.toString(context.round()))) {
            existingHits = NumberUtils.toInt(stored[3]);
        }

        game.setStoredValue(
                key,
                context.tilePosition()
                        + "|"
                        + context.unitHolderName()
                        + "|"
                        + context.round()
                        + "|"
                        + (existingHits + hits));
    }

    public static void clearMirrorShielding(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(CANCELLED_HITS + player.getFaction());
        }
    }

    private static int getCancelledHits(Game game, Player player, CombatContext context) {
        if (context == null) {
            return 0;
        }

        String[] stored =
                game.getStoredValue(CANCELLED_HITS + player.getFaction()).split("\\|", 4);
        if (stored.length != 4
                || !stored[0].equals(context.tilePosition())
                || !stored[1].equals(context.unitHolderName())
                || !stored[2].equals(Integer.toString(context.round()))) {
            return 0;
        }

        return NumberUtils.toInt(stored[3]);
    }

    private static CombatContext getCombatContext(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || combat.tilePosition() == null
                || combat.unitHolderName() == null
                || !combat.factions().contains(player.getFaction())) {
            return null;
        }

        int round = 1;
        for (String faction : combat.factions()) {
            String tracker = "combatRoundTracker" + faction + combat.tilePosition() + combat.unitHolderName();
            round = Math.max(round, NumberUtils.toInt(game.getStoredValue(tracker), 0));
        }

        return new CombatContext(combat.tilePosition(), combat.unitHolderName(), round, combat.factions());
    }

    private static void sendGroundAssignHitsButtons(
            ButtonInteractionEvent event, Game game, Player opponent, Tile tile, String planetName, int hits) {
        game.setStoredValue(opponent.getFaction() + "latestAssignHits", "groundcombat");

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                opponent.factionButtonChecker() + "autoAssignGroundHits_" + planetName + "_" + hits,
                "Auto-Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.gray(
                opponent.factionButtonChecker() + "cancelGroundHits_" + tile.getPosition() + "_" + hits,
                "Cancel a Hit"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                opponent.getRepresentationNoPing() + ", assign " + hits + " hit" + (hits == 1 ? "" : "s")
                        + " from _Mirror Shielding_.",
                buttons);
    }

    private record CombatContext(String tilePosition, String unitHolderName, int round, List<String> factions) {}
}
