package ti4.contest.replay.house.mentak;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.service.CombatReplayAbilityWindowText;
import ti4.contest.replay.service.CombatReplayHouseAbilityVoteService;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@Service
@RequiredArgsConstructor
public class CombatReplayMentakAbilityService {

    public static final String MENTAK_DECOY_PREFIX = "combatReplayMentakDecoy_";
    public static final String MENTAK_MANAGE_VOTE_PREFIX = "combatReplayMentakManageVote_";
    public static final String MENTAK_DO_NOT_USE = MENTAK_DECOY_PREFIX + "doNotUse_";

    private static final String DO_NOT_USE_ABILITY = "DO_NOT_USE";
    private static final String SYSTEM_USER_ID = "0";
    private static final String SYSTEM_USER_NAME = "Mentak Delegation";

    private final CombatContestSettings settings;
    private final CombatCandidateRepository candidateRepository;
    private final CombatReplayHouseAbilityUseRepository houseAbilityUseRepository;
    private final CombatReplayHouseFavorService houseFavorService;
    private final CombatReplayHouseAbilityVoteService voteService;
    private final CombatReplayHouseService houseService;

    public void postDecoyButtons(TextChannel channel, CombatCandidateEntity candidate) {
        if (!shouldOfferDecoyVoting(candidate)) return;
        if (channel == null) return;

        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "## " + FactionEmojis.getFactionIcon(CombatReplayHouse.MENTAK.displayName())
                        + " Mentak Delegation False Colors\n"
                        + CombatReplayAbilityWindowText.votesLockLine(previewLeadSeconds())
                        + "\n"
                        + favorBalanceLine()
                        + "\n"
                        + falseColorsSummaryLine(),
                List.of(
                        Buttons.blue(MENTAK_MANAGE_VOTE_PREFIX + candidate.getId(), "Manage False Colors"),
                        Buttons.red(MENTAK_DO_NOT_USE + candidate.getId(), "Do Not Use False Colors")));
    }

    public boolean shouldOfferDecoyVoting(CombatCandidateEntity candidate) {
        return settings.isHousesEnabled() && candidate != null;
    }

    public String falseColorsSummaryLine() {
        return "-# False Colors: choose one decoy ship. The winning choice appears in the opening formation, then vanishes when combat ends.";
    }

    private String favorBalanceLine() {
        return "-# Total Favor: `" + houseFavorService.balance(CombatReplayHouse.MENTAK) + "`";
    }

    private int previewLeadSeconds() {
        return settings.getHouseAbilities().getMentak().getPreviewLeadSeconds();
    }

    public void sendEphemeralVoteControls(ButtonInteractionEvent event, String buttonId) {
        Long candidateId = manageVoteCandidateId(buttonId);
        if (candidateId == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not read the Mentak false-colors request.");
            return;
        }

        CombatCandidateEntity candidate = loadCandidateForVote(candidateId);
        if (candidate == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not find an open false-colors window for that combat.");
            return;
        }

        MessageHelper.sendEphemeralMessageToEventChannel(event, "Choose one False Colors option.");
        Game game = loadGame(candidate.getGameName());
        sendEphemeralDecoyButtonsForFaction(event, game, candidate, candidate.getAttackerFaction());
        sendEphemeralDecoyButtonsForFaction(event, game, candidate, candidate.getDefenderFaction());
    }

    public boolean userHasHouse(String discordUserId) {
        if (settings.getRuntime().isDevMode()) return true;
        return houseService.houseForUser(discordUserId) == CombatReplayHouse.MENTAK;
    }

    public VoteResult voteDecoy(
            long candidateId, String targetFaction, UnitType unitType, String discordUserId, String discordUserName) {
        CombatCandidateEntity candidate = loadCandidateForVote(candidateId);
        if (candidate == null)
            return VoteResult.rejected("Could not find an open false-colors window for that combat.");
        if (!isDecoyUnit(unitType)) {
            return VoteResult.rejected("Mentak Delegation cannot fly false colors with that ship.");
        }

        Game game = loadGame(candidate.getGameName());
        Player target = game == null ? null : game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) return VoteResult.rejected("Could not find the target faction for that decoy.");

        return recordVote(
                candidate.getId(),
                optionKey(target.getFaction(), unitType),
                target.getFaction() + " " + unitType.humanReadableName() + " Decoy",
                discordUserId,
                discordUserName);
    }

    public VoteResult voteDoNotUse(long candidateId, String discordUserId, String discordUserName) {
        CombatCandidateEntity candidate = loadCandidateForVote(candidateId);
        if (candidate == null)
            return VoteResult.rejected("Could not find an open false-colors window for that combat.");
        return recordVote(candidate.getId(), DO_NOT_USE_ABILITY, "Do Not Use", discordUserId, discordUserName);
    }

    public void resolveVoteIfNeeded(CombatCandidateEntity candidate) {
        if (houseAbilityUseRepository.existsByCandidateIdAndHouse(candidate.getId(), CombatReplayHouse.MENTAK)) return;

        CombatReplayHouseAbilityVoteService.WinningVote winningVote =
                voteService.winningVote(candidate.getId(), CombatReplayHouse.MENTAK, this::optionLabel);
        if (winningVote == null) {
            resolveNoSelection(candidate.getId());
            return;
        }
        if (DO_NOT_USE_ABILITY.equals(winningVote.optionKey())) {
            resolveDoNotUse(candidate.getId(), winningVote);
            return;
        }

        MentakOption option = parseOption(winningVote.optionKey());
        if (option == null) return;
        Game game = loadGame(candidate.getGameName());
        Player target = game == null ? null : game.getPlayerFromColorOrFaction(option.targetFaction());
        if (target == null) return;

        if (!claimUse(
                candidate.getId(),
                decoyFavorCost(option.unitType()),
                winningVote.discordUserId(),
                winningVote.discordUserName())) {
            resolveInsufficientFavor(candidate.getId(), winningVote.optionKey());
            return;
        }
        String replayAbilitiesJson = CombatReplayDecoys.addDecoy(
                candidate.getReplayAbilitiesJson(),
                new CombatReplayDecoys.DecoyUnit(
                        target.getFaction(),
                        target.getFactionEmoji(),
                        target.getColorID(),
                        option.unitType(),
                        Constants.SPACE,
                        1));
        candidate.setReplayAbilitiesJson(replayAbilitiesJson);
        refreshWarSunDecoyTechSummary(candidate, game, replayAbilitiesJson, option.unitType());
        candidateRepository.save(candidate);

        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "## False Colors\nMentak Delegation flew false colors. A "
                            + option.unitType().humanReadableName() + " decoy was placed with "
                            + target.getRepresentationNoPing() + " (`" + winningVote.voteCount() + "` votes).");
        }
    }

    private VoteResult recordVote(
            long candidateId, String optionKey, String optionLabel, String discordUserId, String discordUserName) {
        return VoteResult.from(voteService.recordVote(
                candidateId,
                CombatReplayHouse.MENTAK,
                optionKey,
                optionLabel,
                discordUserId,
                discordUserName,
                this::favorCost,
                ignored -> "",
                "Mentak Delegation has already resolved its ability for this combat.",
                "Mentak Delegation lacks the Favor for that ability."));
    }

    private boolean claimUse(long candidateId, int favorCost, String discordUserId, String discordUserName) {
        return voteService.claimUse(candidateId, CombatReplayHouse.MENTAK, favorCost, discordUserId, discordUserName);
    }

    private void refreshWarSunDecoyTechSummary(
            CombatCandidateEntity candidate, Game game, String replayAbilitiesJson, UnitType unitType) {
        if (unitType != UnitType.Warsun || game == null) return;
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        String techSummary = LazaxCombatSupport.formatCombatTechSummary(
                tile, attacker, defender, CombatReplayDecoys.read(replayAbilitiesJson));
        if (StringUtils.isNotBlank(techSummary)) {
            candidate.setPreReplayContextText(techSummary);
        }
    }

    private void resolveDoNotUse(long candidateId, CombatReplayHouseAbilityVoteService.WinningVote winningVote) {
        if (!claimUse(candidateId, 0, winningVote.discordUserId(), winningVote.discordUserName())) {
            return;
        }
        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "## False Colors\nMentak Delegation chose not to use False Colors for this combat (`"
                            + winningVote.voteCount() + "` votes).");
        }
    }

    private void resolveNoSelection(long candidateId) {
        if (!claimUse(candidateId, 0, SYSTEM_USER_ID, SYSTEM_USER_NAME)) return;
        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel, "## False Colors\nNo False Colors option reached the voting threshold for this combat.");
        }
    }

    private void resolveInsufficientFavor(long candidateId, String optionKey) {
        if (!claimUse(candidateId, 0, SYSTEM_USER_ID, SYSTEM_USER_NAME)) return;
        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "## False Colors\nMentak Delegation could not use "
                            + optionLabel(optionKey)
                            + " because it lacked the required Favor.");
        }
    }

    private void sendEphemeralDecoyButtonsForFaction(
            ButtonInteractionEvent event, Game game, CombatCandidateEntity candidate, String faction) {
        List<Button> buttons = decoyButtonsForFaction(candidate, faction);
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, factionSectionTitle(game, faction), buttons);
    }

    private List<Button> decoyButtonsForFaction(CombatCandidateEntity candidate, String faction) {
        List<Button> buttons = new ArrayList<>();
        if (StringUtils.isBlank(faction)) return buttons;
        buttons.add(decoyButton(candidate, faction, UnitType.Destroyer));
        buttons.add(decoyButton(candidate, faction, UnitType.Cruiser));
        buttons.add(decoyButton(candidate, faction, UnitType.Dreadnought));
        buttons.add(decoyButton(candidate, faction, UnitType.Warsun));
        return buttons;
    }

    Button decoyButton(CombatCandidateEntity candidate, String faction, UnitType unitType) {
        int favorCost = decoyFavorCost(unitType);
        Button button = Buttons.blue(
                MENTAK_DECOY_PREFIX + candidate.getId() + "_" + faction + "_" + unitType.name(),
                unitType.humanReadableName() + " Decoy" + favorCostSuffix(favorCost),
                unitType.getUnitTypeEmoji());
        return houseFavorService.canAfford(CombatReplayHouse.MENTAK, favorCost) ? button : button.asDisabled();
    }

    private String favorCostSuffix(int cost) {
        return " (-" + cost + " Favor)";
    }

    private int favorCost(String optionKey) {
        if (DO_NOT_USE_ABILITY.equals(optionKey)) return 0;
        MentakOption option = parseOption(optionKey);
        return option == null ? 0 : decoyFavorCost(option.unitType());
    }

    private int decoyFavorCost(UnitType unitType) {
        CombatContestSettings.Mentak mentak = settings.getHouseAbilities().getMentak();
        return switch (unitType) {
            case Destroyer -> mentak.getDestroyerDecoyFavorCost();
            case Cruiser -> mentak.getCruiserDecoyFavorCost();
            case Dreadnought -> mentak.getDreadnoughtDecoyFavorCost();
            case Warsun -> mentak.getWarSunDecoyFavorCost();
            default -> 0;
        };
    }

    private boolean windowOpen(CombatCandidateEntity candidate) {
        return candidate != null
                && candidate.getStatus() == CombatCandidateStatus.RESOLVED
                && candidate.getPromotionStatus() == CombatCandidatePromotionStatus.PENDING
                && candidate.getMentakPreviewPostedAt() != null;
    }

    private CombatCandidateEntity loadCandidateForVote(long candidateId) {
        CombatCandidateEntity candidate =
                candidateRepository.findById(candidateId).orElse(null);
        return windowOpen(candidate) ? candidate : null;
    }

    private Long manageVoteCandidateId(String buttonId) {
        if (buttonId == null || !buttonId.startsWith(MENTAK_MANAGE_VOTE_PREFIX)) return null;
        try {
            return Long.parseLong(buttonId.substring(MENTAK_MANAGE_VOTE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isDecoyUnit(UnitType unitType) {
        return unitType == UnitType.Destroyer
                || unitType == UnitType.Cruiser
                || unitType == UnitType.Dreadnought
                || unitType == UnitType.Warsun;
    }

    private int minimumAbilityVotesToResolve() {
        return voteService.minimumAbilityVotesToResolve();
    }

    private String voteSummary(Long candidateId) {
        return voteService.voteSummary(candidateId, CombatReplayHouse.MENTAK, this::optionLabel);
    }

    private String optionLabel(String optionKey) {
        if (DO_NOT_USE_ABILITY.equals(optionKey)) return "Do Not Use";
        MentakOption option = parseOption(optionKey);
        if (option != null) {
            return StringUtils.capitalize(option.targetFaction()) + " "
                    + option.unitType().humanReadableName() + " Decoy";
        }
        return optionKey;
    }

    private String optionKey(String targetFaction, UnitType unitType) {
        return targetFaction + "_" + unitType.name();
    }

    private MentakOption parseOption(String optionKey) {
        if (StringUtils.isBlank(optionKey)) return null;
        String[] parts = optionKey.split("_", 2);
        if (parts.length != 2) return null;
        try {
            UnitType unitType = UnitType.valueOf(parts[1]);
            return isDecoyUnit(unitType) ? new MentakOption(parts[0], unitType) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String factionSectionTitle(Game game, String faction) {
        Player target = game == null ? null : game.getPlayerFromColorOrFaction(faction);
        if (target == null) {
            return "### " + FactionEmojis.getFactionIcon(faction) + " " + StringUtils.capitalize(faction);
        }
        String label = target.getFactionModel() == null
                ? target.getFaction()
                : target.getFactionModel().getFactionName();
        return "### " + target.getFactionEmoji() + " " + label;
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
        TextChannel channel = guild.getTextChannelsByName(CombatReplayHouse.MENTAK.channelName(), true).stream()
                .findFirst()
                .orElse(null);
        if (channel == null) {
            BotLogger.warning("Lazax house channel not found: " + CombatReplayHouse.MENTAK.channelName());
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

    private record MentakOption(String targetFaction, UnitType unitType) {}
}
