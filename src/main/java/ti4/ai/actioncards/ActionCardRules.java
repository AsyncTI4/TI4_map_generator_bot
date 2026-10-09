package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.secrets.SpyNetwork;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.service.actioncard.SabotageService;
import ti4.service.agenda.IsPlayerElectedService;

@UtilityClass
public class ActionCardRules {

    private static final String PLAY_KEY = "acPlay|";
    private static final String REF_KEY = "acResolveRef|";
    private static final String FIELD = "~";
    private static final String TURN_SCOPE = "turn:";
    private static final String SUMMIT_SCOPE = "summit";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String SUMMIT_PRESET = "resolvePreassignment_Summit";
    private static final String SUMMIT_STORED = "Summit";
    private static final String FRONTLINE_TARGET = "placeOneNDone_skipbuild_3gf_";
    private static final String CRUISER_TARGET = "placeOneNDone_skipbuild_cruiser_";
    private static final String DONE_GAINING = "Done Gaining";
    private static final int SUMMIT_TOKENS = 2;
    private static final long CONFIRM_MILLIS = 60_000L;
    private static final long ANNOUNCEMENT_MILLIS = 5 * 60_000L;
    private static final long PROMPT_MILLIS = 2 * 60_000L;
    private static final long HOLD_MILLIS = 60_000L;
    private static final long SETTLE_MILLIS = 10_000L;
    private static final long OTHER_CANCEL_GRACE_MILLIS = 5 * 60_000L;
    private static final long WINDOW_CEILING_MILLIS = 24 * 60 * 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final double DOCK_OR_CARRIER = 3.0;
    private static final double NEAR_ENEMY_SHIPS = 2.0;
    private static final double PLANET_WORTH_SHARE = 0.25;
    private static final int COMFORTABLE_GARRISON = 2;
    private static final Set<String> CENSURE = Set.of("censure", "absol_censure");
    private static final Map<String, String> RESOLVE = Map.of(
            ActionCardValue.MINING, "miningInitiative",
            ActionCardValue.INDUSTRIAL, "industrialInitiative",
            ActionCardValue.ECONOMIC, "economicInitiative",
            ActionCardValue.MESSIAH, "riseOfAMessiah",
            ActionCardValue.FRONTLINE, "resolveFrontline",
            ActionCardValue.WAR_EFFORT, "resolveWarEffort",
            ActionCardValue.SUMMIT, "resolveSummit");
    private static final Set<String> WITH_TARGET =
            Set.of(ActionCardValue.FRONTLINE, ActionCardValue.WAR_EFFORT, ActionCardValue.SUMMIT);

    public static Optional<AiDecision> playBeforePassing(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        String key = turnKey(context);
        if (context.memory().has(key) || !mayPlay(game, seat) || keepsSpyNetworkCards(seat)) return Optional.empty();
        Optional<ActionCardValue.Candidate> best = ActionCardValue.bestComponentPlay(game, seat);
        if (best.isEmpty()) return Optional.empty();
        return startPlay(context, key, best.get().alias());
    }

    public static Optional<AiDecision> holdOwnTurn(AiTurnContext context) {
        if (!context.isActivePlayer()) return Optional.empty();
        Optional<CardPlay> play = context.memory().get(turnKey(context)).flatMap(CardPlay::decode);
        if (play.isEmpty() || !play.get().active()) return Optional.empty();
        String title = SabotageWindow.title(play.get().alias());
        return Optional.of(new AiDecision.Wait(context.now() + HOLD_MILLIS, "the " + title + " action card"));
    }

    public static Optional<AiDecision> continuePlay(AiTurnContext context) {
        recover(context);
        return advance(context, turnKey(context)).or(() -> advance(context, PLAY_KEY + SUMMIT_SCOPE));
    }

