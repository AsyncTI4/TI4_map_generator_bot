package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.CombatStatsService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class PonthousAbilityHandler {
    private static final String USE_PONTHOUS = "usePonthousLegendaryAbility_";
    private static final String PONTHOUS = "ponthous";
    private static final String PONTHOUS_BOTH = "attachment_ponthousboth.png";
    private static final String PONTHOUS_RES = "attachment_positiveres3.png";
    private static final String PONTHOUS_INF = "attachment_positiveinf3.png";
    private static final String LAST_STAND = "last_stand";
    private static final String USE_LAST_STAND = "useLastStand_";
    private static final String DECLINE_LAST_STAND = "declineLastStand_";
    private static final String RESOLVE_LAST_STAND = "resolveLastStand_";

    // Ponthous LPC
    public static List<Button> offerFracturedSouls(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(player.factionButtonChecker() + USE_PONTHOUS + "res", "Ponthous +"));
        buttons.add(Buttons.red(player.factionButtonChecker() + USE_PONTHOUS + "inf", "Ponthous -"));

        return buttons;
    }

    @ButtonHandler(USE_PONTHOUS)
    public static void resolvePonthousLegendaryPlanetAbility(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !player.getPlanets().contains(PONTHOUS)
                || !player.getExhaustedPlanets().contains(PONTHOUS)
                || !player.getExhaustedPlanetsAbilities().contains(PONTHOUS)) {
            return;
        }

        String resOrInf = buttonID.replace(USE_PONTHOUS, "");

        if ("res".equals(resOrInf)) {
            setPonthousAttachment(game, PONTHOUS_RES);
        } else if ("inf".equals(resOrInf)) {
            setPonthousAttachment(game, PONTHOUS_INF);
        } else {
            return;
        }

        player.refreshPlanet(PONTHOUS);
        ButtonHelper.deleteMessage(event);
    }

    public static void resetFracturedSouls(Game game, Player player) {
        if (player.hasPlanet(PONTHOUS)) {
            setPonthousAttachment(game, PONTHOUS_BOTH);
        }
    }

    // Last Stand
    public static boolean offerLastStand(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder holder,
            UnitKey unitKey,
            UnitState state,
            String assignHitsType) {
        if (!player.hasAbility(LAST_STAND)
                || !List.of("spacecombat", "groundcombat").contains(assignHitsType)
                || tile == null
                || holder == null
                || unitKey == null
                || !tile.getPosition().equals(game.getActiveSystem())) {
            return false;
        }

        UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
        Map<UnitModel, Integer> participants =
                CombatRollService.getUnitsInCombat(tile, holder, player, event, CombatRollType.combatround, game);
        if (unitModel == null
                || holder.getUnitCountForState(unitKey, state) != 1
                || !participants.containsKey(unitModel)
                || participants.values().stream().mapToInt(Integer::intValue).sum() != 1) {
            return false;
        }

        Player opponent = CombatRollService.getOpponent(player, List.of(holder), game);
        if (opponent == null) {
            return false;
        }

        String payload = String.join(
                "|", tile.getPosition(), holder.getName(), unitKey.asyncID(), state.name(), opponent.getFaction());
        List<Button> buttons = List.of(
                Buttons.red(player.factionButtonChecker() + USE_LAST_STAND + payload, "Use Last Stand"),
                Buttons.gray(
                        player.factionButtonChecker() + DECLINE_LAST_STAND + payload,
                        "Destroy " + unitKey.humanReadableName()));
        MessageHelper.editMessageButtons(event, buttons);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may use _Last Stand_ because your last remaining ship would be destroyed.");
        return true;
    }

    public static boolean requiresManualLastStandAssignment(
            Game game, Player player, Tile tile, UnitHolder holder, ButtonInteractionEvent event) {
        if (!player.hasAbility(LAST_STAND)
                || tile == null
                || holder == null
                || !tile.getPosition().equals(game.getActiveSystem())) {
            return false;
        }

        return CombatRollService.getUnitsInCombat(tile, holder, player, event, CombatRollType.combatround, game)
                        .values()
                        .stream()
                        .mapToInt(Integer::intValue)
                        .sum()
                == 1;
    }

    @ButtonHandler(USE_LAST_STAND)
    public static void useLastStand(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(USE_LAST_STAND, "").split("\\|", -1);
        if (payload.length != 5) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[1]);
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(payload[2]), player.getColor());
        UnitState state = Units.findUnitState(payload[3]);
        Player opponent = game.getPlayerFromColorOrFaction(payload[4]);
        UnitModel unitModel = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (!player.hasAbility(LAST_STAND)
                || tile == null
                || holder == null
                || unitKey == null
                || unitModel == null
                || opponent == null
                || !tile.getPosition().equals(game.getActiveSystem())
                || holder.getUnitCountForState(unitKey, state) != 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        CombatStatsService.CombatRoundProfile profile =
                CombatStatsService.getCombatRoundProfile(true, unitModel, player, tile, opponent, false);
        List<Die> dice = DiceHelper.rollDice(profile.hitsOn(), profile.diceCount());
        int hits = DiceHelper.countSuccesses(dice);
        String result = player.getRepresentationNoPing() + " used _Last Stand_ with " + unitKey.humanReadableName()
                + ".\n> "
                + DiceHelper.formatDiceOutput(dice);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);
        ButtonHelper.deleteMessage(event);

        if (hits > 0) {
            List<Button> hitButtons = new ArrayList<>();
            if (!"space".equals(holder.getName())) {
                hitButtons.add(Buttons.green(
                        opponent.factionButtonChecker() + "autoAssignGroundHits_" + holder.getName() + "_" + hits,
                        "Auto-assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
                hitButtons.add(Buttons.red(
                        opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                                + "deleteThis_groundcombat",
                        "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
            } else {
                hitButtons.add(Buttons.green(
                        opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits,
                        "Auto-assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
                hitButtons.add(Buttons.red(
                        opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                                + "deleteThis_spacecombat",
                        "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    opponent.getRepresentationNoPing() + ", assign the hits from _Last Stand_.",
                    hitButtons);
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", after the _Last Stand_ hits are assigned, resolve whether your unit survives.",
                List.of(Buttons.green(
                        player.factionButtonChecker() + RESOLVE_LAST_STAND + String.join("|", payload),
                        "Resolve Last Stand")));
    }

    @ButtonHandler(DECLINE_LAST_STAND)
    public static void declineLastStand(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveLastStandUnit(event, game, player, buttonID.replace(DECLINE_LAST_STAND, ""), true);
    }

    @ButtonHandler(RESOLVE_LAST_STAND)
    public static void resolveLastStand(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.replace(RESOLVE_LAST_STAND, "");
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 5) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        Player opponent = game.getPlayerFromColorOrFaction(parts[4]);
        boolean opponentStillParticipates = holder != null
                && opponent != null
                && CombatRollService.getUnitsInCombat(tile, holder, opponent, event, CombatRollType.combatround, game)
                                .values()
                                .stream()
                                .mapToInt(Integer::intValue)
                                .sum()
                        > 0;
        resolveLastStandUnit(event, game, player, payload, opponentStillParticipates);
    }

    private static void resolveLastStandUnit(
            ButtonInteractionEvent event, Game game, Player player, String payload, boolean destroyUnit) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 5) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(parts[2]), player.getColor());
        UnitState state = Units.findUnitState(parts[3]);
        if (!player.hasAbility(LAST_STAND)
                || tile == null
                || holder == null
                || unitKey == null
                || !tile.getPosition().equals(game.getActiveSystem())
                || holder.getUnitCountForState(unitKey, state) != 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (destroyUnit) {
            DestroyUnitService.destroyUnit(
                    event, tile, game, new ParsedUnit(unitKey, 1, holder.getName()), true, state);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " destroyed " + unitKey.humanReadableName()
                            + ", because an opposing participating unit remains.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " keeps " + unitKey.humanReadableName()
                            + ", because _Last Stand_ destroyed all opposing participating units.");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void setPonthousAttachment(Game game, String attachment) {
        Planet ponthous = game.getPlanetsInfo().get(PONTHOUS);
        if (ponthous == null) {
            return;
        }
        ponthous.removeToken(PONTHOUS_BOTH);
        ponthous.removeToken(PONTHOUS_RES);
        ponthous.removeToken(PONTHOUS_INF);
        ponthous.addToken(attachment);
    }
}
