package ti4.service.combat;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
import ti4.contest.replay.core.renderers.CombatRollPayloadRenderer;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix.VyserixBreakthroughHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.DisasterWatchHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatV2DiceData.RollResult;
import ti4.service.combat.CombatV2RollData.Context;
import ti4.service.combat.CombatV2RollData.ContextResult;
import ti4.service.combat.CombatV2RollData.PreparedRoll;
import ti4.service.combat.CombatV2RollData.Rejected;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.combat.CombatV2RollData.Resolution;
import ti4.service.combat.CombatV2RollData.Round;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.spring.context.SpringContext;

/** Runs combat roll setup, dice evaluation, post-roll effects, output, and hit prompts in order. */
@UtilityClass
public class CombatV2RollService {

    public static int combatRound(Request request) {
        ContextResult setupResult = CombatV2RollSetup.combatRound(request);
        PreparedRoll prepared = reportSetupResult(request, setupResult);
        if (prepared == null) return 0;
        Context context = prepared.context();

        RollResult rolled = CombatV2RollEngineService.roll(prepared.plan());
        CombatV2ModifierService.consume(context);

        Resolution resolution = assembleResolution(context, advanceRound(context), rolled);
        boolean tracked = publishRollMessage(resolution);
        recordRollStatistics(resolution);
        offerThalnosIfRelevant(resolution);
        announceSurprisingRoll(resolution, tracked);
        afterCombatRound(resolution);
        return resolution.hits();
    }

    public static int automatedCombatRound(Request request) {
        ContextResult setupResult = CombatV2RollSetup.automatedCombatRound(request);
        PreparedRoll prepared = reportSetupResult(request, setupResult);
        if (prepared == null) return 0;
        Context context = prepared.context();

        RollResult rolled = CombatV2RollEngineService.roll(prepared.plan());
        CombatV2ModifierService.consume(context);

        Resolution resolution = assembleResolution(context, advanceRound(context), rolled);
        boolean tracked = publishRollMessage(resolution);
        recordRollStatistics(resolution);
        offerThalnosIfRelevant(resolution);
        announceSurprisingRoll(resolution, tracked);
        afterCombatRound(resolution);
        return resolution.hits();
    }

    public static int antiFighterBarrage(Request request) {
        ContextResult setupResult = CombatV2RollSetup.antiFighterBarrage(request);
        PreparedRoll prepared = reportSetupResult(request, setupResult);
        if (prepared == null) return 0;
        Context context = prepared.context();

        RollResult rolled = CombatV2RollEngineService.roll(prepared.plan());
        CombatV2ModifierService.consume(context);

        Resolution resolution = assembleResolution(context, describeRoll(context), rolled);
        boolean tracked = publishRollMessage(resolution);
        recordRollStatistics(resolution);
        offerThalnosIfRelevant(resolution);
        announceSurprisingRoll(resolution, tracked);
        afterAntiFighterBarrage(resolution);
        return resolution.hits();
    }

    public static int bombardment(Request request) {
        return CombatV2BombardmentService.execute(request);
    }

    public static int bombardmentTarget(Request request) {
        CombatV2BombardmentService.prepareTarget(request);
        ContextResult setupResult = CombatV2RollSetup.bombardment(request);
        PreparedRoll prepared = reportSetupResult(request, setupResult);
        if (prepared == null) return 0;
        Context context = prepared.context();

        RollResult rolled = CombatV2RollEngineService.roll(prepared.plan());
        CombatV2ModifierService.consume(context);

        Resolution resolution = assembleResolution(context, describeRoll(context), rolled);
        resolution = CombatV2BombardmentService.applyEffects(resolution);
        boolean tracked = publishRollMessage(resolution);
        recordRollStatistics(resolution);
        offerThalnosIfRelevant(resolution);
        announceSurprisingRoll(resolution, tracked);
        afterBombardment(resolution);
        return resolution.hits();
    }