    private static void recover(AiTurnContext context) {
        String key = turnKey(context);
        if (context.memory().has(key) || !context.isActivePlayer()) return;
        if (!"action".equalsIgnoreCase(context.game().getPhaseOfGame())) return;
        for (Map.Entry<String, String> card : RESOLVE.entrySet()) {
            if (ActionCardValue.SUMMIT.equals(card.getKey())) continue;
            Optional<Match> resolve = ownedPublic(context, Prompts.turnStart(context), card.getValue()::equals);
            if (resolve.isEmpty()) continue;
            long playedAt = resolve.get().prompt().createdAtMillis();
            Optional<AiPrompt> announcement =
                    SabotageWindow.announcement(context, card.getKey(), playedAt - CONFIRM_MILLIS);
            CardPlay rebuilt = new CardPlay(
                    Stage.WINDOW,
                    context.now(),
                    playedAt - CONFIRM_MILLIS,
                    card.getKey(),
                    announcement.map(AiPrompt::messageId).orElse(""),
                    announcement.isPresent(),
                    lastPlayIndex(context.game(), context.seat(), card.getKey()),
                    "");
            context.memory()
                    .put(key, (announcement.isPresent() ? rebuilt : rebuilt.at(Stage.RESOLVE, context.now())).encode());
            return;
        }
    }

    private static int lastPlayIndex(Game game, Player seat, String alias) {
        String title = SabotageWindow.title(alias);
        String playerId = GameStats.getTrackedPlayerId(seat);
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        for (int index = plays.size() - 1; index >= 0; index--) {
            if (title.equals(plays.get(index).getActionCard())
                    && playerId != null
                    && playerId.equals(plays.get(index).getPlayerId())) {
                return index;
            }
        }
        return 0;
    }

    private static boolean censured(Game game, Player seat) {
        for (String law : CENSURE) {
            if (IsPlayerElectedService.isPlayerElected(game, seat, law)) return true;
        }
        return false;
    }

    private static long settleMillis(Game game, Player seat, CardPlay play) {
        if (play.windowExpected()) return SETTLE_MILLIS;
        boolean otherCancels = game.getRealPlayers().stream()
                .filter(player -> player != seat)
                .anyMatch(player -> SabotageService.couldUseInstinctTraining(player)
                        || SabotageService.couldUseWatcherMech(player, game));
        return otherCancels ? OTHER_CANCEL_GRACE_MILLIS : SETTLE_MILLIS;
    }

