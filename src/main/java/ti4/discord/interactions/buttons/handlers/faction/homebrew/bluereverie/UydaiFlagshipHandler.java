package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class UydaiFlagshipHandler {
    private static final String REROLL = "uydaiFlagshipReroll_";
    private static final String USE_REROLL = "useUydaiFlagshipReroll_";
    private static final String SELECT_PLAYER = "selectUydaiFlagshipRerollPlayer_";
    private static final String SELECT_UNIT = "selectUydaiFlagshipRerollUnit_";
    private static final String DECLINE = "declineUydaiFlagshipReroll_";

    public static void addCombatButton(
            List<Button> buttons,
            Game game,
            Tile tile,
            UnitHolder combatHolder,
            Player firstCombatant,
            Player secondCombatant,
            int round) {
        if (combatHolder == null || firstCombatant == secondCombatant) return;
        for (Player flagshipOwner : game.getRealPlayers()) {
            if (!ButtonHelper.doesPlayerHaveFSHere("uydai_flagship", flagshipOwner, tile)) continue;
            String key = rerollKey(flagshipOwner, tile, combatHolder, round);
            if (!game.getStoredValue(key).isEmpty()) continue;

            buttons.add(Buttons.gray(
                    flagshipOwner.factionButtonChecker()
                            + USE_REROLL
                            + payload(
                                    tile,
                                    combatHolder,
                                    round,
                                    firstCombatant.getFaction() + "," + secondCombatant.getFaction()),
                    "Use Desert Sage Reroll",
                    FactionEmojis.uydai));
        }
    }

    @ButtonHandler(USE_REROLL)
    public static void useReroll(ButtonInteractionEvent event, Game game, Player flagshipOwner, String buttonID) {
        RerollContext context = parseContext(game, buttonID.substring(USE_REROLL.length()), flagshipOwner);
        String[] combatants =
                context == null ? new String[0] : context.targetFaction().split(",", 2);
        if (combatants.length != 2
                || !ButtonHelper.doesPlayerHaveFSHere("uydai_flagship", flagshipOwner, context.tile())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Desert Sage reroll is no longer available.");
            return;
        }
        Player firstCombatant = game.getPlayerFromColorOrFaction(combatants[0]);
        Player secondCombatant = game.getPlayerFromColorOrFaction(combatants[1]);
        if (firstCombatant == null || secondCombatant == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Those combatants are no longer available.");
            return;
        }
        context = new RerollContext(
                context.tile(),
                context.combatHolder(),
                getCombatRound(game, firstCombatant, secondCombatant, context.tile(), context.combatHolder()),
                context.targetFaction());
        String key = rerollKey(flagshipOwner, context.tile(), context.combatHolder(), context.round());
        if (!game.getStoredValue(key).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That Desert Sage reroll has already been used this combat round.");
            return;
        }
        game.setStoredValue(key, "pending");
        List<Button> buttons = List.of(
                Buttons.gray(
                        flagshipOwner.factionButtonChecker()
                                + SELECT_PLAYER
                                + payload(
                                        context.tile(),
                                        context.combatHolder(),
                                        context.round(),
                                        firstCombatant.getFaction()),
                        "Reroll " + firstCombatant.getFactionNameOrColor(),
                        FactionEmojis.uydai),
                Buttons.gray(
                        flagshipOwner.factionButtonChecker()
                                + SELECT_PLAYER
                                + payload(
                                        context.tile(),
                                        context.combatHolder(),
                                        context.round(),
                                        secondCombatant.getFaction()),
                        "Reroll " + secondCombatant.getFactionNameOrColor(),
                        FactionEmojis.uydai),
                Buttons.red(
                        flagshipOwner.factionButtonChecker()
                                + DECLINE
                                + payload(context.tile(), context.combatHolder(), context.round(), ""),
                        "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                flagshipOwner.getRepresentationNoPing()
                        + ", choose a player to reroll 1 combat die with the _Desert Sage_. You must still declare which die is being rerolled.",
                buttons);
    }

    @ButtonHandler(SELECT_PLAYER)
    public static void selectPlayer(ButtonInteractionEvent event, Game game, Player flagshipOwner, String buttonID) {
        RerollContext context = parseContext(game, buttonID.substring(SELECT_PLAYER.length()), flagshipOwner);
        Player combatant = context == null ? null : game.getPlayerFromColorOrFaction(context.targetFaction());
        if (combatant == null || !isPending(game, flagshipOwner, context)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Desert Sage reroll is no longer available.");
            return;
        }
        List<UnitModel> units = CombatRollService.getUnitsInCombat(
                        context.tile(), context.combatHolder(), combatant, event, CombatRollType.combatround, game)
                .keySet()
                .stream()
                .toList();
        if (units.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player no longer has units in this combat.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitModel unit : units) {
            buttons.add(Buttons.gray(
                    flagshipOwner.factionButtonChecker() + SELECT_UNIT
                            + payload(context.tile(), context.combatHolder(), context.round(), combatant.getFaction())
                            + "|"
                            + unit.getAsyncId(),
                    "Reroll 1 " + unit.getName(),
                    unit.getUnitEmoji()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                flagshipOwner.getRepresentationNoPing()
                        + ", choose the unit that will reroll 1 combat die. You must still declare which die is being rerolled.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_UNIT)
    public static void selectUnit(ButtonInteractionEvent event, Game game, Player flagshipOwner, String buttonID) {
        String[] payload = buttonID.substring(SELECT_UNIT.length()).split("\\|", 5);
        if (payload.length != 5) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Desert Sage reroll is no longer available.");
            return;
        }
        RerollContext context =
                parseContext(game, String.join("|", payload[0], payload[1], payload[2], payload[3]), flagshipOwner);
        Player combatant = context == null ? null : game.getPlayerFromColorOrFaction(context.targetFaction());
        UnitModel unit = combatant == null ? null : combatant.getUnitFromAsyncID(payload[4]);
        boolean unitInCombat = unit != null
                && CombatRollService.getUnitsInCombat(
                                context.tile(),
                                context.combatHolder(),
                                combatant,
                                event,
                                CombatRollType.combatround,
                                game)
                        .containsKey(unit);
        if (!isPending(game, flagshipOwner, context)
                || !ButtonHelper.doesPlayerHaveFSHere("uydai_flagship", flagshipOwner, context.tile())
                || !unitInCombat) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That unit is no longer eligible for the Desert Sage reroll.");
            return;
        }
        game.setStoredValue(rerollKey(flagshipOwner, context.tile(), context.combatHolder(), context.round()), "used");
        DiceHelper.Die reroll = DiceHelper.rollDice(
                        unit.getCombatDieHitsOnForAbility(CombatRollType.combatround, combatant), 1)
                .getFirst();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                flagshipOwner.getRepresentationNoPing() + " used the _Desert Sage_ to have "
                        + combatant.getRepresentationNoPing() + " reroll 1 die from " + unit.getName()
                        + ". The reroll is " + reroll.getGreenDieIfSuccessOrRedDieIfFailure()
                        + ". They must still declare which die is being rerolled and adjust the hit total if needed.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE)
    public static void decline(ButtonInteractionEvent event, Game game, Player flagshipOwner, String buttonID) {
        RerollContext context = parseContext(game, buttonID.substring(DECLINE.length()), flagshipOwner);
        if (context != null) {
            game.setStoredValue(
                    rerollKey(flagshipOwner, context.tile(), context.combatHolder(), context.round()), "declined");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean isPending(Game game, Player flagshipOwner, RerollContext context) {
        return context != null
                && "pending"
                        .equals(game.getStoredValue(
                                rerollKey(flagshipOwner, context.tile(), context.combatHolder(), context.round())));
    }

    private static RerollContext parseContext(Game game, String payload, Player flagshipOwner) {
        String[] parts = payload.split("\\|", 4);
        if (parts.length != 4) return null;
        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder combatHolder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        if (tile == null
                || combatHolder == null
                || !ButtonHelper.doesPlayerHaveFSHere("uydai_flagship", flagshipOwner, tile)) {
            return null;
        }
        try {
            return new RerollContext(tile, combatHolder, Integer.parseInt(parts[2]), parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String payload(Tile tile, UnitHolder combatHolder, int round, String targetFaction) {
        return tile.getPosition() + "|" + combatHolder.getName() + "|" + round + "|" + targetFaction;
    }

    private static String rerollKey(Player flagshipOwner, Tile tile, UnitHolder combatHolder, int round) {
        return REROLL + flagshipOwner.getFaction() + "_" + tile.getPosition() + "_" + combatHolder.getName() + "_"
                + round;
    }

    private static int getCombatRound(
            Game game, Player firstCombatant, Player secondCombatant, Tile tile, UnitHolder combatHolder) {
        return Math.max(
                getCombatRound(game, firstCombatant, tile, combatHolder),
                getCombatRound(game, secondCombatant, tile, combatHolder));
    }

    private static int getCombatRound(Game game, Player player, Tile tile, UnitHolder combatHolder) {
        String round = game.getStoredValue(
                "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatHolder.getName());
        return round.isEmpty() ? 1 : Integer.parseInt(round);
    }

    private record RerollContext(Tile tile, UnitHolder combatHolder, int round, String targetFaction) {}
}
