package ti4.contest.replay.house.naalu;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import ti4.contest.replay.service.CombatReplayHouseService;
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
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.FactionEmojis;

@Service
@Order(10)
@RequiredArgsConstructor
public class CombatReplayNaaluAbilityService implements CombatReplayHouseAbility {

    public static final String NAALU_PEEK_PREFIX = "combatReplayNaaluPeek_";
    public static final String NAALU_ACTION_CARDS = NAALU_PEEK_PREFIX + "actionCards_";
    public static final String NAALU_ROUND_ONE_ROLLS = NAALU_PEEK_PREFIX + "roundOneRolls_";
    public static final String NAALU_DO_NOT_USE = NAALU_PEEK_PREFIX + "doNotUse_";

    private static final String NAALU_ACTION_CARDS_ABILITY = "NAALU_ACTION_CARDS";
    private static final String NAALU_ROUND_ONE_ROLLS_ABILITY = "NAALU_ROUND_ONE_ROLLS";
    private static final String DO_NOT_USE_ABILITY = "DO_NOT_USE";
    private static final String SYSTEM_USER_ID = "0";
    private static final String SYSTEM_USER_NAME = "Naalu Delegation";

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayHouseAbilityUseRepository houseAbilityUseRepository;
    private final CombatReplayHouseFavorService houseFavorService;
    private final CombatReplayHouseAbilityVoteService voteService;
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
                        + "\n-# Gift of Foresight: choose one vision to reveal when discussion ends. Predictions and side bets remain open afterward.",
                peekButtons(contest.getId()));
    }

    private String favorBalanceLine() {
        return "-# Total Favor: `" + houseFavorService.balance(CombatReplayHouse.NAALU) + "`";
    }

    private int discussionWindowSeconds() {
        return settings.getReplayExecution().getDiscussionWindowSeconds();
    }

    public boolean userHasHouse(String discordUserId) {
        if (settings.getRuntime().isDevMode()) return true;
        return houseService.houseForUser(discordUserId) == CombatReplayHouse.NAALU;
    }

    public VoteResult voteActionCardPeek(long contestId, String discordUserId, String discordUserName) {
        return vote(contestId, NAALU_ACTION_CARDS_ABILITY, "Action Cards", discordUserId, discordUserName);
    }

    public VoteResult voteRoundOneRollPeek(long contestId, String discordUserId, String discordUserName) {
        return vote(contestId, NAALU_ROUND_ONE_ROLLS_ABILITY, "Round 1 Rolls", discordUserId, discordUserName);
    }

    public VoteResult voteDoNotUse(long contestId, String discordUserId, String discordUserName) {
        return vote(contestId, DO_NOT_USE_ABILITY, "Do Not Use", discordUserId, discordUserName);
    }

    private VoteResult vote(
            long contestId, String optionKey, String optionLabel, String discordUserId, String discordUserName) {
        CombatReplayContestEntity contest = loadContest(contestId);
        if (votingLocked(contest))
            return VoteResult.rejected("The Naalu Gift of Foresight window is closed for this combat.");
        CombatCandidateEntity candidate = loadCandidate(contest);
        if (candidate == null) return VoteResult.rejected("Could not find that combat archive.");
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

    private VoteResult recordVote(
            long candidateId, String optionKey, String optionLabel, String discordUserId, String discordUserName) {
        return VoteResult.from(voteService.recordVote(
                candidateId,
                CombatReplayHouse.NAALU,
                optionKey,
                optionLabel,
                discordUserId,
                discordUserName,
                this::favorCost,
                this::voteSummary,
                "Naalu Delegation has already resolved its ability for this combat.",
                "Naalu Delegation lacks the Favor for that ability."));
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
        String action =
                NAALU_ACTION_CARDS_ABILITY.equals(winningVote.optionKey()) ? "action cards" : "first-round rolls";
        return "## Naalu Gift of Foresight\nNaalu Delegation used Gift of Foresight to view "
                + action
                + " for this combat (`"
                + winningVote.voteCount()
                + "` votes).\n\n"
                + reveal;
    }

    private List<Button> peekButtons(Long contestId) {
        return List.of(
                abilityButton(
                        Buttons.blue(
                                NAALU_ACTION_CARDS + contestId,
                                "Vote: Action Cards" + favorCostSuffix(actionCardPeekFavorCost()),
                                CardEmojis.ActionCard),
                        actionCardPeekFavorCost()),
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
        return 0;
    }

    private int actionCardPeekFavorCost() {
        return settings.getHouseAbilities().getNaalu().getActionCardPeekFavorCost();
    }

    private int roundOneRollPeekFavorCost() {
        return settings.getHouseAbilities().getNaalu().getRoundOneRollPeekFavorCost();
    }

    private boolean votingLocked(CombatReplayContestEntity contest) {
        return contest == null
                || contest.getSideBetMarketPostedAt() != null
                || contest.getPostedAt() == null
                || !LocalDateTime.now()
                        .isBefore(contest.getPostedAt()
                                .plusSeconds(settings.getReplayExecution().getDiscussionWindowSeconds()));
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
        return optionKey;
    }

    private List<CombatCandidateEventEntity> orderedEvents(CombatCandidateEntity candidate) {
        return candidateEventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId());
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

    public record VoteResult(boolean accepted, String message) {
        public static VoteResult from(CombatReplayHouseAbilityVoteService.VoteResult result) {
            return new VoteResult(result.accepted(), result.message());
        }

        public static VoteResult accepted(String message) {
            return new VoteResult(true, message);
        }

        public static VoteResult rejected(String message) {
            return new VoteResult(false, message);
        }
    }
}
