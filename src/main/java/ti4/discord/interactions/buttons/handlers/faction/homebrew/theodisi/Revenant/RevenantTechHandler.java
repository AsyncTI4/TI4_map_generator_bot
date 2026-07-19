package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;

@UtilityClass
public class RevenantTechHandler {
    private static final String ETERNAL_AEGIS = "threvenantr";
    private static final String USE_ETERNAL_AEGIS = "useEternalAegis_";

    public static void addEternalAegisButton(
            List<Button> buttons,
            Game game,
            Player defender,
            Player attacker,
            Tile tile,
            UnitHolder combatOnHolder,
            int hits) {
        int canceledHits = Math.min(hits, getEternalAegisCancellationCount(defender));
        if (!defender.hasTech(ETERNAL_AEGIS) || canceledHits < 1) {
            return;
        }

        buttons.add(Buttons.green(
                defender.factionButtonChecker() + USE_ETERNAL_AEGIS + attacker.getFaction() + "|" + tile.getPosition()
                        + "|" + combatOnHolder.getName() + "|" + hits,
                "Eternal Aegis: Cancel " + canceledHits + " Hit" + (canceledHits == 1 ? "" : "s")));
    }

    @ButtonHandler(USE_ETERNAL_AEGIS)
    public static void useEternalAegis(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(USE_ETERNAL_AEGIS.length()).split("\\|", 4);
        if (parts.length != 4 || !player.hasTech(ETERNAL_AEGIS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[1]);
        UnitHolder combatOnHolder = tile == null ? null : tile.getUnitHolders().get(parts[2]);
        int hits;
        try {
            hits = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String attackerRound = game.getStoredValue("combatRoundTracker" + parts[0] + parts[1] + parts[2]);
        int canceledHits = Math.min(hits, getEternalAegisCancellationCount(player));
        if (tile == null
                || combatOnHolder == null
                || hits < 1
                || canceledHits < 1
                || (!attackerRound.isBlank() && !"1".equals(attackerRound))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remainingHits = hits - canceledHits;
        String cancellationMessage = player.getRepresentationUnfogged() + " canceled " + canceledHits + " hit"
                + (canceledHits == 1 ? "" : "s") + " with _Eternal Aegis_.";
        if (remainingHits == 0) {
            event.getMessage()
                    .editMessage(cancellationMessage)
                    .setComponents(List.of())
                    .queue();
            return;
        }

        List<Button> buttons = new ArrayList<>();
        String factionChecker = player.factionButtonChecker();
        String assignmentMessage;
        if (combatOnHolder instanceof Planet) {
            buttons.add(Buttons.green(
                    factionChecker + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + remainingHits,
                    "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                    "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + "cancelGroundHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
            assignmentMessage = cancellationMessage + "\n" + player.getRepresentation() + " may autoassign "
                    + remainingHits + " hit" + (remainingHits == 1 ? "" : "s") + ".";
        } else {
            buttons.add(Buttons.green(
                    factionChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + remainingHits,
                    "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                    "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
            assignmentMessage = cancellationMessage + "\n" + player.getRepresentationNoPing()
                    + ", you may automatically assign " + (remainingHits == 1 ? "the hit" : "the hits") + ". "
                    + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, remainingHits, event, true);
        }
        event.getMessage()
                .editMessage(assignmentMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
    }

    private static int getEternalAegisCancellationCount(Player player) {
        return (int) player.getLeaders().stream()
                .filter(leader -> !leader.isLocked() && !leader.isExhausted())
                .map(Leader::getType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .count();
    }
}
