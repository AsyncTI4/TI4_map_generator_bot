package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.BombardmentService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AshenUnitHandler {

    private static final String ASHEN_INF_ID = "ashen_infantry";
    private static final String ASHEN_INF2_ID = "ashen_infantry2";
    private static final String ASHEN_FLAGSHIP = "ashen_flagship";
    private static final String ASHEN_MECH = "ashen_mech";
    private static final String ASHEN_DN = "ashen_dreadnought";
    private static final String ASHEN_DN2 = "ashen_dreadnought2";
    private static final String ASHFALL_ENGINE_PREFIX = "ashenAshfallEngine_";
    private static final String ASHEN_MECH_REVIVE_PREFIX = "ashenMechRevive_";
    private static final String ASHEN_MECH_PENDING_PREFIX = "ashenMechPending_";
    private static final String ASHEN_FLAGSHIP_BOMBARDMENT_PREFIX = "ashenFlagshipBombard_";

    public static boolean resolveAshenInfDestroy(
            Game game, Player player, List<RemovedUnit> units, GenericInteractionCreateEvent event) {
        if (game == null
                || player == null
                || units == null
                || (!player.hasUnit(ASHEN_INF_ID) && !player.hasUnit(ASHEN_INF2_ID))) {
            return false;
        }

        MessageChannel resultChannel = player.getCorrectChannel();
        MessageChannel promptChannel = event == null ? resultChannel : event.getMessageChannel();
        boolean handled = false;
        boolean offeredBtPrompt = false;
        for (RemovedUnit unit : units) {
            if (!player.unitBelongsToPlayer(unit.unitKey()) || unit.unitKey().unitType() != UnitType.Infantry) {
                continue;
            }

            String planet = unit.uh() instanceof Planet ? unit.uh().getName() : null;
            for (int x = 0; x < unit.getTotalRemoved(); x++) {
                boolean btPromptedThisRoll = resolveSingleAshenInfDestroy(
                        game, player, unit.tile(), planet, resultChannel, promptChannel, !offeredBtPrompt);
                if (btPromptedThisRoll) {
                    offeredBtPrompt = true;
                }
            }
            handled = true;
        }
        return handled;
    }

    public static void resolveAshenMechDestroy(Game game, Player player, RemovedUnit unit) {
        if (game == null || player == null || unit == null || !player.hasUnit(ASHEN_MECH)) {
            return;
        }
        if (!player.unitBelongsToPlayer(unit.unitKey()) || unit.unitKey().unitType() != UnitType.Mech) {
            return;
        }

        UnitModel unitModel = player.getUnitFromUnitKey(unit.unitKey());
        if (unitModel == null || !ASHEN_MECH.equals(unitModel.getId())) {
            return;
        }

        int pending = getPendingAshenMechs(game, player) + unit.getTotalRemoved();
        game.setStoredValue(getAshenMechPendingKey(player), Integer.toString(pending));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " had "
                        + StringHelper.pluralize(unit.getTotalRemoved(), unitModel.getName())
                        + " destroyed. It will be placed on a planet you control in your home system at the start of"
                        + " your next turn.");
    }

    public static void resolveAshenMechCheck(Player player, Game game) {
        if (game == null || player == null) {
            return;
        }

        int pending = getPendingAshenMechs(game, player);
        if (pending < 1) {
            return;
        }

        List<Button> buttons = getAshenMechRevivalButtons(player, game);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you had "
                            + StringHelper.pluralize(pending, "Balefire Sentinel")
                            + " to revive, but the bot couldn't find any planets you control in your home system to"
                            + " place them on.");
            game.removeStoredValue(getAshenMechPendingKey(player));
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you have "
                        + StringHelper.pluralize(pending, "Balefire Sentinel")
                        + " to place on planets you control in your home system.",
                buttons);
    }

    public static void clearFlagshipBombardmentContexts(Game game) {
        if (game == null) {
            return;
        }
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                game.removeStoredValue(getAshenFlagshipBombardmentKey(planet.getName()));
            }
        }
    }

    public static void prepareFlagshipBombardmentContext(Game game, Player player, String bombardPlanet) {
        if (game == null || bombardPlanet == null || bombardPlanet.isBlank()) {
            return;
        }
        game.removeStoredValue(getAshenFlagshipBombardmentKey(bombardPlanet));
        if (player == null || !player.hasUnit(ASHEN_FLAGSHIP)) {
            return;
        }

        String assignedUnits = game.getStoredValue("assignedBombardment" + player.getFaction());
        if (assignedUnits.isBlank()) {
            return;
        }

        boolean hasFlagshipAssigned = false;
        for (String assignedUnit : assignedUnits.split(";")) {
            if (assignedUnit.isBlank() || !assignedUnit.endsWith("_" + bombardPlanet)) {
                continue;
            }
            String assignedAlias = assignedUnit.split("_", 2)[0];
            if (isBombardmentModifierAssignment(assignedAlias)) {
                continue;
            }
            if (!"fs".equals(assignedAlias)) {
                return;
            }
            hasFlagshipAssigned = true;
        }

        if (hasFlagshipAssigned) {
            game.setStoredValue(getAshenFlagshipBombardmentKey(bombardPlanet), player.getFaction());
        }
    }

    public static void resolveFlagshipBombardmentInfantryDeath(
            GenericInteractionCreateEvent event, Game game, Player defender, RemovedUnit unit) {
        if (event == null
                || game == null
                || defender == null
                || unit == null
                || unit.unitKey().unitType() != UnitType.Infantry) {
            return;
        }
        if (!(unit.uh() instanceof Planet planetHolder)) {
            return;
        }
        if (!"bombardment".equalsIgnoreCase(game.getStoredValue(defender.getFaction() + "latestAssignHits"))) {
            return;
        }

        String attackerFaction = game.getStoredValue(getAshenFlagshipBombardmentKey(planetHolder.getName()));
        if (attackerFaction.isBlank()) {
            return;
        }

        Player attacker = game.getPlayerFromColorOrFaction(attackerFaction);
        if (attacker == null || !attacker.hasUnit(ASHEN_FLAGSHIP) || unit.tile() == null) {
            return;
        }

        AddUnitService.addUnits(
                event,
                unit.tile(),
                game,
                attacker.getColor(),
                unit.getTotalRemoved() + " infantry " + planetHolder.getName());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                attacker.getRepresentation() + " committed "
                        + StringHelper.pluralize(unit.getTotalRemoved(), "infantry") + " to "
                        + Helper.getPlanetRepresentation(planetHolder.getName(), game) + " with _The Pyre_.");
    }

    private static boolean resolveSingleAshenInfDestroy(
            Game game,
            Player player,
            Tile tile,
            String planet,
            MessageChannel resultChannel,
            MessageChannel promptChannel,
            boolean canOfferBtPrompt) {
        int threshold = player.hasUnit(ASHEN_INF2_ID) ? 6 : 9;
        Die die = new Die(threshold);

        StringBuilder message = new StringBuilder(UnitEmojis.infantry + " died. Rolling for resurrection. ");
        message.append(die.getGreenDieIfSuccessOrRedDieIfFailure());

        if (!die.isSuccess()) {
            message.append(" Failure.");
            boolean offeredBt = canOfferBtPrompt
                    && AshenBreakthroughHandler.offerFromFireResolveInfantryButton(
                            player, game, tile, planet, die, promptChannel);
            if (offeredBt) {
                message.append(
                        " You may exhaust _From Fire, Resolve_ from your cards info thread to treat this roll as a 10 and start _Phoenix Rising_.");
            }
            if (RandomHelper.isOneInX(20)) {
                message.append(
                        " That infantry is now permanently dead, destined to be forgotten as just one more amongst untold billions who will die in this war.");
                message.append(" Already, you can't even remember ")
                        .append(RandomHelper.isOneInX(2) ? "his" : "her")
                        .append(" ")
                        .append(RandomHelper.isOneInX(2) ? "face" : "name")
                        .append(".");
            }
            MessageHelper.sendMessageToChannel(resultChannel, message.toString());
            return offeredBt;
        }

        if (AshenAbilityHandler.offerPhoenixRising(player, game, tile, planet, die, promptChannel)) {
            message.append(
                    " Success. You may use _Phoenix Rising_ to place that infantry back on the planet, or decline to choose a _Cinderborn_ revive with or without producing 1 hit.");
            MessageHelper.sendMessageToChannel(resultChannel, message.toString());
            return false;
        }

        message.append(" Success. You may revive that infantry with or without producing 1 hit.");
        MessageHelper.sendMessageToChannel(resultChannel, message.toString());
        AshenAbilityHandler.offerCinderbornReviveChoice(player, game, tile, planet, promptChannel);
        return false;
    }

    public static void offerAshfallEngineOnDestroy(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit) {
        if (event == null || game == null || player == null || unit == null) {
            return;
        }
        if (!player.unitBelongsToPlayer(unit.unitKey()) || unit.unitKey().unitType() != UnitType.Dreadnought) {
            return;
        }

        UnitModel um = player.getUnitFromUnitKey(unit.unitKey());
        if (um == null) {
            return;
        }
        if (!ASHEN_DN.equals(um.getId()) && !ASHEN_DN2.equals(um.getId())) {
            return;
        }

        Tile tile = unit.tile();
        if (tile == null) {
            return;
        }

        List<String> planets = BombardmentService.getBombardablePlanets(player, game, tile);
        if (tile.isScar() || planets.isEmpty()) {
            return;
        }

        offerAshfallEngineButtons(event, game, player, tile, planets, false, unit.getTotalRemoved(), um.getId());
    }

    public static void offerAshfallEngineButtons(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            List<String> planets,
            boolean fromSustain,
            int triggerCount,
            String unitId) {
        if (event == null
                || game == null
                || player == null
                || tile == null
                || planets == null
                || planets.isEmpty()
                || triggerCount < 1
                || unitId == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planet : planets) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker()
                            + ASHFALL_ENGINE_PREFIX
                            + tile.getPosition()
                            + "|"
                            + planet
                            + "|"
                            + (fromSustain ? "sustain" : "destroy")
                            + "|"
                            + triggerCount
                            + "|"
                            + unitId,
                    "Use Ashfall Engine on " + Helper.getPlanetRepresentationNoResInf(planet, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        String timing = fromSustain ? "used SUSTAIN DAMAGE" : "was destroyed";
        String amountText = fromSustain || triggerCount == 1
                ? ""
                : " A total of " + triggerCount + " Ashfall Engines were destroyed.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", your _Ashfall Engine_ "
                        + timing
                        + ". You may use its BOMBARDMENT ability against a planet in this system."
                        + amountText,
                buttons);
    }

    public static void offerAshfallEngineOnSustain(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder holder,
            UnitType unitType) {
        if (event == null || game == null || player == null || tile == null || holder == null || unitType == null) {
            return;
        }
        UnitModel unitModel = player.getUnitFromUnitKey(Units.getUnitKey(unitType, player.getColorID()));
        if (unitModel == null) {
            return;
        }
        if (!ASHEN_DN2.equals(unitModel.getId())) {
            return;
        }
        if (!"space".equalsIgnoreCase(holder.getName())) {
            return;
        }

        List<String> planets = BombardmentService.getBombardablePlanets(player, game, tile);
        if (tile.isScar() || planets.isEmpty()) {
            return;
        }

        offerAshfallEngineButtons(event, game, player, tile, planets, true, 1, ASHEN_DN2);
    }

    public static void offerAshfallEngineOnAutoSustain(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, int triggerCount) {
        if (event == null || game == null || player == null || tile == null || triggerCount < 1) {
            return;
        }
        if (!player.hasUnit(ASHEN_DN2)) {
            return;
        }

        List<String> planets = BombardmentService.getBombardablePlanets(player, game, tile);
        if (tile.isScar() || planets.isEmpty()) {
            return;
        }

        offerAshfallEngineButtons(event, game, player, tile, planets, true, triggerCount, ASHEN_DN2);
    }

    @ButtonHandler(ASHEN_MECH_REVIVE_PREFIX)
    public static void reviveAshenMech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (event == null || player == null || game == null) {
            return;
        }

        int pending = getPendingAshenMechs(game, player);
        if (pending < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planet = buttonID.substring(ASHEN_MECH_REVIVE_PREFIX.length());
        Tile tile = game.getTileFromPlanet(planet);
        Tile homeTile = player.getHomeSystemTile();
        if (homeTile == null
            || !homeTile.equals(tile)
            || !player.getPlanets().contains(planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        pending--;
        if (pending > 0) {
            game.setStoredValue(getAshenMechPendingKey(player), Integer.toString(pending));
            MessageHelper.editMessageButtons(event, getAshenMechRevivalButtons(player, game));
        } else {
            game.removeStoredValue(getAshenMechPendingKey(player));
            ButtonHelper.deleteMessage(event);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed 1 Balefire Sentinel on "
                        + Helper.getPlanetRepresentation(planet, game) + ". You have " + pending + " left to place.");
    }

    @ButtonHandler(ASHFALL_ENGINE_PREFIX)
    public static void resolveAshfallEngineRoll(
            ButtonInteractionEvent event, Player player, String buttonId, Game game) {
        String payload = buttonId.substring(ASHFALL_ENGINE_PREFIX.length());
        String[] parts = payload.split("\\|", 5);
        if (parts.length < 5 || event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePos = parts[0];
        String planet = parts[1];
        boolean fromSustain = "sustain".equalsIgnoreCase(parts[2]);
        int triggerCount = Integer.parseInt(parts[3]);
        String unitId = parts[4];

        if (fromSustain && !ASHEN_DN2.equals(unitId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!ASHEN_DN.equals(unitId) && !ASHEN_DN2.equals(unitId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile.isScar()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation()
                            + ", you cannot use BOMBARDMENT (or any other unit abilities) in an entropic scar.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!BombardmentService.getBombardablePlanets(player, game, tile).contains(planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        try {
            if (fromSustain && !ASHEN_DN2.equals(unitId)) {
                return;
            }
            resolveAshfallBombardment(event, game, player, tile, planet, triggerCount, unitId);
        } finally {
            ButtonHelper.deleteMessage(event);
        }
    }

    private static void resolveAshfallBombardment(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Tile tile,
            String planet,
            int triggerCount,
            String unitId) {
        game.removeStoredValue(getAshenFlagshipBombardmentKey(planet));
        UnitModel unitModel = Mapper.getUnit(unitId);
        if (triggerCount < 1
                || unitModel == null
                || (!ASHEN_DN.equals(unitModel.getId()) && !ASHEN_DN2.equals(unitModel.getId()))) {
            return;
        }

        UnitHolder space = tile.getSpaceUnitHolder();
        int toHit = unitModel.getCombatDieHitsOnForAbility(CombatRollType.bombardment, player);
        int rollsPerUnit = unitModel.getCombatDieCountForAbility(CombatRollType.bombardment, player);
        int extraRolls = getAshfallExtraBombardmentRolls(player, game);
        List<Die> resultRolls = DiceHelper.rollDice(toHit, (rollsPerUnit * triggerCount) + extraRolls);
        int hits = DiceHelper.countSuccesses(resultRolls);
        int totalHits = hits;
        boolean usesX89c4 = player.hasTech("x89c4");
        if (usesX89c4) {
            totalHits *= 2;
        }
        if (game.isConventionsOfWarAbandonedMode()) {
            totalHits *= 3;
        }
        boolean useDoubleBoomEmoji = usesX89c4;
        if (player.hasStoredValue("RazeFaction")) {
            useDoubleBoomEmoji = true;
            totalHits *= 2;
        }
        if (totalHits < 1) {
            useDoubleBoomEmoji = false;
        }

        String combatSummary =
                CombatMessageHelper.displayCombatSummary(player, tile, space, CombatRollType.bombardment);
        String unitRoll = CombatMessageHelper.displayUnitRoll(
                unitModel, toHit, 0, triggerCount, rollsPerUnit, extraRolls, resultRolls, hits);
        String message =
                combatSummary + unitRoll + CombatMessageHelper.displayHitResults(totalHits, useDoubleBoomEmoji);
        if (totalHits > 0 && usesX89c4) {
            message += "\n" + player.getFactionEmoji() + " produced "
                    + ((totalHits / 2) == 1 ? "1 additional hit" : (totalHits / 2) + " additional hits")
                    + " using "
                    + Mapper.getTech("x89c4").getNameRepresentation() + ".";
        }
        if (totalHits > 0 && player.hasTech("dszelir")) {
            message += "\n" + player.getFactionEmoji()
                    + " You have _Shard Volley_ and thus should produce an additional hit to the ones rolled above.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);

        if (totalHits > 0) {
            AshenLeadersHandler.offerCommanderBombardmentButtons(event, game, player, totalHits);
            sendBombardmentHitButtons(event, game, player, tile, planet, totalHits);
        }
    }

    private static int getAshfallExtraBombardmentRolls(Player player, Game game) {
        int extraRolls = 0;
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            extraRolls++;
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            extraRolls++;
        }
        return extraRolls;
    }

    private static void sendBombardmentHitButtons(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String planet, int hits) {
        if (!game.isFowMode()) {
            for (Player p2 : game.getRealPlayersNNeutral()) {
                if (p2 == player) {
                    continue;
                }
                if (FoWHelper.playerHasUnitsOnPlanet(p2, game.getUnitHolderFromPlanet(planet))) {
                    if (p2.isRealPlayer()) {
                        List<Button> buttons = new ArrayList<>();
                        buttons.add(Buttons.red(
                                p2.factionButtonChecker() + "getDamageButtons_" + tile.getPosition() + "_bombardment",
                                "Assign Hit" + (hits == 1 ? "" : "s")));
                        MessageHelper.sendMessageToChannelWithButtons(
                                game.isFowMode() ? p2.getCorrectChannel() : event.getMessageChannel(),
                                p2.getRepresentation() + ", please assign the BOMBARDMENT hit" + (hits == 1 ? "" : "s")
                                        + ".",
                                buttons);
                    } else {
                        List<Button> buttons2 = new ArrayList<>();
                        buttons2.add(Buttons.green(
                                p2.dummyPlayerSpoof() + "autoAssignGroundHits_"
                                        + game.getUnitHolderFromPlanet(planet).getName() + "_" + hits,
                                "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
                        MessageHelper.sendMessageToChannelWithButtons(
                                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                                player.getRepresentation() + ", please assign the BOMBARDMENT hit"
                                        + (hits == 1 ? "" : "s") + " for the dummy player.",
                                buttons2);
                    }
                }
            }
        }
    }

    private static List<Button> getAshenMechRevivalButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        Tile home = player.getHomeSystemTile();
        if (home == null) {
            return buttons;
        }

        for (UnitHolder unitHolder : home.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet
                    && player.getPlanets().contains(planet.getName())
                    && !planet.isSpaceStation()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + ASHEN_MECH_REVIVE_PREFIX + planet.getName(),
                        "Place 1 Mech on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        UnitEmojis.mech));
            }
        }
        return buttons;
    }

    private static int getPendingAshenMechs(Game game, Player player) {
        String pending = game.getStoredValue(getAshenMechPendingKey(player));
        return pending.isBlank() ? 0 : Integer.parseInt(pending);
    }

    private static String getAshenMechPendingKey(Player player) {
        return ASHEN_MECH_PENDING_PREFIX + player.getFaction();
    }

    private static String getAshenFlagshipBombardmentKey(String planet) {
        return ASHEN_FLAGSHIP_BOMBARDMENT_PREFIX + planet;
    }

    private static boolean isBombardmentModifierAssignment(String assignedAlias) {
        return "plasma".equals(assignedAlias) || "argentcommander".equals(assignedAlias);
    }
}
