package ti4.contest.replay.house.mentak;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHouseAbilityVoteEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
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
    public static final String MENTAK_DECOY_QUANTITY_PREFIX = MENTAK_DECOY_PREFIX + "quantity_";
    public static final String MENTAK_PREDICTION_PREFIX = "combatReplayMentakPrediction_";
    public static final String MENTAK_MANAGE_VOTE_PREFIX = "combatReplayMentakManageVote_";
    public static final String MENTAK_DO_NOT_USE = MENTAK_DECOY_PREFIX + "doNotUse_";

    private static final String DO_NOT_USE_ABILITY = "DO_NOT_USE";
    private static final String PREDICTION_OPTION_PREFIX = "PREDICTION_";
    private static final int MAX_DECOY_COUNT = 5;
    private static final int MINIMUM_UNIT_TYPE_VOTES_TO_RESOLVE = 2;
    private static final String SYSTEM_USER_ID = "0";
    private static final String SYSTEM_USER_NAME = "Mentak Delegation";

    private final CombatContestSettings settings;
    private final CombatCandidateRepository candidateRepository;
    private final CombatReplayHouseAbilityUseRepository houseAbilityUseRepository;
    private final CombatReplayHouseFavorService houseFavorService;
    private final CombatReplayHouseAbilityVoteService voteService;
    private final CombatReplayHousePhaseService phaseService;
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

    public boolean repostOpenFalseColorsVotingButtons() {
        CombatCandidateEntity candidate = candidateRepository.findByStatus(CombatCandidateStatus.RESOLVED).stream()
                .filter(phaseService::mentakPreviewOpen)
                .filter(candidateEntity ->
                        candidateEntity.getPromotionStatus() == CombatCandidatePromotionStatus.PENDING)
                .filter(candidateEntity -> candidateEntity.getMentakPreviewPostedAt() != null)
                .max(Comparator.comparing(CombatCandidateEntity::getMentakPreviewPostedAt))
                .orElse(null);
        if (candidate == null) return false;

        TextChannel channel = houseChannel();
        if (channel == null) return false;
        postDirectDecoyVotingButtons(channel, candidate);
        return true;
    }

    private void postDirectDecoyVotingButtons(TextChannel channel, CombatCandidateEntity candidate) {
        Game game = loadGame(candidate.getGameName());
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "## " + FactionEmojis.getFactionIcon(CombatReplayHouse.MENTAK.displayName())
                        + " Mentak Delegation False Colors\n"
                        + CombatReplayAbilityWindowText.votesLockLine(previewLeadSeconds())
                        + "\n"
                        + favorBalanceLine()
                        + "\n"
                        + falseColorsSummaryLine(),
                List.of(Buttons.red(MENTAK_DO_NOT_USE + candidate.getId(), "Vote: Do Not Use False Colors")));
        postDecoyButtonsForFaction(channel, game, candidate, candidate.getAttackerFaction());
        postDecoyButtonsForFaction(channel, game, candidate, candidate.getDefenderFaction());
    }

    public boolean shouldOfferDecoyVoting(CombatCandidateEntity candidate) {
        return phaseService.discussionOpenForCandidate(candidate);
    }

    public String falseColorsSummaryLine() {
        return "-# False Colors: choose one decoy ship type and count. Ship type votes are pooled; tied ship types choose the most expensive ship, tied counts choose the lowest count.";
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

        MessageHelper.sendEphemeralMessageToEventChannel(event, "Choose a side and ship type for False Colors.");
        Game game = loadGame(candidate.getGameName());
        sendEphemeralDecoyButtonsForFaction(event, game, candidate, candidate.getAttackerFaction());
        sendEphemeralDecoyButtonsForFaction(event, game, candidate, candidate.getDefenderFaction());
    }

    public boolean userHasHouse(String discordUserId) {
        if (settings.getRuntime().isDevMode()) return true;
        return houseService.houseForUser(discordUserId) == CombatReplayHouse.MENTAK;
    }

    public void postPredictionOverrideButtons(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (contest == null || contest.getId() == null || candidate == null) return;
        TextChannel channel = houseChannel();
        if (channel == null) return;

        Player attacker = game == null ? null : game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game == null ? null : game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "## " + FactionEmojis.getFactionIcon(CombatReplayHouse.MENTAK.displayName())
                        + " Mentak Delegation Hidden Wagers\n"
                        + "-# Public reactions remain on the table. These sealed pirate marks are what the archive will count for Mentak delegates when predictions lock.",
                List.of(
                        predictionButton(contest.getId(), candidate.getAttackerFaction(), attacker),
                        predictionButton(contest.getId(), candidate.getDefenderFaction(), defender)));
    }

    public CombatReplayInteractionResult votePredictionOverride(
            long contestId, String predictedFaction, String discordUserId, String discordUserName) {
        if (StringUtils.isBlank(predictedFaction)) {
            return CombatReplayInteractionResult.rejected("Could not read that Mentak prediction.");
        }
        return voteService.recordVote(
                predictionVoteKey(contestId),
                CombatReplayHouse.MENTAK,
                predictionOptionKey(predictedFaction),
                StringUtils.capitalize(predictedFaction),
                discordUserId,
                discordUserName,
                ignored -> 0,
                this::predictionVoteSummary,
                "Mentak Delegation has already resolved its ability for this combat.",
                "Mentak Delegation cannot afford that prediction.");
    }

    public Map<String, String> predictionOverridesFor(Long contestId) {
        if (contestId == null) return Map.of();
        Map<String, String> overrides = new HashMap<>();
        for (CombatReplayHouseAbilityVoteEntity vote :
                voteService.votesFor(predictionVoteKey(contestId), CombatReplayHouse.MENTAK)) {
            String faction = predictionFaction(vote.getOptionKey());
            if (StringUtils.isBlank(faction)) continue;
            overrides.put(vote.getDiscordUserId(), faction);
        }
        return overrides;
    }

    public CombatReplayInteractionResult voteDecoy(
            long candidateId,
            String targetFaction,
            UnitType unitType,
            int count,
            String discordUserId,
            String discordUserName) {
        CombatCandidateEntity candidate = loadCandidateForVote(candidateId);
        if (candidate == null)
            return CombatReplayInteractionResult.rejected(
                    "Could not find an open false-colors window for that combat.");
        if (!isDecoyUnit(unitType)) {
            return CombatReplayInteractionResult.rejected("Mentak Delegation cannot fly false colors with that ship.");
        }
        if (count < 1 || count > MAX_DECOY_COUNT) {
            return CombatReplayInteractionResult.rejected("Mentak Delegation can deploy between 1 and 5 decoy ships.");
        }

        Game game = loadGame(candidate.getGameName());
        Player target = game == null ? null : game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null)
            return CombatReplayInteractionResult.rejected("Could not find the target faction for that decoy.");

        return recordVote(
                candidate.getId(),
                optionKey(target.getFaction(), unitType, count),
                optionLabel(target.getFaction(), unitType, count),
                discordUserId,
                discordUserName);
    }

    public void sendEphemeralQuantityControls(
            ButtonInteractionEvent event, long candidateId, String targetFaction, UnitType unitType) {
        CombatCandidateEntity candidate = loadCandidateForVote(candidateId);
        if (candidate == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not find an open false-colors window for that combat.");
            return;
        }
        if (!isDecoyUnit(unitType)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Mentak Delegation cannot fly false colors with that ship.");
            return;
        }

        Game game = loadGame(candidate.getGameName());
        Player target = game == null ? null : game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not find the target faction for that decoy.");
            return;
        }

        String message = target.getRepresentationNoPing() + " " + unitType.humanReadableName()
                + " decoys\nMentak Favor: `" + houseFavorService.balance(CombatReplayHouse.MENTAK) + "`";
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event, message, quantityButtons(candidate, target.getFaction(), unitType));
    }

    public CombatReplayInteractionResult voteDoNotUse(long candidateId, String discordUserId, String discordUserName) {
        CombatCandidateEntity candidate = loadCandidateForVote(candidateId);
        if (candidate == null)
            return CombatReplayInteractionResult.rejected(
                    "Could not find an open false-colors window for that combat.");
        return recordVote(candidate.getId(), DO_NOT_USE_ABILITY, "Do Not Use", discordUserId, discordUserName);
    }

    public void resolveVoteIfNeeded(CombatCandidateEntity candidate) {
        if (houseAbilityUseRepository.existsByCandidateIdAndHouse(candidate.getId(), CombatReplayHouse.MENTAK)) return;

        CombatReplayHouseAbilityVoteService.WinningVote winningVote = winningMentakVote(candidate.getId());
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
                decoyFavorCost(option.unitType(), option.count()),
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
                        option.count()));
        candidate.setReplayAbilitiesJson(replayAbilitiesJson);
        refreshWarSunDecoyTechSummary(candidate, game, replayAbilitiesJson, option.unitType());
        candidateRepository.save(candidate);

        TextChannel channel = houseChannel();
        if (channel != null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "## False Colors\nMentak Delegation flew false colors. A "
                            + option.count() + "x " + option.unitType().humanReadableName()
                            + " decoy stack was placed with "
                            + target.getRepresentationNoPing() + " (`" + winningVote.voteCount() + "` votes).");
        }
    }

    private CombatReplayInteractionResult recordVote(
            long candidateId, String optionKey, String optionLabel, String discordUserId, String discordUserName) {
        return voteService.recordVote(
                candidateId,
                CombatReplayHouse.MENTAK,
                optionKey,
                optionLabel,
                discordUserId,
                discordUserName,
                this::favorCost,
                ignored -> "",
                "Mentak Delegation has already resolved its ability for this combat.",
                "Mentak Delegation lacks the Favor for that ability.");
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

    private void postDecoyButtonsForFaction(
            TextChannel channel, Game game, CombatCandidateEntity candidate, String faction) {
        List<Button> buttons = decoyButtonsForFaction(candidate, faction);
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(channel, factionSectionTitle(game, faction), buttons);
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
        return Buttons.blue(
                MENTAK_DECOY_PREFIX + candidate.getId() + "_" + faction + "_" + unitType.name(),
                unitType.humanReadableName() + " Decoys",
                unitType.getUnitTypeEmoji());
    }

    private Button predictionButton(Long contestId, String faction, Player player) {
        String label = player == null || player.getFactionModel() == null
                ? "Predict " + StringUtils.capitalize(faction)
                : "Predict " + player.getFactionModel().getFactionName();
        String buttonId = MENTAK_PREDICTION_PREFIX + contestId + "_" + faction;
        return player == null
                ? Buttons.blue(buttonId, label, FactionEmojis.getFactionIcon(faction))
                : Buttons.blue(buttonId, label, player.getFactionEmoji());
    }

    List<Button> quantityButtons(CombatCandidateEntity candidate, String faction, UnitType unitType) {
        List<Button> buttons = new ArrayList<>();
        if (StringUtils.isBlank(faction)) return buttons;
        for (int count = 1; count <= MAX_DECOY_COUNT; count++) {
            int favorCost = decoyFavorCost(unitType, count);
            Button button = Buttons.blue(
                    MENTAK_DECOY_QUANTITY_PREFIX
                            + candidate.getId()
                            + "_"
                            + faction
                            + "_"
                            + unitType.name()
                            + "_"
                            + count,
                    count + "x" + favorCostSuffix(favorCost),
                    unitType.getUnitTypeEmoji());
            buttons.add(
                    houseFavorService.canAfford(CombatReplayHouse.MENTAK, favorCost) ? button : button.asDisabled());
        }
        return buttons;
    }

    private String favorCostSuffix(int cost) {
        return " (-" + cost + " Favor)";
    }

    private int favorCost(String optionKey) {
        if (DO_NOT_USE_ABILITY.equals(optionKey)) return 0;
        MentakOption option = parseOption(optionKey);
        return option == null ? 0 : decoyFavorCost(option.unitType(), option.count());
    }

    private int decoyFavorCost(UnitType unitType, int count) {
        if (count <= 0) return 0;
        int baseCost = decoyFavorCost(unitType);
        return baseCost + ((count - 1) * additionalDecoyFavorCost(baseCost));
    }

    private int additionalDecoyFavorCost(int baseCost) {
        return (baseCost * 3 + 3) / 4;
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

    private CombatCandidateEntity loadCandidateForVote(long candidateId) {
        CombatCandidateEntity candidate =
                candidateRepository.findById(candidateId).orElse(null);
        return phaseService.mentakPreviewOpen(candidate) ? candidate : null;
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
        return MINIMUM_UNIT_TYPE_VOTES_TO_RESOLVE;
    }

    private String voteSummary(Long candidateId) {
        return voteService.voteSummary(candidateId, CombatReplayHouse.MENTAK, this::optionLabel)
                + "\n-# Ship type threshold: `"
                + minimumAbilityVotesToResolve()
                + "` pooled votes. Quantity ties choose the lower count.";
    }

    private String predictionVoteSummary(Long contestVoteKey) {
        return voteService.voteSummary(contestVoteKey, CombatReplayHouse.MENTAK, this::predictionOptionLabel)
                + "\n-# These are private Mentak winner predictions. Public reactions are only cover.";
    }

    private String optionLabel(String optionKey) {
        if (DO_NOT_USE_ABILITY.equals(optionKey)) return "Do Not Use";
        MentakOption option = parseOption(optionKey);
        if (option != null) {
            return optionLabel(option.targetFaction(), option.unitType(), option.count());
        }
        return optionKey;
    }

    private String predictionOptionLabel(String optionKey) {
        String faction = predictionFaction(optionKey);
        return StringUtils.isBlank(faction) ? optionKey : StringUtils.capitalize(faction);
    }

    private Long predictionVoteKey(long contestId) {
        return -Math.abs(contestId);
    }

    private String predictionOptionKey(String faction) {
        return PREDICTION_OPTION_PREFIX + faction;
    }

    private String predictionFaction(String optionKey) {
        if (StringUtils.isBlank(optionKey) || !optionKey.startsWith(PREDICTION_OPTION_PREFIX)) return null;
        return optionKey.substring(PREDICTION_OPTION_PREFIX.length());
    }

    private String optionLabel(String targetFaction, UnitType unitType, int count) {
        return StringUtils.capitalize(targetFaction) + " "
                + count
                + "x "
                + unitType.humanReadableName()
                + " Decoy"
                + (count == 1 ? "" : "s");
    }

    private String optionKey(String targetFaction, UnitType unitType, int count) {
        return targetFaction + "_" + unitType.name() + "_" + count;
    }

    private MentakOption parseOption(String optionKey) {
        if (StringUtils.isBlank(optionKey)) return null;
        String[] parts = optionKey.split("_", 3);
        if (parts.length != 3) return null;
        try {
            UnitType unitType = UnitType.valueOf(parts[1]);
            int count = Integer.parseInt(parts[2]);
            return isDecoyUnit(unitType) && count >= 1 && count <= MAX_DECOY_COUNT
                    ? new MentakOption(parts[0], unitType, count)
                    : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    CombatReplayHouseAbilityVoteService.WinningVote winningMentakVote(Long candidateId) {
        List<CombatReplayHouseAbilityVoteEntity> votes = voteService.votesFor(candidateId, CombatReplayHouse.MENTAK);
        if (votes.isEmpty()) return null;

        Map<MentakUnitChoice, List<CombatReplayHouseAbilityVoteEntity>> votesByUnitChoice = new HashMap<>();
        List<CombatReplayHouseAbilityVoteEntity> doNotUseVotes = new ArrayList<>();
        for (CombatReplayHouseAbilityVoteEntity vote : votes) {
            if (DO_NOT_USE_ABILITY.equals(vote.getOptionKey())) {
                doNotUseVotes.add(vote);
                continue;
            }
            MentakOption option = parseOption(vote.getOptionKey());
            if (option == null) continue;
            votesByUnitChoice
                    .computeIfAbsent(
                            new MentakUnitChoice(option.targetFaction(), option.unitType()),
                            ignored -> new ArrayList<>())
                    .add(vote);
        }

        int threshold = minimumAbilityVotesToResolve();
        MentakUnitChoice winningUnitChoice = votesByUnitChoice.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= threshold)
                .max(Comparator.comparingInt(
                                (Map.Entry<MentakUnitChoice, List<CombatReplayHouseAbilityVoteEntity>> entry) ->
                                        entry.getValue().size())
                        .thenComparing(entry -> decoyFavorCost(entry.getKey().unitType())))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (winningUnitChoice == null) {
            return doNotUseVotes.size() >= threshold ? doNotUseWinningVote(doNotUseVotes) : null;
        }

        List<CombatReplayHouseAbilityVoteEntity> winningUnitVotes = votesByUnitChoice.get(winningUnitChoice);
        if (doNotUseVotes.size() > winningUnitVotes.size()) {
            return doNotUseWinningVote(doNotUseVotes);
        }

        Map<Integer, List<CombatReplayHouseAbilityVoteEntity>> votesByCount = new HashMap<>();
        for (CombatReplayHouseAbilityVoteEntity vote : winningUnitVotes) {
            MentakOption option = parseOption(vote.getOptionKey());
            if (option == null) continue;
            votesByCount
                    .computeIfAbsent(option.count(), ignored -> new ArrayList<>())
                    .add(vote);
        }

        Map.Entry<Integer, List<CombatReplayHouseAbilityVoteEntity>> winningCount = votesByCount.entrySet().stream()
                .max(Comparator.comparingInt((Map.Entry<Integer, List<CombatReplayHouseAbilityVoteEntity>> entry) ->
                                entry.getValue().size())
                        .thenComparing(Map.Entry.<Integer, List<CombatReplayHouseAbilityVoteEntity>>comparingByKey()
                                .reversed()))
                .orElse(null);
        if (winningCount == null) return null;

        CombatReplayHouseAbilityVoteEntity firstVote = winningCount.getValue().getFirst();
        String optionKey =
                optionKey(winningUnitChoice.targetFaction(), winningUnitChoice.unitType(), winningCount.getKey());
        return new CombatReplayHouseAbilityVoteService.WinningVote(
                optionKey,
                optionLabel(optionKey),
                firstVote.getDiscordUserId(),
                firstVote.getDiscordUserName(),
                winningUnitVotes.size());
    }

    private CombatReplayHouseAbilityVoteService.WinningVote doNotUseWinningVote(
            List<CombatReplayHouseAbilityVoteEntity> votes) {
        CombatReplayHouseAbilityVoteEntity firstVote = votes.getFirst();
        return new CombatReplayHouseAbilityVoteService.WinningVote(
                DO_NOT_USE_ABILITY,
                optionLabel(DO_NOT_USE_ABILITY),
                firstVote.getDiscordUserId(),
                firstVote.getDiscordUserName(),
                votes.size());
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

    private record MentakOption(String targetFaction, UnitType unitType, int count) {}

    private record MentakUnitChoice(String targetFaction, UnitType unitType) {}
}
