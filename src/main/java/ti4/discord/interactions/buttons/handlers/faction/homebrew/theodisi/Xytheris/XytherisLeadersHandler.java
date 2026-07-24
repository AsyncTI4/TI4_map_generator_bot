package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Xytheris;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class XytherisLeadersHandler {
    public static final String MYRIX_AGENT_WINDOW = "myrixAgentWindow";
    public static final String MYRIX_AGENT_UNIT = "myrixAgentUnit_";
    private static final String MYRIX_AGENT_PENDING = "myrixAgentPending";
    private static final String AGENT_ID = "xytherisagent";
    private static final String USE_MYRIX_AGENT = "useMyrixAgent";
    private static final String MYRIX_SHIP = "myrixAgentShip_";
    private static final String HERO_ID = "xytherishero";
    private static final String HERO_ROLL_KEY = "xytherisHeroRoll_";
    private static final String USE_HERO_ROLL = "useXytherisHeroRoll_";
    private static final String DECLINE_HERO_ROLL = "declineXytherisHeroRoll_";

    // Agent
    public void offerMyrixAgentButtons(Game game, Player activPlayer, Tile activeTile) {
        game.setStoredValue(MYRIX_AGENT_WINDOW, activPlayer.getFaction());

        if (!activPlayer.hasUnexhaustedLeader(AGENT_ID)) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                activPlayer.getCorrectChannel(),
                activPlayer.getRepresentationUnfogged() + ", you may exhaust _Myrix, the Wax-Sealed_.",
                List.of(
                        Buttons.green(activPlayer.factionButtonChecker() + USE_MYRIX_AGENT, "Use Myrix"),
                        Buttons.red(activPlayer.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler(USE_MYRIX_AGENT)
    public static void useMyrixAgent(ButtonInteractionEvent event, Game game, Player player) {
        Leader agent = player.getLeader(AGENT_ID).orElse(null);
        Player target = game.getPlayerFromColorOrFaction(game.getStoredValue(MYRIX_AGENT_WINDOW));
        if (agent == null
                || !player.hasUnexhaustedLeader(AGENT_ID)
                || target == null
                || target != game.getActivePlayer()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "_Myrix, the Wax-Sealed_ can only be used immediately after a player activates a system.");
            return;
        }

        Map<String, Button> buttonsByUnitLocation = new LinkedHashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(target)) {
                    UnitModel unit = target.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                    if (unit == null || !unit.getIsShip()) {
                        continue;
                    }
                    String unitLocation = tile.getPosition() + ";" + holder.getName() + ";" + unitKey.asyncID();
                    buttonsByUnitLocation.put(
                            unitLocation,
                            Buttons.green(
                                    target.factionButtonChecker() + MYRIX_SHIP + unitLocation,
                                    "Choose 1 " + unitKey.humanReadableName() + " in " + tile.getPosition(),
                                    unitKey.unitEmoji()));
                }
            }
        }

        if (buttonsByUnitLocation.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player has no ships to select.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, player, agent);
        game.removeStoredValue(MYRIX_AGENT_WINDOW);
        game.setStoredValue(MYRIX_AGENT_PENDING, target.getFaction());
        List<Button> buttons = new ArrayList<>(buttonsByUnitLocation.values());
        String prompt = target.getRepresentationUnfogged()
                + ", choose a ship to gain capacity and PRODUCTION from _Myrix, the Wax-Sealed_.";
        for (int index = 0; index < buttons.size(); index += 25) {
            MessageHelper.sendMessageToChannelWithButtons(
                    target.getCorrectChannel(), prompt, buttons.subList(index, Math.min(index + 25, buttons.size())));
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MYRIX_SHIP)
    public static void selectMyrixAgentShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game.getActivePlayer() != player || !player.getFaction().equals(game.getStoredValue(MYRIX_AGENT_PENDING))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] unitLocation = buttonID.substring(MYRIX_SHIP.length()).split(";", 3);
        Tile tile = unitLocation.length == 3 ? game.getTileByPosition(unitLocation[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(unitLocation[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(unitLocation[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        if (unit == null || !unit.getIsShip()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int abilityCount = getUnitAbilityCount(unit);
        game.setStoredValue(MYRIX_AGENT_UNIT + player.getFaction(), String.join(";", unitLocation));
        game.removeStoredValue(MYRIX_AGENT_PENDING);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " selected " + unit.getUnitEmoji() + " " + unit.getName()
                        + " for _Myrix, the Wax-Sealed_ (+" + abilityCount
                        + " capacity and PRODUCTION; it will move before identical ships).");
        ButtonHelper.deleteMessage(event);
    }

    public static int getMyrixAgentBonus(Game game, Player player, Tile tile, UnitHolder holder, UnitKey unitKey) {
        String[] values =
                game.getStoredValue(MYRIX_AGENT_UNIT + player.getFaction()).split(";", 3);
        if (values.length != 3
                || !player.unitBelongsToPlayer(unitKey)
                || !tile.getPosition().equals(values[0])
                || !holder.getName().equals(values[1])
                || !unitKey.asyncID().equals(values[2])
                || holder.getUnitCount(unitKey) < 1) {
            return 0;
        }
        UnitModel unit = player.getUnitFromAsyncID(unitKey.asyncID());
        return unit != null && unit.getIsShip() ? getUnitAbilityCount(unit) : 0;
    }

    public static void moveMyrixAgentShipToActiveSystem(Game game, Player player, Tile activeSystem) {
        String[] selection =
                game.getStoredValue(MYRIX_AGENT_UNIT + player.getFaction()).split(";", 3);
        if (selection.length != 3) {
            return;
        }

        var moved = game.getTacticalActionDisplacement().get(selection[0] + "-" + selection[1]);
        if (moved == null
                || moved.entrySet().stream()
                        .noneMatch(entry -> player.unitBelongsToPlayer(entry.getKey())
                                && selection[2].equals(entry.getKey().asyncID())
                                && entry.getValue().stream()
                                                .mapToInt(Integer::intValue)
                                                .sum()
                                        > 0)) {
            return;
        }
        game.setStoredValue(
                MYRIX_AGENT_UNIT + player.getFaction(), activeSystem.getPosition() + ";space;" + selection[2]);
    }

    public static void clearMyrixAgentEffects(Game game) {
        game.removeStoredValue(MYRIX_AGENT_WINDOW);
        game.removeStoredValue(MYRIX_AGENT_PENDING);
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(MYRIX_AGENT_UNIT + player.getFaction());
        }
    }

    // Hero
    public static boolean offerHeroUnitAbilityRoll(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder holder,
            CombatRollType rollType) {
        if (!hasActiveHero(player) || !isUnitAbilityRoll(rollType)) {
            return false;
        }

        String context = getHeroRollContext(tile, holder, rollType);
        String key = HERO_ROLL_KEY + player.getFaction();
        String currentRoll = game.getStoredValue(key);
        if (("apply|" + context).equals(currentRoll) || ("decline|" + context).equals(currentRoll)) {
            return false;
        }
        if ("used".equals(currentRoll)) {
            return false;
        }
        if (currentRoll.startsWith("pending|")) {
            return true;
        }

        game.setStoredValue(key, "pending|" + context);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", you may use _Call of the Queen - The Endless Swarm_ to apply +4 to this "
                        + rollType
                        + " roll.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_HERO_ROLL + context,
                                "Apply +4 with Call of the Queen"),
                        Buttons.red(player.factionButtonChecker() + DECLINE_HERO_ROLL + context, "Roll Without +4")));
        return true;
    }

    public static boolean hasHeroUnitAbilityRollBonus(
            Game game, Player player, Tile tile, UnitHolder holder, CombatRollType rollType) {
        return ("apply|" + getHeroRollContext(tile, holder, rollType))
                .equals(game.getStoredValue(HERO_ROLL_KEY + player.getFaction()));
    }

    @ButtonHandler(USE_HERO_ROLL)
    public static void useHeroUnitAbilityRoll(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveHeroUnitAbilityRoll(event, game, player, buttonID, true);
    }

    @ButtonHandler(DECLINE_HERO_ROLL)
    public static void declineHeroUnitAbilityRoll(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveHeroUnitAbilityRoll(event, game, player, buttonID, false);
    }

    public static void clearHeroUnitAbilityRoll(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(HERO_ROLL_KEY + player.getFaction());
        }
    }

    private static void resolveHeroUnitAbilityRoll(
            ButtonInteractionEvent event, Game game, Player player, String buttonID, boolean applyBonus) {
        String prefix = applyBonus ? USE_HERO_ROLL : DECLINE_HERO_ROLL;
        String[] values = buttonID.substring(prefix.length()).split(";", 3);
        Tile tile = values.length == 3 ? game.getTileByPosition(values[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        CombatRollType rollType;
        try {
            rollType = values.length == 3 ? CombatRollType.valueOf(values[2]) : null;
        } catch (IllegalArgumentException e) {
            rollType = null;
        }

        String context = rollType == null || holder == null ? "" : getHeroRollContext(tile, holder, rollType);
        String key = HERO_ROLL_KEY + player.getFaction();
        if (!hasActiveHero(player)
                || !isUnitAbilityRoll(rollType)
                || !("pending|" + context).equals(game.getStoredValue(key))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(key, (applyBonus ? "apply|" : "decline|") + context);
        CombatRollService.secondHalfOfCombatRoll(player, game, event, tile, holder.getName(), rollType, false);
        if (applyBonus) {
            game.setStoredValue(key, "used");
        } else {
            game.removeStoredValue(key);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasActiveHero(Player player) {
        return player.getLeader(HERO_ID).map(Leader::isActive).orElse(false);
    }

    private static boolean isUnitAbilityRoll(CombatRollType rollType) {
        return rollType == CombatRollType.SpaceCannonOffence
                || rollType == CombatRollType.SpaceCannonDefence
                || rollType == CombatRollType.AFB
                || rollType == CombatRollType.bombardment;
    }

    private static String getHeroRollContext(Tile tile, UnitHolder holder, CombatRollType rollType) {
        return tile.getPosition() + ";" + holder.getName() + ";" + rollType.name();
    }

    // Shared Xytheris Ability roll counter
    private static int getUnitAbilityCount(UnitModel unit) {
        return (unit.getSpaceCannonDieCount() > 0 ? 1 : 0)
                + (unit.getBombardDieCount() > 0 ? 1 : 0)
                + (unit.getSustainDamage() ? 1 : 0)
                + (unit.getProductionValue() > 0 || unit.getBasicProduction() != null ? 1 : 0)
                + (unit.getPlanetaryShield() ? 1 : 0)
                + (unit.getAbility().stream().anyMatch(ability -> ability.toLowerCase(Locale.ROOT)
                                .contains("deploy"))
                        ? 1
                        : 0)
                + (unit.getAfbDieCount() > 0 ? 1 : 0);
    }
}
