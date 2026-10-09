package ti4.ai.trade;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.agenda.IsPlayerElectedService;

@UtilityClass
class TradeLegality {

    private static final String WINDOW_KEY = "tradeWindow|";
    private static final String CENSURE = "tf-censure";
    private static final String GUILD_SHIPS = "guild_ships";
    private static final List<String> CONVOYS = List.of("convoys", "sigma_trade_convoys", "viability_trade_convoys");
    private static final String ACTION = "action";
    private static final String STRATEGY = "strategy";
    private static final String AGENDA = "agenda";
    private static final String AGENDA_COUNT = "agendaCount";
    private static final String TURN_WINDOW = "turn|";
    private static final String AGENDA_WINDOW = "agenda|";
    private static final String PHASE_WINDOW = "phase|";
    private static final String ROUND_KEY = "round|";
    private static final String ACTION_KEY = "action|";
    private static final String SEPARATOR = "|";
    private static final String AT = "@";
    private static final int MAX_CACHED = 64;
    private static final Set<Purpose> WINDOWLESS = EnumSet.of(
            Purpose.SETTLEMENT,
            Purpose.SETTLEMENT_FEE,
            Purpose.EVEN_WASH,
            Purpose.COUNTER,
            Purpose.CLEANUP,
            Purpose.ADOPTED);
    private static final Map<String, Cached> NEIGHBOURS = new ConcurrentHashMap<>();

    private record Cached(WeakReference<Game> game, long modified, Set<String> factions) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }
    }

    static boolean canTransact(Game game, Player first, Player second) {
        if (censured(game, first) || censured(game, second)) return false;
        return Standings.same(first, second) || !isActionPhase(game) || openInActionPhase(game, first, second);
    }

    static boolean canTransactInActionPhase(Game game, Player first, Player second) {
        if (censured(game, first) || censured(game, second)) return false;
        return Standings.same(first, second) || openInActionPhase(game, first, second);
    }

    static boolean isLegal(Game game, Player seat, Player partner, Deal deal) {
        return deal.isDebtOnly() || canTransact(game, seat, partner);
    }

    static List<Player> legalPartners(Game game, Player seat) {
        return Standings.others(game, seat).stream()
                .filter(partner -> DealItem.tradable(partner.getFaction()))
                .filter(partner -> canTransact(game, seat, partner))
                .toList();
    }

    static boolean neighbours(Game game, Player first, Player second) {
        return neighbourFactions(game, first).contains(second.getFaction())
                || neighbourFactions(game, second).contains(first.getFaction());
    }

    static boolean isActionPhase(Game game) {
        return ACTION.equalsIgnoreCase(game.getPhaseOfGame());
    }

    static boolean isStrategyPhase(Game game) {
        return STRATEGY.equalsIgnoreCase(game.getPhaseOfGame());
    }

    static boolean isAgendaPhase(Game game) {
        return phase(game).toLowerCase(Locale.ROOT).startsWith(AGENDA);
    }

    static String window(Game game) {
        if (isActionPhase(game)) return TURN_WINDOW + game.getActivePlayerID() + AT + turnStart(game);
        if (isAgendaPhase(game)) {
            return AGENDA_WINDOW
                    + game.getRound()
                    + SEPARATOR
                    + game.getStoredValue(AGENDA_COUNT)
                    + SEPARATOR
                    + Integer.toHexString(
                            String.valueOf(game.getCurrentAgendaInfo()).hashCode());
        }
        return PHASE_WINDOW + game.getRound() + SEPARATOR + phase(game);
    }

    static String openKey(Game game, Purpose purpose, Deal deal) {
        if (purpose == Purpose.SETTLEMENT_FEE && deal.isDebtOnly()) return ROUND_KEY + game.getRound();
        if (isAgendaPhase(game)) return window(game);
        return ACTION_KEY + game.getRound();
    }

    static boolean windowUsed(AiTurnContext context, Player partner) {
        return context.memory().has(windowKey(context.game(), partner));
    }

    static void useWindow(AiTurnContext context, Player partner, Purpose purpose) {
        if (!WINDOWLESS.contains(purpose)) useWindow(context, partner);
    }

    static void useWindow(AiTurnContext context, Player partner) {
        context.memory().put(windowKey(context.game(), partner), String.valueOf(context.now()));
    }

    private static String windowKey(Game game, Player partner) {
        return WINDOW_KEY + window(game) + SEPARATOR + partner.getFaction();
    }

    private static String phase(Game game) {
        return Objects.toString(game.getPhaseOfGame(), "");
    }

    private static long turnStart(Game game) {
        Date change = game.getLastActivePlayerChange();
        return change == null ? 0 : change.getTime();
    }

    private static boolean censured(Game game, Player player) {
        return IsPlayerElectedService.isPlayerElected(game, player, CENSURE);
    }

    private static boolean openInActionPhase(Game game, Player first, Player second) {
        return (first.hasSpaceStation() && second.hasSpaceStation())
                || game.isAgeOfCommerceMode()
                || first.hasAbility(GUILD_SHIPS)
                || second.hasAbility(GUILD_SHIPS)
                || hasConvoys(first)
                || hasConvoys(second)
                || neighbours(game, first, second);
    }

    private static boolean hasConvoys(Player player) {
        return CONVOYS.stream().anyMatch(player.getPromissoryNotesInPlayArea()::contains);
    }

    private static Set<String> neighbourFactions(Game game, Player player) {
        String key = game.getName() + SEPARATOR + player.getFaction();
        Cached cached = NEIGHBOURS.get(key);
        if (cached != null && cached.isFor(game)) return cached.factions();
        Set<String> factions = player.getNeighbouringPlayers(false).stream()
                .map(Player::getFaction)
                .collect(Collectors.toUnmodifiableSet());
        if (NEIGHBOURS.size() > MAX_CACHED) NEIGHBOURS.clear();
        NEIGHBOURS.put(key, new Cached(new WeakReference<>(game), game.getLastModifiedDate(), factions));
        return factions;
    }
}
