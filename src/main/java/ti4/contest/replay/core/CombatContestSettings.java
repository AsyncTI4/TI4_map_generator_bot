package ti4.contest.replay.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Properties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Mutable in-memory settings for the replay contest system.
 */
@Component
@Getter
@Setter
public class CombatContestSettings {

    public static final int PROMOTION_LOOKBACK_FALLBACK_MAX_HOURS = 8;

    @Setter(AccessLevel.NONE)
    private boolean isProd;

    private CandidateSelection candidateSelection = new CandidateSelection();
    private Promotion promotion = new Promotion();
    private ReplayExecution replayExecution = new ReplayExecution();
    private Retention retention = new Retention();
    private Runtime runtime = new Runtime();
    private SideBets sideBets = new SideBets();
    private boolean decoysEnabled;
    private HouseAbilities houseAbilities = new HouseAbilities();
    private boolean housesEnabled = true;

    public CombatContestSettings() {
        loadEnvironmentDefaults(false);
        validate();
    }

    @Autowired
    public CombatContestSettings(@Value("${combat-contest.is-dev:false}") boolean isDev) {
        isProd = !isDev;
        loadEnvironmentDefaults(isProd);
        validate();
    }

    public void validate() {
        require(candidateSelection != null, "candidateSelection is required.");
        require(candidateSelection.window != null, "candidateSelection.window is required.");
        require(promotion != null, "promotion is required.");
        require(replayExecution != null, "replayExecution is required.");
        require(retention != null, "retention is required.");
        require(runtime != null, "runtime is required.");
        require(sideBets != null, "sideBets is required.");
        require(houseAbilities != null, "houseAbilities is required.");
        require(houseAbilities.naalu != null, "houseAbilities.naalu is required.");
        require(houseAbilities.mentak != null, "houseAbilities.mentak is required.");
        require(houseAbilities.hacan != null, "houseAbilities.hacan is required.");
        require(
                houseAbilities.minimumAbilityVotesToResolve > 0,
                "houseAbilities.minimumAbilityVotesToResolve must be > 0.");
        require(houseAbilities.baseCombatFavorGain >= 0, "houseAbilities.baseCombatFavorGain must be >= 0.");
        require(houseAbilities.initialHousePoints >= 0, "houseAbilities.initialHousePoints must be >= 0.");
        require(
                houseAbilities.catchupFavorPointsPerBonus > 0,
                "houseAbilities.catchupFavorPointsPerBonus must be > 0.");
        require(houseAbilities.catchupFavorBonusStep >= 0, "houseAbilities.catchupFavorBonusStep must be >= 0.");
        require(houseAbilities.maxCatchupFavorBonus >= 0, "houseAbilities.maxCatchupFavorBonus must be >= 0.");
        require(
                candidateSelection.window.lookbackMinutes > 0,
                "candidateSelection.window.lookbackMinutes must be > 0.");
        require(
                candidateSelection.window.refreshCronIntervalSeconds > 0,
                "candidateSelection.window.refreshCronIntervalSeconds must be > 0.");
        require(
                candidateSelection.targetCandidatesPerHour >= 0,
                "candidateSelection.targetCandidatesPerHour must be >= 0.");
        require(promotion.intervalSeconds > 0, "promotion.intervalSeconds must be > 0.");
        require(promotion.candidateLookbackHours > 0, "promotion.candidateLookbackHours must be > 0.");
        require(promotion.maxPromotionsPerHour >= 0, "promotion.maxPromotionsPerHour must be >= 0.");
        require(replayExecution.startDelaySeconds >= 0, "replayExecution.startDelaySeconds must be >= 0.");
        require(replayExecution.discussionWindowSeconds >= 0, "replayExecution.discussionWindowSeconds must be >= 0.");
        require(replayExecution.sideBetWindowSeconds >= 0, "replayExecution.sideBetWindowSeconds must be >= 0.");
        require(replayExecution.replayIntervalSeconds > 0, "replayExecution.replayIntervalSeconds must be > 0.");
        require(replayExecution.maxEventGapSeconds >= 0, "replayExecution.maxEventGapSeconds must be >= 0.");
        require(
                replayExecution.pendingResolutionWindowSeconds > 0,
                "replayExecution.pendingResolutionWindowSeconds must be > 0.");
        require(retention.observationRetentionDays > 0, "retention.observationRetentionDays must be > 0.");
        require(retention.eventRetentionDays > 0, "retention.eventRetentionDays must be > 0.");
        require(sideBets.maxBetsPerUser >= 0, "sideBets.maxBetsPerUser must be >= 0.");
        require(sideBets.costPoints >= 0, "sideBets.costPoints must be >= 0.");
        require(sideBets.dynamicPayoutCap >= 1, "sideBets.dynamicPayoutCap must be >= 1.");
        require(
                houseAbilities.naalu.actionCardPeekFavorCost >= 0,
                "houseAbilities.naalu.actionCardPeekFavorCost must be >= 0.");
        require(
                houseAbilities.naalu.roundOneRollPeekFavorCost >= 0,
                "houseAbilities.naalu.roundOneRollPeekFavorCost must be >= 0.");
        require(
                houseAbilities.mentak.previewLeadSeconds >= 0,
                "houseAbilities.mentak.previewLeadSeconds must be >= 0.");
        require(
                houseAbilities.mentak.destroyerDecoyFavorCost >= 0,
                "houseAbilities.mentak.destroyerDecoyFavorCost must be >= 0.");
        require(
                houseAbilities.mentak.cruiserDecoyFavorCost >= 0,
                "houseAbilities.mentak.cruiserDecoyFavorCost must be >= 0.");
        require(
                houseAbilities.mentak.dreadnoughtDecoyFavorCost >= 0,
                "houseAbilities.mentak.dreadnoughtDecoyFavorCost must be >= 0.");
        require(
                houseAbilities.mentak.warSunDecoyFavorCost >= 0,
                "houseAbilities.mentak.warSunDecoyFavorCost must be >= 0.");
        require(
                houseAbilities.hacan.maxSubsidiesPerContest >= 0,
                "houseAbilities.hacan.maxSubsidiesPerContest must be >= 0.");
        require(houseAbilities.hacan.subsidyFavorOnHit >= 0, "houseAbilities.hacan.subsidyFavorOnHit must be >= 0.");
        require(
                houseAbilities.hacan.marketMakerPointsPerBet >= 0,
                "houseAbilities.hacan.marketMakerPointsPerBet must be >= 0.");
        require(
                houseAbilities.hacan.lowTradeConvoysFavorCost >= 0,
                "houseAbilities.hacan.lowTradeConvoysFavorCost must be >= 0.");
        require(
                houseAbilities.hacan.lowTradeConvoysPredictionBonus >= 0,
                "houseAbilities.hacan.lowTradeConvoysPredictionBonus must be >= 0.");
        require(
                houseAbilities.hacan.mediumTradeConvoysFavorCost >= 0,
                "houseAbilities.hacan.mediumTradeConvoysFavorCost must be >= 0.");
        require(
                houseAbilities.hacan.mediumTradeConvoysPredictionBonus >= 0,
                "houseAbilities.hacan.mediumTradeConvoysPredictionBonus must be >= 0.");
        require(
                houseAbilities.hacan.highTradeConvoysFavorCost >= 0,
                "houseAbilities.hacan.highTradeConvoysFavorCost must be >= 0.");
        require(
                houseAbilities.hacan.highTradeConvoysPredictionBonus >= 0,
                "houseAbilities.hacan.highTradeConvoysPredictionBonus must be >= 0.");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @JsonProperty("isProd")
    public boolean isProd() {
        return isProd;
    }

    private void loadEnvironmentDefaults(boolean isProd) {
        String fileName = isProd ? "config/combat-contest-prod.yml" : "config/combat-contest-dev.yml";
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(fileName));
        factory.afterPropertiesSet();
        Properties properties = factory.getObject();
        if (properties == null || properties.isEmpty()) {
            throw new IllegalStateException("Combat contest settings file is empty: " + fileName);
        }

        BeanWrapper wrapper = new BeanWrapperImpl(this);
        for (var entry : properties.entrySet()) {
            String propertyName = String.valueOf(entry.getKey());
            if ("isProd".equals(propertyName)) continue;
            if (!wrapper.isWritableProperty(propertyName)) {
                throw new IllegalStateException("Unknown combat contest setting: " + propertyName);
            }
            wrapper.setPropertyValue(propertyName, entry.getValue());
        }
    }

