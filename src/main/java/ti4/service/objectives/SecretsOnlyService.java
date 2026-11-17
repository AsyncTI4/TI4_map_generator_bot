package ti4.service.objectives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
public class SecretsOnlyService {

    public static final int VICTORY_POINTS = 10;
    public static final int HAND_LIMIT = 4;
    public static final int STARTING_OFFER_SIZE = 4;
    public static final int STARTING_KEEP_COUNT = 2;
    public static final int STATUS_PHASE_OFFER_SIZE = 2;
    public static final int STATUS_PHASE_KEEP_COUNT = 1;
    public static final String STARTING_OFFER_TAG = "setup";
    public static final String KEEP_BUTTON_PREFIX = "secretsOnlyKeep_";
    static final String ACTION_CARD_DECK = "action_cards_secrets_only";
    static final String EXPLORE_DECK = "explores_secrets_only";
    static final String SECRET_OBJECTIVE_DECK = "secret_objectives_secrets_only";
    static final String STEAL_THEIR_THUNDER = "stealthunderso";
    static final String UNCHARTED_PATH = "unchartedpathso";
    private static final String REPEAT_SCORES_KEY = "secretsOnlyRepeatScores";
    private static final int UNCHARTED_PATH_ROUND = 3;
    private static final String OFFER_KEY_PREFIX = "secretsOnlyOffer";
    private static final String PICKS_KEY_PREFIX = "secretsOnlyPicks";

    private static final String RULES_SUMMARY = """
            ### Secrets Only mode enabled
            > - No public objectives are revealed; secret objectives are the main way to score. Play to %d victory points.
            > - Each player may hold up to %d unscored secret objectives. Scored ones do not count toward that limit.
            > - At the start of the game each player keeps %d of %d secret objectives. Each status phase, %d of %d.
            > - A scored secret objective is shuffled back into the deck, so another player can draw and score it.
            > - No player may score the same secret objective twice.
            > - 6 _Covert Exchange_ action cards and 2 secret objective explores per exploration deck were added.
            """;

