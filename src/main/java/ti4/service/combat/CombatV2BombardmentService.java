package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraUnitHandler;
import ti4.discord.interactions.commands.planet.PlanetExhaust;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.json.JsonMapperManager;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatV2RollData.Context;
import ti4.service.combat.CombatV2RollData.Effect;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.combat.CombatV2RollData.Resolution;
import tools.jackson.core.type.TypeReference;

/** Selects bombardment targets, assigns eligible units, and rolls once for each targeted planet. */
@UtilityClass
class CombatV2BombardmentService {

    static void prepareTarget(Request request) {
        if (!request.playerHasUnit("ashen_flagship")) return;
        String target = request.storedValue("bombardmentTarget" + request.getFaction());
        if (!target.isBlank()) {
            AshenUnitHandler.prepareFlagshipBombardmentContext(request.game(), request.player(), target);
        }
    }

    static int execute(Request request) {
        AshenUnitHandler.clearFlagshipBombardmentContexts(request.game());
        String assignmentKey = "assignedBombardment" + request.getFaction();
        if (request.storedValueIsEmpty(assignmentKey)) autoAssign(request, assignmentKey);

        List<BombardmentAssignment> assignments = JsonMapperManager.basic()
                .readValue(request.storedValue(assignmentKey), new TypeReference<List<BombardmentAssignment>>() {});

        List<String> bombardedPlanets = new ArrayList<>();
        for (String planet : bombardablePlanets(request)) {
            if (assignments.stream().noneMatch(assignment -> assignment.planet().equals(planet))) continue;
            request.setStoredValue("bombardmentTarget" + request.getFaction(), planet);
            CombatV2RollService.bombardmentTarget(request);
            bombardedPlanets.add(planet);
        }

        if (bombardedPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(request.messageChannel(), CombatV2Messages.noBombardmentTarget());
        } else if (ButtonHelper.doesPlayerHaveFSHere("kalora_flagship", request.player(), request.tile())) {
            KaloraUnitHandler.flagshipBombardmentReroll(
                    request.player(), request.messageChannel(), request.getTilePosition(), bombardedPlanets);
        }
        return 0;
    }

    private static List<String> bombardablePlanets(Request request) {
        List<String> planets = new ArrayList<>();
        for (Planet planet : request.planetHolders()) {
            boolean canTarget = !request.playerControlsPlanet(planet.getName())
                    || FoWHelper.otherPlayersHaveUnitsOnPlanet(request.player(), planet);
            boolean protectedByConvention = planet.getPlanetTypes().contains("cultural")
                    && ButtonHelper.isLawInPlay(request.game(), "conventions");
            if (canTarget && !protectedByConvention) planets.add(planet.getName());
        }
        return planets;
    }