    @Getter
    @Setter
    public static class CandidateSelection {
        private Window window = new Window();
        private int targetCandidatesPerHour = 16;
    }

    @Getter
    @Setter
    public static class Window {
        private int lookbackMinutes = 1440;
        private int refreshCronIntervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class Promotion {
        private boolean enabled;
        private int intervalSeconds = 60;
        private int candidateLookbackHours = 12;
        private int maxPromotionsPerHour = 1;
    }

    @Getter
    @Setter
    public static class ReplayExecution {
        private int startDelaySeconds = 15 * 60;
        private int discussionWindowSeconds = 5 * 60;
        private int sideBetWindowSeconds = 10 * 60;
        private int replayIntervalSeconds = 15;
        private int maxEventGapSeconds = 30;
        private int pendingResolutionWindowSeconds = 900;
    }

    @Getter
    @Setter
    public static class Retention {
        private int observationRetentionDays = 2;
        private int eventRetentionDays = 2;
    }

    @Getter
    @Setter
    public static class Runtime {
        private boolean devMode;
        private boolean trackAllCombatsAsCandidates;
        private boolean immediatePromotionOnResolve;
    }

    @Getter
    @Setter
    public static class SideBets {
        private boolean enableSideBets = true;
        private int maxBetsPerUser = 3;
        private int costPoints = 1;
        private int dynamicPayoutCap = 100;
    }

    @Getter
    @Setter
    public static class HouseAbilities {
        private Naalu naalu = new Naalu();
        private Mentak mentak = new Mentak();
        private Hacan hacan = new Hacan();
        private int minimumAbilityVotesToResolve = 3;
        private int baseCombatFavorGain = 10;
        private int initialHousePoints = 1000;
        private int catchupFavorPointsPerBonus = 100;
        private int catchupFavorBonusStep;
        private int maxCatchupFavorBonus;
    }

    @Getter
    @Setter
    public static class Naalu {
        private int actionCardPeekFavorCost = 30;
        private int roundOneRollPeekFavorCost = 50;
    }

    @Getter
    @Setter
    public static class Mentak {
        private int previewLeadSeconds = 5 * 60;
        private int destroyerDecoyFavorCost = 30;
        private int cruiserDecoyFavorCost = 40;
        private int dreadnoughtDecoyFavorCost = 60;
        private int warSunDecoyFavorCost = 80;
    }

    @Getter
    @Setter
    public static class Hacan {
        private int maxSubsidiesPerContest = 2;
        private int subsidyFavorOnHit = 10;
        private int marketMakerPointsPerBet = 1;
        private int lowTradeConvoysFavorCost = 10;
        private int lowTradeConvoysPredictionBonus = 5;
        private int mediumTradeConvoysFavorCost = 20;
        private int mediumTradeConvoysPredictionBonus = 10;
        private int highTradeConvoysFavorCost = 30;
        private int highTradeConvoysPredictionBonus = 15;
    }
}
