package ti4.ai.nekro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.actioncards.ActionCardResponses;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.GalacticThreatRules;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.FactionBrain;
import ti4.ai.brain.StrategyCard;
import ti4.ai.explore.ExplorationRules;
import ti4.ai.explore.RelicActionRules;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.promissory.PromissoryRules;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringRules;
import ti4.ai.secrets.ActionSecretRules;
import ti4.ai.secrets.SecretCostRules;
import ti4.ai.secrets.SecretRules;
import ti4.ai.secrets.SecretValue;
import ti4.ai.strategy.ChecksAndBalances;
import ti4.ai.strategy.StrategyCardRanking;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.tactical.CombatRules;
import ti4.ai.tactical.IntegratedEconomyRules;
import ti4.ai.tactical.SingularityRules;
import ti4.ai.tactical.SlingRelayRules;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.ai.tech.TechRules;
import ti4.ai.trade.TradeRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;

public class NekroBrain implements FactionBrain {

    private static final String TIED_PLANETS = "tiedPlanets_";
    private static final String FLEET_LOGISTICS = "fl";
    private static final String CHECKS_AND_BALANCES = ChecksAndBalances.GIVE_PREFIX;
    private static final String GIVE_AWAY_KEY = "checksAndBalancesRecipient|";
    private static final String AC_DISCARD = "ac_discard_from_hand_";
    private static final Set<String> PUBLIC_WINDOW_PREFIXES = Set.of(
            "sc_no_follow_",
            Constants.PO_SCORING,
            Constants.PO_NO_SCORING,
            Constants.SO_NO_SCORING,
            "redistributeCCButtons",
            "pass_on_abilities",
            "declineToQueueAWhen",
            "declineToQueueAnAfter",
            "no_sabotage",
            "combatRoll_",
            "ringTile_",
            "getTilesThisFarAway_",
            "spend_",
            "reduceTG_",
            "deleteButtons_tacticalAction",
            "getTech_",
            "resolveAgendaVote_outcomeTie",
            CHECKS_AND_BALANCES,
            "outcome_",
            "planetOutcomes_",
            "exhaustForVotes_",
            "draw_1_AC",
            "nekroAgentRes_",
            PromissoryRules.GREYFIRE_TARGET,
            TIED_PLANETS,
            "sendTGTo_",
            "integratedBuild_",
            "resolveVolatile",
            "resolveExpedition",
            "resolveCoreMine",
            "resolveLocalFab_",
            "freelancersBuild_",
            "decline_explore",
            "comm_for_AC",
            "gain_1_comms",
            "gain_2_comms",
            "convert_2_comms",
            "addIonStorm_",
            "crownofemphidiaexplore",
            "acquireATech");
    private static final String NO_SABOTAGE = "no_sabotage";
    private static final long MIN_SELF_PLAY_PASS_DELAY_SECONDS = 5;
    private static final int SELF_PLAY_PASS_DELAY_SPREAD_SECONDS = 10;
    private static final long SAME_TURN_TOLERANCE_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final long MIN_SABOTAGE_PASS_DELAY_MINUTES = 10;
    private static final long SABOTAGE_PASS_DELAY_SPREAD_MINUTES = 30;

