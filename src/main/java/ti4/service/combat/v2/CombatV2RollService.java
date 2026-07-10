package ti4.service.combat.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.tuple.Pair;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNote;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
import ti4.contest.replay.core.renderers.CombatRollPayloadRenderer;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix.VyserixBreakthroughHandler;
import ti4.discord.interactions.commands.planet.PlanetExhaust;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.v2.CombatV2DiceData.HitTotalOperation;
import ti4.service.combat.v2.CombatV2DiceData.HitTotalRule;
import ti4.service.combat.v2.CombatV2DiceData.ModifierDuration;
import ti4.service.combat.v2.CombatV2DiceData.ModifierEffect;
import ti4.service.combat.v2.CombatV2DiceData.RollModifier;
import ti4.service.combat.v2.CombatV2DiceData.RollPlan;
import ti4.service.combat.v2.CombatV2DiceData.RollResult;
import ti4.service.combat.v2.CombatV2DiceData.RollSegment;
import ti4.service.combat.v2.CombatV2DiceData.RollSource;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.v2.CombatV2DiceData.ValueModifier;
import ti4.service.combat.v2.CombatV2RollData.BombardmentModifiers;
import ti4.service.combat.v2.CombatV2RollData.Context;
import ti4.service.combat.v2.CombatV2RollData.Request;
import ti4.service.combat.v2.CombatV2RollData.Resolution;
import ti4.service.combat.v2.CombatV2RollData.ResolvedModifier;
import ti4.service.combat.v2.CombatV2RollData.Round;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.HacanFlagshipService;
import ti4.spring.context.SpringContext;
import tools.jackson.core.type.TypeReference;

/** Runs combat roll setup, dice evaluation, post-roll effects, output, and hit prompts in order. */
@UtilityClass
public class CombatV2RollService {

    public static int combatRound(Request request) {
        return runCombatRound(request, false);
    }

    public static int automatedCombatRound(Request request) {
        return runCombatRound(request, true);
    }

    public static int thalnosCombatRound(Request request) {
        try {
            Context context = prepareCombatRound(request, false);
            if (context == null) return 0;
            context = selectThalnosUnits(context);
            if (context.rollingUnits().isEmpty()) {
                MessageHelper.sendMessageToChannel(context.messageChannel(), "There were no units selected to reroll.");
                return 0;
            }

            Resolution resolution = performRoll(context, advanceRound(context), repairStartOfRoundUnits(context));
            publishResult(resolution);
            destroyThalnosMisses(resolution);
            afterCombatRound(resolution);
            return resolution.hits();
        } finally {
            request.game().setStoredValue("thalnosPlusOne", "false");
        }
    }

    private static int runCombatRound(Request request, boolean automated) {
        Context context = prepareCombatRound(request, automated);
        if (context == null) return 0;

        PreRollEffects effects = repairStartOfRoundUnits(context);
        Resolution resolution = performRoll(context, advanceRound(context), effects);
        publishResult(resolution);
        afterCombatRound(resolution);
        return resolution.hits();
    }

    public static int antiFighterBarrage(Request request) {
        Context context = prepareAntiFighterBarrage(request);
        if (context == null) return 0;

        Resolution resolution = performRoll(context, describeRoll(context), PreRollEffects.none());
        publishResult(resolution);
        afterAntiFighterBarrage(resolution);
        return resolution.hits();
    }

