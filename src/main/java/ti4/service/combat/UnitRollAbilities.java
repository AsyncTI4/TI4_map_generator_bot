package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
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
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
class UnitRollAbilities {
    // MODIFY PRIMARY-ROLL HITS

    static void resolveJolNarFlagshipExtraHits(UnitRollExecution.UnitRollState unit) {
        if (unit.unitModel.getUnitType() != UnitType.Flagship
                || !ValefarZService.hasFlagshipAbility(unit.pipeline.game, unit.pipeline.player, "jolnar_flagship"))
            return;
        unit.groupAllHitsProbability *=
                Math.pow(2.0 / (11 - unit.toHit + unit.modifierToHit), unit.numRolls * unit.multiplier);
        for (Die die : unit.activeDice) {
            if (die.getResult() >= 9) unit.addHits(2);
            unit.addMaximumHits(2);
        }
    }

    static void resolveTeklarEliteExtraHits(UnitRollExecution.UnitRollState unit) {
        if (unit.unitModel.getUnitType() != UnitType.Infantry || !unit.pipeline.player.hasUnit("tk-tekklarelite"))
            return;
        unit.groupAllHitsProbability *=
                Math.pow(2.0 / (11 - unit.toHit + unit.modifierToHit), unit.numRolls * unit.multiplier);
        for (Die die : unit.activeDice) {
            if (die.isSuccess()) unit.addHits(1);
            unit.addMaximumHits(1);
        }
    }

    private static int applyTeklarEliteToRerollHits(
            UnitRollExecution.UnitRollState unit, List<Die> rerolls, int rerollHits) {
        if (unit.unitModel.getUnitType() != UnitType.Infantry || !unit.pipeline.player.hasUnit("tk-tekklarelite")) {
            return rerollHits;
        }
        return rerollHits + DiceHelper.countSuccesses(rerolls);
    }