    public static Optional<AiDecision> presetSummit(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.getActionCards().containsKey(ActionCardValue.SUMMIT) || !mayPlay(game, seat)) return Optional.empty();
        String key = PLAY_KEY + SUMMIT_SCOPE;
        boolean tracked = context.memory()
                .get(key)
                .flatMap(CardPlay::decode)
                .filter(CardPlay::active)
                .isPresent();
        if (tracked) return Optional.empty();
        CardPlay preset =
                new CardPlay(Stage.PRESET, context.now(), context.now(), ActionCardValue.SUMMIT, "", false, 0, "");
        if (game.getStoredValue(SUMMIT_STORED).contains(seat.getFaction())) {
            context.memory().put(key, preset.encode());
            return Optional.empty();
        }
        if (CommandTokenPolicy.gainWithinReinforcements(game, seat) < 1) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.firstEnabled(
                    candidate -> candidate.isUnowned() && SUMMIT_PRESET.equals(candidate.handlerId()));
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                context.memory().put(key, preset.encode());
                return Optional.of(AiDecision.press(prompt, button.get(), "pre-play Summit for 2 command tokens"));
            }
        }
        return Optional.empty();
    }

    static boolean mayPlay(Game game, Player seat) {
        if (censured(game, seat) || ButtonHelper.isPlayerOverLimit(game, seat)) return false;
        Player active = game.getActivePlayer();
        return !(seat.isPassed() && active != null && active.hasTech("tp"));
    }

    private static boolean keepsSpyNetworkCards(Player seat) {
        return SpyNetwork.holds(seat) && seat.getAcCount() <= SpyNetwork.CARDS;
    }

    private static String turnKey(AiTurnContext context) {
        return PLAY_KEY + TURN_SCOPE + context.turnKey();
    }

    private static Optional<AiDecision> startPlay(AiTurnContext context, String key, String alias) {
        Integer index = context.seat().getActionCards().get(alias);
        if (index == null) return Optional.empty();
        String title = SabotageWindow.title(alias);
        Optional<Match> play = playButton(context, index, Long.MIN_VALUE);
        if (play.isPresent()) {
            context.memory().put(key, played(context, alias).encode());
            return Optional.of(play.get().press("play " + title));
        }
        Optional<Match> menu = Prompts.owned(Prompts.thisTurn(context), context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        context.memory()
                .put(key, new CardPlay(Stage.MENU, context.now(), context.now(), alias, "", false, 0, "").encode());
        return Optional.of(menu.get().press("open the component actions to play " + title));
    }

    private static CardPlay played(AiTurnContext context, String alias) {
        int plays = context.game().getGameStats().getActionCardPlays().size();
        boolean window = SabotageWindow.expected(context.game(), context.seat(), alias);
        return new CardPlay(Stage.PLAYED, context.now(), context.now(), alias, "", window, plays, "");
    }

    private static Optional<Match> playButton(AiTurnContext context, int index, long since) {
        String id = Constants.AC_PLAY_FROM_HAND + index;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button =
                    prompt.firstEnabled(candidate -> candidate.isUnowned() && id.equals(candidate.handlerId()));
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(new Match(prompt, button.get()));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> advance(AiTurnContext context, String key) {
        Optional<CardPlay> stored = context.memory().get(key).flatMap(CardPlay::decode);
        if (stored.isEmpty() || !stored.get().active()) return Optional.empty();
        CardPlay play = stored.get();
        return switch (play.stage()) {
            case MENU -> fromMenu(context, key, play);
            case PRESET -> afterPreset(context, key, play);
            case PLAYED -> afterPlay(context, key, play);
            case WINDOW -> duringWindow(context, key, play);
            case RESOLVE -> resolve(context, key, play);
            case TARGET -> target(context, key, play);
            case DONE, CANCELED -> Optional.empty();
        };
    }

    private static Optional<AiDecision> move(AiTurnContext context, String key, CardPlay next) {
        context.memory().put(key, next.encode());
        return next.active() ? advance(context, key) : Optional.empty();
    }

    private static Optional<AiDecision> stop(AiTurnContext context, String key, CardPlay play, Stage stage) {
        context.memory().put(key, play.at(stage, context.now()).encode());
        return Optional.empty();
    }

    private static boolean expired(AiTurnContext context, CardPlay play, long limit) {
        return context.now() > play.since() + limit;
    }

    private static Optional<AiDecision> fromMenu(AiTurnContext context, String key, CardPlay play) {
        Integer index = context.seat().getActionCards().get(play.alias());
        if (index == null) return stop(context, key, play, Stage.DONE);
        Optional<Match> button = playButton(context, index, play.since() - CLOCK_SKEW_MILLIS);
        if (button.isEmpty()) {
            return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        context.memory().put(key, played(context, play.alias()).encode());
        return Optional.of(button.get().press("play " + SabotageWindow.title(play.alias())));
    }

    private static Optional<AiDecision> afterPreset(AiTurnContext context, String key, CardPlay play) {
        if (context.seat().getActionCards().containsKey(play.alias())) return Optional.empty();
        if (censured(context.game(), context.seat())) return stop(context, key, play, Stage.DONE);
        boolean window = SabotageWindow.expected(context.game(), context.seat(), play.alias());
        CardPlay played =
                new CardPlay(window ? Stage.WINDOW : Stage.RESOLVE, context.now(), 0, play.alias(), "", window, 0, "");
        return move(context, key, played);
    }

    private static Optional<AiDecision> afterPlay(AiTurnContext context, String key, CardPlay play) {
        if (context.seat().getActionCards().containsKey(play.alias())) {
            return expired(context, play, CONFIRM_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        return move(context, key, play.at(play.windowExpected() ? Stage.WINDOW : Stage.RESOLVE, context.now()));
    }

    private static Optional<AiDecision> duringWindow(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, play, Stage.CANCELED);
        }
        CardPlay current = play;
        if (current.announcementId().isBlank()) {
            Optional<AiPrompt> announcement = SabotageWindow.announcement(context, play.alias(), play.playedAt());
            if (announcement.isEmpty()) {
                return expired(context, play, ANNOUNCEMENT_MILLIS)
                        ? stop(context, key, play, Stage.DONE)
                        : Optional.empty();
            }
            current = current.withAnnouncement(announcement.get().messageId());
            context.memory().put(key, current.encode());
        }
        rememberResolveButton(context, key, current);
        boolean overdue = context.now() > current.since() + WINDOW_CEILING_MILLIS;
        if (!overdue && SabotageWindow.open(context, current.announcementId(), current.since()))
            return Optional.empty();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, current, Stage.CANCELED);
        }
        return move(context, key, current.at(Stage.RESOLVE, context.now()));
    }

    private static Optional<AiDecision> resolve(AiTurnContext context, String key, CardPlay play) {
        String handler = RESOLVE.get(play.alias());
        Game game = context.game();
        Player seat = context.seat();
        if (handler == null || censured(game, seat)) return stop(context, key, play, Stage.DONE);
        if (context.now() < play.since() + settleMillis(game, seat, play)) return Optional.empty();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, play, Stage.CANCELED);
        }
        Optional<Match> button = ownedPublic(context, play.playedAt() - CLOCK_SKEW_MILLIS, handler::equals)
                .or(() -> rememberedResolveButton(context, key, handler));
        if (button.isEmpty()) {
            return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        context.memory().remove(REF_KEY + key);
        CardPlay next = play.at(WITH_TARGET.contains(play.alias()) ? Stage.TARGET : Stage.DONE, context.now())
                .withExtra("");
        context.memory().put(key, next.encode());
        return Optional.of(button.get().press("resolve " + SabotageWindow.title(play.alias())));
    }

    private static void rememberResolveButton(AiTurnContext context, String key, CardPlay play) {
        String handler = RESOLVE.get(play.alias());
        if (handler == null || context.memory().has(REF_KEY + key)) return;
        ownedPublic(context, play.playedAt() - CLOCK_SKEW_MILLIS, handler::equals)
                .ifPresent(match -> context.memory()
                        .put(
                                REF_KEY + key,
                                String.join(
                                        FIELD,
                                        match.prompt().channelId(),
                                        match.prompt().messageId(),
                                        match.button().customId(),
                                        match.button().label())));
    }

    private static Optional<Match> rememberedResolveButton(AiTurnContext context, String key, String handler) {
        String[] fields = StringUtils.splitPreserveAllTokens(
                context.memory().get(REF_KEY + key).orElse(""), FIELD);
        if (fields == null || fields.length != 4) return Optional.empty();
        PromptButton button = new PromptButton(0, fields[2], handler, context.faction(), fields[3], false);
        AiPrompt prompt =
                new AiPrompt(fields[0], fields[1], AiPrompt.PromptSource.PUBLIC, "", List.of(button), context.now());
        if (context.alreadyPressed(prompt, button)) return Optional.empty();
        return Optional.of(new Match(prompt, button));
    }

    private static Optional<AiDecision> target(AiTurnContext context, String key, CardPlay play) {
        Optional<AiDecision> choice =
                switch (play.alias()) {
                    case ActionCardValue.FRONTLINE -> frontline(context, key, play);
                    case ActionCardValue.WAR_EFFORT -> warEffort(context, key, play);
                    case ActionCardValue.SUMMIT -> summitTokens(context, key, play);
                    default -> Optional.empty();
                };
        if (choice.isPresent()) return choice;
        return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
    }

    private static Optional<AiDecision> frontline(AiTurnContext context, String key, CardPlay play) {
        List<Match> offered = ownedPublicAll(context, play.since() - CLOCK_SKEW_MILLIS, FRONTLINE_TARGET);
        Game game = context.game();
        Player seat = context.seat();
        Optional<Match> best = offered.stream()
                .max(Comparator.comparingDouble((Match match) -> frontlineValue(
                                game,
                                seat,
                                StringUtils.removeStart(match.button().handlerId(), FRONTLINE_TARGET)))
                        .thenComparing(match -> match.button().handlerId(), Comparator.reverseOrder()));
        if (best.isEmpty()) return Optional.empty();
        context.memory().put(key, play.at(Stage.DONE, context.now()).encode());
        return Optional.of(best.get().press("place 3 infantry with Frontline Deployment"));
    }

    public static double frontlineValue(Game game, Player seat, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null) return 0;
        double value = PLANET_WORTH_SHARE * (planet.getResources() + planet.getInfluence());
        UnitHolder space = BoardView.space(tile);
        boolean loads = tile.getPlanetUnitHolders().stream()
                        .anyMatch(holder -> BoardView.count(holder, seat, UnitType.Spacedock) > 0)
                || BoardView.ships(space, seat).keySet().stream().anyMatch(type -> BoardView.capacity(seat, type) > 0);
        if (loads) value += DOCK_OR_CARRIER;
        for (String position : FoWHelper.getAdjacentTiles(game, tile.getPosition(), seat, false)) {
            Tile nearby = game.getTileByPosition(position);
            if (nearby != null && nearby != tile && BoardView.hasEnemyShips(game, seat, nearby)) {
                value += NEAR_ENEMY_SHIPS;
                break;
            }
        }
        return value - Math.max(0, BoardView.groundForces(planet, seat) - COMFORTABLE_GARRISON);
    }

    private static Optional<AiDecision> warEffort(AiTurnContext context, String key, CardPlay play) {
        List<Match> offered = ownedPublicAll(context, play.since() - CLOCK_SKEW_MILLIS, CRUISER_TARGET);
        if (offered.isEmpty()) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        Set<String> legal = new HashSet<>();
        ActionCardValue.cruiserTiles(game, seat).forEach(tile -> legal.add(tile.getPosition()));
        Optional<Match> best = offered.stream()
                .filter(match ->
                        legal.contains(StringUtils.removeStart(match.button().handlerId(), CRUISER_TARGET)))
                .max(Comparator.comparingDouble(match -> cruiserValue(
                        game, seat, StringUtils.removeStart(match.button().handlerId(), CRUISER_TARGET))));
        context.memory().put(key, play.at(Stage.DONE, context.now()).encode());
        return best.map(match -> match.press("place a cruiser with War Effort"));
    }

    private static double cruiserValue(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return 0;
        double value = BoardView.nonFighterShips(BoardView.space(tile), seat);
        for (String nearby : FoWHelper.getAdjacentTiles(game, position, seat, false)) {
            Tile adjacent = game.getTileByPosition(nearby);
            if (adjacent != null && adjacent != tile && BoardView.hasEnemyShips(game, seat, adjacent)) {
                value += NEAR_ENEMY_SHIPS * 5;
                break;
            }
        }
        if (tile == seat.getHomeSystemTile()) value += 1;
        return value;
    }

    private static Optional<AiDecision> summitTokens(AiTurnContext context, String key, CardPlay play) {
        String faction = context.faction();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < play.since() - CLOCK_SKEW_MILLIS) continue;
            Optional<PromptButton> done = prompt.firstEnabled(button -> button.isOwnedBy(faction)
                    && "deleteButtons".equals(button.handlerId())
                    && button.label().startsWith(DONE_GAINING));
            if (done.isEmpty()) continue;
            int wanted;
            if (StringUtils.isNumeric(play.extra())) {
                wanted = Integer.parseInt(play.extra());
            } else {
                wanted = Math.min(
                        SUMMIT_TOKENS, CommandTokenPolicy.gainWithinReinforcements(context.game(), context.seat()));
                context.memory().put(key, play.withExtra(String.valueOf(wanted)).encode());
            }
            if (CommandTokenPolicy.netGainSoFar(context.game(), context.seat()) < wanted) {
                String pool = CommandTokenPolicy.poolToGrow(context.game(), context.seat());
                Optional<PromptButton> grow =
                        prompt.firstEnabled(button -> button.isOwnedBy(faction) && pool.equals(button.handlerId()));
                if (grow.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, grow.get(), "gain a command token from Summit"));
                }
            }
            context.memory().put(key, play.at(Stage.DONE, context.now()).encode());
            return Optional.of(AiDecision.press(prompt, done.get(), "finish gaining Summit's command tokens"));
        }
        return Optional.empty();
    }

    private static Optional<Match> ownedPublic(AiTurnContext context, long since, Predicate<String> handler) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button = prompt.firstEnabled(candidate -> candidate.isOwnedBy(context.faction())
                    && handler.test(candidate.handlerId())
                    && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }

    private static List<Match> ownedPublicAll(AiTurnContext context, long since, String prefix) {
        List<Match> matches = new ArrayList<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isOwnedBy(context.faction())
                        && button.handlerId().startsWith(prefix)
                        && !context.alreadyPressed(prompt, button)) {
                    matches.add(new Match(prompt, button));
                }
            }
        }
        return matches;
    }
}
