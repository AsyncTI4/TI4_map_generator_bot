package ti4.service.info;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.helpers.StringHelper;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class KnownCardsService {

    public enum KnownCardType {
        ACTION_CARD("ac", "Action Cards", CardEmojis.ActionCard, "steal"),
        SECRET_OBJECTIVE("so", "Secret Objectives", CardEmojis.SecretObjective, "discard");

        private final String key;
        private final String title;
        private final CardEmojis emoji;
        private final String hiddenRemovalWord;

        KnownCardType(String key, String title, CardEmojis emoji, String hiddenRemovalWord) {
            this.key = key;
            this.title = title;
            this.emoji = emoji;
            this.hiddenRemovalWord = hiddenRemovalWord;
        }
    }

    private record KnownCard(String name, int hiddenRemovalsAtReveal) {

        private static KnownCard parse(String stored) {
            int separator = stored.lastIndexOf(':');
            return new KnownCard(stored.substring(0, separator), Integer.parseInt(stored.substring(separator + 1)));
        }

        private String serialize() {
            return name + ":" + hiddenRemovalsAtReveal;
        }
    }

    public static String actionCardName(String actionCardId) {
        ActionCardModel model = Mapper.getActionCard(actionCardId);
        return model == null ? null : model.getName();
    }

    public static String secretObjectiveName(String secretObjectiveId) {
        SecretObjectiveModel model = Mapper.getSecretObjective(secretObjectiveId);
        return model == null ? null : model.getName();
    }

    public static void recordCardLeftHand(Player target, KnownCardType type) {
        incrementCounter(target, leftHandKey(type));
    }

    public static void recordCardLeftHandPublicly(Game game, Player target, KnownCardType type, String cardName) {
        incrementCounter(target, leftHandPubliclyKey(type));
        if (cardName == null) {
            return;
        }
        for (Player viewer : game.getPlayers().values()) {
            if (viewer != target) {
                forgetOneCopy(viewer, target, type, cardName);
            }
        }
    }

    public static void recordCardPassed(Player from, Player to, KnownCardType type, String cardName) {
        if (from == null || to == null || from == to || cardName == null) {
            return;
        }
        forgetOneCopy(to, from, type, cardName);
        excludeLatestRemovalFromHiddenCount(to, from, type);
        recordCardShown(from, to, type, cardName);
    }

    public static void recordCardShown(Player viewer, Player target, KnownCardType type, String cardName) {
        if (viewer == null || viewer == target || cardName == null) {
            return;
        }
        viewer.addToStoredList(
                knownCardsKey(target, type), new KnownCard(cardName, hiddenRemovals(target, type)).serialize());
    }

    public static void recordCardShownToAll(Game game, Player target, KnownCardType type, String cardName) {
        for (Player viewer : game.getRealPlayers()) {
            recordCardShown(viewer, target, type, cardName);
        }
    }

    public static void recordHandShown(Player viewer, Player target, KnownCardType type) {
        if (viewer == null || viewer == target) {
            return;
        }
        String key = knownCardsKey(target, type);
        viewer.removeStoredValue(key);
        int hiddenRemovals = hiddenRemovals(target, type);
        String[] entries = handCardNames(target, type).stream()
                .map(name -> new KnownCard(name, hiddenRemovals).serialize())
                .toArray(String[]::new);
        if (entries.length > 0) {
            viewer.addToStoredList(key, entries);
        }
    }

    public static void recordHandShownToAll(Game game, Player target, KnownCardType type) {
        for (Player viewer : game.getRealPlayers()) {
            recordHandShown(viewer, target, type);
        }
    }

    public static List<MessageEmbed> getKnownCardHandEmbeds(Game game, Player viewer) {
        List<MessageEmbed> embeds = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == viewer) {
                continue;
            }
            String description = knownCardsDescription(game, viewer, target);
            if (description.isEmpty()) {
                continue;
            }
            embeds.add(new EmbedBuilder()
                    .setTitle(embedTitle(game, viewer, target))
                    .setDescription(description)
                    .setColor(Color.GRAY)
                    .build());
        }
        return embeds;
    }

    private static String embedTitle(Game game, Player viewer, Player target) {
        String unfogged = target.getUserName() + " - " + target.getFactionNameOrColor();
        return FoWHelper.identityOrColorIfCanSeeStats(game, target, viewer, unfogged);
    }

    private static String knownCardsDescription(Game game, Player viewer, Player target) {
        List<String> sections = new ArrayList<>();
        for (KnownCardType type : KnownCardType.values()) {
            List<KnownCard> knownCards = getKnownCards(viewer, target, type);
            if (!knownCards.isEmpty()) {
                sections.add(section(target, type, knownCards));
            }
        }
        if (sections.isEmpty()) {
            return "";
        }
        String factionLine = game.isFowMode() ? "" : target.getFactionEmoji() + "\n";
        return factionLine + String.join("\n\n", sections);
    }

    private static String section(Player target, KnownCardType type, List<KnownCard> knownCards) {
        int hiddenRemovalsNow = hiddenRemovals(target, type);
        StringBuilder sb = new StringBuilder();
        sb.append(type.emoji).append(" **").append(type.title).append(":**");
        for (KnownCard card : knownCards) {
            sb.append("\n- ").append(card.name());
            int sinceReveal = hiddenRemovalsNow - card.hiddenRemovalsAtReveal();
            if (sinceReveal > 0) {
                sb.append(" (")
                        .append(StringHelper.pluralize(sinceReveal, type.hiddenRemovalWord))
                        .append(" since reveal)");
            }
        }
        return sb.toString();
    }

    private static void forgetOneCopy(Player viewer, Player target, KnownCardType type, String cardName) {
        String key = knownCardsKey(target, type);
        for (String stored : viewer.getStoredList(key)) {
            if (KnownCard.parse(stored).name().equals(cardName)) {
                viewer.removeFromStoredList(key, stored);
                return;
            }
        }
    }

    private static void excludeLatestRemovalFromHiddenCount(Player viewer, Player target, KnownCardType type) {
        String key = knownCardsKey(target, type);
        String[] shifted = getKnownCards(viewer, target, type).stream()
                .map(card -> new KnownCard(card.name(), card.hiddenRemovalsAtReveal() + 1).serialize())
                .toArray(String[]::new);
        viewer.removeStoredValue(key);
        if (shifted.length > 0) {
            viewer.addToStoredList(key, shifted);
        }
    }

    private static List<KnownCard> getKnownCards(Player viewer, Player target, KnownCardType type) {
        return viewer.getStoredList(knownCardsKey(target, type)).stream()
                .map(KnownCard::parse)
                .toList();
    }

    private static List<String> handCardNames(Player target, KnownCardType type) {
        Collection<String> ids = type == KnownCardType.ACTION_CARD
                ? target.getActionCards().keySet()
                : target.getSecrets().keySet();
        return ids.stream()
                .map(id -> type == KnownCardType.ACTION_CARD ? actionCardName(id) : secretObjectiveName(id))
                .filter(name -> name != null)
                .toList();
    }

    private static int hiddenRemovals(Player target, KnownCardType type) {
        return counter(target, leftHandKey(type)) - counter(target, leftHandPubliclyKey(type));
    }

    private static void incrementCounter(Player player, String key) {
        player.setStoredValue(key, String.valueOf(counter(player, key) + 1));
    }

    private static int counter(Player player, String key) {
        String value = player.getStoredValue(key);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static String knownCardsKey(Player target, KnownCardType type) {
        return "knownCards_" + type.key + "_" + target.getUserID();
    }

    private static String leftHandKey(KnownCardType type) {
        return "cardsLeftHand_" + type.key;
    }

    private static String leftHandPubliclyKey(KnownCardType type) {
        return "cardsLeftHandPublicly_" + type.key;
    }
}
