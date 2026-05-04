package ti4.contest.replay.house.hacan;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.house.CombatReplayHouseAbility;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.service.CombatReplayAbilityWindowText;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@Service
@Order(20)
@RequiredArgsConstructor
public class CombatReplayHacanAbilityService implements CombatReplayHouseAbility {

    public static final String MANAGE_VOTE_BUTTON_PREFIX = "combatReplayHacanManageVote_";
    public static final String MANAGE_MARKET_COMPACT_BUTTON_PREFIX = "combatReplayHacanManageMarketCompact_";
    public static final String MANAGE_TRADE_CONVOYS_BUTTON_PREFIX = "combatReplayHacanManageTradeConvoys_";

    private final CombatReplayHacanMarketCompactService marketCompactService;
    private final CombatReplayHacanTradeConvoysService tradeConvoysService;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatReplayHouseFavorService houseFavorService;

    @Override
    public void postDiscussionWindowAbilities(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        boolean offerMarketCompact = marketCompactService.shouldOfferVoting(candidate);
        if (!offerMarketCompact) return;

        TextChannel channel = houseChannel();
        if (channel == null) return;

        StringBuilder message = new StringBuilder("## ")
                .append(FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName()))
                .append(" Hacan Delegation Vote\n")
                .append(CombatReplayAbilityWindowText.votesLockLine(discussionWindowSeconds()))
                .append("\n")
                .append(favorBalanceLine())
                .append("\n");
        if (offerMarketCompact) {
            message.append(marketCompactService.marketCompactSummaryLine()).append("\n");
        }
        List<Button> buttons =
                List.of(Buttons.blue(MANAGE_MARKET_COMPACT_BUTTON_PREFIX + contest.getId(), "Manage Market Compact"));

        MessageHelper.sendMessageToChannelWithButtons(
                channel, message.toString().trim(), buttons);
    }

    private String favorBalanceLine() {
        return "-# Total Favor: `" + houseFavorService.balance(CombatReplayHouse.HACAN) + "`";
    }

    private int discussionWindowSeconds() {
        return marketCompactService.discussionWindowSeconds();
    }

    @Override
    public void beforeSideBetMarket(
            MessageChannel channel, Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        marketCompactService.lockMarketsIfNeeded(game, contest, candidate);
    }

    public void sendEphemeralVoteControls(ButtonInteractionEvent event, String buttonId) {
        VoteContext context = voteContext(event, buttonId, MANAGE_VOTE_BUTTON_PREFIX);
        if (context == null) return;

        boolean sentControls = false;
        if (marketCompactService.shouldOfferVoting(context.candidate())) {
            marketCompactService.sendEphemeralVotingControls(
                    event, context.game(), context.contest(), context.candidate());
            sentControls = true;
        }
        if (tradeConvoysService.shouldOfferVoting(context.contest(), context.candidate())) {
            tradeConvoysService.sendEphemeralVotingControls(event, context.contest(), context.candidate());
            sentControls = true;
        }
        if (!sentControls) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The Hacan voting window is closed.");
        }
    }

    public void sendEphemeralMarketCompactControls(ButtonInteractionEvent event, String buttonId) {
        VoteContext context = voteContext(event, buttonId, MANAGE_MARKET_COMPACT_BUTTON_PREFIX);
        if (context == null) return;
        if (!marketCompactService.shouldOfferVoting(context.candidate())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The Hacan Market Compact window is closed.");
            return;
        }
        marketCompactService.sendEphemeralVotingControls(event, context.game(), context.contest(), context.candidate());
    }

    public void sendEphemeralTradeConvoysControls(ButtonInteractionEvent event, String buttonId) {
        VoteContext context = voteContext(event, buttonId, MANAGE_TRADE_CONVOYS_BUTTON_PREFIX);
        if (context == null) return;
        if (!tradeConvoysService.shouldOfferVoting(context.contest(), context.candidate())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The Hacan Trade Convoys window is closed.");
            return;
        }
        tradeConvoysService.sendEphemeralVotingControls(event, context.contest(), context.candidate());
    }

    private VoteContext voteContext(ButtonInteractionEvent event, String buttonId, String prefix) {
        Long contestId = parseContestId(buttonId, prefix);
        if (contestId == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not read that Hacan vote panel.");
            return null;
        }

        CombatReplayContestEntity contest =
                replayContestRepository.findById(contestId).orElse(null);
        CombatCandidateEntity candidate = contest == null || contest.getCandidateId() == null
                ? null
                : candidateRepository.findById(contest.getCandidateId()).orElse(null);
        if (contest == null || candidate == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that Hacan vote.");
            return null;
        }

        return new VoteContext(contest, candidate, loadReplayGame(candidate));
    }

    private Long parseContestId(String buttonId, String prefix) {
        if (buttonId == null || !buttonId.startsWith(prefix)) return null;
        try {
            return Long.parseLong(buttonId.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Game loadReplayGame(CombatCandidateEntity candidate) {
        if (candidate == null || candidate.getGameName() == null) return null;
        if (!GameManager.isValid(candidate.getGameName())) return null;
        return GameManager.getManagedGame(candidate.getGameName()).getGame();
    }

    private TextChannel houseChannel() {
        Guild guild = JdaService.guildPrimary;
        if (guild == null) return null;
        TextChannel channel = guild.getTextChannelsByName(CombatReplayHouse.HACAN.channelName(), true).stream()
                .findFirst()
                .orElse(null);
        if (channel == null) {
            BotLogger.warning("Lazax house channel not found: " + CombatReplayHouse.HACAN.channelName());
        }
        return channel;
    }

    private record VoteContext(CombatReplayContestEntity contest, CombatCandidateEntity candidate, Game game) {}
}
