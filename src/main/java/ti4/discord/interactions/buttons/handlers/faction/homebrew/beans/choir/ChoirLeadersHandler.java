package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class ChoirLeadersHandler {
    private static final String AGENT = "choiragent";
    private static final String USE_AGENT = "choirAgentUse_";
    private static final String SELECT_AGENT_TARGET = "choirAgentTarget_";
    private static final String SELECT_SHIP = "choirAgentShip_";
    private static final String READY_PLANET = "choirAgentReadyPlanet_";
    private static final String COMMANDER = "choircommander";
    private static final String COMMANDER_MODE = "choirCommanderMode_";
    private static final String COMMANDER_SYSTEM = "choirCommanderSystem_";
    private static final String COMMANDER_UNIT = "choirCommanderUnit_";
    private static final String COMMANDER_DECLINE = "choirCommanderDecline";
    private static final String COMMANDER_PENDING = "choirCommanderPending_";
    private static final String HERO = "choirhero";
    private static final String USE_HERO = "choirHeroFuneralProcession_";

    public static void addFuneralProcessionButton(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile, boolean isSpaceCombat) {
        if (!isSpaceCombat || !canUseFuneralProcession(game, player, opponent, tile)) {
            return;
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + USE_HERO + tile.getPosition() + "|" + opponent.getFaction(),
                "Use Funeral Procession",
                FactionEmojis.choir));
    }

    public static boolean hasActiveFuneralProcession(Player player) {
        return player.getLeader(HERO).map(Leader::isActive).orElse(false);
    }

    @ButtonHandler(USE_HERO)
    public static void useFuneralProcession(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(USE_HERO.length()).split("\\|", 2);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[0]) : null;
        Player opponent = values.length == 2 ? game.getPlayerFromColorOrFaction(values[1]) : null;
        if (!canUseFuneralProcession(game, player, opponent, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.unsafeGetLeader(HERO).setActive(true);
        int damagedShips = damageOpponentShips(game, opponent, tile);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used **Funeral Procession - Last Communion** and damaged "
                        + damagedShips + " of " + opponent.getRepresentationNoPing()
                        + "'s ships. Their damaged ships may move out of systems containing their command tokens until the end of this game round.");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static Button getLyraOfTheCryptStartTurnButton(Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + USE_AGENT + player.getFaction(),
                "Use Lyra of the Crypt",
                FactionEmojis.choir);
    }

    public static Button getLyraOfTheCryptCardsInfoButton(Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_AGENT_TARGET,
                "Use Lyra of the Crypt on Someone Else",
                FactionEmojis.choir);
    }

    public static void offerKeynOfTheHollow(Game game, Player player) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER) || isKeynPending(game, player)) {
            return;
        }
        game.setStoredValue(COMMANDER_PENDING + player.getFaction(), game.getCurrentAgendaInfo());
        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + COMMANDER_MODE + "repair", "Repair Unit"),
                Buttons.red(player.factionButtonChecker() + COMMANDER_MODE + "damage", "Damage Unit"),
                Buttons.gray(player.factionButtonChecker() + COMMANDER_DECLINE, "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", your prediction was correct. Choose how to resolve **Keyn of the Hollow**.",
                buttons);
    }

    @ButtonHandler(COMMANDER_MODE)
    public static void chooseKeynOfTheHollowMode(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String mode = buttonID.substring(COMMANDER_MODE.length());
        boolean repair = "repair".equals(mode);
        boolean damage = "damage".equals(mode);
        if ((!repair && !damage) || !isKeynPending(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Tile> tiles = getKeynTiles(game, player, repair);
        if (tiles.isEmpty()) {
            clearKeynPending(game, player);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no eligible units for **Keyn of the Hollow**.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = tiles.stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + COMMANDER_SYSTEM + mode + "|" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a system containing a unit to "
                        + (repair ? "repair" : "damage") + " with **Keyn of the Hollow**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(COMMANDER_SYSTEM)
    public static void chooseKeynOfTheHollowSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(COMMANDER_SYSTEM.length()).split("\\|", 2);
        boolean repair = values.length == 2 && "repair".equals(values[0]);
        boolean damage = values.length == 2 && "damage".equals(values[0]);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[1]) : null;
        if ((!repair && !damage) || !isKeynPending(game, player) || tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getKeynUnitButtons(game, player, tile, repair);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a unit to " + (repair ? "repair" : "damage")
                        + " with **Keyn of the Hollow**.",
                NewStuffHelper.paginateWithPinnedButtons(
                        buttons,
                        List.of(Buttons.gray(player.factionButtonChecker() + COMMANDER_DECLINE, "Decline")),
                        player.factionButtonChecker() + COMMANDER_UNIT + (repair ? "repair|" : "damage|"),
                        25,
                        0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(COMMANDER_UNIT)
    public static void resolveKeynOfTheHollowUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(COMMANDER_UNIT.length()).split("\\|", 5);
        boolean repair = values.length == 5 && "repair".equals(values[0]);
        boolean damage = values.length == 5 && "damage".equals(values[0]);
        Tile tile = values.length == 5 ? game.getTileByPosition(values[1]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[2]);
        UnitKey unitKey = holder == null || values.length != 5 ? null : findUnitKey(holder, values[3], values[4]);
        UnitModel unit = unitKey == null ? null : game.getUnitFromUnitKey(unitKey);
        if ((!repair && !damage)
                || !isKeynPending(game, player)
                || !isKeynUnitEligible(player, holder, unitKey, unit, repair)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (repair) {
            holder.removeDamagedUnit(unitKey, 1);
        } else {
            holder.addDamagedUnit(unitKey, 1);
        }
        clearKeynPending(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " " + (repair ? "repaired" : "damaged") + " 1 " + unit.getName()
                        + " with **Keyn of the Hollow**.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(COMMANDER_DECLINE)
    public static void declineKeynOfTheHollow(ButtonInteractionEvent event, Game game, Player player) {
        clearKeynPending(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_AGENT)
    public static void useLyraOfTheCrypt(ButtonInteractionEvent event, Game game, Player choirPlayer, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_AGENT.length()));
        if (!choirPlayer.hasUnexhaustedLeader(AGENT)
                || target == null
                || getDamagedShips(game, target).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Leader agent = choirPlayer.getLeader(AGENT).orElse(null);
        if (agent == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, choirPlayer, agent);
        sendDamagedShipButtons(game, choirPlayer, target);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_AGENT_TARGET)
    public static void selectLyraOfTheCryptTarget(ButtonInteractionEvent event, Game game, Player choirPlayer) {
        if (!choirPlayer.hasUnexhaustedLeader(AGENT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .map(target -> Buttons.green(
                        choirPlayer.factionButtonChecker() + USE_AGENT + target.getFaction(),
                        target.getFogSafeDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                choirPlayer.getCardsInfoThread(),
                choirPlayer.getRepresentationNoPing() + ", choose a player for **Lyra of the Crypt**.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_SHIP)
    public static void selectLyraOfTheCryptShip(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] values = buttonID.substring(SELECT_SHIP.length()).split("\\|", 4);
        Player choirPlayer = values.length == 4 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        List<ShipTarget> targets = choirPlayer == null ? List.of() : getDamagedShips(game, target);
        String message =
                target.getRepresentationNoPing() + ", choose 1 damaged ship to repair with **Lyra of the Crypt**.";
        String prefix = target.factionButtonChecker() + SELECT_SHIP + (values.length > 0 ? values[0] + "|" : "");
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getDamagedShipButtons(game, target, choirPlayer, targets),
                message,
                prefix,
                buttonID)) {
            return;
        }

        Tile tile = values.length == 4 ? game.getTileByPosition(values[1]) : null;
        UnitHolder holder = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey unitKey = holder == null || values.length != 4 ? null : findUnitKey(holder, values[2], values[3]);
        UnitModel unit = unitKey == null ? null : game.getUnitFromUnitKey(unitKey);
        if (choirPlayer == null
                || !choirPlayer.getLeader(AGENT).map(Leader::isExhausted).orElse(false)
                || game.getActivePlayer() != target
                || unit == null
                || !unit.getIsShip()
                || !target.unitBelongsToPlayer(unitKey)
                || holder.getDamagedUnitCount(unitKey) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        holder.removeDamagedUnit(unitKey, 1);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                choirPlayer.getRepresentationNoPing() + " repaired 1 " + unit.getName()
                        + (game.isFowMode() && choirPlayer != target
                                ? " belonging to you"
                                : " belonging to " + target.getRepresentationNoPing())
                        + " with **Lyra of the Crypt**.");
        ButtonHelper.deleteMessage(event);
        if (choirPlayer != target) {
            sendReadyPlanetButtons(game, choirPlayer, unit.getCost());
        }
    }

    @ButtonHandler(READY_PLANET)
    public static void readyLyraOfTheCryptPlanet(
            ButtonInteractionEvent event, Game game, Player choirPlayer, String buttonID) {
        String[] values = buttonID.substring(READY_PLANET.length()).split("\\|", 2);
        float shipCost = values.length == 2 ? parseShipCost(values[0]) : -1;
        String planet = values.length == 2 ? values[1] : null;
        if (planet == null || !isEligibleReadyPlanet(choirPlayer, planet, shipCost)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        choirPlayer.refreshPlanet(planet);
        MessageHelper.sendMessageToChannel(
                choirPlayer.getCorrectChannel(),
                choirPlayer.getRepresentationNoPing() + " readied " + Helper.getPlanetRepresentation(planet, game)
                        + " with **Lyra of the Crypt**.");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendDamagedShipButtons(Game game, Player choirPlayer, Player target) {
        String message =
                target.getRepresentationNoPing() + ", choose 1 damaged ship to repair with **Lyra of the Crypt**.";
        List<Button> buttons = getDamagedShipButtons(game, target, choirPlayer, getDamagedShips(game, target));
        NewStuffHelper.sendPaginated(
                target.getCorrectChannel(),
                message,
                buttons,
                List.of(Buttons.gray(target.factionButtonChecker() + "deleteButtons", "Decline")),
                target.factionButtonChecker() + SELECT_SHIP + choirPlayer.getFaction() + "|");
    }

    private static List<Tile> getKeynTiles(Game game, Player player, boolean repair) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getUnitHolders().values().stream()
                        .anyMatch(holder -> holder.getUnitKeys().stream()
                                .anyMatch(unitKey -> isKeynUnitEligible(
                                        player, holder, unitKey, game.getUnitFromUnitKey(unitKey), repair))))
                .toList();
    }

    private static List<Button> getKeynUnitButtons(Game game, Player player, Tile tile, boolean repair) {
        List<Button> buttons = new ArrayList<>();
        for (var entry : tile.getUnitHolders().entrySet()) {
            UnitHolder holder = entry.getValue();
            for (UnitKey unitKey : holder.getUnitKeys()) {
                UnitModel unit = game.getUnitFromUnitKey(unitKey);
                if (!isKeynUnitEligible(player, holder, unitKey, unit, repair)) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + COMMANDER_UNIT + (repair ? "repair|" : "damage|")
                                + tile.getPosition() + "|" + entry.getKey() + "|" + unitKey.asyncID() + "|"
                                + unitKey.colorID(),
                        (repair ? "Repair " : "Damage ") + unit.getName() + " in "
                                + tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static boolean isKeynUnitEligible(
            Player player, UnitHolder holder, UnitKey unitKey, UnitModel unit, boolean repair) {
        return holder != null
                && player.unitBelongsToPlayer(unitKey)
                && unit != null
                && (repair
                        ? holder.getDamagedUnitCount(unitKey) > 0
                        : unit.getSustainDamage()
                                && holder.getUnitCount(unitKey) > holder.getDamagedUnitCount(unitKey));
    }

    private static boolean isKeynPending(Game game, Player player) {
        String pendingAgenda = game.getStoredValue(COMMANDER_PENDING + player.getFaction());
        return game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER)
                && !pendingAgenda.isBlank()
                && java.util.Objects.equals(game.getCurrentAgendaInfo(), pendingAgenda);
    }

    private static void clearKeynPending(Game game, Player player) {
        game.removeStoredValue(COMMANDER_PENDING + player.getFaction());
    }

    private static void sendReadyPlanetButtons(Game game, Player choirPlayer, float shipCost) {
        List<Button> buttons = choirPlayer.getExhaustedPlanets().stream()
                .filter(planet -> isEligibleReadyPlanet(choirPlayer, planet, shipCost))
                .sorted()
                .map(planet -> Buttons.green(
                        choirPlayer.factionButtonChecker() + READY_PLANET + shipCost + "|" + planet,
                        "Ready " + Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                choirPlayer.getCorrectChannel(),
                choirPlayer.getRepresentationNoPing()
                        + ", you may ready 1 exhausted planet with a resource value less than " + shipCost
                        + " with **Lyra of the Crypt**.",
                NewStuffHelper.paginateWithPinnedButtons(
                        buttons,
                        List.of(Buttons.gray(choirPlayer.factionButtonChecker() + "deleteButtons", "Decline")),
                        choirPlayer.factionButtonChecker() + READY_PLANET + shipCost + "|",
                        25,
                        0));
    }

    private static List<ShipTarget> getDamagedShips(Game game, Player player) {
        List<ShipTarget> targets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder holder = tile.getSpaceUnitHolder();
            for (UnitKey unitKey : holder.getUnitKeys()) {
                UnitModel unit = game.getUnitFromUnitKey(unitKey);
                if (player.unitBelongsToPlayer(unitKey)
                        && unit != null
                        && unit.getIsShip()
                        && holder.getDamagedUnitCount(unitKey) > 0) {
                    targets.add(new ShipTarget(tile, unitKey, unit));
                }
            }
        }
        targets.sort(Comparator.comparing(target -> target.tile().getPosition()));
        return targets;
    }

    private static List<Button> getDamagedShipButtons(
            Game game, Player targetPlayer, Player choirPlayer, List<ShipTarget> targets) {
        return targets.stream()
                .map(target -> Buttons.green(
                        targetPlayer.factionButtonChecker() + SELECT_SHIP + choirPlayer.getFaction() + "|"
                                + target.tile().getPosition() + "|"
                                + target.unitKey().asyncID() + "|"
                                + target.unitKey().colorID(),
                        "Repair " + target.unit().getName() + " in "
                                + target.tile().getRepresentationForButtons(game, targetPlayer)))
                .toList();
    }

    private static UnitKey findUnitKey(UnitHolder holder, String asyncId, String colorId) {
        return holder.getUnitKeys().stream()
                .filter(unitKey ->
                        unitKey.asyncID().equals(asyncId) && unitKey.colorID().equals(colorId))
                .findFirst()
                .orElse(null);
    }

    private static boolean isEligibleReadyPlanet(Player player, String planet, float shipCost) {
        PlanetModel model = Mapper.getPlanet(planet);
        return player.getPlanets().contains(planet)
                && player.getExhaustedPlanets().contains(planet)
                && model != null
                && model.getResources() < shipCost;
    }

    private static float parseShipCost(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean canUseFuneralProcession(Game game, Player player, Player opponent, Tile tile) {
        if (player == null
                || opponent == null
                || tile == null
                || !player.hasLeaderUnlocked(HERO)
                || player.getLeader(HERO).map(Leader::isActive).orElse(true)) {
            return false;
        }
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || !combat.factions().contains(player.getFaction())
                || !combat.factions().contains(opponent.getFaction())
                || (combat.tilePosition() != null && !tile.getPosition().equals(combat.tilePosition()))
                || (combat.unitHolderName() != null && !"space".equals(combat.unitHolderName()))) {
            return false;
        }
        return game.getStoredValue("combatRoundTracker" + player.getFaction() + tile.getPosition() + "space")
                        .isBlank()
                && game.getStoredValue("combatRoundTracker" + opponent.getFaction() + tile.getPosition() + "space")
                        .isBlank();
    }

    private static int damageOpponentShips(Game game, Player opponent, Tile tile) {
        UnitHolder holder = tile.getSpaceUnitHolder();
        int damagedShips = 0;
        for (UnitKey unitKey : holder.getUnitKeys()) {
            UnitModel unit = game.getUnitFromUnitKey(unitKey);
            if (!opponent.unitBelongsToPlayer(unitKey)
                    || unit == null
                    || !unit.getIsShip()
                    || !unit.getSustainDamage()) {
                continue;
            }
            int undamaged = holder.getUnitCount(unitKey) - holder.getDamagedUnitCount(unitKey);
            if (undamaged > 0) {
                holder.addDamagedUnit(unitKey, undamaged);
                damagedShips += undamaged;
            }
        }
        return damagedShips;
    }

    private record ShipTarget(Tile tile, UnitKey unitKey, UnitModel unit) {}
}
