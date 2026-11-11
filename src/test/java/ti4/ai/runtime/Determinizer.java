package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import ti4.game.Game;
import ti4.game.Player;

/**
 * Samples the hidden information a deciding seat cannot know, so a rollout does not plan around the real hands:
 * every other seat's action cards and unscored secret objectives go back into their decks, the hidden decks (action
 * cards, secret objectives, unrevealed public objectives, agendas, explores, relics) are shuffled, and each other seat
 * is dealt the same number of cards it held, in the same hand slots. Counts are public, so they are kept. The deciding
 * seat's own hand, every revealed card and every unrevealed objective the seat peeked at stay as they are. Promissory
 * notes are not resampled.
 */
final class Determinizer {

    private Determinizer() {}

    static void resample(Game game, Player seat, Random random) {
        List<Player> others =
                game.getRealPlayers().stream().filter(player -> player != seat).toList();
        Map<Player, List<Integer>> actionCardSlots = returnActionCards(game, others);
        Map<Player, List<Integer>> secretSlots = returnSecrets(game, others);
        shuffleHiddenDecks(game, seat, random);
        actionCardSlots.forEach((player, slots) -> {
            for (int slot : slots) {
                if (game.getActionCards().isEmpty()) break;
                player.setActionCard(game.getActionCards().removeFirst(), slot);
            }
        });
        secretSlots.forEach((player, slots) -> {
            for (int slot : slots) {
                if (game.getSecretObjectives().isEmpty()) break;
                player.setSecret(game.getSecretObjectives().removeFirst(), slot);
            }
        });
    }

    private static Map<Player, List<Integer>> returnActionCards(Game game, List<Player> others) {
        Map<Player, List<Integer>> slots = new LinkedHashMap<>();
        for (Player player : others) {
            List<Integer> held = new ArrayList<>();
            for (Map.Entry<String, Integer> card :
                    new ArrayList<>(player.getActionCards().entrySet())) {
                game.getActionCards().add(card.getKey());
                held.add(card.getValue());
                player.removeActionCard(card.getValue());
            }
            slots.put(player, held);
        }
        return slots;
    }

    private static Map<Player, List<Integer>> returnSecrets(Game game, List<Player> others) {
        Map<Player, List<Integer>> slots = new LinkedHashMap<>();
        for (Player player : others) {
            List<Integer> held = new ArrayList<>();
            for (Map.Entry<String, Integer> secret :
                    new ArrayList<>(player.getSecretsUnscored().entrySet())) {
                game.getSecretObjectives().add(secret.getKey());
                held.add(secret.getValue());
                player.removeSecret(secret.getValue());
            }
            slots.put(player, held);
        }
        return slots;
    }

    private static void shuffleHiddenDecks(Game game, Player seat, Random random) {
        game.shuffleDecks();
        game.shuffleAgendas();
        shuffleUnseen(
                game.getPublicObjectives1Peekable(),
                game.getPublicObjectives1(),
                game.getPublicObjectives1Peeked(),
                seat,
                random);
        shuffleUnseen(
                game.getPublicObjectives2Peekable(),
                game.getPublicObjectives2(),
                game.getPublicObjectives2Peeked(),
                seat,
                random);
    }

    // Reveals take the peekable list first and then the deck, so the two are one hidden order. An objective the seat
    // peeked at keeps its place; the rest are dealt again into the remaining places.
    private static void shuffleUnseen(
            List<String> peekable, List<String> deck, Map<String, List<String>> peeked, Player seat, Random random) {
        List<String> stack = deck == null ? new ArrayList<>() : deck;
        Predicate<String> unseen =
                objective -> !peeked.getOrDefault(objective, List.of()).contains(seat.getUserID());
        List<String> order = new ArrayList<>(peekable);
        order.addAll(stack);
        order.removeIf(unseen.negate());
        Collections.shuffle(order, random);
        Iterator<String> next = order.iterator();
        peekable.replaceAll(objective -> unseen.test(objective) ? next.next() : objective);
        stack.replaceAll(objective -> unseen.test(objective) ? next.next() : objective);
    }
}