    private final List<Function<AiTurnContext, Optional<AiDecision>>> rules = List.of(
            ActionSecretRules::observe,
            PromissoryRules::observe,
            TradeRules::observe,
            this::finishCommandTokenGain,
            StrategyCardRules::exhaustAiDevelopmentForResearch,
            PaymentRules::pay,
            SecretCostRules::pay,
            NekroLeaderRules::payAgentCost,
            NekroRules::propagationTokens,
            NekroRules::commanderDraw,
            NekroRules::dacxiveAnimators,
            TechRules::reviveInfantry,
            TechRules::placeSpinnerInfantry,
            TechRules::placeSelfAssemblyMech,
            TechRules::placeMagenInfantry,
            TechRules::continueProductionBiomes,
            SlingRelayRules::next,
            TechRules::nullificationField,
            TechRules::neuralParasite,
            TechRules::salvageOperations,
            IntegratedEconomyRules::next,
            ExplorationRules::next,
            RelicActionRules::next,
            StrategyCardRules::chooseTechnology,
            StrategyCardRules::gainTokens,
            StrategyCardRules::placeStructure,
            StrategyCardRules::readyPlanets,
            StrategyCardRules::buildWithWarfare,
            NekroLeaderRules::chooseAgentTarget,
            NekroLeaderRules::continueHero,
            ActionCardRules::continuePlay,
            ActionCardRules::presetSummit,
            PromissoryRules::continuePlay,
            this::queueProveEndurance,
            this::discardExcessSecret,
            this::discardExcessActionCards,
            TradeRules::rescindStale,
            TradeRules::answerOffers,
            ActionCardResponses::respond,
            PromissoryRules::answerOffers,
            PromissoryRules::playCeasefire,
            PromissoryRules::playCombatNotes,
            SingularityRules::next,
            CombatRules::next,
            ActionSecretRules::next,
            SecretRules::scoreAgendaSecret,
            PromissoryRules::queueAgendaNote,
            this::declineWhens,
            GalacticThreatRules::next,
            this::declineAfters,
            AgendaVoting::next,
            this::abstainFromAgenda,
            this::breakAgendaTie,
            TradeRules::payDebtInAgenda,
            this::pickStrategyCard,
            NekroLeaderRules::startAgent,
            NekroLeaderRules::startHero,
            ActionCardRules::holdOwnTurn,
            PromissoryRules::holdForCeasefire,
            TradeRules::continueDraft,
            TradeRules::settleTrade,
            TradeRules::startDeals,
            this::takeTurn,
            StrategyCardRules::follow,
            this::declineStrategyCardFollow,
            this::scorePublicObjective,
            SecretRules::scoreStatusSecret,
            this::startStatusHomework,
            this::passOnSabotage);

    @Override
    public String id() {
        return "nekro";
    }

    @Override
    public Set<String> publicWindowHandlerPrefixes() {
        return PUBLIC_WINDOW_PREFIXES;
    }

    @Override
    public AiDecision decide(AiTurnContext context) {
        for (Function<AiTurnContext, Optional<AiDecision>> rule : rules) {
            Optional<AiDecision> decision = rule.apply(context);
            if (decision.isPresent()) return decision.get();
        }
        return AiDecision.idle();
    }

    private Optional<AiDecision> finishCommandTokenGain(AiTurnContext context) {
        Game game = context.game();
        if (!"statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        Player seat = context.seat();
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            if (!prompt.isHidden()
                    || !prompt.hasHandlerPrefix("increase_tactic_cc")
                    || prompt.firstEnabled(NekroBrain::isDoneRedistributing).isEmpty()) {
                continue;
            }
            int gained = CommandTokenPolicy.netGainSoFar(game, seat);
            if (gained < gainTarget(game, seat)) {
                String handler = CommandTokenPolicy.poolToGrow(game, seat);
                return prompt.enabledHandler(handler)
                        .map(button -> AiDecision.press(prompt, button, "gain a command token"));
            }
            Optional<PromptButton> surplus =
                    CommandTokenPolicy.poolToRedistributeFrom(game, seat).flatMap(prompt::enabledHandler);
            if (surplus.isPresent()) {
                return Optional.of(
                        AiDecision.press(prompt, surplus.get(), "move a surplus command token to where it is needed"));
            }
            return prompt.firstEnabled(NekroBrain::isDoneRedistributing)
                    .map(button -> AiDecision.press(prompt, button, "finish gaining command tokens"));
        }
        return Optional.empty();
    }

    private static int gainTarget(Game game, Player seat) {
        return CommandTokenPolicy.statusPhaseGainWithinReinforcements(game, seat);
    }

    private static boolean isDoneRedistributing(PromptButton button) {
        return "deleteButtons".equals(button.handlerId()) && button.label().startsWith("Done Redistributing");
    }

