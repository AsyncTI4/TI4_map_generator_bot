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
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.BombardmentService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AshenUnitHandler {

    private static final String ASHEN_INF_ID = "ashen_infantry";
    private static final String ASHEN_INF2_ID = "ashen_infantry2";
    private static final String ASHEN_DN = "ashen_dreadnought";
    private static final String ASHEN_DN2 = "ashen_dreadnought2";
    private static final String ASHFALL_ENGINE_PREFIX = "ashenAshfallEngine_";

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
        for (RemovedUnit unit : units) {
            if (!player.unitBelongsToPlayer(unit.unitKey()) || unit.unitKey().unitType() != UnitType.Infantry) {
                continue;
            }

            String planet = unit.uh() instanceof Planet ? unit.uh().getName() : null;
            for (int x = 0; x < unit.getTotalRemoved(); x++) {
                resolveSingleAshenInfDestroy(game, player, unit.tile(), planet, resultChannel, promptChannel);
            }
            handled = true;
        }
        return handled;
    }

    private static void resolveSingleAshenInfDestroy(
            Game game,
            Player player,
            Tile tile,
            String planet,
            MessageChannel resultChannel,
            MessageChannel promptChannel) {
        int threshold = player.hasUnit(ASHEN_INF2_ID) ? 6 : 9;
        Die die = new Die(threshold);

        StringBuilder message = new StringBuilder(UnitEmojis.infantry + " died. Rolling for resurrection. ");
        message.append(die.getGreenDieIfSuccessOrRedDieIfFailure());

        if (!die.isSuccess()) {
            message.append(" Failure.");
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
            return;
        }

        if (AshenAbilityHandler.offerPhoenixRising(player, game, tile, planet, die, promptChannel)) {
            message.append(
                    " Success. You may use _Phoenix Rising_ to place that infantry back on the planet, or decline to choose a _Cinderborn_ revive with or without producing 1 hit.");
            MessageHelper.sendMessageToChannel(resultChannel, message.toString());
            return;
        }

        message.append(" Success. You may revive that infantry with or without producing 1 hit.");
        MessageHelper.sendMessageToChannel(resultChannel, message.toString());
        AshenAbilityHandler.offerCinderbornReviveChoice(player, game, tile, planet, promptChannel);
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
}