    private static void autoAssign(Request request, String assignmentKey) {
        request.removeStoredValue(assignmentKey);
        String target = bestTarget(request);
        List<BombardmentAssignment> assignments = new ArrayList<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> units = CombatV2UnitService.getUnitsInBombardment(request);

        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey().getLeft();
            UnitHolder holder = entry.getKey().getRight();
            int galvanized = holder.getGalvanizedUnitCount(unit.getUnitType(), request.getColorId());
            for (int count = 0; count < entry.getValue(); count++) {
                assignments.add(new BombardmentAssignment(
                        unit.getAsyncId(), target, galvanized-- > 0, BombardmentAssignmentType.UNIT));
            }
        }
        if (request.playerHasTech("ps") || request.playerHasTech("absol_ps")) {
            assignments.add(new BombardmentAssignment("plasmascoring", target, false, BombardmentAssignmentType.TECH));
        }
        if (request.playerHasLeaderUnlockedOrAlliance("argentcommander") || request.playerHasTech("tf-zealous")) {
            assignments.add(
                    new BombardmentAssignment("argentcommander", target, false, BombardmentAssignmentType.LEADER));
        }
        request.setStoredValue(assignmentKey, JsonMapperManager.basic().writeValueAsString(assignments));
    }

    private static String bestTarget(Request request) {
        String fallback = "";
        for (String planet : bombardablePlanets(request)) {
            fallback = planet;
            for (Player opponent : request.otherPlayers()) {
                if (ButtonHelper.getNumberOfGroundForces(opponent, request.unitHolderFromPlanet(planet)) > 0)
                    return planet;
            }
        }
        return fallback;
    }

    static Resolution applyEffects(Resolution rolled) {
        Context context = rolled.context();
        int effectiveHits = rolled.hits();
        String message = rolled.message();
        List<Effect> effects = new ArrayList<>();

        if (context.opponent() != context.player() && context.opponentHasTech("proxima") && effectiveHits > 0) {
            int canceled = proximaCancellation(rolled, effectiveHits);
            if (canceled > 0) {
                effectiveHits -= canceled;
                String suffix = CombatV2Messages.proximaCanceled(canceled);
                effects.add(new Effect("proxima", -canceled, suffix.stripLeading()));
                message += suffix;
            }
        }
        if (context.playerHasBreakthrough("ashenbt")) {
            message = AshenBreakthroughHandler.appendBombardmentManualReminder(
                    context.player(), context.rollType(), message);
        }

        return new Resolution(
                context,
                rolled.round(),
                message,
                rolled.rolledHits(),
                effectiveHits,
                rolled.whiff(),
                rolled.slam(),
                rolled.payload(),
                effects);
    }

    private static int proximaCancellation(Resolution resolution, int hits) {
        Context context = resolution.context();
        if (context.opponentHasTech("tf-proxima")) return 1;
        String target = context.storedValue("bombardmentTarget" + context.getFaction());
        if (target.isBlank()) return 0;
        UnitHolder planet = context.unitHolderFromPlanet(target);
        return planet == null ? 0 : Math.min(hits, planet.getGalvanizedUnitCount(context.getColorId()));
    }

    static void publishOutput(Resolution resolution) {
        Context context = resolution.context();
        String target = context.storedValue("bombardmentTarget" + context.getFaction());

        if (context.isPrivateFowRoll()) {
            MessageHelper.sendMessageToChannel(
                    context.playerChannel(), CombatV2Messages.bombardmentNotRelayed(context.player()));
        }
        AshenLeadersHandler.offerCommanderBombardmentButtons(
                context.event(), context.game(), context.player(), resolution.hits());
        if (resolution.hits() > 0) {
            publishAssignmentPrompt(resolution, target);
            offerMeteorSlings(resolution, target);
            if (context.playerHasUnlockedBreakthrough("kalorabt")) {
                KaloraBreakthroughHandler.offerCommitInfantryButton(
                        context.event(), context.game(), context.player(), context.tile(), target);
            }
        }
        exhaustX89Target(resolution, target);
    }

    private static void publishAssignmentPrompt(Resolution resolution, String target) {
        Context context = resolution.context();
        int hits = resolution.hits();
        if (AshenLeadersHandler.offerHeroBombardmentAssignButtons(
                context.event(), context.game(), context.player(), hits, target)) return;
        if (context.isFowMode()) return;

        var buttons = CombatV2Buttons.bombardmentAssignment(context, hits);
        for (Player targetPlayer : context.playersAndNeutral()) {
            if (targetPlayer == context.player()
                    || target.isBlank()
                    || !FoWHelper.playerHasUnitsOnPlanet(targetPlayer, context.unitHolderFromPlanet(target))) continue;
            if (targetPlayer.isRealPlayer()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        context.messageChannel(), CombatV2Messages.bombardmentAssignment(targetPlayer, hits), buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(
                        context.messageChannel(),
                        CombatV2Messages.dummyBombardmentAssignment(context.player(), hits),
                        CombatV2Buttons.dummyBombardmentAssignment(targetPlayer, target, hits));
            }
        }
    }

    private static void offerMeteorSlings(Resolution resolution, String target) {
        Context context = resolution.context();
        if (!context.playerHasAbility("meteor_slings") && !context.playerHasPromissoryNote("dspnkhra")) return;
        MessageHelper.sendMessageToChannelWithButtons(
                context.messageChannel(),
                CombatV2Messages.meteorSlings(context.player(), resolution.hits()),
                CombatV2Buttons.meteorSlings(context, target));
    }

    private static void exhaustX89Target(Resolution resolution, String target) {
        Context context = resolution.context();
        if (!context.playerHasTech("x89c4") || target.isBlank()) return;
        for (Player targetPlayer : context.realPlayers()) {
            if (!targetPlayer.hasPlanetReady(target)) continue;
            PlanetExhaust.doAction(targetPlayer, target, context.game());
            MessageHelper.sendMessageToChannel(
                    targetPlayer.getCorrectChannel(),
                    CombatV2Messages.x89Exhausted(targetPlayer, target, context.game(), context.player()));
            return;
        }
    }
}