    public static int spaceCannonOffense(Request request) {
        if (request.anyRealPlayer(player -> player.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.resolveEmpSpaceCannonOffenseBlock(request)) return 0;
        ContextResult setupResult = CombatV2RollSetup.spaceCannonOffense(request);
        PreparedRoll prepared = reportSetupResult(request, setupResult);
        if (prepared == null) return 0;
        Context context = prepared.context();

        RollResult rolled = CombatV2RollEngineService.roll(prepared.plan());
        CombatV2ModifierService.consume(context);

        Resolution resolution = assembleResolution(context, describeRoll(context), rolled);
        boolean tracked = publishRollMessage(resolution);
        recordRollStatistics(resolution);
        offerThalnosIfRelevant(resolution);
        announceSurprisingRoll(resolution, tracked);
        afterSpaceCannonOffense(resolution);
        return resolution.hits();
    }

    public static int spaceCannonDefense(Request request) {
        if (request.anyRealPlayer(player -> player.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.resolveEmpSpaceCannonDefenseBlock(request)) return 0;
        ContextResult setupResult = CombatV2RollSetup.spaceCannonDefense(request);
        PreparedRoll prepared = reportSetupResult(request, setupResult);
        if (prepared == null) return 0;
        Context context = prepared.context();

        RollResult rolled = CombatV2RollEngineService.roll(prepared.plan());
        CombatV2ModifierService.consume(context);

        Resolution resolution = assembleResolution(context, describeRoll(context), rolled);
        boolean tracked = publishRollMessage(resolution);
        recordRollStatistics(resolution);
        offerThalnosIfRelevant(resolution);
        announceSurprisingRoll(resolution, tracked);
        afterSpaceCannonDefense(resolution);
        return resolution.hits();
    }

    private static PreparedRoll reportSetupResult(Request request, ContextResult result) {
        if (result instanceof Rejected rejected) {
            MessageHelper.sendMessageToChannel(request.messageChannel(), rejected.message());
            return null;
        }

        PreparedRoll prepared = (PreparedRoll) result;
        for (String notice : prepared.context().notices()) {
            MessageHelper.sendMessageToChannel(request.messageChannel(), notice);
        }
        return prepared;
    }

    private static Resolution assembleResolution(Context context, Round round, RollResult rolled) {
        CombatRollPayload payload = CombatV2RollPayloads.create(context, round, rolled);
        String message = trimTrailingSemicolon(CombatRollPayloadRenderer.render(payload));
        return new Resolution(
                context,
                round,
                message,
                rolled.totalHits(),
                rolled.totalHits(),
                rolled.whiff(),
                rolled.slam(),
                payload,
                List.of());
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
                resolution.whiff(),
                resolution.slam(),
                resolution.payload());

        return tracked;
    }

    private static void recordRollStatistics(Resolution resolution) {
        Context context = resolution.context();
        Player player = context.player();
        player.setActualHits(player.getActualHits() + resolution.rolledHits());
        String surprisingRoll = resolution.slam() ? "hits" : resolution.whiff() ? "miss" : "none";
        context.setStoredValue("surprisingDiceRoll", surprisingRoll);
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
    }

    private static void afterAntiFighterBarrage(Resolution resolution) {
        Context context = resolution.context();
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
        CombatV2BombardmentService.publishOutput(resolution);
    }

    private static void afterSpaceCannonOffense(Resolution resolution) {
        Context context = resolution.context();
        int hits = resolution.hits();
        if (isFowDummyTarget(context, hits) && !context.isPrivateFowRoll()) {
            sendFowDummyPrompt(context, resolution.round(), hits);
            return;
        }
        sendSpaceCannonOffencePrompt(context, hits);
    }

    private static void afterSpaceCannonDefense(Resolution resolution) {
        Context context = resolution.context();
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
        if (context.isFowMode() || trackedCandidateRoll || (!resolution.slam() && !resolution.whiff())) return;

        String disaster = CombatV2Messages.surprisingRoll(
                context.player(), context.opponent(), context.gameName(), resolution.message(), resolution.slam());
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
}