    public static int bombardment(Request request) {
        AshenUnitHandler.clearFlagshipBombardmentContexts(request.game());
        String assignmentKey = "assignedBombardment" + request.player().getFaction();
        if (request.game().getStoredValue(assignmentKey).isEmpty()) autoAssign(request, assignmentKey);

        List<BombardmentAssignment> assignments = JsonMapperManager.basic()
                .readValue(
                        request.game().getStoredValue(assignmentKey),
                        new TypeReference<List<BombardmentAssignment>>() {});

        List<String> bombardedPlanets = new ArrayList<>();
        for (String planet : bombardablePlanets(request)) {
            if (assignments.stream().noneMatch(assignment -> assignment.planet().equals(planet))) continue;
            request.game().setStoredValue("bombardmentTarget" + request.player().getFaction(), planet);
            bombardmentTarget(request);
            bombardedPlanets.add(planet);
        }

        if (bombardedPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    request.event().getMessageChannel(), CombatV2Messages.noBombardmentTarget());
        } else if (ButtonHelper.doesPlayerHaveFSHere("kalora_flagship", request.player(), request.tile())) {
            KaloraUnitHandler.flagshipBombardmentReroll(
                    request.player(),
                    request.event().getMessageChannel(),
                    request.tile().getPosition(),
                    bombardedPlanets);
        }
        return 0;
    }

    public static int bombardmentTarget(Request request) {
        prepareBombardmentTarget(request);
        Context context = prepareBombardment(request);
        if (context == null) return 0;

        Resolution resolution = performRoll(context, describeRoll(context), PreRollEffects.none());
        resolution = applyBombardmentEffects(resolution);
        publishResult(resolution);
        afterBombardment(resolution);
        return resolution.hits();
    }

    public static int spaceCannonOffense(Request request) {
        if (request.game().getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.resolveEmpSpaceCannonOffenseBlock(request)) return 0;
        Context context = prepareSpaceCannonOffense(request);
        if (context == null) return 0;

        Resolution resolution = performRoll(context, describeRoll(context), PreRollEffects.none());
        publishResult(resolution);
        afterSpaceCannonOffense(resolution);
        return resolution.hits();
    }

    public static int spaceCannonDefense(Request request) {
        if (request.game().getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.resolveEmpSpaceCannonDefenseBlock(request)) return 0;
        Context context = prepareSpaceCannonDefense(request);
        if (context == null) return 0;

        Resolution resolution = performRoll(context, describeRoll(context), PreRollEffects.none());
        publishResult(resolution);
        afterSpaceCannonDefense(resolution);
        return resolution.hits();
    }

    /** Rolls an explicitly supplied combat-round unit group without publishing normal combat prompts. */
    public static Resolution rollCombatRoundUnits(Request request, Map<UnitModel, Integer> units, Player opponent) {
        UnitHolder holder = unitHolder(request);
        if (holder == null) return null;
        return rollSuppliedUnits(
                request,
                units,
                opponent,
                holder,
                CombatV2UnitService.combatRoundUnits(request, holder, opponent),
                CombatRollType.combatround);
    }

    /** Rolls an explicitly supplied bombardment unit group without publishing normal bombardment prompts. */
    public static Resolution rollBombardmentUnits(Request request, Map<UnitModel, Integer> units, Player opponent) {
        UnitHolder holder = unitHolder(request);
        if (holder == null) return null;
        return rollSuppliedUnits(
                request,
                units,
                opponent,
                holder,
                CombatV2UnitService.bombardmentUnits(request, opponent),
                CombatRollType.bombardment);
    }

    private static Resolution rollSuppliedUnits(
            Request request,
            Map<UnitModel, Integer> units,
            Player opponent,
            UnitHolder holder,
            Map<UnitModel, Integer> opponentUnits,
            CombatRollType rollType) {
        Map<Pair<UnitModel, UnitHolder>, Integer> located = new java.util.LinkedHashMap<>();
        units.forEach((unit, count) -> located.put(Pair.of(unit, holder), count));
        ModifierInputs inputs = new ModifierInputs(
                request.player(), request.game(), request.tile(), rollType, holder, opponent, units, opponentUnits);
        BombardmentModifiers bombardment = CombatV2Modifiers.bombardmentModifiers(inputs);
        Context supplied = new Context(
                request.player(),
                request.game(),
                request.event(),
                request.tile(),
                request.unitHolderName(),
                rollType,
                false,
                holder,
                opponent,
                located,
                opponentUnits,
                CombatV2Modifiers.resolve(inputs, bombardment),
                bombardment,
                List.of());
        return performRoll(supplied, describeRoll(supplied), PreRollEffects.none());
    }

    private static Resolution performRoll(Context context, Round round, PreRollEffects effects) {
        RollPlan plan = buildRollPlan(context);
        RollResult rolled = CombatV2RollEngine.roll(plan);
        CombatV2Modifiers.consumeTemporaryModifiers(context, plan);
        return assembleResolution(context, round, rolled, effects);
    }

    private static void publishResult(Resolution resolution) {
        boolean tracked = publishRollMessage(resolution);
        recordRollStatistics(resolution);
        offerThalnosIfRelevant(resolution);
        announceSurprisingRoll(resolution, tracked);
        afterAnyRoll(resolution);
    }

    private static Resolution assembleResolution(
            Context context, Round round, RollResult rolled, PreRollEffects effects) {
        CombatRollPayload payload = CombatV2RollPayloads.create(context, round, rolled, effects.notes());
        String message = trimTrailingSemicolon(CombatRollPayloadRenderer.render(payload));
        message += hitAdjustmentMessages(context, rolled);
        return new Resolution(context, round, message, rolled, payload);
    }

    private static PreRollEffects repairStartOfRoundUnits(Context context) {
        if (context.rollType() != CombatRollType.combatround || !Constants.SPACE.equalsIgnoreCase(context.holderName()))
            return PreRollEffects.none();

        List<CombatRollNote> notes = new ArrayList<>();
        if (ButtonHelper.doesPlayerHaveFSHere("letnev_flagship", context.player(), context.tile())
                && context.combatHolder().getDamagedUnitCount(UnitType.Flagship, context.getColorId()) > 0) {
            context.tile()
                    .removeUnitDamage(
                            context.holderName(),
                            Mapper.getUnitKey(AliasHandler.resolveUnit("fs"), context.getColorId()),
                            1);
            notes.add(new CombatRollNote(
                    CombatRollNoteType.UNIT_REPAIRED,
                    CombatRollNotePlacement.BEFORE_MODIFIERS,
                    "letnev_flagship",
                    "letnev_flagship",
                    1,
                    Map.of("timing", "START_OF_COMBAT_ROUND")));
        }
        if (context.player().ownsUnit("naaz_voltron")
                && context.combatHolder().getDamagedUnitCount(UnitType.Mech, context.getColorId()) > 0) {
            context.tile()
                    .removeUnitDamage(
                            context.holderName(),
                            Mapper.getUnitKey(AliasHandler.resolveUnit("mf"), context.getColorId()),
                            1);
            notes.add(new CombatRollNote(
                    CombatRollNoteType.UNIT_REPAIRED,
                    CombatRollNotePlacement.BEFORE_MODIFIERS,
                    "naaz_voltron",
                    "naaz_voltron",
                    1,
                    Map.of("timing", "START_OF_COMBAT_ROUND")));
        }
        return new PreRollEffects(notes);
    }

    private record PreRollEffects(List<CombatRollNote> notes) {
        private PreRollEffects {
            notes = List.copyOf(notes);
        }

        private static PreRollEffects none() {
            return new PreRollEffects(List.of());
        }
    }

    private static Round describeRoll(Context context) {
        String displayName = CombatV2Messages.rollDisplayName(context);
        return new Round(0, 0, displayName);
    }

    private static Round advanceRound(Context context) {
        Player player = context.player();
        Game game = context.game();
        Tile tile = context.tile();
        UnitHolder holder = context.combatHolder();

        int opponentRound = readRound(game, context.opponent(), tile, holder);
        int rollingRound = readRound(game, player, tile, holder);
        boolean thalnos = "true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"));
        if (rollingRound == 0 || !thalnos) {
            rollingRound = rollingRound == 0 ? 1 : rollingRound + 1;
            game.setStoredValue(roundKey(player, tile, holder), Integer.toString(rollingRound));
            if (!thalnos) consumeRoundModifiers(game, player);
        }

        String location =
                holder instanceof Planet ? Mapper.getPlanet(holder.getName()).getName() : tile.getRepresentation();
        String displayName = CombatV2Messages.combatRoundDisplayName(location, thalnos, rollingRound);
        return new Round(rollingRound, opponentRound, displayName);
    }

    private static int readRound(Game game, Player player, Tile tile, UnitHolder holder) {
        String value = game.getStoredValue(roundKey(player, tile, holder));
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static String roundKey(Player player, Tile tile, UnitHolder holder) {
        return "combatRoundTracker" + player.getFaction() + tile.getPosition() + holder.getName();
    }

    private static void consumeRoundModifiers(Game game, Player player) {
        if (game.getStoredValue("solagent").equalsIgnoreCase(player.getFaction())) game.removeStoredValue("solagent");
        if (game.getStoredValue("letnevagent").equalsIgnoreCase(player.getFaction())) {
            game.removeStoredValue("letnevagent");
        }
        if (game.getStoredValue("classifiedWeapons").startsWith(player.getFaction() + ";")) {
            game.removeStoredValue("classifiedWeapons");
        }
    }

    private static String trimTrailingSemicolon(String message) {
        return message.endsWith(";\n") ? message.substring(0, message.length() - 2) : message;
    }

    private static boolean publishRollMessage(Resolution resolution) {
        Context context = resolution.context();
        Round round = resolution.round();

        FOWCombatThreadMirroring.mirrorCombatMessage(
                context.event(), context.player(), context.game(), resolution.message());
        if (round.rollingSideRound() > round.opponentRound()) {
            MessageHelper.sendMessageToChannel(
                    context.messageChannel(), CombatV2Messages.startOfRound(round.rollingSideRound()));
        }
        MessageHelper.sendMessageToChannel(context.messageChannel(), resolution.message());
        relayPrivateFowSpaceCannon(resolution);

        CombatReplayService replay = SpringContext.getBean(CombatReplayService.class);
        boolean tracked = replay.isTrackedCandidateRoll(
                context.game(), context.player(), context.opponent(), context.tile(), context.rollType());
        replay.mirrorCombatRoll(
                context.game(),
                context.player(),
                context.opponent(),
                context.tile(),
                resolution.message(),
                context.rollType(),
                resolution.roll().whiff(),
                resolution.roll().slam(),
                resolution.payload());

        return tracked;
    }

    private static void recordRollStatistics(Resolution resolution) {
        Context context = resolution.context();
        Player player = context.player();
        int expectedHitsTimes10 = resolution.roll().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .flatMap(segment -> segment.dice().stream())
                .mapToInt(die -> 11 - die.threshold())
                .sum();
        player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + expectedHitsTimes10);
        player.setActualHits(player.getActualHits() + resolution.roll().rawHits());
        String surprisingRoll =
                resolution.roll().surprisingSlam() ? "hits" : resolution.roll().surprisingWhiff() ? "miss" : "none";
        context.game().setStoredValue("surprisingDiceRoll", surprisingRoll);
    }

    private static void afterCombatRound(Resolution resolution) {
        Context context = resolution.context();
        Round round = resolution.round();
        int hits = resolution.hits();
        if (!context.isFowMode()) {
            if (context.combatHolder() instanceof Planet) {
                sendPublicGroundPrompt(context, round, hits);
            } else {
                sendPublicSpacePrompt(context, round, hits);
            }
        } else if (context.opponentIsDummyOrNpc() && hits > 0) {
            sendFowDummyPrompt(context, round, hits);
        }
        publishGloryValor(resolution);
        offerHacanNearMisses(resolution);
    }

    private static void afterAntiFighterBarrage(Resolution resolution) {
        Context context = resolution.context();
        destroyStrikeWingInfantry(resolution);
        sendAfbPrompt(context, resolution.hits());
        if (context.playerHasUnlockedBreakthrough("vyserixbt")) {
            VyserixBreakthroughHandler.offerMoraySystemButtons(
                    context.event(), context.game(), context.player(), context.tile(), resolution.hits());
        }
    }

    private static void afterBombardment(Resolution resolution) {
        Context context = resolution.context();
        if (isFowDummyTarget(context, resolution.hits())) {
            sendFowDummyPrompt(context, resolution.round(), resolution.hits());
        }
        publishDragonBombardmentHits(resolution);
        awardVadenBombardmentTradeGood(resolution);
        publishBombardmentOutput(resolution);
    }

    private static void afterSpaceCannonOffense(Resolution resolution) {
        Context context = resolution.context();
        activateJusticerRail(resolution);
        publishGledgeExploration(resolution);
        int hits = resolution.hits();
        if (isFowDummyTarget(context, hits) && !context.isPrivateFowRoll()) {
            sendFowDummyPrompt(context, resolution.round(), hits);
            return;
        }
        sendSpaceCannonOffencePrompt(context, hits);
    }

    private static void afterSpaceCannonDefense(Resolution resolution) {
        Context context = resolution.context();
        publishGledgeExploration(resolution);
        if (isFowDummyTarget(context, resolution.hits())) {
            sendFowDummyPrompt(context, resolution.round(), resolution.hits());
        }
    }

    private static void relayPrivateFowSpaceCannon(Resolution resolution) {
        Context context = resolution.context();
        if (!context.isFowMode()
                || context.rollType() != CombatRollType.SpaceCannonOffence
                || !context.isPrivateFowRoll()) return;
        MessageHelper.sendMessageToChannel(
                context.opponentChannel(),
                CombatV2Messages.privateSpaceCannonRelay(
                        context.opponent(),
                        FOWCombatThreadMirroring.parseCombatRollMessage(resolution.message(), context.player())));
        MessageHelper.sendMessageToChannel(
                context.playerChannel(), CombatV2Messages.privateRollSent(context.opponent()));
    }

    private static void offerThalnosIfRelevant(Resolution resolution) {
        boolean available = resolution.payload().notes().stream()
                .anyMatch(note -> note.type() == CombatRollNoteType.REROLL_AVAILABLE);
        if (!available) return;
        Context context = resolution.context();
        MessageHelper.sendMessageToChannelWithButtons(
                context.messageChannel(), CombatV2Messages.thalnosPrompt(), CombatV2Buttons.thalnos(context));
    }

    private static void announceSurprisingRoll(Resolution resolution, boolean trackedCandidateRoll) {
        Context context = resolution.context();
        if (context.isFowMode()
                || trackedCandidateRoll
                || (!resolution.roll().surprisingSlam() && !resolution.roll().surprisingWhiff())) return;

        String disaster = CombatV2Messages.surprisingRoll(
                context.player(),
                context.opponent(),
                context.gameName(),
                resolution.message(),
                resolution.roll().surprisingSlam());
        DisasterWatchHelper.sendMessageInDisasterWatch(context.game(), disaster);
    }

    private static boolean isFowDummyTarget(Context context, int hits) {
        return context.isFowMode() && hits > 0 && context.opponentIsDummyOrNpc();
    }

    private static boolean isPrivateFowRoll(Context context) {
        return context.isPrivateFowRoll();
    }

    private static void sendAfbPrompt(Context context, int hits) {
        if (hits < 1) return;
        Player opponent = context.opponent();
        if (context.isFowMode() && !opponent.isDummy() && !opponent.isNpc()) return;
        String message = CombatV2Messages.afbAssignment(opponent, hits);
        MessageHelper.sendMessageToChannel(
                context.messageChannel(), message, CombatV2Buttons.antiFighterBarrage(context, opponent, hits));
    }

    private static void sendSpaceCannonOffencePrompt(Context context, int hits) {
        if (hits < 1 || context.opponent() == context.player()) return;
        Player opponent = context.opponent();
        boolean privateFowRoll = isPrivateFowRoll(context);
        if (context.isFowMode() && !privateFowRoll) return;
        var channel = privateFowRoll ? opponent.getCorrectChannel() : context.messageChannel();
        MessageHelper.sendMessageToChannel(channel, CombatV2Messages.spaceCannonSuffered(opponent, hits));

        String assignment = CombatV2Messages.automaticAssignment(
                opponent,
                hits,
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        opponent, context.game(), context.tile(), hits, context.event(), true, true));
        MessageHelper.sendMessageToChannelWithButtons(
                channel, assignment, CombatV2Buttons.spaceCannonOffense(context, opponent, hits));
    }

    private static void sendPublicGroundPrompt(Context context, Round round, int hits) {
        Player opponent = context.opponent();
        MessageHelper.sendMessageToChannel(
                context.messageChannel(),
                CombatV2Messages.combatHitsSuffered(opponent, hits, round.rollingSideRound()));

        if (context.automated()) {
            if (opponent.hasTech("vpw") && hits > 0) {
                MessageHelper.sendMessageToChannel(
                        context.messageChannel(), CombatV2Messages.valkyrieSuffered(context.player()));
            }
            return;
        }

        List<Button> buttons = CombatV2Buttons.nextRound(context, round, opponent);
        if (hits < 1) {
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        context.messageChannel(),
                        CombatV2Messages.mayRollNextRound(opponent, round.opponentRound() + 1),
                        buttons);
            }
            return;
        }

        CombatV2Buttons.groundAssignment(context, opponent, hits, buttons);
        if (!opponent.isDummy() && !opponent.isNpc()) {
            AshenPromissoryHandler.addFromTheAshesButton(
                    buttons, context.game(), opponent, context.player(), context.tile(), context.combatHolder(), hits);
            if (opponent.hasUnit("crystellum_mech")) {
                CrystellumUnitHandler.offerRefractumButtonIfRelevant(
                        buttons, opponent, context.game(), context.tile(), context.combatHolder(), hits);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                context.messageChannel(), CombatV2Messages.mayAutoAssign(opponent, hits), buttons);
        if (opponent.hasTech("vpw")) sendValkyriePrompt(context, hits);
    }

    private static void sendValkyriePrompt(Context context, int originalHits) {
        Player roller = context.player();
        MessageHelper.sendMessageToChannelWithButtons(
                context.messageChannel(),
                CombatV2Messages.valkyrieAssignment(roller),
                CombatV2Buttons.valkyrie(context, roller, originalHits));
    }

    private static void sendPublicSpacePrompt(Context context, Round round, int hits) {
        Player opponent = context.opponent();
        List<Button> buttons = CombatV2Buttons.nextRound(context, round, opponent);
        MessageHelper.sendMessageToChannel(
                context.messageChannel(),
                CombatV2Messages.combatHitsSuffered(opponent, hits, round.rollingSideRound()));

        if (hits < 1) {
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        context.messageChannel(),
                        CombatV2Messages.mayRollNextRound(opponent, round.opponentRound() + 1),
                        buttons);
            }
            return;
        }

        CombatV2Buttons.spaceAssignment(context, opponent, hits, buttons);
        if (!opponent.isDummy() && !opponent.isNpc()) {
            CrystellumAbilityHandler.addRefractionButtonIfRelevant(
                    buttons, opponent, context.game(), context.tile(), hits);
        }

        String relicName = opponent.hasRelic("metalivoidshielding")
                ? Mapper.getRelic("metalivoidshielding").getName()
                : null;
        String message = CombatV2Messages.spaceAssignment(
                opponent,
                hits,
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        opponent, context.game(), context.tile(), hits, context.event(), true),
                relicName,
                opponent.hasUnlockedBreakthrough("crystellumbt") && round.rollingSideRound() == 1);
        MessageHelper.sendMessageToChannelWithButtons(context.messageChannel(), message, buttons);
    }

    private static void sendFowDummyPrompt(Context context, Round round, int hits) {
        Player opponent = context.opponent();
        List<Button> buttons = CombatV2Buttons.nextRound(context, round, opponent);
        if (context.combatHolder() instanceof Planet) {
            buttons.add(CombatV2Buttons.dummyGroundAssignment(context, opponent, hits));
            MessageHelper.sendMessageToChannelWithButtons(
                    context.messageChannel(), CombatV2Messages.mayAutoAssign(opponent, hits), buttons);
        } else {
            buttons.add(CombatV2Buttons.dummySpaceAssignment(context, opponent, hits));
            String message = CombatV2Messages.automaticAssignment(
                    opponent,
                    hits,
                    ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                            opponent, context.game(), context.tile(), hits, context.event(), true));
            MessageHelper.sendMessageToChannelWithButtons(context.messageChannel(), message, buttons);
        }
    }

    private static void prepareBombardmentTarget(Request request) {
        if (!request.player().hasUnit("ashen_flagship")) return;
        String target = request.game()
                .getStoredValue("bombardmentTarget" + request.player().getFaction());
        if (!target.isBlank()) {
            AshenUnitHandler.prepareFlagshipBombardmentContext(request.game(), request.player(), target);
        }
    }

    private static List<String> bombardablePlanets(Request request) {
        List<String> planets = new ArrayList<>();
        for (Planet planet : request.tile().getPlanetUnitHolders()) {
            boolean canTarget = !request.player().getPlanetsAllianceMode().contains(planet.getName())
                    || FoWHelper.otherPlayersHaveUnitsOnPlanet(request.player(), planet);
            boolean protectedByConvention = planet.getPlanetTypes().contains("cultural")
                    && ButtonHelper.isLawInPlay(request.game(), "conventions");
            if (canTarget && !protectedByConvention) planets.add(planet.getName());
        }
        return planets;
    }

    private static void autoAssign(Request request, String assignmentKey) {
        request.game().removeStoredValue(assignmentKey);
        String target = bestTarget(request);
        List<BombardmentAssignment> assignments = new ArrayList<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> units = CombatV2UnitService.getUnitsInBombardment(request);

        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey().getLeft();
            UnitHolder holder = entry.getKey().getRight();
            int galvanized = holder.getGalvanizedUnitCount(
                    unit.getUnitType(), request.player().getColorID());
            for (int count = 0; count < entry.getValue(); count++) {
                assignments.add(new BombardmentAssignment(
                        unit.getAsyncId(), target, galvanized-- > 0, BombardmentAssignmentType.UNIT));
            }
        }
        if (request.player().hasTech("ps") || request.player().hasTech("absol_ps")) {
            assignments.add(new BombardmentAssignment("plasmascoring", target, false, BombardmentAssignmentType.TECH));
        }
        if (request.game().playerHasLeaderUnlockedOrAlliance(request.player(), "argentcommander")
                || request.player().hasTech("tf-zealous")) {
            assignments.add(
                    new BombardmentAssignment("argentcommander", target, false, BombardmentAssignmentType.LEADER));
        }
        request.game().setStoredValue(assignmentKey, JsonMapperManager.basic().writeValueAsString(assignments));
    }

    private static String bestTarget(Request request) {
        String fallback = "";
        for (String planet : bombardablePlanets(request)) {
            fallback = planet;
            for (Player opponent : request.game().getRealPlayersExcludingThis(request.player())) {
                if (ButtonHelper.getNumberOfGroundForces(
                                opponent, request.game().getUnitHolderFromPlanet(planet))
                        > 0) return planet;
            }
        }
        return fallback;
    }

    private static Resolution applyBombardmentEffects(Resolution rolled) {
        Context context = rolled.context();
        int effectiveHits = rolled.hits();
        String message = rolled.message();
        if (context.opponent() != context.player() && context.opponentHasTech("proxima") && effectiveHits > 0) {
            int canceled = proximaCancellation(rolled, effectiveHits);
            if (canceled > 0) {
                effectiveHits -= canceled;
                String suffix = CombatV2Messages.proximaCanceled(canceled);
                message += suffix;
            }
        }
        if (context.playerHasBreakthrough("ashenbt")) {
            message = AshenBreakthroughHandler.appendBombardmentManualReminder(
                    context.player(), context.rollType(), message);
        }

        return new Resolution(
                context, rolled.round(), message, rolled.roll().withTotalHits(effectiveHits), rolled.payload());
    }

    private static int proximaCancellation(Resolution resolution, int hits) {
        Context context = resolution.context();
        if (context.opponentHasTech("tf-proxima")) return 1;
        String target = context.storedValue("bombardmentTarget" + context.getFaction());
        if (target.isBlank()) return 0;
        UnitHolder planet = context.unitHolderFromPlanet(target);
        return planet == null ? 0 : Math.min(hits, planet.getGalvanizedUnitCount(context.getColorId()));
    }

    private static void publishBombardmentOutput(Resolution resolution) {
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

    private static Context prepareCombatRound(Request request, boolean automated) {
        UnitHolder holder = unitHolder(request);
        if (holder == null) return missingHolder(request);
        Player opponent = opponentAtHolder(request, holder);
        return context(
                request,
                CombatRollType.combatround,
                automated,
                holder,
                opponent,
                CombatV2UnitService.selectCombatRound(request, holder),
                CombatV2UnitService.combatRoundUnits(request, holder, opponent));
    }

    private static Context prepareAntiFighterBarrage(Request request) {
        UnitHolder holder = unitHolder(request);
        if (holder == null) return missingHolder(request);
        Player opponent = opponentAtHolder(request, holder);
        return context(
                request,
                CombatRollType.AFB,
                false,
                holder,
                opponent,
                CombatV2UnitService.selectAntiFighterBarrage(request, holder),
                CombatV2UnitService.antiFighterBarrageUnits(request, opponent));
    }

    private static Context prepareBombardment(Request request) {
        UnitHolder holder = unitHolder(request);
        if (holder == null) return missingHolder(request);
        String target = request.game()
                .getStoredValue("bombardmentTarget" + request.player().getFaction());
        Player opponent = target.isBlank()
                ? null
                : request.game().getRealPlayersNNeutral().stream()
                        .filter(player -> player.getPlanets().contains(target))
                        .findFirst()
                        .orElse(null);
        if (opponent == null) opponent = opponentAtHolder(request, holder);
        return context(
                request,
                CombatRollType.bombardment,
                false,
                holder,
                opponent,
                CombatV2UnitService.selectBombardment(request),
                CombatV2UnitService.bombardmentUnits(request, opponent));
    }

    private static Context prepareSpaceCannonOffense(Request request) {
        UnitHolder holder = unitHolder(request);
        if (holder == null) return missingHolder(request);
        Player opponent = spaceCannonOpponent(request, holder);
        return context(
                request,
                CombatRollType.SpaceCannonOffence,
                false,
                holder,
                opponent,
                CombatV2UnitService.selectSpaceCannonOffense(request),
                CombatV2UnitService.spaceCannonOffenseUnits(request, opponent));
    }

    private static Context prepareSpaceCannonDefense(Request request) {
        UnitHolder holder = unitHolder(request);
        if (holder == null) return missingHolder(request);
        if (!(holder instanceof Planet)) {
            return reject(
                    request,
                    CombatV2Messages.spaceCannonNeedsPlanet(request.tile().getPosition()));
        }
        Player opponent = spaceCannonOpponent(request, holder);
        return context(
                request,
                CombatRollType.SpaceCannonDefence,
                false,
                holder,
                opponent,
                CombatV2UnitService.selectSpaceCannonDefense(request, holder),
                CombatV2UnitService.spaceCannonDefenseUnits(request, holder, opponent));
    }

    private static Player opponentAtHolder(Request request, UnitHolder holder) {
        Player opponent = CombatV2UnitService.getOpponent(request, List.of(holder));
        return opponent == null ? request.player() : opponent;
    }

    private static Player spaceCannonOpponent(Request request, UnitHolder holder) {
        List<UnitHolder> holders = new ArrayList<>(List.of(holder));
        if (request.tile().getSpaceUnitHolder() != null)
            holders.add(request.tile().getSpaceUnitHolder());
        Player opponent = CombatV2UnitService.getOpponent(request, holders);
        return opponent == null ? request.player() : opponent;
    }

    private static Context missingHolder(Request request) {
        return reject(
                request,
                CombatV2Messages.missingHolder(
                        request.unitHolderName(), request.tile().getPosition()));
    }

    private static Context noUnits(Request request, CombatRollType rollType) {
        String fightingOn = Constants.SPACE.equalsIgnoreCase(request.unitHolderName())
                ? request.unitHolderName()
                : Helper.getPlanetRepresentation(request.unitHolderName(), request.game());
        return reject(
                request,
                CombatV2Messages.noUnits(
                        fightingOn,
                        request.tile().getPosition(),
                        request.player().getColor(),
                        request.player().getFactionEmoji(),
                        rollType));
    }

    private static Context reject(Request request, String message) {
        MessageHelper.sendMessageToChannel(request.event().getMessageChannel(), message);
        return null;
    }

    private static Context context(
            Request request,
            CombatRollType rollType,
            boolean automated,
            UnitHolder holder,
            Player opponent,
            CombatV2UnitService.UnitSelection selection,
            Map<UnitModel, Integer> opponentUnits) {
        ModifierInputs modifierInputs = new ModifierInputs(
                request.player(),
                request.game(),
                request.tile(),
                rollType,
                holder,
                opponent,
                selection.flatUnits(),
                opponentUnits);
        BombardmentModifiers bombardmentModifiers = CombatV2Modifiers.bombardmentModifiers(modifierInputs);
        List<ResolvedModifier> modifiers = CombatV2Modifiers.resolve(modifierInputs, bombardmentModifiers);
        Context context = new Context(
                request.player(),
                request.game(),
                request.event(),
                request.tile(),
                request.unitHolderName(),
                rollType,
                automated,
                holder,
                opponent,
                selection.units(),
                opponentUnits,
                modifiers,
                bombardmentModifiers,
                selection.notices());
        if (context.rollingUnits().isEmpty()) return noUnits(request, rollType);
        for (String notice : context.notices()) {
            MessageHelper.sendMessageToChannel(request.event().getMessageChannel(), notice);
        }
        return context;
    }

    private static UnitHolder unitHolder(Request request) {
        return request.tile().getUnitHolders().get(request.unitHolderName());
    }

    private static RollPlan buildRollPlan(Context context) {
        List<UnitRollPlan> baseUnits = new ArrayList<>();
        for (var rollingUnit : context.rollingUnitEntries()) {
            UnitModel unit = rollingUnit.getKey().getLeft();
            UnitRollPlan plan = new UnitRollPlan(
                    unit,
                    rollingUnit.getKey().getRight(),
                    rollingUnit.getValue(),
                    unit.getCombatDieCountForAbility(context.rollType(), context.player()),
                    unit.getCombatDieHitsOnForAbility(context.rollType(), context.player()),
                    RollSource.PRIMARY,
                    CombatV2Modifiers.forUnit(context, rollingUnit));
            baseUnits.add(normalizeOneShotUnitQuantity(context, plan));
        }
        List<UnitRollPlan> units = splitSingleUnitBonus(context, baseUnits);
        return new RollPlan(units, 0, hitTotalRules(context));
    }

    private static UnitRollPlan normalizeOneShotUnitQuantity(Context context, UnitRollPlan plan) {
        boolean experimentalBattleStation = context.rollType() == CombatRollType.SpaceCannonOffence
                && plan.dicePerUnit() == 3
                && "spacedock".equalsIgnoreCase(plan.unit().getBaseType());
        boolean tnelisAgent = context.rollType() == CombatRollType.bombardment
                && plan.dicePerUnit() > 1
                && "destroyer".equalsIgnoreCase(plan.unit().getBaseType());
        UnitRollPlan normalized = !experimentalBattleStation && !tnelisAgent
                ? plan
                : new UnitRollPlan(
                        plan.unit(),
                        plan.holder(),
                        1,
                        plan.baseDicePerUnit(),
                        plan.baseHitsOn(),
                        plan.initialSource(),
                        plan.modifiers());
        return normalizeThalnosPlan(context, normalized);
    }

    private static UnitRollPlan normalizeThalnosPlan(Context context, UnitRollPlan plan) {
        if (!"true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"))) return plan;
        boolean untrackedExtraDice = plan.dicePerUnit() > 1 || plan.extraDice() > 0;
        List<RollModifier> modifiers = new ArrayList<>(withExtraDice(plan.modifiers(), 0));
        if (untrackedExtraDice) {
            modifiers.add(new ValueModifier(
                    "thalnos_untracked_extra_dice", ModifierEffect.FLAT_HITS, 0, ModifierDuration.PERMANENT, ""));
        }
        return new UnitRollPlan(
                plan.unit(), plan.holder(), plan.quantity(), 1, plan.baseHitsOn(), plan.initialSource(), modifiers);
    }

    private static List<UnitRollPlan> splitSingleUnitBonus(Context context, List<UnitRollPlan> units) {
        if (context.rollType() != CombatRollType.combatround
                || "true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"))) return units;

        boolean supercharge = context.playerHasTech("tf-supercharge");
        boolean gravleash = context.playerHasUnlockedBreakthrough("letnevbt")
                && Constants.SPACE.equalsIgnoreCase(context.holderName());
        if (!supercharge && !gravleash) return units;

        String key = "highestValueSingleUnit" + context.getFaction();
        String selectedId = context.storedValue(key);
        UnitRollPlan selected = units.stream()
                .filter(unit -> unit.unit().getAsyncId().equalsIgnoreCase(selectedId))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            context.game().removeStoredValue(key);
            selected = units.stream()
                    .max(java.util.Comparator.<UnitRollPlan>comparingInt(
                                    unit -> unit.dicePerUnit() + Math.min(1, unit.extraDice()))
                            .thenComparing(unit -> unit.unit().getAsyncId()))
                    .orElse(null);
        }
        if (selected == null) return units;

        int bonus = supercharge
                ? 2
                : (int) context.rollingUnitModels().stream()
                        .filter(UnitModel::getIsShip)
                        .count();
        RollSource selectedSource =
                supercharge ? RollSource.SUPERCHARGE_SELECTED_UNIT : RollSource.GRAVLEASH_SELECTED_UNIT;
        RollSource restSource = supercharge ? RollSource.SUPERCHARGE_REST : RollSource.GRAVLEASH_REST;

        List<UnitRollPlan> split = new ArrayList<>();
        for (UnitRollPlan unit : units) {
            if (unit != selected) {
                split.add(unit);
                continue;
            }
            int selectedExtra = Math.min(1, unit.extraDice());
            List<RollModifier> selectedModifiers = withExtraDice(unit.modifiers(), selectedExtra);
            selectedModifiers = new ArrayList<>(selectedModifiers);
            selectedModifiers.add(new ValueModifier(
                    supercharge ? "tf-supercharge" : "letnevbt",
                    ModifierEffect.TO_HIT,
                    bonus,
                    ModifierDuration.PERMANENT,
                    supercharge ? "Supercharge" : "Gravleash Maneuvers"));
            split.add(new UnitRollPlan(
                    unit.unit(),
                    unit.holder(),
                    1,
                    unit.baseDicePerUnit(),
                    unit.baseHitsOn(),
                    selectedSource,
                    selectedModifiers));

            if (unit.quantity() > 1) {
                split.add(new UnitRollPlan(
                        unit.unit(),
                        unit.holder(),
                        unit.quantity() - 1,
                        unit.baseDicePerUnit(),
                        unit.baseHitsOn(),
                        restSource,
                        withExtraDice(unit.modifiers(), unit.extraDice() - selectedExtra)));
            }
        }
        context.game().removeStoredValue(key);
        return List.copyOf(split);
    }

    private static List<RollModifier> withExtraDice(List<RollModifier> modifiers, int dice) {
        List<RollModifier> allocated = modifiers.stream()
                .filter(modifier ->
                        !(modifier instanceof ValueModifier value) || value.effect() != ModifierEffect.EXTRA_DICE)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (dice > 0) {
            allocated.add(new ValueModifier(
                    "single_unit_allocated_extra_dice",
                    ModifierEffect.EXTRA_DICE,
                    dice,
                    ModifierDuration.PERMANENT,
                    ""));
        }
        return List.copyOf(allocated);
    }

    private static Context selectThalnosUnits(Context context) {
        Map<Pair<UnitModel, UnitHolder>, Integer> selected = new java.util.LinkedHashMap<>();
        for (var entry : context.rollingUnitEntries()) {
            UnitModel unit = entry.getKey().getLeft();
            int quantity = 0;
            for (String key : context.game().getThalnosUnits().keySet()) {
                String[] parts = key.split("_");
                if (parts.length < 3
                        || !parts[0].equals(context.getTilePosition())
                        || !parts[1].equals(context.holderName())) continue;
                String selectedType = parts[2].replace("damaged", "");
                if (selectedType.equals(unit.getBaseType())) {
                    quantity += context.game().getSpecificThalnosUnit(key);
                }
            }
            if (quantity > 0) selected.put(entry.getKey(), quantity);
        }
        Map<UnitModel, Integer> flat = new java.util.LinkedHashMap<>();
        selected.forEach((unit, count) -> flat.merge(unit.getLeft(), count, Integer::sum));
        ModifierInputs inputs = new ModifierInputs(
                context.player(),
                context.game(),
                context.tile(),
                context.rollType(),
                context.combatHolder(),
                context.opponent(),
                flat,
                context.opponentUnits());
        BombardmentModifiers bombardment = CombatV2Modifiers.bombardmentModifiers(inputs);
        return new Context(
                context.player(),
                context.game(),
                context.event(),
                context.tile(),
                context.unitHolderName(),
                context.rollType(),
                context.automated(),
                context.combatHolder(),
                context.opponent(),
                selected,
                context.opponentUnits(),
                CombatV2Modifiers.resolve(inputs, bombardment),
                bombardment,
                context.notices());
    }

    private static void destroyThalnosMisses(Resolution resolution) {
        Context context = resolution.context();
        boolean hacanProtected = context.playerHasUnit("hacan_flagship") || context.playerHasUnit("tk-fallofkenara");
        for (var unit : resolution.roll().units()) {
            if (unit.initialMisses() < 1 || hacanProtected) continue;
            boolean untracked = unit.plan().modifiers().stream()
                    .anyMatch(modifier -> "thalnos_untracked_extra_dice".equals(modifier.id()));
            if (untracked) {
                MessageHelper.sendMessageToChannel(
                        context.messageChannel(),
                        context.getFactionEmoji() + " had " + unit.initialMisses() + " "
                                + unit.plan().unit().getName()
                                + (unit.initialMisses() == 1 ? "" : "s") + " miss"
                                + (unit.initialMisses() == 1 ? "" : "es")
                                + " on a Thalnos roll, but no units were removed due to extra rolls being unaccounted for.");
                continue;
            }
            DestroyUnitService.destroyUnits(
                    context.event(),
                    context.tile(),
                    context.game(),
                    context.getColor(),
                    unit.initialMisses() + " "
                            + unit.plan().unit().getUnitType().plainName() + " " + context.holderName(),
                    true);
        }
    }

    private static List<HitTotalRule> hitTotalRules(Context context) {
        List<HitTotalRule> rules = new ArrayList<>();
        boolean groundOrBombardment = context.rollType() == CombatRollType.bombardment
                || (context.rollType() == CombatRollType.combatround && context.combatHolder() instanceof Planet);
        if (groundOrBombardment && context.playerHasTech("x89c4")) {
            rules.add(new HitTotalRule("x89c4", HitTotalOperation.MULTIPLY, 2));
        }
        if (context.rollType() == CombatRollType.bombardment && context.game().isConventionsOfWarAbandonedMode()) {
            rules.add(new HitTotalRule("abandoned_conventions_of_war", HitTotalOperation.MULTIPLY, 3));
        }
        if (context.rollType() == CombatRollType.bombardment && context.player().hasStoredValue("RazeFaction")) {
            rules.add(new HitTotalRule("raze", HitTotalOperation.MULTIPLY, 2));
        }
        if (context.rollType() == CombatRollType.bombardment && context.playerHasTech("dszelir")) {
            rules.add(new HitTotalRule("dszelir", HitTotalOperation.ADD_IF_HIT, 1));
        }
        if (context.rollType() != CombatRollType.combatround && context.playerHasTech("tf-shardsaturation")) {
            rules.add(new HitTotalRule("tf-shardsaturation", HitTotalOperation.ADD_IF_HIT, 1));
        }
        return List.copyOf(rules);
    }

    private static String hitAdjustmentMessages(Context context, RollResult rolled) {
        StringBuilder message = new StringBuilder();
        for (var adjustment : rolled.hitAdjustments()) {
            int added = adjustment.after() - adjustment.before();
            switch (adjustment.id()) {
                case "x89c4" -> message.append(CombatV2Messages.x89AdditionalHits(context.player(), added));
                case "dszelir" -> message.append(CombatV2Messages.shardVolley(context.player()));
                case "tf-shardsaturation" -> message.append(CombatV2Messages.shardSaturation(context.player()));
                default -> {}
            }
        }
        return message.toString();
    }

    private static void afterAnyRoll(Resolution resolution) {
        publishValorMessages(resolution);
        publishBelkoseaCommodities(resolution);
        awardMercenaryCaptainsCommodity(resolution);
    }

    private static void publishGloryValor(Resolution resolution) {
        long tens = countTens(resolution, true);
        boolean applied = resolution.context().resolvedModifiers().stream()
                .anyMatch(modifier -> "v2_glory_valor".equals(modifier.ruleId()));
        if (!applied || tens == 0) return;
        ButtonHelperAbilities.readyBannerHalls(resolution.context().game());
        for (int count = 0; count < tens; count++) {
            MessageHelper.sendMessageToChannel(
                    resolution.context().messageChannel(),
                    CombatV2Messages.gloryValor(
                            resolution.context().player(),
                            resolution.context().game().isTwilightsFallMode()));
        }
    }

    private static void offerHacanNearMisses(Resolution resolution) {
        Context context = resolution.context();
        boolean relevantUnit = (context.playerHasUnit("hacan_flagship")
                        && context.rollingUnitModels().stream()
                                .anyMatch(unit -> unit.getUnitType() == UnitType.Flagship))
                || (context.playerHasUnit("tk-fallofkenara")
                        && context.rollingUnitModels().stream()
                                .anyMatch(unit -> unit.getUnitType() == UnitType.Warsun));
        if (!relevantUnit || "true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"))) return;
        int nearMisses = (int) resolution.roll().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .flatMap(segment -> segment.dice().stream())
                .filter(die -> die.result() + 1 == die.threshold())
                .count();
        if (nearMisses > 0) {
            HacanFlagshipService.startHacanFlagshipNormal(
                    context.event(), context.game(), context.player(), context.tile(), nearMisses);
        }
    }

    private static void destroyStrikeWingInfantry(Resolution resolution) {
        Context context = resolution.context();
        if (context.player() == context.opponent()) return;
        int qualifying = (int) resolution.roll().units().stream()
                .filter(unit ->
                        "argent_destroyer2".equalsIgnoreCase(unit.plan().unit().getId())
                                || "tf-swa".equalsIgnoreCase(unit.plan().unit().getId()))
                .flatMap(unit -> unit.segments().stream())
                .flatMap(segment -> segment.dice().stream())
                .filter(die -> die.result() > 8)
                .count();
        UnitHolder space = context.tile().getSpaceUnitHolder();
        int kills = Math.min(
                qualifying,
                space.getUnitCount(UnitType.Infantry, context.opponent().getColor()));
        if (kills < 1) return;
        Units.UnitKey infantry =
                Units.getUnitKey(UnitType.Infantry, context.opponent().getColorID());
        DestroyUnitService.destroyUnit(context.event(), context.tile(), context.game(), infantry, kills, space, true);
    }

    private static void publishDragonBombardmentHits(Resolution resolution) {
        Context context = resolution.context();
        if (context.isFowMode()) return;
        int hits = resolution.roll().units().stream()
                .filter(unit ->
                        "tf-dragonfreed".equalsIgnoreCase(unit.plan().unit().getId()))
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> initialRoll(segment.source()))
                .mapToInt(RollSegment::hits)
                .sum();
        if (hits < 1) return;

        String originalTarget = context.storedValue("bombardmentTarget" + context.getFaction());
        Tile origin = originalTarget.isBlank()
                ? context.game().getTileByPosition(context.game().getActiveSystem())
                : context.game().getTileFromPlanet(originalTarget);
        if (origin == null) return;
        for (String position :
                FoWHelper.getAdjacentTiles(context.game(), origin.getPosition(), context.player(), false, true)) {
            Tile adjacent = context.game().getTileByPosition(position);
            if (adjacent == null) continue;
            for (Planet planet : adjacent.getPlanetUnitHolders()) {
                if (planet.getName().equalsIgnoreCase(originalTarget)) continue;
                for (Player target : context.playersAndNeutral()) {
                    if (!FoWHelper.playerHasUnitsOnPlanet(target, planet)) continue;
                    if (target.isRealPlayer()) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                context.messageChannel(),
                                CombatV2Messages.dragonBombardment(target, hits, planet.getName(), context.game()),
                                CombatV2Buttons.dragonBombardment(adjacent.getPosition(), hits));
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(
                                context.messageChannel(),
                                CombatV2Messages.dragonBombardmentForDummy(
                                        context.player(), hits, planet.getName(), context.game()),
                                CombatV2Buttons.dragonBombardmentForDummy(target, planet.getName(), hits));
                    }
                }
            }
        }
    }

    private static void awardVadenBombardmentTradeGood(Resolution resolution) {
        boolean earned = resolution.roll().units().stream()
                .filter(unit ->
                        "vaden_flagship".equalsIgnoreCase(unit.plan().unit().getId()))
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> beforeRerolls(segment.source()))
                .flatMap(segment -> segment.dice().stream())
                .anyMatch(die -> die.result() > 4);
        if (!earned) return;
        Context context = resolution.context();
        context.player().setTg(context.player().getTg() + 1);
        ButtonHelperAbilities.pillageCheck(context.player(), context.game());
        ButtonHelperAgents.resolveArtunoCheck(context.player(), 1);
        MessageHelper.sendMessageToChannel(
                context.playerChannel(), CombatV2Messages.vadenBombardmentTradeGood(context.player()));
    }

    private static void activateJusticerRail(Resolution resolution) {
        Context context = resolution.context();
        boolean rolled = resolution.roll().units().stream().anyMatch(unit -> "tf-justicerrail"
                .equalsIgnoreCase(unit.plan().unit().getId()));
        if (rolled) context.game().setStoredValue(context.getFaction() + "graviton", "yes");
    }

    private static void publishGledgeExploration(Resolution resolution) {
        Context context = resolution.context();
        for (var unit : resolution.roll().units()) {
            String id = unit.plan().unit().getId();
            int prompts;
            boolean allPds;
            if ("gledge_pds2".equalsIgnoreCase(id)) {
                prompts = unit.hits();
                allPds = true;
            } else if ("gledge_pds".equalsIgnoreCase(id)) {
                prompts = (int) unit.segments().stream()
                        .flatMap(segment -> segment.dice().stream())
                        .filter(die -> die.result() >= 9)
                        .count();
                allPds = false;
            } else {
                continue;
            }
            for (int count = 0; count < prompts; count++) {
                List<Button> planetButtons = gledgeExplorationButtons(context, allPds);
                MessageHelper.sendMessageToChannelWithButtons(
                        context.playerChannel(),
                        allPds
                                ? CombatV2Messages.gledgePds2Explore(context.player())
                                : CombatV2Messages.gledgePdsExplore(context.player()),
                        CombatV2Buttons.exploration(planetButtons));
            }
        }
    }

    private static void publishValorMessages(Resolution resolution) {
        boolean applied = resolution.context().resolvedModifiers().stream()
                .anyMatch(modifier -> "v2_valor".equals(modifier.ruleId()));
        if (!applied) return;
        long tens = countTens(resolution, false);
        for (int count = 0; count < tens; count++) {
            MessageHelper.sendMessageToChannel(
                    resolution.context().messageChannel(),
                    CombatV2Messages.gloryValor(resolution.context().player(), false));
        }
    }

    private static long countTens(Resolution resolution, boolean includeMunitions) {
        return resolution.roll().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> beforeRerolls(segment.source())
                        || (includeMunitions && segment.source() == RollSource.MUNITIONS_RESERVES))
                .flatMap(segment -> segment.dice().stream())
                .filter(die -> die.result() == 10)
                .count();
    }

    private static boolean beforeRerolls(RollSource source) {
        return initialRoll(source) || source == RollSource.SIGMA_JOL_NAR_FLAGSHIP;
    }

    private static boolean initialRoll(RollSource source) {
        return switch (source) {
            case PRIMARY, SUPERCHARGE_SELECTED_UNIT, SUPERCHARGE_REST, GRAVLEASH_SELECTED_UNIT, GRAVLEASH_REST -> true;
            default -> false;
        };
    }

    private static void publishBelkoseaCommodities(Resolution resolution) {
        int hits = resolution.roll().units().stream()
                .filter(unit ->
                        "belkosea_mech".equalsIgnoreCase(unit.plan().unit().getId()))
                .mapToInt(unit -> unit.segments().isEmpty()
                        ? 0
                        : unit.segments().getFirst().hits())
                .sum();
        if (hits < 1) return;
        Context context = resolution.context();
        ButtonHelperFactionSpecific.offerMahactInfButtons(context.player(), context.game());
        MessageHelper.sendMessageToChannel(
                context.messageChannel(), CombatV2Messages.belkoseaCommodities(context.player(), hits));
    }

    private static void awardMercenaryCaptainsCommodity(Resolution resolution) {
        Context context = resolution.context();
        if (resolution.roll().rawHits() < 1
                || !"neutral".equalsIgnoreCase(context.getFaction())
                || !context.storedValue("mercenarycaptaintrigged").isEmpty()) return;
        for (Player player : context.realPlayers()) {
            if (!player.hasTech("tf-mercenarycaptains")) continue;
            player.setCommodities(player.getCommodities() + 1);
            context.game().setStoredValue("mercenarycaptaintrigged", "yes");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), CombatV2Messages.mercenaryCaptains(player));
        }
    }

    private static List<Button> gledgeExplorationButtons(Context context, boolean allPds) {
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = allPds
                ? CheckUnitContainmentService.getTilesContainingPlayersUnits(
                        context.game(), context.player(), UnitType.Pds)
                : List.of(context.tile());
        for (Tile tile : tiles) {
            for (String planetName : ButtonHelper.getPlanetsWithSpecificUnit(context.player(), tile, "pds")) {
                Planet planet = context.unitHolderFromPlanet(planetName) instanceof Planet p ? p : null;
                if (planet == null
                        || org.apache.commons.lang3.StringUtils.isBlank(planet.getOriginalPlanetType())
                        || !context.player().getPlanetsAllianceMode().contains(planet.getName())
                        || !FoWHelper.playerHasUnitsOnPlanet(context.player(), tile, planet.getName())) continue;
                buttons.addAll(ButtonHelper.getPlanetExplorationButtons(context.game(), planet, context.player()));
            }
        }
        return buttons;
    }
}
