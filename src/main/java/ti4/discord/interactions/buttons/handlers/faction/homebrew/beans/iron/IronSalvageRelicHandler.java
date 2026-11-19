package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.info.RelicInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class IronSalvageRelicHandler {
    private static final String SALVAGE_COLLECTORS = "salvage_collectors";
    private static final String COLLECT = "ironCollectSalvage_";
    private static final String USE_DREAD_COMBAT = "ironUseSalvageDreadCombat_";
    private static final String COLLECTED = "ironSalvageCollected_";
    private static final String COLLECTIBLE = "ironSalvageCollectible_";
    private static final Map<UnitType, String> SALVAGE_BY_UNIT = Map.of(
            UnitType.Warsun, "salvage_war_sun",
            UnitType.Flagship, "salvage_flagship",
            UnitType.Dreadnought, "salvage_dreadnought",
            UnitType.Carrier, "salvage_carrier",
            UnitType.Cruiser, "salvage_cruiser",
            UnitType.Destroyer, "salvage_destroyer");

    public static Set<String> getSalvageRelics() {
        return Set.copyOf(SALVAGE_BY_UNIT.values());
    }

    public static boolean isSalvageRelic(String relic) {
        return getSalvageRelics().contains(relic);
    }

    public static boolean isAvailable(Game game, String relic) {
        return isSalvageRelic(relic) && game.getPlayers().values().stream().noneMatch(player -> player.hasRelic(relic));
    }

    public static void onSalvageGained(Player player) {
        CommanderUnlockCheckService.checkPlayer(player, "iron");
    }

    public static String getUnitRulesText(Player player, UnitType unitType) {
        String relic = SALVAGE_BY_UNIT.get(unitType);
        if (relic == null || !player.hasRelic(relic)) return "";
        return Mapper.getRelic(relic).getText();
    }

    public static boolean hasSalvageForUnitUpgrade(Player player, String baseUpgrade) {
        String unit = baseUpgrade.replace("2", "");
        return SALVAGE_BY_UNIT.entrySet().stream()
                .anyMatch(entry -> unit.startsWith(entry.getKey().getValue()) && player.hasRelic(entry.getValue()));
    }

    public static void offerSalvageCollectors(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits, boolean combat) {
        if (!combat) return;
        for (Player player : game.getRealPlayersNNeutral()) {
            if (!player.hasAbility(SALVAGE_COLLECTORS)
                    || hasCollected(game, player)
                    || !isParticipatingInCombat(game, player)) continue;
            List<String> relics = destroyedUnits.stream()
                    .map(RemovedUnit::unitKey)
                    .map(key -> SALVAGE_BY_UNIT.get(key.unitType()))
                    .filter(relic -> relic != null && isAvailable(game, relic))
                    .distinct()
                    .toList();
            if (relics.isEmpty()) continue;
            setCollectibleSalvage(game, player, relics);
            List<Button> buttons = relics.stream()
                    .map(relic -> Buttons.gray(
                            player.factionButtonChecker() + COLLECT + relic,
                            Mapper.getRelic(relic).getName(),
                            FactionEmojis.iron))
                    .toList();
            MessageHelper.sendMessageToChannelWithButtons(
                    game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                    player.getRepresentation() + ", choose a Salvage card to collect with **Salvage Collectors**.",
                    buttons);
        }
    }

    @ButtonHandler(COLLECT)
    public static void collectSalvage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring(COLLECT.length());
        if (!player.hasAbility(SALVAGE_COLLECTORS)
                || hasCollected(game, player)
                || !isCollectibleSalvage(game, player, relic)
                || !isAvailable(game, relic)) return;
        player.addRelic(relic);
        onSalvageGained(player);
        game.setStoredValue(COLLECTED + player.getFaction(), "true");
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + " collected "
                        + Mapper.getRelic(relic).getNameRepresentation() + " with **Salvage Collectors**.");
        RelicInfoService.sendRelicInfo(player);
        ButtonHelper.deleteMessage(event);
    }

    public static void addDreadnoughtCombatButton(List<Button> buttons, Player player, Tile tile) {
        if (!player.hasRelic("salvage_dreadnought")
                || tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, player) < 1) return;
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + USE_DREAD_COMBAT + tile.getPosition(),
                "Use Dreadnought",
                FactionEmojis.iron));
    }

    public static void offerDreadnoughtCombatButton(Game game, Player player, Tile tile) {
        if (!game.isFowMode()
                || !player.hasRelic("salvage_dreadnought")
                || tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, player) < 1) return;
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may use _Dreadnought_.",
                Buttons.blue(
                        player.factionButtonChecker() + USE_DREAD_COMBAT + tile.getPosition(),
                        "Use Dreadnought",
                        FactionEmojis.iron));
    }

    @ButtonHandler(USE_DREAD_COMBAT)
    public static void useDreadnoughtCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_DREAD_COMBAT.length()));
        if (tile == null
                || !player.hasRelic("salvage_dreadnought")
                || tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, player) < 1) return;
        UnitModel dreadnought = player.getUnitByType(UnitType.Dreadnought);
        if (dreadnought == null) return;
        int threshold = dreadnought.getCombatDieHitsOnForAbility(CombatRollType.combatround, player);
        List<DiceHelper.Die> rolls = DiceHelper.rollDice(threshold, 1);
        int hits = DiceHelper.countSuccesses(rolls);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                CombatMessageHelper.displayCombatSummary(
                                player, tile, tile.getSpaceUnitHolder(), CombatRollType.combatround)
                        + "_Dreadnought_:\n"
                        + CombatMessageHelper.displayUnitRoll(dreadnought, threshold, 0, 1, 1, 0, rolls, hits)
                        + CombatMessageHelper.displayHitResults(hits));
        if (hits > 0) {
            Player opponent = getCombatOpponent(game, player);
            if (opponent != null) {
                CombatRollService.sendSpaceAssignHitsButtons(
                        event,
                        game,
                        opponent,
                        tile,
                        hits,
                        game.isFowMode() ? opponent.getCorrectChannel() : event.getMessageChannel());
            }
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void clearCombatState(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(COLLECTED + player.getFaction());
            game.removeStoredValue(COLLECTIBLE + player.getFaction());
        }
    }

    public static boolean carrierIgnoresAnomalies(Game game, Player player, UnitType unitType) {
        return player.isActivePlayer() && unitType == UnitType.Carrier && player.hasRelic("salvage_carrier");
    }

    private static Player getCombatOpponent(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null) return null;
        return combat.factions().stream()
                .filter(faction -> !player.getFaction().equals(faction))
                .map(game::getPlayerFromColorOrFaction)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static boolean hasCollected(Game game, Player player) {
        return "true".equals(game.getStoredValue(COLLECTED + player.getFaction()));
    }

    private static void setCollectibleSalvage(Game game, Player player, List<String> relics) {
        Set<String> collectible = new java.util.HashSet<>(getCollectibleSalvage(game, player));
        collectible.addAll(relics);
        game.setStoredValue(COLLECTIBLE + player.getFaction(), String.join("|", collectible));
    }

    private static boolean isCollectibleSalvage(Game game, Player player, String relic) {
        return getCollectibleSalvage(game, player).contains(relic);
    }

    private static Set<String> getCollectibleSalvage(Game game, Player player) {
        String stored = game.getStoredValue(COLLECTIBLE + player.getFaction());
        return stored.isBlank() ? Set.of() : Set.of(stored.split("\\|"));
    }

    private static boolean isParticipatingInCombat(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        return combat != null && combat.factions().contains(player.getFaction());
    }
}