    public static void setActive(GenericInteractionCreateEvent event, Game game, boolean active) {
        apply(game, active);
        if (!active) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Secrets Only mode disabled. Its action cards and explore cards were removed from the decks."
                            + " Victory points and secret objective limit were left unchanged.");
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event,
                RULES_SUMMARY.formatted(
                        VICTORY_POINTS,
                        HAND_LIMIT,
                        STARTING_KEEP_COUNT,
                        STARTING_OFFER_SIZE,
                        STATUS_PHASE_KEEP_COUNT,
                        STATUS_PHASE_OFFER_SIZE));
    }

    public static void apply(Game game, boolean active) {
        game.setSecretsOnlyMode(active);
        if (!active) {
            removeModeCards(game);
            return;
        }
        game.setHomebrew(true);
        game.setVp(VICTORY_POINTS);
        game.setMaxSOCountPerPlayer(HAND_LIMIT);
        addModeCards(game);
    }

    public static void addModeCards(Game game) {
        for (String acID : Mapper.getDeck(ACTION_CARD_DECK).getNewDeck()) {
            if (!isActionCardInGame(game, acID)) {
                game.getActionCards().add(acID);
            }
        }
        game.shuffleActionCards();
        for (String exploreID : Mapper.getDeck(EXPLORE_DECK).getNewDeck()) {
            if (!game.getAllExplores().contains(exploreID)
                    && !game.getAllExploreDiscard().contains(exploreID)) {
                game.getAllExplores().add(exploreID);
            }
        }
        game.shuffleExplores();
        SecretObjectiveDeckService.shuffleIntoDeck(
                game, Mapper.getDeck(SECRET_OBJECTIVE_DECK).getNewDeck());
    }

    static void removeModeCards(Game game) {
        SecretObjectiveDeckService.removeFromDeck(
                game, Mapper.getDeck(SECRET_OBJECTIVE_DECK).getNewDeck());
        List<String> modeActionCards = Mapper.getDeck(ACTION_CARD_DECK).getNewDeck();
        game.getActionCards().removeAll(modeActionCards);
        List<String> modeExplores = Mapper.getDeck(EXPLORE_DECK).getNewDeck();
        game.getAllExplores().removeAll(modeExplores);
        game.getAllExploreDiscard().removeAll(modeExplores);
    }

    private static boolean isActionCardInGame(Game game, String acID) {
        return game.getActionCards().contains(acID)
                || game.getDiscardActionCards().containsKey(acID)
                || game.getPlayers().values().stream()
                        .anyMatch(player -> player.getActionCards().containsKey(acID));
    }

    public static void offerStartingSecretObjectives(Game game) {
        offerToAll(game, STARTING_OFFER_SIZE, STARTING_KEEP_COUNT, STARTING_OFFER_TAG);
    }

    public static void offerStatusPhaseSecretObjectives(Game game) {
        offerToAll(game, STATUS_PHASE_OFFER_SIZE, STATUS_PHASE_KEEP_COUNT, "round" + game.getRound());
    }

    private static void offerToAll(Game game, int count, int picks, String tag) {
        for (Player player : game.getRealPlayers()) {
            offer(game, player, count, picks, tag);
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "Each player was offered " + count + " " + CardEmojis.SecretObjective + " to keep " + picks
                        + " from. Check your `#cards-info` threads.");
    }

    private static void offer(Game game, Player player, int count, int picks, String tag) {
        int offerSize = count + (player.hasAbility("plausible_deniability") ? 1 : 0);
        List<String> offered = drawOffer(game, player, offerSize, picks, tag);
        if (offered.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", no secret objectives you have not already scored are left in the deck, so none were offered.");
            return;
        }
        sendOffer(player, tag, offered, picksLeft(player, tag));
    }

    static List<String> drawOffer(Game game, Player player, int count, int picks, String tag) {
        List<String> pending = pendingOffer(player, tag);
        if (!pending.isEmpty()) {
            return pending;
        }
        List<String> offered = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String soID = game.nextSecretObjectiveScorableBy(player);
            if (soID == null) {
                break;
            }
            game.removeSOFromGame(soID);
            offered.add(soID);
        }
        if (!offered.isEmpty()) {
            player.addToStoredList(offerKey(tag), offered.toArray(String[]::new));
            player.setStoredValue(picksKey(tag), String.valueOf(Math.min(picks, offered.size())));
        }
        return offered;
    }

    private static void sendOffer(Player player, String tag, List<String> offered, int picks) {
        StringBuilder message = new StringBuilder(player.getRepresentationUnfogged())
                .append(", keep ")
                .append(picks)
                .append(" of these secret objectives. The others are shuffled back into the deck.\n");
        List<Button> buttons = new ArrayList<>();
        for (String soID : offered) {
            message.append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(soID));
            SecretObjectiveModel model = Mapper.getSecretObjective(soID);
            String name = model == null ? soID : model.getName();
            buttons.add(
                    Buttons.green(KEEP_BUTTON_PREFIX + tag + "_" + soID, "Keep " + name, CardEmojis.SecretObjective));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message.toString(), buttons);
    }

    public static boolean keep(Game game, Player player, String tag, String soID) {
        List<String> offered = pendingOffer(player, tag);
        if (!offered.remove(soID)) {
            return false;
        }
        player.setSecret(soID);
        int picksLeft = picksLeft(player, tag) - 1;
        player.removeStoredValue(offerKey(tag));
        if (picksLeft > 0 && !offered.isEmpty()) {
            player.addToStoredList(offerKey(tag), offered.toArray(String[]::new));
            player.setStoredValue(picksKey(tag), String.valueOf(picksLeft));
            return true;
        }
        player.removeStoredValue(picksKey(tag));
        offered.forEach(game::addSOToGame);
        Collections.shuffle(game.getSecretObjectives());
        return true;
    }

    public static int picksLeft(Player player, String tag) {
        if (pendingOffer(player, tag).isEmpty()) {
            return 0;
        }
        String picks = player.getStoredValue(picksKey(tag));
        return picks.isEmpty() ? 1 : Integer.parseInt(picks);
    }

    public static boolean hasPendingOffer(Player player) {
        return player.getStoredValueMap().keySet().stream().anyMatch(key -> key.startsWith(OFFER_KEY_PREFIX));
    }

    public static boolean anyPlayerHasPendingOffer(Game game) {
        return game.getRealPlayers().stream().anyMatch(SecretsOnlyService::hasPendingOffer);
    }

    static List<String> pendingOffer(Player player, String tag) {
        return player.getStoredList(offerKey(tag));
    }

    public static boolean isInPendingOffer(Player player, String soID) {
        return player.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(OFFER_KEY_PREFIX))
                .anyMatch(key -> player.getStoredList(key).contains(soID));
    }

    public static void recordScore(Game game, Player player, String soID) {
        boolean scoredByAnother = game.getRealPlayers().stream()
                .anyMatch(other -> other != player && other.getSecretsScored().containsKey(soID));
        if (scoredByAnother && !player.getStoredList(REPEAT_SCORES_KEY).contains(soID)) {
            player.addToStoredList(REPEAT_SCORES_KEY, soID);
        }
    }

    private static int repeatScores(Player player) {
        return (int) player.getStoredList(REPEAT_SCORES_KEY).stream()
                .filter(player.getSecretsScored()::containsKey)
                .count();
    }

    public static int threshold(String soID) {
        return switch (soID) {
            case STEAL_THEIR_THUNDER -> 1;
            case UNCHARTED_PATH -> 3;
            default -> 0;
        };
    }

    public static int progress(String soID, Game game, Player player) {
        return switch (soID) {
            case STEAL_THEIR_THUNDER -> Math.min(1, repeatScores(player));
            case UNCHARTED_PATH -> {
                int unique = uniqueScores(game, player);
                yield game.getRound() >= UNCHARTED_PATH_ROUND ? unique : Math.min(unique, threshold(soID) - 1);
            }
            default -> 0;
        };
    }

    private static int uniqueScores(Game game, Player player) {
        return (int) player.getSecretsScored().keySet().stream()
                .filter(soID -> game.getRealPlayers().stream()
                        .noneMatch(other ->
                                other != player && other.getSecretsScored().containsKey(soID)))
                .count();
    }

    public static boolean hasAlreadyScored(Player player, int soNumber) {
        SecretObjectiveModel model = player.getSecret(soNumber);
        return model != null && player.getSecretsScored().containsKey(model.getAlias());
    }

    public static String drawScorableSecretWithoutLimitCheck(Game game, Player player) {
        String soID = game.nextSecretObjectiveScorableBy(player);
        if (soID == null) {
            return null;
        }
        game.removeSOFromGame(soID);
        player.setSecret(soID);
        return soID;
    }

    private static String offerKey(String tag) {
        return OFFER_KEY_PREFIX + tag;
    }

    private static String picksKey(String tag) {
        return PICKS_KEY_PREFIX + tag;
    }
}
