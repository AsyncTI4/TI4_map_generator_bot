package ti4.service.megagame;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.StrategyCardModel;

@UtilityClass
public class MegagameGroupService {

    public static final String SC_GROUPS_TAG = "megagame_sc_groups";
    public static final int EMPYREAN_COMMANDER_NEIGHBOURS = 10;
    private static final int UNPICKED_CARDS_PER_GROUP = 2;
    private static final String GROUP_KEY = "megagameGroup_";
    private static final String SPEAKER_KEY = "megagameSpeaker_";
    private static final String PICKER_KEY = "megagamePicker_";
    private static final String LETTERS = "ABCDEFGH";
    private static final String GREEK = "αβγδεζηθ";
    private static final List<String> NAMES =
            List.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta");
    private static final List<Color> COLOURS = List.of(
            new Color(255, 140, 0),
            new Color(0, 200, 0),
            new Color(230, 0, 230),
            new Color(70, 130, 255),
            new Color(230, 30, 30),
            new Color(0, 200, 200),
            new Color(230, 230, 0),
            new Color(160, 160, 160));

    public static boolean isActive(Game game) {
        return game != null && hasTag(game) && !game.isFowMode() && isConfigured(game);
    }

    public static boolean hasTag(Game game) {
        return game.getTags().stream().anyMatch(SC_GROUPS_TAG::equalsIgnoreCase);
    }

    public static boolean isConfigured(Game game) {
        return !groups(game).isEmpty();
    }

