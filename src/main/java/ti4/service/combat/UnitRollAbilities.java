package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.UnitRollType;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronLeadersHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.combat.UnitRollExecution.RerollResult;
import ti4.service.combat.UnitRollExecution.UnitGroupRollState;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
class UnitRollAbilities {
    // MODIFY PRIMARY-ROLL HITS

    static void resolveJolNarFlagshipExtraHits(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (unit.unitModel.getUnitType() != UnitType.Flagship
                || !ValefarZService.hasFlagshipAbility(unit.context.game, unit.context.player, "jolnar_flagship"))
            return;
        int bonusHits = (int) roll.activeDice().stream()
                        .filter(die -> die.getResult() >= 9)
                        .count()
                * 2;
        double probabilityFactor =
                Math.pow(2.0 / (11 - unit.toHit + roll.modifierToHit), roll.currentDiceCount() * roll.multiplier);
        roll.recordBonusHitOutcome(bonusHits, roll.activeDice().size() * 2, probabilityFactor);
    }

    static void resolveTeklarEliteExtraHits(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (unit.unitModel.getUnitType() != UnitType.Infantry || !unit.context.player.hasUnit("tk-tekklarelite"))
            return;
        int bonusHits = DiceHelper.countSuccesses(roll.activeDice());
        double probabilityFactor =
                Math.pow(2.0 / (11 - unit.toHit + roll.modifierToHit), roll.currentDiceCount() * roll.multiplier);
        roll.recordBonusHitOutcome(bonusHits, roll.activeDice().size(), probabilityFactor);
    }

    static void resolveZephyrionCommanderExtraHits(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if ((unit.context.rollType != CombatRollType.SpaceCannonDefence
                        && unit.context.rollType != CombatRollType.SpaceCannonOffence)
                || !unit.context.game.playerHasLeaderUnlockedOrAlliance(unit.context.player, "zephyrioncommander"))
            return;
        int bonusHits = (int)
                roll.activeDice().stream().filter(die -> die.getResult() == 10).count();
        roll.recordBonusHitOutcome(bonusHits, roll.activeDice().size(), 1.0);
    }

