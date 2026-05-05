package ti4.contest.replay.house.naalu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.renderers.CombatRollPayloadRenderer;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.house.CombatReplayHouseAbility;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.service.CombatReplayAbilityWindowText;
import ti4.contest.replay.service.CombatReplayHouseAbilityVoteService;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHousePhaseService;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.contest.replay.service.CombatReplayInteractionResult;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.FactionEmojis;

@Service
@Order(10)
@RequiredArgsConstructor
public class CombatReplayNaaluAbilityService implements CombatReplayHouseAbility {

    public static final String NAALU_PEEK_PREFIX = "combatReplayNaaluPeek_";
    public static final String NAALU_ACTION_CARDS = NAALU_PEEK_PREFIX + "actionCards_";
    public static final String NAALU_ROUND_ONE_ROLLS = NAALU_PEEK_PREFIX + "roundOneRolls_";
    public static final String NAALU_LUCK_OMENS = NAALU_PEEK_PREFIX + "luckOmens_";
    public static final String NAALU_DO_NOT_USE = NAALU_PEEK_PREFIX + "doNotUse_";

    private static final String NAALU_ACTION_CARDS_ABILITY = "NAALU_ACTION_CARDS";
    private static final String NAALU_ROUND_ONE_ROLLS_ABILITY = "NAALU_ROUND_ONE_ROLLS";
    private static final String NAALU_LUCK_OMENS_ABILITY = "NAALU_LUCK_OMENS";
    private static final String DO_NOT_USE_ABILITY = "DO_NOT_USE";
    private static final String SYSTEM_USER_ID = "0";
    private static final String SYSTEM_USER_NAME = "Naalu Delegation";
    private static final double AVERAGE_DELTA_LIMIT = 0.5;
    private static final double STRONG_LUCK_DELTA_LIMIT = 1.5;

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayHouseAbilityUseRepository houseAbilityUseRepository;
    private final CombatReplayHouseFavorService houseFavorService;
    private final CombatReplayHouseAbilityVoteService voteService;
    private final CombatReplayHousePhaseService phaseService;
    private final CombatReplayHouseService houseService;
    private final ReplayDispatchSerializer payloadSerializer;

    @Override
    public void postDiscussionWindowAbilities(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        postAbilityButtonsIfNeeded(contest, candidate);
    }

    @Override
    public void beforeSideBetMarket(
            MessageChannel channel, Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        resolveVoteIfNeeded(contest, candidate);
    }