    private Optional<AiDecision> discardExcessSecret(AiTurnContext context) {
        Player seat = context.seat();
        if (seat.getSecretsUnscored().size() <= allowedUnscoredSecrets(context.game(), seat)) return Optional.empty();
        Optional<String> worst = SecretValue.lowestValueSecret(context.game(), seat);
        if (worst.isEmpty()) return Optional.empty();
        Integer handIndex = seat.getSecretsUnscored().get(worst.get());
        String handler = "discardSecret_" + handIndex;
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handler);
            if (button.isPresent()) {
                return Optional.of(AiDecision.press(prompt, button.get(), "discard the least promising secret"));
            }
        }
        return Optional.empty();
    }

    private static int allowedUnscoredSecrets(Game game, Player seat) {
        if (game.getRound() <= 1 && !game.isExtraSecretMode() && noRealObjectiveRevealed(game)) return 1;
        return Math.max(0, seat.getMaxSOCount() - seat.getSoScored());
    }

    public static boolean noRealObjectiveRevealed(Game game) {
        return game.getRevealedPublicObjectives().keySet().stream().allMatch(game.getCustomPublicVP()::containsKey);
    }

    private Optional<AiDecision> discardExcessActionCards(AiTurnContext context) {
        if (!ButtonHelper.isPlayerOverLimit(context.game(), context.seat())) return Optional.empty();
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> discard = ActionCardValue.worstDiscard(
                    context.game(),
                    context.seat(),
                    prompt,
                    AC_DISCARD,
                    "",
                    button -> !context.alreadyPressed(prompt, button));
            if (discard.isPresent()) {
                return Optional.of(AiDecision.press(prompt, discard.get(), "discard down to the action card limit"));
            }
        }
        return Optional.empty();
    }

    private Optional<AiDecision> queueProveEndurance(AiTurnContext context) {
        if (!context.seat().getSecretsUnscored().containsKey("pe")) return Optional.empty();
        return firstHandler(context, "autoProveEndurance_yes", "score Prove Endurance when passing last");
    }

    private Optional<AiDecision> declineWhens(AiTurnContext context) {
        if (context.game().getStoredValue("declinedWhens").contains(context.faction() + "_")) return Optional.empty();
        return firstHandler(context, "declineToQueueAWhen", "pass on playing a \"when\"");
    }

    private Optional<AiDecision> declineAfters(AiTurnContext context) {
        if (context.game().getStoredValue("declinedAfters").contains(context.faction() + "_")) return Optional.empty();
        if (GalacticThreatRules.holdsQueuedPrediction(context.game(), context.seat())) return Optional.empty();
        return firstHandler(context, "declineToQueueAnAfter", "pass on playing an \"after\"");
    }

    private Optional<AiDecision> abstainFromAgenda(AiTurnContext context) {
        if (!context.isActivePlayer()) return Optional.empty();
        for (AiPrompt prompt : promptsThisTurn(context)) {
            Optional<PromptButton> abstain = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && "resolveAgendaVote_0".equals(button.handlerId())
                    && !context.alreadyPressed(prompt, button));
            if (abstain.isPresent())
                return Optional.of(AiDecision.press(prompt, abstain.get(), "abstain from the vote"));
        }
        return Optional.empty();
    }

    private Optional<AiDecision> breakAgendaTie(AiTurnContext context) {
        if (!context.seat().getUserID().equals(context.game().getSpeakerUserID())
                || !context.isActivePlayer()
                || "agendaEnd".equalsIgnoreCase(context.game().getPhaseOfGame())) {
            return Optional.empty();
        }
        for (AiPrompt prompt : promptsThisTurn(context)) {
            if (prompt.isHidden()) continue;
            List<PromptButton> outcomes = prompt.enabledButtons().stream()
                    .filter(button -> button.isUnowned() || button.isOwnedBy(context.faction()))
                    .filter(AgendaPolicy::isTieOutcome)
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .toList();
            Optional<PromptButton> outcome = AgendaPolicy.chooseOutcome(context.game(), context.seat(), outcomes);
            if (outcome.isPresent()) {
                return Optional.of(AiDecision.press(prompt, outcome.get(), "break the agenda tie as speaker"));
            }
            List<PromptButton> owners = prompt.enabledButtons().stream()
                    .filter(button -> button.isUnowned() && AgendaPolicy.isTiedPlanetOwner(button))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .toList();
            Optional<PromptButton> owner = AgendaPolicy.choosePlanetOwner(context.game(), context.seat(), owners);
            if (owner.isPresent()) {
                return Optional.of(AiDecision.press(prompt, owner.get(), "choose whose planet breaks the tie"));
            }
        }
        return Optional.empty();
    }

    private Optional<AiDecision> pickStrategyCard(AiTurnContext context) {
        if (!context.isActivePlayer()
                || !"strategy".equalsIgnoreCase(context.game().getPhaseOfGame())) {
            return Optional.empty();
        }
        Optional<AiDecision> giveAway = giveAwayStrategyCard(context);
        if (giveAway.isPresent()) return giveAway;
        for (AiPrompt prompt : promptsThisTurn(context)) {
            List<PromptButton> picks = prompt.enabledButtons().stream()
                    .filter(button -> button.handlerId().startsWith("scPick_"))
                    .filter(button -> button.isOwnedBy(context.faction()))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .toList();
            if (ChecksAndBalances.inPlay(context.game())) {
                Optional<ChecksAndBalances.Plan> plan = ChecksAndBalances.plan(context.game(), context.seat(), picks);
                if (plan.isPresent()) {
                    context.memory()
                            .put(GIVE_AWAY_KEY + context.turnKey(), plan.get().recipientFaction());
                    return Optional.of(AiDecision.press(
                            prompt, plan.get().card(), plan.get().reason()));
                }
            }
            Optional<PromptButton> best = StrategyCardRanking.best(context.game(), context.seat(), picks);
            if (best.isPresent()) return Optional.of(AiDecision.press(prompt, best.get(), "pick a strategy card"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> giveAwayStrategyCard(AiTurnContext context) {
        String planned = context.memory().get(GIVE_AWAY_KEY + context.turnKey()).orElse(null);
        for (AiPrompt prompt : promptsThisTurn(context)) {
            List<PromptButton> recipients = prompt.enabledButtons().stream()
                    .filter(button -> button.isUnowned() && button.handlerId().startsWith(CHECKS_AND_BALANCES))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .toList();
            Optional<PromptButton> recipient =
                    ChecksAndBalances.recipient(context.game(), context.seat(), planned, recipients);
            if (recipient.isPresent()) {
                return Optional.of(AiDecision.press(prompt, recipient.get(), "hand over the strategy card"));
            }
        }
        return Optional.empty();
    }

    private Optional<AiDecision> takeTurn(AiTurnContext context) {
        if (!context.isActivePlayer()
                || !"action".equalsIgnoreCase(context.game().getPhaseOfGame())) {
            return Optional.empty();
        }
        if (TacticalRules.inProgress(context)) return TacticalRules.continueAction(context);
        Optional<AiDecision> primary = StrategyCardRules.resolvePrimary(context);
        if (primary.isPresent()) return primary;
        List<AiPrompt> thisTurn = promptsThisTurn(context);
        Optional<AiDecision> secondAction =
                TacticalRules.secondAction(context, thisTurn).or(() -> secondStrategicAction(context, thisTurn));
        if (secondAction.isPresent()) return secondAction;
        Optional<AiDecision> endOfTurnTech = TechRules.endOfTurn(context, thisTurn);
        if (endOfTurnTech.isPresent()) return endOfTurnTech;
        for (String handler : List.of("turnEnd", "endOfTurnAbilities")) {
            Optional<AiDecision> end = ownedHandler(context, thisTurn, handler, "end the turn");
            if (end.isPresent()) return end;
        }
        if (TacticalRules.pickingSystem(context)) return TacticalRules.start(context);
        if (TacticalRules.actionTaken(context)) return Optional.empty();
        Optional<AiDecision> warfareFirst = warfareBeforeFollowUp(context, thisTurn);
        if (warfareFirst.isPresent()) return warfareFirst;
        Optional<AiDecision> sling = SlingRelayRules.insteadOfTacticalAction(context, thisTurn);
        if (sling.isPresent()) return sling;
        Optional<AiDecision> relicAction = RelicActionRules.insteadOfTacticalAction(context, thisTurn);
        if (relicAction.isPresent()) return relicAction;
        Optional<AiDecision> tactical = TacticalRules.start(context);
        if (tactical.isPresent()) return tactical;
        for (AiPrompt prompt : thisTurn) {
            Optional<PromptButton> play = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && button.handlerId().startsWith("strategicAction_")
                    && unplayed(context.game(), StrategyCardRanking.initiative(button)));
            if (play.isPresent()) {
                return Optional.of(StrategyCardRules.play(
                        context, prompt, play.get(), StrategyCardRanking.initiative(play.get())));
            }
        }
        Optional<AiDecision> card = ActionCardRules.playBeforePassing(context);
        if (card.isPresent()) return card;
        Optional<AiDecision> relicBeforePassing = RelicActionRules.beforePassing(context, thisTurn);
        if (relicBeforePassing.isPresent()) return relicBeforePassing;
        Optional<AiDecision> beforePassing = TechRules.beforePassing(context, thisTurn);
        if (beforePassing.isPresent()) return beforePassing;
        for (String handler : List.of("passForRound", "passingAbilities")) {
            Optional<AiDecision> pass = ownedHandler(context, thisTurn, handler, "pass for the round");
            if (pass.isPresent()) return pass;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> secondStrategicAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!context.seat().hasTech(FLEET_LOGISTICS)
                || !TacticalRules.actionTaken(context)
                || TacticalRules.secondActionStarted(context)) {
            return Optional.empty();
        }
        for (AiPrompt prompt : thisTurn) {
            Optional<PromptButton> play = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && button.handlerId().startsWith("strategicAction_")
                    && !context.alreadyPressed(prompt, button)
                    && unplayed(context.game(), StrategyCardRanking.initiative(button))
                    && worthPlayingNow(context, StrategyCardRanking.initiative(button)));
            if (play.isPresent()) {
                TacticalRules.markSecondAction(context, false);
                return Optional.of(StrategyCardRules.play(
                        context, prompt, play.get(), StrategyCardRanking.initiative(play.get())));
            }
        }
        return Optional.empty();
    }

    private static boolean worthPlayingNow(AiTurnContext context, int initiative) {
        return StrategyCard.of(context.game(), initiative) == StrategyCard.IMPERIAL
                && StrategyCardRanking.imperialScoresNow(context.game(), context.seat());
    }

    private static Optional<AiDecision> warfareBeforeFollowUp(AiTurnContext context, List<AiPrompt> thisTurn) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasTech(FLEET_LOGISTICS)) return Optional.empty();
        boolean attack = TacticalPlanner.bestForWarfare(game, seat)
                .filter(plan -> plan.kind() == TacticalPlan.Kind.ATTACK)
                .isPresent();
        if (!attack) return Optional.empty();
        for (AiPrompt prompt : thisTurn) {
            Optional<PromptButton> warfare = prompt.firstEnabled(button -> {
                int initiative = StrategyCardRanking.initiative(button);
                return button.isOwnedBy(context.faction())
                        && button.handlerId().startsWith("strategicAction_")
                        && !context.alreadyPressed(prompt, button)
                        && unplayed(game, initiative)
                        && StrategyCard.of(game, initiative) == StrategyCard.WARFARE
                        && StrategyCardRules.isThundersEdgeWarfare(game, initiative);
            });
            if (warfare.isPresent()) {
                return Optional.of(StrategyCardRules.play(
                        context, prompt, warfare.get(), StrategyCardRanking.initiative(warfare.get())));
            }
        }
        return Optional.empty();
    }

    private static boolean unplayed(Game game, int initiative) {
        return initiative > 0 && !game.getPlayedSCs().contains(initiative);
    }

    private Optional<AiDecision> declineStrategyCardFollow(AiTurnContext context) {
        Player seat = context.seat();
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            Optional<PromptButton> decline = prompt.firstEnabled(button -> {
                if (!button.handlerId().startsWith("sc_no_follow_")) return false;
                int initiative = StrategyCardRanking.initiative(button);
                return initiative > 0
                        && context.game().getPlayedSCs().contains(initiative)
                        && !seat.hasFollowedSC(initiative)
                        && !seat.getSCs().contains(initiative)
                        && !StrategyCardRules.waitsToFollow(context, initiative)
                        && !context.alreadyPressed(prompt, button);
            });
            if (decline.isPresent()) {
                return Optional.of(AiDecision.press(prompt, decline.get(), "not follow a strategy card"));
            }
        }
        return Optional.empty();
    }

    private Optional<AiDecision> scorePublicObjective(AiTurnContext context) {
        Game game = context.game();
        if (!"statusScoring".equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        String key = context.faction() + "round" + game.getRound() + "PO";
        if (!game.getStoredValue(key).isEmpty()) return Optional.empty();
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            Optional<PromptButton> noScoring = prompt.enabledHandler(Constants.PO_NO_SCORING);
            if (noScoring.isEmpty()) continue;
            List<PromptButton> scoring = prompt.enabledButtons().stream()
                    .filter(button -> button.handlerId().startsWith(Constants.PO_SCORING))
                    .toList();
            Optional<ObjectivePolicy.ScoringChoice> best =
                    ObjectivePolicy.bestScorablePublic(game, context.seat(), scoring);
            if (best.isPresent()) {
                return Optional.of(
                        ScoringRules.scoreAndExpectPayment(context, prompt, best.get(), "score a public objective"));
            }
            return Optional.of(AiDecision.press(prompt, noScoring.get(), "no public objective to score"));
        }
        return Optional.empty();
    }

    private Optional<AiDecision> startStatusHomework(AiTurnContext context) {
        Game game = context.game();
        if (!"statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        String key = "statusHomeworkReactionFor" + context.faction() + "Round" + game.getRound();
        if (!game.getStoredValue(key).isEmpty()) return Optional.empty();
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            Optional<PromptButton> redistribute = prompt.enabledHandler("redistributeCCButtons");
            if (redistribute.isPresent()) {
                return Optional.of(AiDecision.press(prompt, redistribute.get(), "gain status phase command tokens"));
            }
        }
        return Optional.empty();
    }

    private Optional<AiDecision> passOnSabotage(AiTurnContext context) {
        List<AiPrompt> windows = new ArrayList<>();
        Set<String> visible = new HashSet<>();
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            visible.add(prompt.messageId());
            if (prompt.enabledHandler(NO_SABOTAGE).isPresent()) windows.add(prompt);
        }
        windows.addAll(scrolledOutSabotageWindows(context, visible));
        for (AiPrompt prompt : windows) {
            Optional<PromptButton> pass = prompt.enabledHandler(NO_SABOTAGE);
            if (pass.isEmpty() || context.alreadyPressed(prompt, pass.get()) || windowClosedFor(context, prompt)) {
                continue;
            }
            long readyAt = prompt.createdAtMillis() + sabotagePassDelayMillis(context, prompt);
            if (context.now() < readyAt) return Optional.of(new AiDecision.Wait(readyAt, "sabotage window"));
            return Optional.of(AiDecision.press(prompt, pass.get(), "no sabotage"));
        }
        return Optional.empty();
    }

    private static List<AiPrompt> scrolledOutSabotageWindows(AiTurnContext context, Set<String> visible) {
        Game game = context.game();
        String channelId = game.getMainChannelID();
        if (channelId == null) return List.of();
        List<AiPrompt> windows = new ArrayList<>();
        for (GameMessage window : GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD)) {
            if (visible.contains(window.messageId()) || !StringUtils.isNumeric(window.messageId())) continue;
            if (window.factionsThatReacted().contains(context.faction())) continue;
            long created = TimeUtil.getTimeCreated(Long.parseLong(window.messageId()))
                    .toInstant()
                    .toEpochMilli();
            PromptButton pass = new PromptButton(0, NO_SABOTAGE, NO_SABOTAGE, null, "No Sabotage", false);
            windows.add(new AiPrompt(
                    channelId, window.messageId(), AiPrompt.PromptSource.PUBLIC, "", List.of(pass), created));
        }
        return windows;
    }

    private static long sabotagePassDelayMillis(AiTurnContext context, AiPrompt prompt) {
        int seed = prompt.messageId().hashCode() ^ (int) context.profile().seed();
        if (AiSeats.isSelfPlay(context.game())) {
            return TimeUnit.SECONDS.toMillis(
                    MIN_SELF_PLAY_PASS_DELAY_SECONDS + Math.floorMod(seed, SELF_PLAY_PASS_DELAY_SPREAD_SECONDS));
        }
        long spread = Math.floorMod(
                prompt.messageId().hashCode() ^ context.profile().seed(), SABOTAGE_PASS_DELAY_SPREAD_MINUTES);
        return TimeUnit.MINUTES.toMillis(MIN_SABOTAGE_PASS_DELAY_MINUTES + spread);
    }

    private static boolean windowClosedFor(AiTurnContext context, AiPrompt prompt) {
        return GameMessageManager.getOne(context.game().getName(), prompt.messageId())
                .map(GameMessage::factionsThatReacted)
                .map(factions -> factions.contains(context.faction()))
                .orElse(true);
    }

    private Optional<AiDecision> firstHandler(AiTurnContext context, String handler, String reason) {
        for (AiPrompt prompt : newestFirst(context.prompts())) {
            Optional<PromptButton> button = prompt.enabledHandler(handler);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(AiDecision.press(prompt, button.get(), reason));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> ownedHandler(
            AiTurnContext context, List<AiPrompt> prompts, String handler, String reason) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> button = prompt.firstEnabled(
                    candidate -> candidate.handlerId().equals(handler) && candidate.isOwnedBy(context.faction()));
            if (button.isPresent()) return Optional.of(AiDecision.press(prompt, button.get(), reason));
        }
        return Optional.empty();
    }

    private static List<AiPrompt> promptsThisTurn(AiTurnContext context) {
        long turnStart = context.game().getLastActivePlayerChange().getTime() - SAME_TURN_TOLERANCE_MILLIS;
        return newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= turnStart)
                .toList();
    }

    private static List<AiPrompt> newestFirst(List<AiPrompt> prompts) {
        return prompts.stream()
                .sorted(Comparator.comparingLong(AiPrompt::createdAtMillis).reversed())
                .toList();
    }
}