    static void resolveZephyrionCommanderExtraHits(UnitRollExecution.UnitRollState unit) {
        if ((unit.pipeline.rollType != CombatRollType.SpaceCannonDefence
                        && unit.pipeline.rollType != CombatRollType.SpaceCannonOffence)
                || !unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "zephyrioncommander"))
            return;
        for (Die die : unit.activeDice) {
            if (die.getResult() == 10) unit.addHits(1);
            unit.addMaximumHits(1);
        }
    }

    static void activateJusticerGraviton(UnitRollExecution.UnitRollState unit) {
        if (!"tf-justicerrail".equals(unit.unitModel.getId())
                || unit.pipeline.rollType != CombatRollType.SpaceCannonOffence
                || unit.hitRolls < 1) return;
        unit.pipeline.game.setStoredValue(unit.pipeline.player.getFaction() + "graviton", "yes");
    }

    // AFTER PRIMARY DICE: ACTIVATION, REROLLS, AND MISS RESOLUTION

    static void resolveJolNarCommanderRerolls(UnitRollExecution.UnitRollState unit) {
        if ((!unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "jolnarcommander")
                        && !unit.pipeline.player.hasTech("tf-tacticalbrilliance"))
                || unit.pipeline.rollType == CombatRollType.combatround) return;

        boolean rerollBombardmentHits = unit.pipeline.opponent == unit.pipeline.player
                && unit.pipeline.rollType == CombatRollType.bombardment
                && unit.pipeline.player.hasTech("proxima");
        int diceToReroll = rerollBombardmentHits ? unit.hitRolls : unit.numMisses;
        if (diceToReroll < 1) return;

        RerollResult reroll =
                rerollBombardmentHits ? unit.rollReplacementDice(diceToReroll) : unit.rollMisses(diceToReroll);
        if (rerollBombardmentHits) {
            unit.activeDice.removeIf(Die::isSuccess);
        } else {
            unit.activeDice.removeIf(Predicate.not(Die::isSuccess));
        }
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applyValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        unit.addResolvedHits(rerollHits);
        if (rerollBombardmentHits) unit.pipeline.totalHits -= unit.hitRolls;

        int displayedExtraRolls = rerollBombardmentHits ? unit.extraRollsForUnit : 0;
        UnitRollType payloadRollType = rerollBombardmentHits
                ? UnitRollType.JOL_NAR_COMMANDER_REROLL_HITS
                : UnitRollType.JOL_NAR_COMMANDER_REROLL_MISSES;
        DieRollSource rollSource = rerollBombardmentHits ? DieRollSource.REROLL_HIT : DieRollSource.REROLL_MISS;
        String unitRoll =
                unit.renderAndRecordRoll(displayedExtraRolls, payloadRollType, reroll.dice(), rerollHits, rollSource);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(diceToReroll)
                .append(rerollBombardmentHits ? " hit" : " miss")
                .append(diceToReroll == 1 ? "" : rerollBombardmentHits ? "s" : "es")
                .append(" due to Ta Zern, the Jol-Nar Commander:\n")
                .append(unitRoll);
    }

    static void offerGledgePdsExploration(UnitRollExecution.UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.SpaceCannonOffence
                && unit.pipeline.rollType != CombatRollType.SpaceCannonDefence) return;

        if (unit.pipeline.player.ownsUnit("gledge_pds2") && unit.pipeline.totalHits > 0) {
            String message = unit.pipeline.player.getRepresentation()
                    + ", use the buttons to explore a planet with the PDS that got the hit. It should be noted that the bot has no idea which PDS rolled which dice, but default practice would be to go from lowest tile position to highest, with _Plasma Scoring_ applying to the last die. You can specify any order before rolling though.";
            for (int hit = 0; hit < unit.pipeline.totalHits; hit++) {
                List<Button> buttons = new ArrayList<>();
                for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(
                        unit.pipeline.game, unit.pipeline.player, UnitType.Pds)) {
                    for (String planet : ButtonHelper.getPlanetsWithSpecificUnit(unit.pipeline.player, tile, "pds")) {
                        Planet planetUnit = unit.pipeline.game.getUnitHolderFromPlanet(planet);
                        if (planetUnit == null) continue;
                        planet = planetUnit.getName();
                        if (isNotBlank(planetUnit.getOriginalPlanetType())
                                && unit.pipeline.player.getPlanetsAllianceMode().contains(planet)
                                && FoWHelper.playerHasUnitsOnPlanet(unit.pipeline.player, tile, planet)) {
                            buttons.addAll(ButtonHelper.getPlanetExplorationButtons(
                                    unit.pipeline.game, planetUnit, unit.pipeline.player));
                        }
                    }
                }
                buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
                MessageHelper.sendMessageToChannelWithButtons(
                        unit.pipeline.player.getCorrectChannel(), message, buttons);
            }
        }

        if (!unit.pipeline.player.ownsUnit("gledge_pds")) return;
        String message = unit.pipeline.player.getRepresentation()
                + " use the buttons to explore a planet with the PDS that got the hit.";
        for (Die die : unit.activeDice) {
            if (die.getResult() < 9) continue;
            List<Button> buttons = new ArrayList<>();
            for (String planet :
                    ButtonHelper.getPlanetsWithSpecificUnit(unit.pipeline.player, unit.pipeline.activeSystem, "pds")) {
                Planet planetUnit = unit.pipeline.game.getUnitHolderFromPlanet(planet);
                if (planetUnit == null) continue;
                planet = planetUnit.getName();
                if (isNotBlank(planetUnit.getOriginalPlanetType())
                        && unit.pipeline.player.getPlanetsAllianceMode().contains(planet)
                        && FoWHelper.playerHasUnitsOnPlanet(unit.pipeline.player, unit.pipeline.activeSystem, planet)) {
                    buttons.addAll(ButtonHelper.getPlanetExplorationButtons(
                            unit.pipeline.game, planetUnit, unit.pipeline.player));
                }
            }
            buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
            MessageHelper.sendMessageToChannelWithButtons(unit.pipeline.player.getCorrectChannel(), message, buttons);
        }
    }

    static void resolveIronCommanderRerolls(UnitRollExecution.UnitRollState unit) {
        if (!IronLeadersHandler.shouldAutoRerollCommanderMechMisses(
                        unit.pipeline.game, unit.pipeline.player, unit.unitModel, unit.pipeline.rollType)
                || unit.numMisses < 1) return;
        RerollResult reroll = unit.rollMisses(unit.numMisses);
        unit.activeDice.removeIf(Predicate.not(Die::isSuccess));
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applyValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        unit.addResolvedHits(rerollHits);
        String unitRoll = unit.renderAndRecordRoll(
                0, UnitRollType.IRON_COMMANDER_REROLL_MISSES, reroll.dice(), rerollHits, DieRollSource.REROLL_MISS);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(unit.numMisses)
                .append(" miss")
                .append(unit.numMisses == 1 ? "" : "es")
                .append(" due to Captain Vakros, the Iron Tide Commander:\n")
                .append(unitRoll);
        unit.activeDice.addAll(reroll.dice());
        unit.numMisses -= rerollHits;
    }

    static void resolveThalnosMisses(UnitRollExecution.UnitRollState unit) {
        if (!unit.pipeline.isThalnosReroll) return;
        if (unit.pipeline.hacanFlagship) {
            unit.numMisses -= (int)
                    unit.activeDice.stream().filter(Die::eligibleForHeartPlus).count();
            unit.pipeline.hacanFsButtons.add(buildHacanFlagshipThalnosButton(
                    unit.pipeline.player, unit.unitModel.getUnitType(), unit.activeDice));
        } else if (unit.pipeline.tkHacanWarsun) {
            unit.numMisses = 0;
            unit.pipeline.hacanFsButtons.add(buildTkHacanWSThalnosButton(unit.activeDice));
        }
        if ((unit.pipeline.hacanFlagship || unit.pipeline.tkHacanWarsun) && !unit.extraRollsCount) {
            unit.pipeline.hacanFsThalnosDestroyTypes.add(unit.unitModel.getUnitType());
        }
        if (unit.numMisses > 0 && !unit.extraRollsCount) {
            unit.pipeline
                    .extra
                    .append(unit.pipeline.player.getFactionEmoji())
                    .append(" destroyed ")
                    .append(unit.numMisses)
                    .append(" of their own ")
                    .append(unit.unitModel.getName())
                    .append(unit.numMisses == 1 ? "" : "s")
                    .append(" due to ")
                    .append(unit.numMisses == 1 ? "a Thalnos miss" : "Thalnos misses")
                    .append(".");
            unit.pipeline.delayedAfterTotalNotes.add(new CombatRollPayload.CombatRollNote(
                    CombatRollNoteType.UNIT_DESTROYED_FROM_ROLL,
                    CombatRollNotePlacement.AFTER_TOTAL,
                    "thalnos",
                    unit.unitModel.getId(),
                    unit.numMisses,
                    Map.of(
                            "actorEmoji",
                            unit.pipeline.player.getFactionEmoji(),
                            "unitName",
                            unit.unitModel.getName())));
            unit.destroyMissedUnits();
        } else if (unit.numMisses > 0) {
            MessageHelper.sendMessageToChannel(
                    unit.pipeline.event.getMessageChannel(),
                    unit.pipeline.player.getFactionEmoji() + " had " + unit.numMisses + " "
                            + unit.unitModel.getName() + (unit.numMisses == 1 ? "" : "s") + " miss"
                            + (unit.numMisses == 1 ? "" : "es")
                            + " on a Thalnos roll, but no units were removed due to extra rolls being unaccounted for.");
        }
    }

    static void resolveStrikeWingAlphaInfantryKills(UnitRollExecution.UnitRollState unit) {
        if (unit.pipeline.player == unit.pipeline.opponent
                || (!("argent_destroyer2".equalsIgnoreCase(unit.unitModel.getId())
                        || "tf-swa".equalsIgnoreCase(unit.unitModel.getId())))
                || unit.pipeline.rollType != CombatRollType.AFB) return;
        int availableInfantry =
                unit.pipeline.space.getUnitCount(Units.UnitType.Infantry, unit.pipeline.opponent.getColor());
        if (availableInfantry < 1) return;
        int infantryKills = (int) Stream.concat(unit.primaryDiceHistory.stream(), unit.rerollDiceHistory.stream())
                .filter(die -> die.getResult() > 8)
                .count();
        infantryKills = Math.min(infantryKills, availableInfantry);
        if (infantryKills < 1) return;
        unit.pipeline
                .resultBuilder
                .append("\nDue to the Strike Wing Alpha II destroyer ability, ")
                .append(infantryKills)
                .append(" of ")
                .append(unit.pipeline.opponent.getRepresentation(false, true))
                .append(" infantry were destroyed\n");
        unit.pipeline.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.OPPONENT_UNIT_DESTROYED_FROM_ROLL,
                CombatRollNotePlacement.AFTER_UNIT_ROLLS,
                unit.unitModel.getId(),
                "infantry",
                infantryKills,
                Map.of("opponent", unit.pipeline.opponent.getRepresentation(false, true))));
        UnitKey infantry = Units.getUnitKey(UnitType.Infantry, unit.pipeline.opponent.getColorID());
        DestroyUnitService.destroyUnit(
                unit.pipeline.event,
                unit.pipeline.activeSystem,
                unit.pipeline.game,
                infantry,
                infantryKills,
                unit.pipeline.space,
                true);
    }

    // AFTER HITS: UNIT DESTRUCTION AND REWARDS

    static void rewardMercenaryCaptains(UnitRollExecution.UnitRollState unit) {
        if (unit.pipeline.totalHits < 1
                || !"neutral".equalsIgnoreCase(unit.pipeline.player.getFaction())
                || !unit.pipeline.game.getStoredValue("mercenarycaptaintrigged").isEmpty()) return;
        for (Player player : unit.pipeline.game.getRealPlayers()) {
            if (!player.hasTech("tf-mercenarycaptains")) continue;
            player.setCommodities(player.getCommodities() + 1);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " you gained 1 commodity due to the mercenary captains unit.");
            unit.pipeline.game.setStoredValue("mercenarycaptaintrigged", "yes");
        }
    }

    // REROLL CHAINS

    static List<Die> resolveMunitionsReservesReroll(UnitRollExecution.UnitRollState unit) {
        if (!unit.pipeline.game.getStoredValue("munitionsReserves").equalsIgnoreCase(unit.pipeline.player.getFaction())
                || unit.pipeline.rollType != CombatRollType.combatround
                || unit.numMisses < 1
                || unit.pipeline.isThalnosReroll) return List.of();
        RerollResult reroll = unit.rollMisses(unit.numMisses);
        unit.activeDice.removeIf(Predicate.not(Die::isSuccess));
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applyValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        unit.addResolvedHits(rerollHits);
        String unitRoll = unit.renderAndRecordRoll(
                0, UnitRollType.MUNITIONS_RESERVES_REROLL, reroll.dice(), rerollHits, DieRollSource.MUNITIONS_RESERVES);
        unit.pipeline
                .resultBuilder
                .append("**Munitions Reserve** rerolling ")
                .append(unit.numMisses)
                .append(" miss")
                .append(unit.numMisses == 1 ? "" : "es")
                .append(": ")
                .append(unitRoll);
        unit.activeDice.addAll(reroll.dice());
        return reroll.dice();
    }

    static void resolveInitialKaltrimCommanderRerolls(UnitRollExecution.UnitRollState unit) {
        if (!unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "kaltrimcommander")) return;
        int ones = (int)
                unit.activeDice.stream().filter(die -> die.getResult() == 1).count();
        if (ones < 1) return;
        unit.activeDice.removeIf(die -> die.getResult() == 1);
        RerollResult reroll = unit.rollReplacementDice(ones);
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applyValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        unit.addResolvedHits(rerollHits);
        String unitRoll = unit.renderAndRecordRoll(
                0, UnitRollType.KALTRIM_COMMANDER_REROLL_ONES, reroll.dice(), rerollHits, DieRollSource.REROLL_ONE);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(ones)
                .append(" roll")
                .append(ones == 1 ? "" : "s")
                .append(" of 1 due to the Kaltrim Commander:\n ")
                .append(unitRoll);
        unit.activeDice.addAll(reroll.dice());
    }

    static void resolvePostMunitionsKaltrimCommanderRerolls(
            UnitRollExecution.UnitRollState unit, List<Die> munitionsDice) {
        if (!unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "kaltrimcommander")
                || munitionsDice.isEmpty()) return;
        int ones =
                (int) munitionsDice.stream().filter(die -> die.getResult() == 1).count();
        if (ones < 1) return;
        unit.activeDice.removeAll(
                munitionsDice.stream().filter(die -> die.getResult() == 1).toList());
        RerollResult reroll = unit.rollReplacementDice(ones);
        int rerollHits = applyTeklarEliteToRerollHits(unit, reroll.dice(), reroll.hits());
        rerollHits = applyValorToCombatRerollHits(unit, reroll.dice(), rerollHits);
        unit.addResolvedHits(rerollHits);
        String unitRoll = unit.renderAndRecordRoll(
                0, UnitRollType.KALTRIM_COMMANDER_REROLL_ONES, reroll.dice(), rerollHits, DieRollSource.REROLL_ONE);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(ones)
                .append(" roll")
                .append(ones == 1 ? "" : "s")
                .append(" of 1 due to the Kaltrim Commander:\n ")
                .append(unitRoll);
        unit.activeDice.addAll(reroll.dice());
    }

    // AFTER DICE: CASCADING DICE AND IMMEDIATE ABILITY EFFECTS

    static void resolveDragonFreedBombardment(UnitRollExecution.UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.bombardment
                || !"tf-dragonfreed".equalsIgnoreCase(unit.unitModel.getId())
                || unit.pipeline.game.isFowMode()
                || unit.hitRolls < 1) return;
        String target = unit.pipeline.game.getStoredValue("bombardmentTarget" + unit.pipeline.player.getFaction());
        Tile origin = target.isEmpty()
                ? unit.pipeline.game.getTileByPosition(unit.pipeline.game.getActiveSystem())
                : unit.pipeline.game.getTileFromPlanet(target);
        for (String position : FoWHelper.getAdjacentTiles(
                unit.pipeline.game, origin.getPosition(), unit.pipeline.player, false, true)) {
            offerDragonBombardmentAssignments(unit, unit.pipeline.game.getTileByPosition(position), target);
        }
    }

    static void offerDragonBombardmentAssignments(
            UnitRollExecution.UnitRollState unit, Tile tile, String excludedPlanet) {
        for (UnitHolder holder : tile.getPlanetUnitHolders()) {
            if (holder.getName().equalsIgnoreCase(excludedPlanet)) continue;
            for (Player target : unit.pipeline.game.getRealPlayersNNeutral()) {
                if (!FoWHelper.playerHasUnitsOnPlanet(target, holder)) continue;
                List<Button> buttons = target.isRealPlayer()
                        ? List.of(Buttons.red(
                                "getDamageButtons_" + tile.getPosition() + "_bombardment",
                                "Assign Hit" + (unit.hitRolls == 1 ? "" : "s")))
                        : List.of(Buttons.green(
                                target.dummyPlayerSpoof() + "autoAssignGroundHits_" + holder.getName() + "_"
                                        + unit.hitRolls,
                                "Auto-assign Hit" + (unit.hitRolls == 1 ? "" : "s") + " For Dummy"));
                String message =
                        (target.isRealPlayer() ? target.getRepresentation() : unit.pipeline.player.getRepresentation())
                                + ", please assign the Dragon BOMBARDMENT hit" + (unit.hitRolls == 1 ? "" : "s")
                                + (target.isRealPlayer() ? " on " : " for the dummy player on ")
                                + Helper.getPlanetRepresentation(holder.getName(), unit.pipeline.game) + ".";
                MessageHelper.sendMessageToChannelWithButtons(
                        unit.pipeline.event.getMessageChannel(), message, buttons);
            }
        }
    }

    static void resolveSigmaJolNarFlagshipDice(UnitRollExecution.UnitRollState unit) {
        String id = unit.unitModel.getId();
        if (!"sigma_jolnar_flagship_1".equalsIgnoreCase(id) && !"sigma_jolnar_flagship_2".equalsIgnoreCase(id)) return;
        int additionalDice = unit.hitRolls;
        while (unit.hitRolls < 100 && additionalDice > 0) {
            int remainingHitCapacity = 100 - unit.hitRolls;
            List<Die> rolls = DiceHelper.rollDice(
                    unit.toHit - unit.modifierToHit, Math.min(additionalDice, remainingHitCapacity));
            additionalDice = DiceHelper.countSuccesses(rolls);
            unit.addHits(additionalDice);
            unit.recordAdditionalDice(rolls);
        }
    }

    static void resolveValorExtraHits(UnitRollExecution.UnitRollState unit) {
        List<String> valorAbilities = getActiveValorAbilities(unit);
        if (hasSystemValor(unit)) {
            ButtonHelperAbilities.readyBannerHalls(unit.pipeline.game);
        }

        for (String abilityName : valorAbilities) {
            unit.groupAllHitsProbability *=
                    Math.pow(1.0 / (11 - unit.toHit + unit.modifierToHit), unit.numRolls * unit.multiplier);
            for (Die die : unit.activeDice) {
                if (die.getResult() >= 10) {
                    unit.addHits(1);
                    MessageHelper.sendMessageToChannel(
                            unit.pipeline.event.getMessageChannel(),
                            unit.pipeline.player.getRepresentation() + " got an extra hit due to the **" + abilityName
                                    + "** ability (it has been accounted for in the hit count).");
                }
                unit.groupMaximumHits++;
            }
        }
    }

    private static int applyValorToCombatRerollHits(
            UnitRollExecution.UnitRollState unit, List<Die> rerollDice, int hits) {
        for (String abilityName : getActiveValorAbilities(unit)) {
            for (Die die : rerollDice) {
                if (die.getResult() < 10) continue;
                hits++;
                MessageHelper.sendMessageToChannel(
                        unit.pipeline.event.getMessageChannel(),
                        unit.pipeline.player.getRepresentation() + " got an extra hit due to the **" + abilityName
                                + "** ability (it has been accounted for in the hit count).");
            }
        }
        return hits;
    }

    private static List<String> getActiveValorAbilities(UnitRollExecution.UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.combatround) return List.of();
        List<String> abilities = new ArrayList<>();
        if (hasSystemValor(unit)) {
            abilities.add(unit.pipeline.game.isTwilightsFallMode() ? "Glorious Halls" : "Valor");
        }
        if (unit.pipeline.player.hasTech("tf-valortf")) abilities.add("Valor");
        return abilities;
    }

    private static boolean hasSystemValor(UnitRollExecution.UnitRollState unit) {
        Player gloryHolder = Helper.getPlayerFromAbility(unit.pipeline.game, "valor");
        if (gloryHolder == null) {
            gloryHolder = unit.pipeline.game.getRealPlayers().stream()
                    .filter(player -> player.hasTech("tf-glorioushalls"))
                    .findFirst()
                    .orElse(null);
        }
        return gloryHolder != null
                && ButtonHelperAgents.getGloryTokenTiles(unit.pipeline.game).contains(unit.pipeline.activeSystem);
    }

    static boolean hasValorAbilityHolder(Game game) {
        return Helper.getPlayerFromAbility(game, "valor") != null
                || game.getRealPlayers().stream().anyMatch(player -> player.hasTech("tf-glorioushalls"));
    }

    static void resolveVadenFlagshipTradeGood(UnitRollExecution.UnitRollState unit) {
        if (!"vaden_flagship".equalsIgnoreCase(unit.unitModel.getId())
                || unit.pipeline.rollType != CombatRollType.bombardment
                || unit.activeDice.stream().noneMatch(Die::isSuccess)) return;
        unit.pipeline.player.setTg(unit.pipeline.player.getTg() + 1);
        ButtonHelperAbilities.pillageCheck(unit.pipeline.player, unit.pipeline.game);
        ButtonHelperAgents.resolveArtunoCheck(unit.pipeline.player, 1);
        MessageHelper.sendMessageToChannel(
                unit.pipeline.player.getCorrectChannel(),
                unit.pipeline.player.getRepresentation()
                        + " gained 1 trade good due to hitting on a BOMBARDMENT roll with the Aurum Vadra (the Vaden flagship).");
    }

    static void resolveUzeanWardogAbility(UnitRollExecution.UnitRollState unit) {
        if (!"belkosea_mech".equalsIgnoreCase(unit.unitModel.getId()) || unit.hitRolls < 1) return;
        ButtonHelperFactionSpecific.offerMahactInfButtons(unit.pipeline.player, unit.pipeline.game);
        MessageHelper.sendMessageToChannel(
                unit.pipeline.event.getMessageChannel(),
                unit.pipeline.player.getRepresentation() + " please gain or convert 1 commodity a total of "
                        + StringHelper.pluralize(unit.hitRolls, "time")
                        + " due to your Uzean Wardog mech unit.");
    }

    // SUPPORTING BUTTON AND UNIT-DESTRUCTION HELPERS

    Button buildHacanFlagshipThalnosButton(Player player, UnitType type, List<Die> results) {
        int amt = results.stream().filter(Die::eligibleForHeartPlus).toList().size();

        String id = player.factionButtonChecker() + "hacanFlagship_" + type.getValue() + "_" + amt;
        String label = " (" + amt + ")";
        return Buttons.green(id, label, type.getUnitTypeEmoji());
    }

    Button buildTkHacanWSThalnosButton(List<Die> results) {
        return null;
    }

    void destroyThalnosMissedUnits(UnitRollExecution.UnitRollState unit) {
        for (String thalnosUnit : unit.pipeline.game.getThalnosUnits().keySet()) {
            String pos = thalnosUnit.split("_")[0];
            String unitHolderName = thalnosUnit.split("_")[1];
            Tile tile = unit.pipeline.game.getTileByPosition(pos);
            String unitName = unit.unitModel.getUnitType().plainName();
            thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
            if (thalnosUnit.equals(unitName)) {
                DestroyUnitService.destroyUnits(
                        unit.pipeline.event,
                        tile,
                        unit.pipeline.game,
                        unit.pipeline.player.getColor(),
                        unit.numMisses + " " + unitName + " " + unitHolderName,
                        true);
                break;
            }
        }
    }
}