    static void activateJusticerGraviton(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!"tf-justicerrail".equals(unit.unitModel.getId())
                || unit.context.rollType != CombatRollType.SpaceCannonOffence
                || roll.currentHits() < 1) return;
        unit.context.game.setStoredValue(unit.context.player.getFaction() + "graviton", "yes");
    }

    // AFTER PRIMARY DICE: ACTIVATION, REROLLS, AND MISS RESOLUTION

    static void resolveJolNarCommanderRerolls(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if ((!unit.context.game.playerHasLeaderUnlockedOrAlliance(unit.context.player, "jolnarcommander")
                        && !unit.context.player.hasTech("tf-tacticalbrilliance"))
                || unit.context.rollType == CombatRollType.combatround) return;

        boolean rerollBombardmentHits = unit.context.opponent == unit.context.player
                && unit.context.rollType == CombatRollType.bombardment
                && unit.context.player.hasTech("proxima");
        int diceToReroll = rerollBombardmentHits ? roll.currentHits() : roll.currentMisses();
        if (diceToReroll < 1) return;

        RerollResult reroll = rerollBombardmentHits
                ? roll.rollReplacementDice(unit, diceToReroll)
                : roll.rollMisses(unit, diceToReroll);
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applySystemValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        rerollHits = applyPersonalValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        if (rerollBombardmentHits) {
            roll.replaceHitsWith(unit, reroll, rerollHits);
        } else {
            roll.replaceMissesWith(unit, reroll, rerollHits);
        }

        int displayedExtraRolls = rerollBombardmentHits ? unit.extraRollsForUnit : 0;
        UnitRollType payloadRollType = rerollBombardmentHits
                ? UnitRollType.JOL_NAR_COMMANDER_REROLL_HITS
                : UnitRollType.JOL_NAR_COMMANDER_REROLL_MISSES;
        DieRollSource rollSource = rerollBombardmentHits ? DieRollSource.REROLL_HIT : DieRollSource.REROLL_MISS;
        String unitRoll = roll.renderAndRecordRoll(
                unit, displayedExtraRolls, payloadRollType, reroll.dice(), rerollHits, rollSource);
        unit.context
                .resultBuilder
                .append("Rerolling ")
                .append(diceToReroll)
                .append(rerollBombardmentHits ? " hit" : " miss")
                .append(diceToReroll == 1 ? "" : rerollBombardmentHits ? "s" : "es")
                .append(" due to Ta Zern, the Jol-Nar Commander:\n")
                .append(unitRoll);
    }

    static void offerGledgePds2Exploration(UnitRollExecution.UnitRollState unit) {
        if (unit.context.rollType != CombatRollType.SpaceCannonOffence
                        && unit.context.rollType != CombatRollType.SpaceCannonDefence
                || !unit.context.player.ownsUnit("gledge_pds2")
                || unit.context.totalHits < 1) return;
        String message = unit.context.player.getRepresentation()
                + ", use the buttons to explore a planet with the PDS that got the hit. It should be noted that the bot has no idea which PDS rolled which dice, but default practice would be to go from lowest tile position to highest, with _Plasma Scoring_ applying to the last die. You can specify any order before rolling though.";
        for (int hit = 0; hit < unit.context.totalHits; hit++) {
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(
                    unit.context.game, unit.context.player, UnitType.Pds)) {
                for (String planet : ButtonHelper.getPlanetsWithSpecificUnit(unit.context.player, tile, "pds")) {
                    Planet planetUnit = unit.context.game.getUnitHolderFromPlanet(planet);
                    if (planetUnit == null) continue;
                    planet = planetUnit.getName();
                    if (isNotBlank(planetUnit.getOriginalPlanetType())
                            && unit.context.player.getPlanetsAllianceMode().contains(planet)
                            && FoWHelper.playerHasUnitsOnPlanet(unit.context.player, tile, planet)) {
                        buttons.addAll(ButtonHelper.getPlanetExplorationButtons(
                                unit.context.game, planetUnit, unit.context.player));
                    }
                }
            }
            buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
            MessageHelper.sendMessageToChannelWithButtons(unit.context.player.getCorrectChannel(), message, buttons);
        }
    }

    static void offerGledgePdsExploration(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if ((unit.context.rollType != CombatRollType.SpaceCannonOffence
                        && unit.context.rollType != CombatRollType.SpaceCannonDefence)
                || !unit.context.player.ownsUnit("gledge_pds")) return;
        String message = unit.context.player.getRepresentation()
                + " use the buttons to explore a planet with the PDS that got the hit.";
        for (Die die : roll.activeDice()) {
            if (die.getResult() < 9) continue;
            List<Button> buttons = new ArrayList<>();
            for (String planet :
                    ButtonHelper.getPlanetsWithSpecificUnit(unit.context.player, unit.context.tile, "pds")) {
                Planet planetUnit = unit.context.game.getUnitHolderFromPlanet(planet);
                if (planetUnit == null) continue;
                planet = planetUnit.getName();
                if (isNotBlank(planetUnit.getOriginalPlanetType())
                        && unit.context.player.getPlanetsAllianceMode().contains(planet)
                        && FoWHelper.playerHasUnitsOnPlanet(unit.context.player, unit.context.tile, planet)) {
                    buttons.addAll(ButtonHelper.getPlanetExplorationButtons(
                            unit.context.game, planetUnit, unit.context.player));
                }
            }
            buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
            MessageHelper.sendMessageToChannelWithButtons(unit.context.player.getCorrectChannel(), message, buttons);
        }
    }

    static void resolveIronCommanderRerolls(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!IronLeadersHandler.shouldAutoRerollCommanderMechMisses(
                        unit.context.game, unit.context.player, unit.unitModel, unit.context.rollType)
                || roll.currentMisses() < 1) return;
        int misses = roll.currentMisses();
        RerollResult reroll = roll.rollMisses(unit, misses);
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applySystemValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        rerollHits = applyPersonalValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        roll.replaceMissesWith(unit, reroll, rerollHits);
        String unitRoll = roll.renderAndRecordRoll(
                unit,
                0,
                UnitRollType.IRON_COMMANDER_REROLL_MISSES,
                reroll.dice(),
                rerollHits,
                DieRollSource.REROLL_MISS);
        unit.context
                .resultBuilder
                .append("Rerolling ")
                .append(misses)
                .append(" miss")
                .append(misses == 1 ? "" : "es")
                .append(" due to Captain Vakros, the Iron Tide Commander:\n")
                .append(unitRoll);
    }

    static void resolveHacanFlagshipThalnosMisses(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!unit.context.isThalnosReroll || !unit.context.hacanFlagship) return;
        roll.excludeMissesFromDestruction((int)
                roll.activeDice().stream().filter(Die::eligibleForHeartPlus).count());
        unit.context.hacanFsButtons.add(
                buildHacanFlagshipThalnosButton(unit.context.player, unit.unitModel.getUnitType(), roll.activeDice()));
    }

    static void resolveFallOfKenaraThalnosMisses(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!unit.context.isThalnosReroll || unit.context.hacanFlagship || !unit.context.tkHacanWarsun) return;
        roll.ignoreMissesForDestruction();
        unit.context.hacanFsButtons.add(buildTkHacanWSThalnosButton(roll.activeDice()));
    }

    static void trackThalnosDestroyTypes(UnitRollExecution.UnitRollState unit) {
        if (!unit.context.isThalnosReroll
                || (!unit.context.hacanFlagship && !unit.context.tkHacanWarsun)
                || unit.extraRollsCount) return;
        unit.context.hacanFsThalnosDestroyTypes.add(unit.unitModel.getUnitType());
    }

    static void resolveGenericThalnosMisses(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!unit.context.isThalnosReroll) return;
        if (roll.currentMisses() > 0 && !unit.extraRollsCount) {
            unit.context
                    .extra
                    .append(unit.context.player.getFactionEmoji())
                    .append(" destroyed ")
                    .append(roll.currentMisses())
                    .append(" of their own ")
                    .append(unit.unitModel.getName())
                    .append(roll.currentMisses() == 1 ? "" : "s")
                    .append(" due to ")
                    .append(roll.currentMisses() == 1 ? "a Thalnos miss" : "Thalnos misses")
                    .append(".");
            unit.context.payloadBuilder.recordUnitDestroyed(
                    unit.unitModel.getId(),
                    unit.unitModel.getName(),
                    roll.currentMisses(),
                    unit.context.player.getFactionEmoji());
            roll.destroyMissedUnits(unit);
        } else if (roll.currentMisses() > 0) {
            MessageHelper.sendMessageToChannel(
                    unit.context.event.getMessageChannel(),
                    unit.context.player.getFactionEmoji() + " had " + roll.currentMisses() + " "
                            + unit.unitModel.getName() + (roll.currentMisses() == 1 ? "" : "s") + " miss"
                            + (roll.currentMisses() == 1 ? "" : "es")
                            + " on a Thalnos roll, but no units were removed due to extra rolls being unaccounted for.");
        }
    }

    static void resolveStrikeWingAlphaInfantryKills(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (unit.context.player == unit.context.opponent
                || (!("argent_destroyer2".equalsIgnoreCase(unit.unitModel.getId())
                        || "tf-swa".equalsIgnoreCase(unit.unitModel.getId())))
                || unit.context.rollType != CombatRollType.AFB) return;
        int availableInfantry =
                unit.context.space.getUnitCount(Units.UnitType.Infantry, unit.context.opponent.getColor());
        if (availableInfantry < 1) return;
        int infantryKills = (int) Stream.concat(roll.primaryDiceHistory().stream(), roll.rerollDiceHistory().stream())
                .filter(die -> die.getResult() > 8)
                .count();
        infantryKills = Math.min(infantryKills, availableInfantry);
        if (infantryKills < 1) return;
        unit.context
                .resultBuilder
                .append("\nDue to the Strike Wing Alpha II destroyer ability, ")
                .append(infantryKills)
                .append(" of ")
                .append(unit.context.opponent.getRepresentation(false, true))
                .append(" infantry were destroyed\n");
        unit.context.payloadBuilder.recordOpponentUnitDestroyed(
                unit.unitModel.getId(),
                "infantry",
                infantryKills,
                unit.context.opponent.getRepresentation(false, true));
        UnitKey infantry = Units.getUnitKey(UnitType.Infantry, unit.context.opponent.getColorID());
        DestroyUnitService.destroyUnit(
                unit.context.event,
                unit.context.tile,
                unit.context.game,
                infantry,
                infantryKills,
                unit.context.space,
                true);
    }

    // AFTER HITS: UNIT DESTRUCTION AND REWARDS

    static void rewardMercenaryCaptains(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (unit.context.totalHits < 1
                || !"neutral".equalsIgnoreCase(unit.context.player.getFaction())
                || !unit.context.game.getStoredValue("mercenarycaptaintrigged").isEmpty()) return;
        for (Player player : unit.context.game.getRealPlayers()) {
            if (!player.hasTech("tf-mercenarycaptains")) continue;
            player.setCommodities(player.getCommodities() + 1);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " you gained 1 commodity due to the mercenary captains unit.");
            unit.context.game.setStoredValue("mercenarycaptaintrigged", "yes");
        }
    }

    // REROLL CHAINS

    static List<Die> resolveMunitionsReservesReroll(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!unit.context.game.getStoredValue("munitionsReserves").equalsIgnoreCase(unit.context.player.getFaction())
                || unit.context.rollType != CombatRollType.combatround
                || roll.currentMisses() < 1
                || unit.context.isThalnosReroll) return List.of();
        int misses = roll.currentMisses();
        RerollResult reroll = roll.rollMisses(unit, misses);
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applySystemValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        rerollHits = applyPersonalValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        roll.replaceMissesWith(unit, reroll, rerollHits);
        String unitRoll = roll.renderAndRecordRoll(
                unit,
                0,
                UnitRollType.MUNITIONS_RESERVES_REROLL,
                reroll.dice(),
                rerollHits,
                DieRollSource.MUNITIONS_RESERVES);
        unit.context
                .resultBuilder
                .append("**Munitions Reserve** rerolling ")
                .append(misses)
                .append(" miss")
                .append(misses == 1 ? "" : "es")
                .append(": ")
                .append(unitRoll);
        return reroll.dice();
    }

    static void resolveInitialKaltrimCommanderRerolls(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!unit.context.game.playerHasLeaderUnlockedOrAlliance(unit.context.player, "kaltrimcommander")) return;
        List<Die> onesRolled =
                roll.activeDice().stream().filter(die -> die.getResult() == 1).toList();
        int ones = onesRolled.size();
        if (ones < 1) return;
        RerollResult reroll = roll.rollReplacementDice(unit, ones);
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applySystemValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        rerollHits = applyPersonalValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        roll.replaceDiceWith(unit, onesRolled, reroll, rerollHits);
        String unitRoll = roll.renderAndRecordRoll(
                unit,
                0,
                UnitRollType.KALTRIM_COMMANDER_REROLL_ONES,
                reroll.dice(),
                rerollHits,
                DieRollSource.REROLL_ONE);
        unit.context
                .resultBuilder
                .append("Rerolling ")
                .append(ones)
                .append(" roll")
                .append(ones == 1 ? "" : "s")
                .append(" of 1 due to the Kaltrim Commander:\n ")
                .append(unitRoll);
    }

    static void resolvePostMunitionsKaltrimCommanderRerolls(
            UnitRollExecution.UnitRollState unit, UnitGroupRollState roll, List<Die> munitionsDice) {
        if (!unit.context.game.playerHasLeaderUnlockedOrAlliance(unit.context.player, "kaltrimcommander")
                || munitionsDice.isEmpty()) return;
        List<Die> onesRolled =
                munitionsDice.stream().filter(die -> die.getResult() == 1).toList();
        int ones = onesRolled.size();
        if (ones < 1) return;
        RerollResult reroll = roll.rollReplacementDice(unit, ones);
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applySystemValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        rerollHits = applyPersonalValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        roll.replaceDiceWith(unit, onesRolled, reroll, rerollHits);
        String unitRoll = roll.renderAndRecordRoll(
                unit,
                0,
                UnitRollType.KALTRIM_COMMANDER_REROLL_ONES,
                reroll.dice(),
                rerollHits,
                DieRollSource.REROLL_ONE);
        unit.context
                .resultBuilder
                .append("Rerolling ")
                .append(ones)
                .append(" roll")
                .append(ones == 1 ? "" : "s")
                .append(" of 1 due to the Kaltrim Commander:\n ")
                .append(unitRoll);
    }

    // AFTER DICE: CASCADING DICE AND IMMEDIATE ABILITY EFFECTS

    static void resolveDragonFreedBombardment(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (unit.context.rollType != CombatRollType.bombardment
                || !"tf-dragonfreed".equalsIgnoreCase(unit.unitModel.getId())
                || unit.context.game.isFowMode()
                || roll.currentHits() < 1) return;
        String target = unit.context.game.getStoredValue("bombardmentTarget" + unit.context.player.getFaction());
        Tile origin = target.isEmpty()
                ? unit.context.game.getTileByPosition(unit.context.game.getActiveSystem())
                : unit.context.game.getTileFromPlanet(target);
        for (String position :
                FoWHelper.getAdjacentTiles(unit.context.game, origin.getPosition(), unit.context.player, false, true)) {
            offerDragonBombardmentAssignments(unit, roll, unit.context.game.getTileByPosition(position), target);
        }
    }

    static void resolveSigmaJolNarFlagshipDice(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        String id = unit.unitModel.getId();
        if (!"sigma_jolnar_flagship_1".equalsIgnoreCase(id) && !"sigma_jolnar_flagship_2".equalsIgnoreCase(id)) return;
        int additionalDice = roll.currentHits();
        while (roll.currentHits() < 100 && additionalDice > 0) {
            int remainingHitCapacity = 100 - roll.currentHits();
            List<Die> rolls = DiceHelper.rollDice(
                    unit.toHit - roll.modifierToHit, Math.min(additionalDice, remainingHitCapacity));
            additionalDice = DiceHelper.countSuccesses(rolls);
            roll.recordAdditionalDice(unit, rolls, additionalDice);
        }
    }

    static void resolveSystemValorExtraHits(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (unit.context.rollType != CombatRollType.combatround || !hasSystemValor(unit)) return;
        ButtonHelperAbilities.readyBannerHalls(unit.context.game);
        String abilityName = unit.context.game.isTwilightsFallMode() ? "Glorious Halls" : "Valor";
        recordValorExtraHits(unit, roll, abilityName);
    }

    static void resolvePersonalValorExtraHits(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (unit.context.rollType != CombatRollType.combatround || !unit.context.player.hasTech("tf-valortf")) return;
        recordValorExtraHits(unit, roll, "Valor");
    }

    static boolean hasValorAbilityHolder(Game game) {
        return Helper.getPlayerFromAbility(game, "valor") != null
                || game.getRealPlayers().stream().anyMatch(player -> player.hasTech("tf-glorioushalls"));
    }

    static void resolveVadenFlagshipTradeGood(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!"vaden_flagship".equalsIgnoreCase(unit.unitModel.getId())
                || unit.context.rollType != CombatRollType.bombardment
                || roll.activeDice().stream().noneMatch(Die::isSuccess)) return;
        unit.context.player.setTg(unit.context.player.getTg() + 1);
        ButtonHelperAbilities.pillageCheck(unit.context.player, unit.context.game);
        ButtonHelperAgents.resolveArtunoCheck(unit.context.player, 1);
        MessageHelper.sendMessageToChannel(
                unit.context.player.getCorrectChannel(),
                unit.context.player.getRepresentation()
                        + " gained 1 trade good due to hitting on a BOMBARDMENT roll with the Aurum Vadra (the Vaden flagship).");
    }

    static void resolveUzeanWardogAbility(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        if (!"belkosea_mech".equalsIgnoreCase(unit.unitModel.getId()) || roll.currentHits() < 1) return;
        ButtonHelperFactionSpecific.offerMahactInfButtons(unit.context.player, unit.context.game);
        MessageHelper.sendMessageToChannel(
                unit.context.event.getMessageChannel(),
                unit.context.player.getRepresentation() + " please gain or convert 1 commodity a total of "
                        + StringHelper.pluralize(roll.currentHits(), "time")
                        + " due to your Uzean Wardog mech unit.");
    }

    // SUPPORTING HELPERS

    private static int applyTeklarEliteToRerollHits(
            UnitRollExecution.UnitRollState unit, List<Die> rerolls, int rerollHits) {
        if (unit.unitModel.getUnitType() != UnitType.Infantry || !unit.context.player.hasUnit("tk-tekklarelite")) {
            return rerollHits;
        }
        return rerollHits + DiceHelper.countSuccesses(rerolls);
    }

    private static void recordValorExtraHits(
            UnitRollExecution.UnitRollState unit, UnitGroupRollState roll, String abilityName) {
        int bonusHits = (int)
                roll.activeDice().stream().filter(die -> die.getResult() >= 10).count();
        double probabilityFactor =
                Math.pow(1.0 / (11 - unit.toHit + roll.modifierToHit), roll.currentDiceCount() * roll.multiplier);
        roll.recordBonusHitOutcome(bonusHits, roll.activeDice().size(), probabilityFactor);
        for (int hit = 0; hit < bonusHits; hit++) {
            sendValorExtraHitMessage(unit, abilityName);
        }
    }

    private static int applySystemValorToCombatRerollHits(
            UnitRollExecution.UnitRollState unit, List<Die> rerollDice, int hits) {
        if (unit.context.rollType != CombatRollType.combatround || !hasSystemValor(unit)) return hits;
        String abilityName = unit.context.game.isTwilightsFallMode() ? "Glorious Halls" : "Valor";
        return applyValorToRerollHits(unit, rerollDice, hits, abilityName);
    }

    private static int applyPersonalValorToCombatRerollHits(
            UnitRollExecution.UnitRollState unit, List<Die> rerollDice, int hits) {
        if (unit.context.rollType != CombatRollType.combatround || !unit.context.player.hasTech("tf-valortf")) {
            return hits;
        }
        return applyValorToRerollHits(unit, rerollDice, hits, "Valor");
    }

    private static int applyValorToRerollHits(
            UnitRollExecution.UnitRollState unit, List<Die> rerollDice, int hits, String abilityName) {
        for (Die die : rerollDice) {
            if (die.getResult() < 10) continue;
            hits++;
            sendValorExtraHitMessage(unit, abilityName);
        }
        return hits;
    }

    private static void sendValorExtraHitMessage(UnitRollExecution.UnitRollState unit, String abilityName) {
        MessageHelper.sendMessageToChannel(
                unit.context.event.getMessageChannel(),
                unit.context.player.getRepresentation() + " got an extra hit due to the **" + abilityName
                        + "** ability (it has been accounted for in the hit count).");
    }

    private static boolean hasSystemValor(UnitRollExecution.UnitRollState unit) {
        Player gloryHolder = Helper.getPlayerFromAbility(unit.context.game, "valor");
        if (gloryHolder == null) {
            gloryHolder = unit.context.game.getRealPlayers().stream()
                    .filter(player -> player.hasTech("tf-glorioushalls"))
                    .findFirst()
                    .orElse(null);
        }
        return gloryHolder != null
                && ButtonHelperAgents.getGloryTokenTiles(unit.context.game).contains(unit.context.tile);
    }

    static void offerDragonBombardmentAssignments(
            UnitRollExecution.UnitRollState unit, UnitGroupRollState roll, Tile tile, String excludedPlanet) {
        for (UnitHolder holder : tile.getPlanetUnitHolders()) {
            if (holder.getName().equalsIgnoreCase(excludedPlanet)) continue;
            for (Player target : unit.context.game.getRealPlayersNNeutral()) {
                if (!FoWHelper.playerHasUnitsOnPlanet(target, holder)) continue;
                List<Button> buttons = target.isRealPlayer()
                        ? List.of(Buttons.red(
                                "getDamageButtons_" + tile.getPosition() + "_bombardment",
                                "Assign Hit" + (roll.currentHits() == 1 ? "" : "s")))
                        : List.of(Buttons.green(
                                target.dummyPlayerSpoof() + "autoAssignGroundHits_" + holder.getName() + "_"
                                        + roll.currentHits(),
                                "Auto-assign Hit" + (roll.currentHits() == 1 ? "" : "s") + " For Dummy"));
                String message =
                        (target.isRealPlayer() ? target.getRepresentation() : unit.context.player.getRepresentation())
                                + ", please assign the Dragon BOMBARDMENT hit"
                                + (roll.currentHits() == 1 ? "" : "s")
                                + (target.isRealPlayer() ? " on " : " for the dummy player on ")
                                + Helper.getPlanetRepresentation(holder.getName(), unit.context.game) + ".";
                MessageHelper.sendMessageToChannelWithButtons(unit.context.event.getMessageChannel(), message, buttons);
            }
        }
    }

    Button buildHacanFlagshipThalnosButton(Player player, UnitType type, List<Die> results) {
        int amt = results.stream().filter(Die::eligibleForHeartPlus).toList().size();

        String id = player.factionButtonChecker() + "hacanFlagship_" + type.getValue() + "_" + amt;
        String label = " (" + amt + ")";
        return Buttons.green(id, label, type.getUnitTypeEmoji());
    }

    Button buildTkHacanWSThalnosButton(List<Die> results) {
        return null;
    }

    void destroyThalnosMissedUnits(UnitRollExecution.UnitRollState unit, UnitGroupRollState roll) {
        for (String thalnosUnit : unit.context.game.getThalnosUnits().keySet()) {
            String pos = thalnosUnit.split("_")[0];
            String unitHolderName = thalnosUnit.split("_")[1];
            Tile tile = unit.context.game.getTileByPosition(pos);
            String unitName = unit.unitModel.getUnitType().plainName();
            thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
            if (thalnosUnit.equals(unitName)) {
                DestroyUnitService.destroyUnits(
                        unit.context.event,
                        tile,
                        unit.context.game,
                        unit.context.player.getColor(),
                        roll.currentMisses() + " " + unitName + " " + unitHolderName,
                        true);
                break;
            }
        }
    }
}