    public static List<String> groups(Game game) {
        List<String> groups = new ArrayList<>();
        for (char letter : LETTERS.toCharArray()) {
            String group = String.valueOf(letter);
            if (!game.getStoredValue(GROUP_KEY + group).isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    public static List<String> memberFactions(Game game, String group) {
        String stored = game.getStoredValue(GROUP_KEY + group);
        if (stored.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(stored.split("_")));
    }

    public static List<Player> members(Game game, String group) {
        return memberFactions(game, group).stream()
                .map(game::getPlayerFromColorOrFaction)
                .filter(Objects::nonNull)
                .filter(Player::isRealPlayer)
                .toList();
    }

    public static String groupOf(Game game, Player player) {
        if (player == null) {
            return null;
        }
        for (String group : groups(game)) {
            if (memberFactions(game, group).contains(player.getFaction())) {
                return group;
            }
        }
        return null;
    }

    public static String groupOfSc(int sc) {
        int digit = sc % 10;
        if (sc < 10 || digit == 0 || digit > LETTERS.length()) {
            return null;
        }
        return String.valueOf(LETTERS.charAt(digit - 1));
    }

    public static int groupDigit(String group) {
        return LETTERS.indexOf(group) + 1;
    }

    public static boolean sameGroup(Game game, Player player, int sc) {
        String scGroup = groupOfSc(sc);
        return scGroup == null || scGroup.equals(groupOf(game, player));
    }

    public static List<Player> groupMembersForSc(Game game, int sc) {
        String scGroup = groupOfSc(sc);
        return scGroup == null ? game.getRealPlayers() : members(game, scGroup);
    }

    public static StrategyCardModel strategyCardByName(Game game, Player player, String name) {
        String group = isActive(game) ? groupOf(game, player) : null;
        if (group == null) {
            return game.getStrategyCardModelByName(name).orElse(null);
        }
        return groupCards(game, group).stream()
                .map(game::getStrategyCardModelByInitiative)
                .flatMap(Optional::stream)
                .filter(model -> model.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static int groupCardFor(Game game, Player player, int cardNumber) {
        String group = isActive(game) ? groupOf(game, player) : null;
        return group == null ? cardNumber : cardNumber * 10 + groupDigit(group);
    }

    public static boolean isLastToPassInGroup(Game game, Player player) {
        if (!isActive(game) || !player.isPassed()) {
            return false;
        }
        String group = groupOf(game, player);
        return group != null && members(game, group).stream().allMatch(Player::isPassed);
    }

    public static boolean allowsCard(Game game, Player player, int sc) {
        return !isActive(game) || sameGroup(game, player, sc);
    }

    public static boolean allowsTarget(Game game, Player player, Player target) {
        if (!isActive(game)) {
            return true;
        }
        String group = groupOf(game, player);
        return group != null && group.equals(groupOf(game, target));
    }

    public static boolean allowsCardInStrategyPhase(Game game, Player player, int sc) {
        if (!isActive(game) || groupOfSc(sc) == null) {
            return true;
        }
        return groupOfSc(sc).equals(groupForStrategyPhase(game, player));
    }

    public static boolean allowsTargetInStrategyPhase(Game game, Player player, Player target) {
        if (!isActive(game)) {
            return true;
        }
        String group = groupForStrategyPhase(game, player);
        return group != null && group.equals(groupForStrategyPhase(game, target));
    }

    static String groupForStrategyPhase(Game game, Player player) {
        String group = groupOf(game, player);
        String phase = StringUtils.defaultString(game.getPhaseOfGame());
        if (group == null || !(phase.startsWith("status") || phase.startsWith("agenda"))) {
            return group;
        }
        List<String> groups = groups(game);
        return groups.get((groups.indexOf(group) + 1) % groups.size());
    }

    public static String pingForSc(Game game, int sc) {
        if (!isActive(game) || groupOfSc(sc) == null) {
            return game.getPing();
        }
        return groupMembersForSc(game, sc).stream()
                .map(Player::getRepresentation)
                .collect(Collectors.joining(" "));
    }

    public static List<Player> outsidersOfScGroup(Game game, int sc) {
        if (groupOfSc(sc) == null) {
            return List.of();
        }
        List<Player> members = groupMembersForSc(game, sc);
        return game.getRealPlayers().stream()
                .filter(player -> !members.contains(player))
                .toList();
    }

    public static List<Player> followOrderFromPlayer(Game game, Player player) {
        return rotatedTo(orderFromSpeaker(game, groupOf(game, player)), player);
    }

    public static List<Player> followOrderForSc(Game game, int sc, Player holder) {
        String group = groupOfSc(sc);
        if (group == null) {
            return rotatedTo(new ArrayList<>(game.getRealPlayers()), holder);
        }
        return rotatedTo(orderFromSpeaker(game, group), holder);
    }

    private static List<Player> rotatedTo(List<Player> order, Player first) {
        int index = order.indexOf(first);
        if (index > 0) {
            Collections.rotate(order, -index);
        }
        return order;
    }

    public static boolean isLastGroup(Game game, String group) {
        List<String> groups = groups(game);
        return !groups.isEmpty() && groups.getLast().equals(group);
    }

    public static Player groupSpeaker(Game game, String group) {
        if (isLastGroup(game, group)) {
            return game.getSpeaker();
        }
        return game.getPlayerFromColorOrFaction(game.getStoredValue(SPEAKER_KEY + group));
    }

    public static boolean isGroupSpeaker(Game game, Player player) {
        String group = groupOf(game, player);
        return group != null && player.equals(groupSpeaker(game, group));
    }

    public static void setGroupSpeaker(Game game, String group, Player player) {
        writeSpeaker(game, group, player, isLastGroup(game, group));
    }

    private static void writeSpeaker(Game game, String group, Player player, boolean lastGroup) {
        if (lastGroup) {
            game.removeStoredValue(SPEAKER_KEY + group);
            game.setSpeaker(player);
            return;
        }
        game.setStoredValue(SPEAKER_KEY + group, player.getFaction());
    }

    public static List<Player> orderFromSpeaker(Game game, String group) {
        if (group == null) {
            return new ArrayList<>();
        }
        return rotatedTo(new ArrayList<>(members(game, group)), groupSpeaker(game, group));
    }

    public static List<Integer> groupCards(Game game, String group) {
        return game.getSCList().stream()
                .filter(sc -> group.equals(groupOfSc(sc)))
                .toList();
    }

    public static Player currentPicker(Game game, String group) {
        return game.getPlayerFromColorOrFaction(game.getStoredValue(PICKER_KEY + group));
    }

    public static void setCurrentPicker(Game game, String group, Player player) {
        game.setStoredValue(PICKER_KEY + group, player == null ? "" : player.getFaction());
    }

    public static String greek(String group) {
        int index = LETTERS.indexOf(group);
        return index < 0 ? "?" : String.valueOf(GREEK.charAt(index));
    }

    public static String name(String group) {
        int index = LETTERS.indexOf(group);
        return index < 0 ? group : NAMES.get(index);
    }

    public static String label(String group) {
        int index = LETTERS.indexOf(group);
        return index < 0 ? "Group ?" : "Group " + greek(group) + " (" + NAMES.get(index) + ")";
    }

    public static Color colour(String group) {
        int index = LETTERS.indexOf(group);
        return index < 0 ? Color.GRAY : COLOURS.get(index);
    }

    public record Layout(List<String> groups, Map<String, List<Player>> members, Map<String, Player> speakers) {}

    public static Layout currentLayout(Game game) {
        List<String> groups = groups(game);
        Map<String, List<Player>> members = new LinkedHashMap<>();
        Map<String, Player> speakers = new LinkedHashMap<>();
        for (String group : groups) {
            members.put(group, members(game, group));
            speakers.put(group, groupSpeaker(game, group));
        }
        return new Layout(groups, members, speakers);
    }

    public static Layout nextRoundLayout(Game game) {
        Layout now = currentLayout(game);
        List<String> groups = now.groups();
        Map<String, List<Player>> members = new LinkedHashMap<>();
        Map<String, Player> speakers = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            String from = groups.get(i);
            String to = groups.get((i + 1) % groups.size());
            List<Player> moving = now.members().get(from);
            Player speaker = now.speakers().get(from);
            if (speaker == null || !moving.contains(speaker)) {
                speaker = moving.isEmpty() ? null : moving.getFirst();
            }
            members.put(to, moving);
            speakers.put(to, speaker);
        }
        List<String> ordered = groups.stream().filter(members::containsKey).toList();
        return new Layout(ordered, members, speakers);
    }

    public static String nextRoundSummary(Game game) {
        Layout next = nextRoundLayout(game);
        StringBuilder sb =
                new StringBuilder("**Next round preview** (after the groups shift one column; nothing is changed):\n");
        for (String group : next.groups()) {
            Player speaker = next.speakers().get(group);
            sb.append("**").append(label(group)).append("**");
            if (next.groups().getLast().equals(group)) {
                sb.append(" (holds the game speaker)");
            }
            sb.append(": ");
            sb.append(next.members().get(group).stream()
                    .map(player -> (player.equals(speaker) ? "🗣️" : "") + player.getFactionEmoji())
                    .collect(Collectors.joining(" ")));
            sb.append("\n");
        }
        return sb.toString();
    }

    public static void rotate(Game game) {
        List<String> groups = groups(game);
        if (groups.size() < 2) {
            return;
        }
        Layout next = nextRoundLayout(game);
        String lastGroup = groups.getLast();
        for (String group : groups) {
            writeMembers(
                    game,
                    group,
                    next.members().getOrDefault(group, List.of()).stream()
                            .map(Player::getFaction)
                            .toList());
            Player speaker = next.speakers().get(group);
            if (speaker != null) {
                writeSpeaker(game, group, speaker, group.equals(lastGroup));
            } else {
                game.removeStoredValue(SPEAKER_KEY + group);
            }
        }
        remapPoliticalStabilityCard(game);
    }

    private static void remapPoliticalStabilityCard(Game game) {
        Player player = game.getPlayerFromColorOrFaction(game.getStoredValue("politicalStabilityFaction"));
        String group = groupOf(game, player);
        if (group == null) {
            return;
        }
        for (Integer sc : new ArrayList<>(player.getSCs())) {
            if (groupOfSc(sc) == null) {
                continue;
            }
            int remapped = sc / 10 * 10 + groupDigit(group);
            if (remapped != sc && game.getSCList().contains(remapped)) {
                player.removeSC(sc);
                player.addSC(remapped);
            }
        }
    }

    public static String initGroups(Game game) {
        clearGroupState(game);
        List<Player> players = game.getRealPlayers();
        boolean anyHeld = players.stream().anyMatch(player -> !player.getSCs().isEmpty());
        Map<String, List<String>> groups = anyHeld ? groupsFromHeldCards(players) : groupsBySeat(game, players);
        groups.forEach((group, factions) -> writeMembers(game, group, factions));
        for (String group : groups.keySet()) {
            if (!isLastGroup(game, group)) {
                setGroupSpeaker(game, group, members(game, group).getFirst());
            }
        }
        int largestGroup = groups.values().stream().mapToInt(List::size).max().orElse(1);
        int cardsPerGroup = groups.keySet().stream()
                .mapToInt(group -> groupCards(game, group).size())
                .min()
                .orElse(1);
        game.setStrategyCardsPerPlayer(Math.max(1, cardsPerGroup / Math.max(1, largestGroup)));
        List<String> unassigned = players.stream()
                .filter(player -> groupOf(game, player) == null)
                .map(Player::getFaction)
                .toList();
        return unassigned.isEmpty() ? "" : "Not placed in a group: " + String.join(", ", unassigned);
    }

    private static Map<String, List<String>> groupsFromHeldCards(List<Player> players) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (char letter : LETTERS.toCharArray()) {
            String group = String.valueOf(letter);
            List<String> factions = players.stream()
                    .filter(player -> !player.getSCs().isEmpty())
                    .filter(player -> group.equals(groupOfSc(player.getLowestSC())))
                    .map(Player::getFaction)
                    .toList();
            if (!factions.isEmpty()) {
                groups.put(group, factions);
            }
        }
        return groups;
    }

    private static Map<String, List<String>> groupsBySeat(Game game, List<Player> players) {
        List<String> cardGroups = game.getSCList().stream()
                .map(MegagameGroupService::groupOfSc)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        if (cardGroups.isEmpty()) {
            return groups;
        }
        int membersPerGroup =
                Math.max(1, groupCards(game, cardGroups.getFirst()).size() - UNPICKED_CARDS_PER_GROUP);
        int groupCount =
                Math.min(cardGroups.size(), Math.max(1, (players.size() + membersPerGroup - 1) / membersPerGroup));
        for (int i = 0; i < players.size(); i++) {
            String group = cardGroups.get(i * groupCount / players.size());
            groups.computeIfAbsent(group, key -> new ArrayList<>())
                    .add(players.get(i).getFaction());
        }
        return groups;
    }

    public static String movePlayer(Game game, Player player, String targetGroup, Integer position) {
        if (!groups(game).contains(targetGroup)) {
            return label(targetGroup) + " is not in use; move players between the existing groups "
                    + String.join(", ", groups(game)) + ".";
        }
        String oldGroup = groupOf(game, player);
        if (targetGroup.equals(oldGroup)) {
            List<String> order = memberFactions(game, targetGroup);
            order.remove(player.getFaction());
            order.add(position == null ? order.size() : Math.clamp(position - 1, 0, order.size()), player.getFaction());
            writeMembers(game, targetGroup, order);
            return "";
        }
        String warning = "";
        if (oldGroup != null) {
            boolean wasSpeaker = player.equals(groupSpeaker(game, oldGroup));
            List<String> oldOrder = orderFromSpeaker(game, oldGroup).stream()
                    .map(Player::getFaction)
                    .toList();
            List<String> remaining = memberFactions(game, oldGroup);
            remaining.remove(player.getFaction());
            writeMembers(game, oldGroup, remaining);
            if (wasSpeaker && oldOrder.size() > 1) {
                Player next = game.getPlayerFromColorOrFaction(oldOrder.get(1));
                setGroupSpeaker(game, oldGroup, next);
                warning = label(oldGroup) + " speaker passed to " + next.getFaction() + ".";
            }
        }
        List<String> target = memberFactions(game, targetGroup);
        int index = position == null ? target.size() : Math.clamp(position - 1, 0, target.size());
        target.add(index, player.getFaction());
        writeMembers(game, targetGroup, target);
        if (groupSpeaker(game, targetGroup) == null) {
            setGroupSpeaker(game, targetGroup, player);
        }
        return warning;
    }

    public static void clearGroupState(Game game) {
        for (char letter : LETTERS.toCharArray()) {
            game.removeStoredValue(GROUP_KEY + letter);
            game.removeStoredValue(SPEAKER_KEY + letter);
            game.removeStoredValue(PICKER_KEY + letter);
        }
    }

    public static boolean isValidGroup(String group) {
        return group != null && group.length() == 1 && LETTERS.contains(group);
    }

    public static String summary(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("SC groups are **")
                .append(isActive(game) ? "enabled" : "disabled")
                .append("** (tag `")
                .append(SC_GROUPS_TAG)
                .append("` ")
                .append(hasTag(game) ? "set" : "not set")
                .append(game.isFowMode() ? ", fog game: not supported" : "")
                .append(").\n");
        for (String group : groups(game)) {
            Player speaker = groupSpeaker(game, group);
            Player picker = currentPicker(game, group);
            sb.append("**").append(label(group)).append("**");
            if (isLastGroup(game, group)) {
                sb.append(" (holds the game speaker)");
            }
            sb.append(": ");
            sb.append(members(game, group).stream()
                    .map(player -> (player.equals(speaker) ? "🗣️" : "") + player.getFactionEmoji())
                    .collect(Collectors.joining(" ")));
            if (picker != null && "strategy".equalsIgnoreCase(game.getPhaseOfGame())) {
                sb.append(" — picking: ").append(picker.getFactionEmoji());
            }
            sb.append("\n");
        }
        String warnings = MegagameWarnings.asText(game);
        if (!warnings.isEmpty()) {
            sb.append(warnings).append("\n");
        }
        return sb.toString();
    }

    private static void writeMembers(Game game, String group, List<String> factions) {
        game.setStoredValue(
                GROUP_KEY + group,
                factions.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining("_")));
    }
}
