package ti4.ai.deal;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiGameWrites;
import ti4.game.Game;

@UtilityClass
public class HumanAiDealStore {

    private static final String DEAL_KEY = "humanAiDeal|";
    private static final String LATEST_KEY = "humanAiDealLatest|";
    private static final String SAVE_REASON = "AI trade offer";

    public static void persist(Game game, HumanAiDeal deal) {
        AiGameWrites.persist(game, SAVE_REASON, latest -> save(latest, deal));
    }

    public static void persistAsNew(Game game, HumanAiDeal deal) {
        AiGameWrites.persist(game, SAVE_REASON, latest -> {
            replaceLatest(latest, deal.aiFaction());
            save(latest, deal);
        });
    }

    public static void save(Game game, HumanAiDeal deal) {
        game.setStoredValue(DEAL_KEY + deal.id(), deal.encode());
        game.setStoredValue(LATEST_KEY + deal.aiFaction(), deal.id());
    }

    public static Optional<HumanAiDeal> find(Game game, String id) {
        return HumanAiDeal.decode(game.getStoredValue(DEAL_KEY + id));
    }

    public static Optional<HumanAiDeal> latestFor(Game game, String aiFaction) {
        String id = game.getStoredValue(LATEST_KEY + aiFaction);
        return id.isEmpty() ? Optional.empty() : find(game, id);
    }

    public static Optional<HumanAiDeal> pendingFor(Game game, String aiFaction) {
        return latestFor(game, aiFaction).filter(deal -> deal.status() == HumanAiDeal.Status.PENDING);
    }

    public static void replaceLatest(Game game, String aiFaction) {
        String id = game.getStoredValue(LATEST_KEY + aiFaction);
        if (!id.isEmpty()) game.removeStoredValue(DEAL_KEY + id);
    }
}
