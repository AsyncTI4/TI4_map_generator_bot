package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import ti4.game.Game;
import ti4.game.Player;

/**
 * Samples the hidden information a deciding seat cannot know, so a rollout does not plan around the real hands:
 * every other seat's action cards and unscored secret objectives go back into their decks, the hidden decks (action
 * cards, secret objectives, unrevealed public objectives, agendas, explores, relics) are shuffled, and each other seat
 * is dealt the same number of cards it held, in the same hand slots. Counts are public, so they are kept. The deciding
 * seat's own hand and every revealed card stay as they are. Promissory notes are not resampled.
 */
final class Determinizer {

    private Determinizer() {}

    static void resample(Game game, Player seat, Random random) {
        List<Player> others =
                game.getRealPlayers().stream().filter(player -> player != seat).toList();
        Map<Player, List<Integer>> actionCardSlots = returnActionCards(game, others);
        Map<Player, List<Integer>> secretSlots = returnSecrets(game, others);
        shuffleHiddenDecks(game, random);
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

    private static void shuffleHiddenDecks(Game game, Random random) {
        game.shuffleDecks();
        game.shuffleAgendas();
        if (game.getPublicObjectives1() != null) Collections.shuffle(game.getPublicObjectives1(), random);
        if (game.getPublicObjectives2() != null) Collections.shuffle(game.getPublicObjectives2(), random);
    }
}