    public void postAbilityButtonsIfNeeded(CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (!settings.isHousesEnabled()) return;

        TextChannel channel = houseChannel();
        if (channel == null) return;

        String matchup =
                factionLabel(candidate.getAttackerFaction()) + " vs " + factionLabel(candidate.getDefenderFaction());
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "## "
                        + FactionEmojis.getFactionIcon(CombatReplayHouse.NAALU.displayName())
                        + " **Naalu Delegation may glimpse the shape of the coming battle for combat `"
                        + contest.getId()
                        + "`: "
                        + matchup
                        + ".**\n"
                        + CombatReplayAbilityWindowText.votesLockLine(discussionWindowSeconds())
                        + "\n"
                        + favorBalanceLine()
                        + "\n-# Gift of Foresight: choose whether to read the combat's omens when discussion ends. Predictions and side bets remain open afterward.",
                peekButtons(contest.getId()));
    }

    public boolean repostOpenGiftOfForesightButtons() {
        CombatReplayContestEntity contest =
                replayContestRepository.findFirstByOrderByIdDesc().orElse(null);
        if (!phaseService.discussionOpen(contest)) return false;
        CombatCandidateEntity candidate = loadCandidate(contest);
        if (candidate == null) return false;
        postAbilityButtonsIfNeeded(contest, candidate);
        return true;
    }

    private String favorBalanceLine() {
        return "-# Total Favor: `" + houseFavorService.balance(CombatReplayHouse.NAALU) + "`";
    }

    private int discussionWindowSeconds() {
        return phaseService.discussionWindowSeconds();
    }

    public boolean userHasHouse(String discordUserId) {
        if (settings.getRuntime().isDevMode()) return true;
        return houseService.houseForUser(discordUserId) == CombatReplayHouse.NAALU;
    }

    public CombatReplayInteractionResult voteActionCardPeek(
            long contestId, String discordUserId, String discordUserName) {
        return vote(contestId, NAALU_ACTION_CARDS_ABILITY, "Action Cards", discordUserId, discordUserName);
    }

    public CombatReplayInteractionResult voteRoundOneRollPeek(
            long contestId, String discordUserId, String discordUserName) {
        return vote(contestId, NAALU_ROUND_ONE_ROLLS_ABILITY, "Round 1 Rolls", discordUserId, discordUserName);
    }

    public CombatReplayInteractionResult voteLuckOmens(long contestId, String discordUserId, String discordUserName) {
        return vote(contestId, NAALU_LUCK_OMENS_ABILITY, "Omens", discordUserId, discordUserName);
    }

    public CombatReplayInteractionResult voteDoNotUse(long contestId, String discordUserId, String discordUserName) {
        return vote(contestId, DO_NOT_USE_ABILITY, "Do Not Use", discordUserId, discordUserName);
    }

    private CombatReplayInteractionResult vote(
            long contestId, String optionKey, String optionLabel, String discordUserId, String discordUserName) {
        CombatReplayContestEntity contest = loadContest(contestId);
        if (!phaseService.discussionOpen(contest))
            return CombatReplayInteractionResult.rejected(
                    "The Naalu Gift of Foresight window is closed for this combat.");
        CombatCandidateEntity candidate = loadCandidate(contest);
        if (candidate == null) return CombatReplayInteractionResult.rejected("Could not find that combat archive.");
        return recordVote(candidate.getId(), optionKey, optionLabel, discordUserId, discordUserName);
    }

    public void resolveVoteIfNeeded(CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (houseAbilityUseRepository.existsByCandidateIdAndHouse(candidate.getId(), CombatReplayHouse.NAALU)) return;

        CombatReplayHouseAbilityVoteService.WinningVote winningVote =
                voteService.winningVote(candidate.getId(), CombatReplayHouse.NAALU, this::optionLabel);
        if (winningVote == null) {
            resolveNoSelection(candidate.getId());
            return;
        }
        if (DO_NOT_USE_ABILITY.equals(winningVote.optionKey())) {
            resolveDoNotUse(candidate.getId(), winningVote);
            return;
        }

        String reveal;
        if (NAALU_ACTION_CARDS_ABILITY.equals(winningVote.optionKey())) {
            reveal = renderActionCardPeek(contest.getId());
        } else if (NAALU_ROUND_ONE_ROLLS_ABILITY.equals(winningVote.optionKey())) {
            reveal = renderRoundOneRollPeek(contest.getId());
        } else if (NAALU_LUCK_OMENS_ABILITY.equals(winningVote.optionKey())) {
            reveal = renderLuckOmens(contest.getId());
        } else {
            return;
        }

        if (!claimUse(
                candidate.getId(),
                favorCost(winningVote.optionKey()),
                winningVote.discordUserId(),
                winningVote.discordUserName())) {
            resolveInsufficientFavor(candidate.getId(), winningVote.optionKey());
            return;
        }

        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(channel, activeResolutionMessage(winningVote, reveal));
        }
    }

    public String renderActionCardPeek(long contestId) {
        CombatReplayContestEntity contest = loadContest(contestId);
        CombatCandidateEntity candidate = loadCandidate(contest);
        if (candidate == null) return "Could not find that combat archive.";

        Game game = loadGame(candidate.getGameName());
        List<String> lines = new ArrayList<>();
        for (CombatCandidateEventEntity event : orderedEvents(candidate)) {
            ReplayDispatchPayload payload = payloadSerializer.read(event);
            if (!(payload instanceof ReplayDispatchPayload.ActionCardPlayedDispatch(String actionCardId))) continue;

            ActionCardModel actionCard = Mapper.getActionCard(actionCardId);
            String cardName = actionCard == null ? actionCardId : actionCard.getName();
            lines.add("- " + actor(game, event.getActorFaction()) + ": _" + cardName + "_");
        }

        if (lines.isEmpty()) {
            return "## Gift of Foresight: Action Cards\nNo action cards have surfaced yet.";
        }
        return "## Gift of Foresight: Action Cards\n" + String.join("\n", lines);
    }

    public String renderRoundOneRollPeek(long contestId) {
        CombatReplayContestEntity contest = loadContest(contestId);
        CombatCandidateEntity candidate = loadCandidate(contest);
        if (candidate == null) return "Could not find that combat archive.";

        Game game = loadGame(candidate.getGameName());
        CombatReplayDecoys.Abilities abilities = CombatReplayDecoys.read(candidate.getReplayAbilitiesJson());
        List<String> rolls = new ArrayList<>();
        for (CombatCandidateEventEntity event : orderedEvents(candidate)) {
            if (event.getEventType() != CombatCandidateEventType.ROLL
                    || !Integer.valueOf(1).equals(event.getRoundNumber())) {
                continue;
            }
            ReplayDispatchPayload payload = payloadSerializer.read(event);
            if (!(payload instanceof ReplayDispatchPayload.CombatRollDispatch combatRoll)) continue;
            if (!isCombatRoundRoll(combatRoll.payload())) continue;

            CombatRollPayload payloadWithDecoys = CombatReplayDecoys.applyToRoll(combatRoll.payload(), abilities);
            String rendered = CombatRollPayloadRenderer.render(payloadWithDecoys);
            if (StringUtils.isBlank(rendered)) continue;
            rolls.add("### " + actor(game, event.getActorFaction()) + "\n" + rendered.strip());
        }

        if (rolls.isEmpty()) {
            return "## Gift of Foresight: Round 1 Rolls\nNo first-round combat rolls have surfaced yet.";
        }
        return "## Gift of Foresight: Round 1 Rolls\n" + String.join("\n\n", rolls);
    }

    public String renderLuckOmens(long contestId) {
        CombatReplayContestEntity contest = loadContest(contestId);
        CombatCandidateEntity candidate = loadCandidate(contest);
        if (candidate == null) return "Could not find that combat archive.";

        Game game = loadGame(candidate.getGameName());
        LuckAccumulator overall = new LuckAccumulator();
        Map<String, LuckAccumulator> actorLuck = new LinkedHashMap<>();
        for (CombatCandidateEventEntity event : orderedEvents(candidate)) {
            if (event.getEventType() != CombatCandidateEventType.ROLL) continue;
            ReplayDispatchPayload payload = payloadSerializer.read(event);
            if (!(payload instanceof ReplayDispatchPayload.CombatRollDispatch combatRoll)) continue;
            if (combatRoll.payload() == null) continue;

            String actorFaction = actorFaction(event, combatRoll.payload());
            LuckAccumulator actorAccumulator =
                    actorLuck.computeIfAbsent(actorFaction, ignored -> new LuckAccumulator());
            for (CombatRollPayload.UnitRoll unitRoll : combatRoll.payload().unitRolls()) {
                for (CombatRollPayload.DieRoll die : unitRoll.dice()) {
                    double expectedHits = hitProbability(die.threshold());
                    int actualHits = die.success() ? 1 : 0;
                    overall.record(actualHits, expectedHits);
                    actorAccumulator.record(actualHits, expectedHits);
                }
            }
        }

        if (overall.diceRolled() == 0) {
            return "## Gift of Foresight: Omens\nNo combat rolls have surfaced yet.";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Overall: **" + luckLabel(overall.delta()) + "**");
        for (Map.Entry<String, LuckAccumulator> entry : actorLuck.entrySet()) {
            LuckAccumulator actorAccumulator = entry.getValue();
            if (actorAccumulator.diceRolled() == 0) continue;
            lines.add(actor(game, entry.getKey()) + ": **" + luckLabel(actorAccumulator.delta()) + "**");
        }
        return "## Gift of Foresight: Omens\n" + String.join("\n", lines);
    }

    private double hitProbability(int threshold) {
        return Math.max(0.0, Math.min(1.0, (11 - threshold) / 10.0));
    }

    private String luckLabel(double delta) {
        if (delta <= -STRONG_LUCK_DELTA_LIMIT) return "Unlucky";
        if (delta < -AVERAGE_DELTA_LIMIT) return "Slightly Unlucky";
        if (delta <= AVERAGE_DELTA_LIMIT) return "Average";
        if (delta < STRONG_LUCK_DELTA_LIMIT) return "Slightly Lucky";
        return "Lucky";
    }

    private CombatReplayInteractionResult recordVote(
            long candidateId, String optionKey, String optionLabel, String discordUserId, String discordUserName) {
        return voteService.recordVote(
                candidateId,
                CombatReplayHouse.NAALU,
                optionKey,
                optionLabel,
                discordUserId,
                discordUserName,
                this::favorCost,
                this::voteSummary,
                "Naalu Delegation has already resolved its ability for this combat.",
                "Naalu Delegation lacks the Favor for that ability.");
    }

    private boolean claimUse(long candidateId, int favorCost, String discordUserId, String discordUserName) {
        return voteService.claimUse(candidateId, CombatReplayHouse.NAALU, favorCost, discordUserId, discordUserName);
    }

    private void resolveDoNotUse(long candidateId, CombatReplayHouseAbilityVoteService.WinningVote winningVote) {
        if (!claimUse(candidateId, 0, winningVote.discordUserId(), winningVote.discordUserName())) {
            return;
        }
        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "## Naalu Gift of Foresight\nNaalu Delegation chose not to use Gift of Foresight for this combat (`"
                            + winningVote.voteCount() + "` votes).");
        }
    }

    private void resolveNoSelection(long candidateId) {
        if (!claimUse(candidateId, 0, SYSTEM_USER_ID, SYSTEM_USER_NAME)) return;
        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "## Naalu Gift of Foresight\nNo Gift of Foresight option reached the voting threshold for this combat.");
        }
    }

    private void resolveInsufficientFavor(long candidateId, String optionKey) {
        if (!claimUse(candidateId, 0, SYSTEM_USER_ID, SYSTEM_USER_NAME)) return;
        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "## Naalu Gift of Foresight\nNaalu Delegation could not use "
                            + optionLabel(optionKey)
                            + " because it lacked the required Favor.");
        }
    }

    private String activeResolutionMessage(CombatReplayHouseAbilityVoteService.WinningVote winningVote, String reveal) {
        return "## Naalu Gift of Foresight\nNaalu Delegation used Gift of Foresight: "
                + optionLabel(winningVote.optionKey())
                + " for this combat (`"
                + winningVote.voteCount()
                + "` votes).\n\n"
                + reveal;
    }

    List<Button> peekButtons(Long contestId) {
        return List.of(
                abilityButton(
                        Buttons.blue(
                                NAALU_LUCK_OMENS + contestId,
                                "Vote: Omens" + favorCostSuffix(luckOmensFavorCost()),
                                FactionEmojis.Naalu),
                        luckOmensFavorCost()),
                abilityButton(
                        Buttons.blue(
                                NAALU_ROUND_ONE_ROLLS + contestId,
                                "Vote: Round 1 Rolls" + favorCostSuffix(roundOneRollPeekFavorCost()),
                                DiceEmojis.d10red_0),
                        roundOneRollPeekFavorCost()),
                Buttons.red(NAALU_DO_NOT_USE + contestId, "Vote: Do Not Use"));
    }

    Button abilityButton(Button button, int favorCost) {
        return houseFavorService.canAfford(CombatReplayHouse.NAALU, favorCost) ? button : button.asDisabled();
    }

    private String favorCostSuffix(int cost) {
        return " (-" + cost + " Favor)";
    }

    private int favorCost(String optionKey) {
        if (NAALU_ACTION_CARDS_ABILITY.equals(optionKey)) return actionCardPeekFavorCost();
        if (NAALU_ROUND_ONE_ROLLS_ABILITY.equals(optionKey)) return roundOneRollPeekFavorCost();
        if (NAALU_LUCK_OMENS_ABILITY.equals(optionKey)) return luckOmensFavorCost();
        return 0;
    }

    private int actionCardPeekFavorCost() {
        return settings.getHouseAbilities().getNaalu().getActionCardPeekFavorCost();
    }

    private int roundOneRollPeekFavorCost() {
        return settings.getHouseAbilities().getNaalu().getRoundOneRollPeekFavorCost();
    }

    private int luckOmensFavorCost() {
        return settings.getHouseAbilities().getNaalu().getLuckOmensFavorCost();
    }

    private int minimumAbilityVotesToResolve() {
        return voteService.minimumAbilityVotesToResolve();
    }

    private String voteSummary(Long candidateId) {
        return voteService.voteSummary(candidateId, CombatReplayHouse.NAALU, this::optionLabel)
                + "\nVotes needed to resolve: `"
                + minimumAbilityVotesToResolve() + "`";
    }

    private String optionLabel(String optionKey) {
        if (DO_NOT_USE_ABILITY.equals(optionKey)) return "Do Not Use";
        if (NAALU_ACTION_CARDS_ABILITY.equals(optionKey)) return "Action Cards";
        if (NAALU_ROUND_ONE_ROLLS_ABILITY.equals(optionKey)) return "Round 1 Rolls";
        if (NAALU_LUCK_OMENS_ABILITY.equals(optionKey)) return "Omens";
        return optionKey;
    }

    private List<CombatCandidateEventEntity> orderedEvents(CombatCandidateEntity candidate) {
        return candidateEventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId());
    }

    private String actorFaction(CombatCandidateEventEntity event, CombatRollPayload payload) {
        if (StringUtils.isNotBlank(event.getActorFaction())) return event.getActorFaction();
        if (payload != null && payload.header() != null) return payload.header().actorFaction();
        return null;
    }

    private boolean isCombatRoundRoll(CombatRollPayload payload) {
        return payload != null && payload.header() != null && payload.header().rollType() == CombatRollType.combatround;
    }

    private String actor(Game game, String actorFaction) {
        Player player = game == null ? null : game.getPlayerFromColorOrFaction(actorFaction);
        if (player != null) return player.getRepresentationNoPing();
        return StringUtils.defaultIfBlank(actorFaction, "Unknown player");
    }

    private String factionLabel(String faction) {
        return FactionEmojis.getFactionIcon(faction) + " " + StringUtils.capitalize(faction);
    }

    private CombatReplayContestEntity loadContest(long contestId) {
        return replayContestRepository.findById(contestId).orElse(null);
    }

    private CombatCandidateEntity loadCandidate(CombatReplayContestEntity contest) {
        if (contest == null) return null;
        return candidateRepository.findById(contest.getCandidateId()).orElse(null);
    }

    private Game loadGame(String gameName) {
        try {
            var managedGame = GameManager.getManagedGame(gameName);
            return managedGame == null ? null : managedGame.getGame();
        } catch (Exception e) {
            return null;
        }
    }

    private TextChannel houseChannel() {
        Guild guild = JdaService.guildPrimary;
        if (guild == null) return null;
        TextChannel channel = guild.getTextChannelsByName(CombatReplayHouse.NAALU.channelName(), true).stream()
                .findFirst()
                .orElse(null);
        if (channel == null) {
            BotLogger.warning("Lazax house channel not found: " + CombatReplayHouse.NAALU.channelName());
        }
        return channel;
    }

    private static class LuckAccumulator {
        private int actualHits;
        private double expectedHits;
        private int diceRolled;

        private void record(int actualHits, double expectedHits) {
            this.actualHits += actualHits;
            this.expectedHits += expectedHits;
            diceRolled++;
        }

        private double delta() {
            return actualHits - expectedHits;
        }

        private int diceRolled() {
            return diceRolled;
        }
    }
}
