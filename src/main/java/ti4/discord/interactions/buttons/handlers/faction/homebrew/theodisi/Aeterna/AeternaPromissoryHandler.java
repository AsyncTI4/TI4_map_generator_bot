package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AeternaPromissoryHandler {
    private static final String AETERNA_PN = "thpnaeterna";
    private static final String STASIS_FIGHTERS = "aeternaStasisFighters_";
    private static final String FUNERAL_SERVICES_COMBAT = "aeternaFuneralServicesCombat_";
    private static final String USE_FUNERAL_SERVICES = "useFuneralServices_";
    private static final String PLACE_STASIS_FIGHTER = "placeAeternaStasisFighter_";

    public static void addFuneralServicesButton(
            Game game, Player player, Player opponent, Tile tile, String combatHolder, List<Button> buttons) {
        Player owner = game == null ? null : game.getPNOwner(AETERNA_PN);
        if (player == null
                || opponent == null
                || tile == null
                || owner == null
                || owner == player
                || !player.hasPlayablePromissoryInHand(AETERNA_PN)
                || player.getPromissoryNotesInPlayArea().contains(AETERNA_PN)) {
            return;
        }
        String combatContext = getCombatContext(game, player, opponent, tile, combatHolder);
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_FUNERAL_SERVICES + combatContext,
                "Use Funeral Services",
                FactionEmojis.aeterna));
    }

    @ButtonHandler(USE_FUNERAL_SERVICES)
    public static void useFuneralServices(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String combatContext = buttonID.substring(USE_FUNERAL_SERVICES.length());
        Player owner = game == null ? null : game.getPNOwner(AETERNA_PN);
        if (game == null
                || player == null
                || owner == null
                || owner == player
                || !player.hasPlayablePromissoryInHand(AETERNA_PN)
                || !isCurrentCombatContext(game, player, combatContext)) {
            return;
        }

        PromissoryNoteHelper.resolvePNPlay(AETERNA_PN, player, game, event);
        if (!player.getPromissoryNotesInPlayArea().contains(AETERNA_PN)) {
            return;
        }
        game.setStoredValue(FUNERAL_SERVICES_COMBAT + player.getFaction(), combatContext);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void rollForStasisFighters(GenericInteractionCreateEvent event, Game game, List<RemovedUnit> units) {
        for (RemovedUnit removed : units) {
            if (removed.unitKey().unitType() != UnitType.Fighter) {
                continue;
            }

            Player owner = removed.getPlayer(game);
            if (owner == null
                    || !owner.getPromissoryNotesInPlayArea().contains(AETERNA_PN)
                    || removed.tile() == null
                    || removed.uh() == null) {
                continue;
            }

            String combatContext = game.getStoredValue(FUNERAL_SERVICES_COMBAT + owner.getFaction());
            if (!isCurrentCombatContext(game, owner, combatContext)
                    || !combatContext.startsWith(
                            removed.tile().getPosition() + "|" + removed.uh().getName() + "|")) {
                continue;
            }

            int successes = 0;
            StringBuilder rolls = new StringBuilder();
            for (int i = 0; i < removed.getTotalRemoved(); i++) {
                DiceHelper.Die die = new DiceHelper.Die(6);
                rolls.append(die.getGreenDieIfSuccessOrRedDieIfFailure()).append(" ");
                if (die.isSuccess()) {
                    successes++;
                }
            }

            if (successes > 0) {
                int inStasis = getStasisFighterCount(game, owner);
                game.setStoredValue(STASIS_FIGHTERS + owner.getFaction(), Integer.toString(inStasis + successes));
            }

            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing() + " rolled " + rolls + " for _Funeral Services_; " + successes
                            + " fighter" + (successes == 1 ? "" : "s") + " entered stasis.");
        }
    }

    public static void offerStasisFighterPlacement(GenericInteractionCreateEvent event, Game game, Player player) {
        int count = getStasisFighterCount(game, player);
        if (!player.getPromissoryNotesInPlayArea().contains(AETERNA_PN)) {
            return;
        }
        if (count < 1) {
            Player owner = game.getPNOwner(AETERNA_PN);
            if (owner != null && owner != player) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, player, owner, AETERNA_PN);
            }
            game.removeStoredValue(STASIS_FIGHTERS + player.getFaction());
            game.removeStoredValue(FUNERAL_SERVICES_COMBAT + player.getFaction());
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_STASIS_FIGHTER + tile.getPosition(),
                    "Place fighter in " + tile.getRepresentationForButtons(game, player)));
        }

        if (buttons.isEmpty()) {
            Player owner = game.getPNOwner(AETERNA_PN);
            if (owner != null && owner != player) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, player, owner, AETERNA_PN);
            }
            game.removeStoredValue(STASIS_FIGHTERS + player.getFaction());
            game.removeStoredValue(FUNERAL_SERVICES_COMBAT + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no systems containing their ships, so their " + count
                            + " fighter" + (count == 1 ? "" : "s")
                            + " in stasis could not be placed. They were removed from stasis and _Funeral Services_"
                            + " was returned to its owner.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", please choose a system in which to place 1 fighter from stasis."
                        + " You have " + count + " remaining.",
                buttons);
    }

    @ButtonHandler(PLACE_STASIS_FIGHTER)
    public static void placeStasisFighter(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int count = getStasisFighterCount(game, player);
        Tile tile = game.getTileByPosition(buttonID.substring(PLACE_STASIS_FIGHTER.length()));

        if (count < 1
                || !player.getPromissoryNotesInPlayArea().contains(AETERNA_PN)
                || tile == null
                || !ButtonHelper.getTilesWithShipsInTheSystem(player, game).contains(tile)) {
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");
        game.setStoredValue(STASIS_FIGHTERS + player.getFaction(), Integer.toString(count - 1));
        ButtonHelper.deleteMessage(event);

        offerStasisFighterPlacement(event, game, player);
    }

    private static int getStasisFighterCount(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(STASIS_FIGHTERS + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isCurrentCombatContext(Game game, Player player, String combatContext) {
        String[] context = combatContext.split("\\|", 6);
        Player activePlayer = game.getActivePlayer();
        String combatants = game.getStoredValue("factionsInCombat");
        StartCombatService.CurrentCombat currentCombat = StartCombatService.getCurrentCombat(game);
        return context.length == 6
                && activePlayer != null
                && currentCombat != null
                && context[0].equals(currentCombat.tilePosition())
                && context[1].equals(currentCombat.unitHolderName())
                && context[2].equals(Integer.toString(game.getRound()))
                && context[3].equals(activePlayer.getFaction())
                && context[4].equals(Integer.toString(activePlayer.getInRoundTurnCount()))
                && context[5].equals(combatants)
                && currentCombat.factions().contains(player.getFaction());
    }

    private static String getCombatContext(Game game, Player player, Player opponent, Tile tile, String combatHolder) {
        Player activePlayer = game.getActivePlayer();
        String combatants = game.getStoredValue("factionsInCombat");
        return tile.getPosition() + "|" + combatHolder + "|" + game.getRound() + "|"
                + (activePlayer == null
                        ? "none|0"
                        : activePlayer.getFaction() + "|" + activePlayer.getInRoundTurnCount())
                + "|" + (combatants.isEmpty() ? player.getFaction() + "_" + opponent.getFaction() : combatants);
    }
}
