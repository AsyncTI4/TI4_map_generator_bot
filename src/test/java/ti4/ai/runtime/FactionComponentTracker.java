package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.testUtils.discord.FakeChannel;
import ti4.testUtils.discord.FakeDiscord;
import ti4.testUtils.discord.FakeMessage;

// What each seat did with its faction's own components in one self-play game: abilities, leaders, faction
// technologies, faction units, its own promissory notes and its breakthrough. Four kinds of evidence are kept apart,
// because they mean different things when judging the AI:
// - offered: the bot posted a button for the component (its id is in the button's id or label);
// - pressed / decided: the AI pressed a button for it, or gave a reason that names it;
// - state: the game state changed in a way only a use explains (a leader exhausted, a technology exhausted, a note
//   in someone else's hand or play area, a flagship or mech placed);
// - message: a bot message without such a button names it (often the bot resolving something on its own).
// Research presses and menus for a faction technology are kept apart from its use, note give menus apart from its
// offers, and a component that only works passively (a commander, an ability with no timing window) is marked passive,
// since no press or state change can show it being used. A component in FOLLOW_THROUGH also counts how often its use
// was followed by the press that finishes it (Ggrocuto Rinn exhausting, then readying a planet).
final class FactionComponentTracker {

    // Every finished game's summary, read once at the end of a batch.
    static final ConcurrentLinkedQueue<Summary> BATCH = new ConcurrentLinkedQueue<>();

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[_|;:,.\\s]+");
    private static final int MIN_CONTAINED_ID = 6;
    private static final int MIN_NAME = 5;
    private static final int REASONS_KEPT = 6;

    // Handlers that never contain the component's id, by component id (lower case, matched as a whole id token).
    private static final Map<String, Set<String>> HANDLER_ALIASES = Map.of("naalubt", Set.of("mindsievefollow"));
    // The press that completes a use, by component id. It only counts while that component's use is open: the shared
    // khraskHeroStep4Ready_ handler is also posted by Reparations and other readying effects.
    private static final Map<String, String> FOLLOW_THROUGH = Map.of("xxchaagent", "khraskherostep4ready_");
    private static final List<String> RESEARCH_HANDLERS = List.of("gettech_", "entropicscar_", "acquireatech");
    private static final List<String> GIVE_MENUS = List.of("naaluherosend_", "send_pns_");

    record Component(String faction, String kind, String id, String name, boolean passive) {
        Component(String faction, String kind, String id, String name) {
            this(faction, kind, id, name, false);
        }

        String key() {
            return faction + "|" + kind + "|" + id;
        }

        // "Sh'val, Harbinger" is "Sh'val" in most of the AI's reasons.
        String shortName() {
            if (name == null) return "";
            int comma = name.indexOf(',');
            return comma < 0 ? name : name.substring(0, comma).trim();
        }
    }

    static final class Usage {
        final Component component;
        int offered;
        int pressed;
        int decided;
        int state;
        int messages;
        int researched;
        int researchOffers;
        int giveMenus;
        int followThrough;
        int firstUse = -1;
        final Set<Integer> rounds = new TreeSet<>();
        final List<String> reasons = new ArrayList<>();
        final Map<String, Integer> stateEvents = new TreeMap<>();

        Usage(Component component) {
            this.component = component;
        }

        boolean used() {
            return pressed > 0 || state > 0;
        }

        void use(int round) {
            rounds.add(round);
            if (firstUse < 0) firstUse = round;
        }
    }

    record Summary(String game, int finalRound, Map<String, Usage> usages, List<String> lines) {}

    private final Map<String, Usage> usages = new LinkedHashMap<>();
    private final Map<String, List<Component>> byFaction = new HashMap<>();
    private final Map<String, String> states = new HashMap<>();
    private final Set<String> openUses = new java.util.HashSet<>();
    private final Map<Long, Integer> seenMessages = new HashMap<>();
    private final Map<Long, Set<String>> offeredOnMessage = new HashMap<>();
    private final List<String> lines = new ArrayList<>();
    private int round;

    synchronized void observe(Game game, FakeDiscord discord) {
        if (game == null) return;
        round = game.getRound();
        for (Player seat : game.getRealPlayers()) {
            byFaction.computeIfAbsent(seat.getFaction(), faction -> catalog(seat));
            observeState(game, seat);
        }
        if (discord != null) observeMessages(discord);
    }

    synchronized void pressed(int round, String faction, String customId, String outcome) {
        if (!"PRESSED".equals(outcome)) return;
        String lower = customId.toLowerCase(Locale.ROOT);
        for (Component component : byFaction.getOrDefault(faction, List.of())) {
            followedThrough(round, component, lower, customId);
            if (!matchesId(component, customId)) continue;
            Usage usage = usage(component);
            if (isResearch(component, lower)) {
                usage.researched++;
                lines.add(String.format(
                        "r%d %s %s:%s researched %s", round, faction, component.kind(), component.id(), customId));
                continue;
            }
            usage.pressed++;
            usage.use(round);
            lines.add(String.format(
                    "r%d %s %s:%s pressed %s", round, faction, component.kind(), component.id(), customId));
        }
    }

    synchronized void decided(int round, String faction, String reason) {
        if (reason == null || reason.isBlank()) return;
        String lower = reason.toLowerCase(Locale.ROOT);
        for (Component component : byFaction.getOrDefault(faction, List.of())) {
            if (!mentions(component, lower) && !mentionsShortName(component, lower)) continue;
            Usage usage = usage(component);
            usage.decided++;
            if (usage.reasons.size() < REASONS_KEPT) usage.reasons.add("r" + round + ": " + reason);
        }
    }

    private void followedThrough(int round, Component component, String lowerId, String customId) {
        String trigger = FOLLOW_THROUGH.get(component.id());
        if (trigger == null || !lowerId.contains(trigger) || !openUses.remove(component.key())) return;
        usage(component).followThrough++;
        lines.add(String.format(
                "r%d %s %s:%s followed through with %s",
                round, component.faction(), component.kind(), component.id(), customId));
    }

    synchronized Summary summary(String game) {
        return new Summary(game, round, Map.copyOf(usages), List.copyOf(lines));
    }

    private List<Component> catalog(Player seat) {
        String faction = seat.getFaction();
        List<Component> components = new ArrayList<>();
        FactionModel model = Mapper.getFaction(faction);
        for (String id : seat.getAbilities()) {
            var ability = Mapper.getAbility(id);
            boolean passive = ability != null
                    && ability.getWindow().isEmpty()
                    && ability.getPermanentEffect().isPresent();
            components.add(new Component(faction, "ability", id, ability == null ? id : ability.getName(), passive));
        }
        for (String id : seat.getLeaderIDs()) {
            var leader = Mapper.getLeader(id);
            String type = leader == null ? "leader" : leader.getType();
            components.add(
                    new Component(faction, type, id, leader == null ? id : leader.getName(), "commander".equals(type)));
        }
        for (String id : seat.getFactionTechs()) {
            var tech = Mapper.getTech(id);
            components.add(new Component(faction, "tech", id, tech == null ? id : tech.getName()));
        }
        for (String id : seat.getUnitsOwned()) {
            UnitModel unit = Mapper.getUnit(id);
            if (unit == null || unit.getFaction().isEmpty()) continue;
            components.add(new Component(faction, "unit:" + unit.getBaseType(), id, unit.getName()));
        }
        List<String> notes =
                model == null || model.getPromissoryNotes() == null ? List.of() : model.getPromissoryNotes();
        for (String id : notes) {
            var note = Mapper.getPromissoryNote(id);
            components.add(new Component(faction, "note", id, note == null ? id : note.getName()));
        }
        for (String id : seat.getBreakthroughIDs()) {
            var breakthrough = Mapper.getBreakthrough(id);
            components.add(
                    new Component(faction, "breakthrough", id, breakthrough == null ? id : breakthrough.getName()));
        }
        components.forEach(this::usage);
        return components;
    }

    private void observeState(Game game, Player seat) {
        String faction = seat.getFaction();
        for (Component component : byFaction.get(faction)) {
            String now = stateOf(game, seat, component);
            if (now == null) continue;
            String before = states.put(component.key(), now);
            if (before == null || before.equals(now)) continue;
            stateChanged(component, before, now);
        }
    }

    private String stateOf(Game game, Player seat, Component component) {
        return switch (component.kind()) {
            case "agent", "commander", "hero", "envoy" -> leaderState(seat, component.id());
            case "tech" ->
                !seat.hasTech(component.id())
                        ? "missing"
                        : seat.getExhaustedTechs().contains(component.id()) ? "exhausted" : "ready";
            case "breakthrough" ->
                !seat.isBreakthroughUnlocked(component.id())
                        ? "locked"
                        : seat.isBreakthroughExhausted(component.id()) ? "exhausted" : "ready";
            case "note" -> noteState(game, seat, component.id());
            default -> component.kind().startsWith("unit:") ? unitsOnBoard(game, seat, component.id()) : null;
        };
    }

    private static String unitsOnBoard(Game game, Player seat, String unitId) {
        UnitModel unit = Mapper.getUnit(unitId);
        if (unit == null || unit.getUnitType() == null) return null;
        return String.valueOf(
                ButtonHelper.getNumberOfUnitsOnTheBoard(game, Units.getUnitKey(unit.getUnitType(), seat.getColor())));
    }

    // Flagships and mechs get a line per change; other faction units (infantry, fighters, carriers) change so often
    // that only their counts are kept.
    private static boolean logsEveryChange(Component component) {
        return !component.kind().startsWith("unit:")
                || "unit:flagship".equals(component.kind())
                || "unit:mech".equals(component.kind());
    }

    private static String leaderState(Player seat, String id) {
        Leader leader = seat.getLeader(id).orElse(null);
        if (leader == null) return "gone";
        if (leader.isLocked()) return "locked";
        if (leader.isActive()) return "active";
        return leader.isExhausted() ? "exhausted" : "ready";
    }

    private static String noteState(Game game, Player owner, String id) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPromissoryNotesInPlayArea().contains(id)) {
                return player == owner ? "own play area" : "in play by " + player.getFaction();
            }
            if (player != owner && player.getPromissoryNotes().containsKey(id)) return "held by " + player.getFaction();
        }
        return owner.getPromissoryNotes().containsKey(id) ? "owner's hand" : "elsewhere";
    }

    private void stateChanged(Component component, String before, String now) {
        Usage usage = usage(component);
        boolean use = isUse(component, before, now);
        String event = before + " -> " + now;
        if (component.kind().startsWith("unit:")) {
            event = Integer.parseInt(now) > Integer.parseInt(before)
                    ? "placed (" + event + ")"
                    : "lost (" + event + ")";
        }
        usage.stateEvents.merge(eventLabel(component, before, now), 1, Integer::sum);
        if (use) {
            usage.state++;
            usage.use(round);
            if (FOLLOW_THROUGH.containsKey(component.id())) openUses.add(component.key());
        }
        if ("ready".equals(now)) openUses.remove(component.key());
        if (!logsEveryChange(component)) return;
        lines.add(String.format(
                "r%d %s %s:%s %s%s",
                round, component.faction(), component.kind(), component.id(), event, use ? " [use]" : ""));
    }

    private static boolean isUse(Component component, String before, String now) {
        return switch (component.kind()) {
            case "agent", "commander", "hero", "envoy" ->
                "exhausted".equals(now) || "active".equals(now) || "gone".equals(now) && !"locked".equals(before);
            case "tech", "breakthrough" -> "exhausted".equals(now);
            case "note" -> now.startsWith("in play by") || now.startsWith("held by");
            default -> component.kind().startsWith("unit:") && Integer.parseInt(now) > Integer.parseInt(before);
        };
    }

    private static String eventLabel(Component component, String before, String now) {
        if (component.kind().startsWith("unit:")) {
            return Integer.parseInt(now) > Integer.parseInt(before) ? "placed" : "lost";
        }
        if (now.startsWith("held by")) return "given away";
        if (now.startsWith("in play by")) return "played by another seat";
        return before + " -> " + now;
    }

    private void observeMessages(FakeDiscord discord) {
        Map<String, List<Component>> seated = byFaction;
        for (FakeChannel channel : discord.channels()) {
            List<FakeMessage> messages = channel.messages();
            int from = seenMessages.getOrDefault(channel.id(), 0);
            for (int i = from; i < messages.size(); i++) observeMessage(messages.get(i), seated);
            seenMessages.put(channel.id(), messages.size());
        }
    }

    private void observeMessage(FakeMessage message, Map<String, List<Component>> seated) {
        List<Button> buttons = message.buttons();
        String content = message.content() == null ? "" : message.content().toLowerCase(Locale.ROOT);
        Set<String> offered = offeredOnMessage.computeIfAbsent(message.id(), id -> new java.util.HashSet<>());
        for (List<Component> components : seated.values()) {
            for (Component component : components) {
                boolean button = buttons.stream().anyMatch(candidate -> offers(component, candidate));
                if (button) {
                    if (offered.add(component.key())) countOffer(component, buttons);
                    continue;
                }
                if (buttons.isEmpty() && mentions(component, content)) usage(component).messages++;
            }
        }
    }

    private void countOffer(Component component, List<Button> buttons) {
        Usage usage = usage(component);
        List<String> ids = buttons.stream()
                .filter(button -> offers(component, button))
                .map(Button::getCustomId)
                .map(id -> id == null ? "" : id.toLowerCase(Locale.ROOT))
                .toList();
        if (ids.stream().allMatch(id -> isResearch(component, id))) {
            usage.researchOffers++;
        } else if (ids.stream().allMatch(id -> isGiveMenu(component, id))) {
            usage.giveMenus++;
        } else {
            usage.offered++;
        }
    }

    static boolean isResearch(Component component, String lowerId) {
        return "tech".equals(component.kind()) && RESEARCH_HANDLERS.stream().anyMatch(lowerId::contains);
    }

    static boolean isGiveMenu(Component component, String lowerId) {
        return "note".equals(component.kind()) && GIVE_MENUS.stream().anyMatch(lowerId::contains);
    }

    static boolean offers(Component component, Button button) {
        String id = button.getCustomId();
        if (id != null && id.startsWith("FFCC_") && !id.startsWith("FFCC_" + component.faction() + "_")) return false;
        if (id != null && matchesId(component, id)) return true;
        String label = button.getLabel();
        return label != null && mentions(component, label.toLowerCase(Locale.ROOT));
    }

    // A whole id token also matches the component id with its underscores removed (peaceAccords_ for peace_accords,
    // orbitalDrop for orbital_drop) or one of its handler aliases. Only whole tokens count, so a short squeezed id
    // cannot match inside an unrelated handler name.
    static boolean matchesId(Component component, String customId) {
        String id = component.id().toLowerCase(Locale.ROOT);
        String compact = id.replace("_", "");
        String lower = customId.toLowerCase(Locale.ROOT);
        if (id.length() >= MIN_CONTAINED_ID && lower.contains(id)) return true;
        Set<String> aliases = HANDLER_ALIASES.getOrDefault(id, Set.of());
        for (String token : TOKEN_SPLIT.split(lower)) {
            if (token.equals(id) || token.equals(compact) || aliases.contains(token)) return true;
        }
        return false;
    }

    private static boolean mentions(Component component, String lowerText) {
        String name = component.name() == null ? "" : component.name().toLowerCase(Locale.ROOT);
        return name.length() >= MIN_NAME && lowerText.contains(name);
    }

    private static boolean mentionsShortName(Component component, String lowerText) {
        String name = component.shortName().toLowerCase(Locale.ROOT);
        return name.length() >= MIN_NAME && lowerText.contains(name);
    }

    private Usage usage(Component component) {
        return usages.computeIfAbsent(component.key(), key -> new Usage(component));
    }

    // One block per faction over the whole batch: for every component, the games it was used in (an AI press or a
    // state change only a use explains) out of the games the faction was seated in, how often the bot offered it, and
    // the AI's own reasons.
    static void printBatch() {
        List<Summary> summaries = new ArrayList<>(BATCH);
        if (summaries.isEmpty()) return;
        Map<String, Integer> seatedGames = new TreeMap<>();
        Map<String, Map<String, List<Usage>>> byFaction = new TreeMap<>();
        for (Summary summary : summaries) {
            Set<String> factions = new TreeSet<>();
            for (Usage usage : summary.usages().values()) {
                factions.add(usage.component.faction());
                byFaction
                        .computeIfAbsent(usage.component.faction(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(usage.component.key(), ignored -> new ArrayList<>())
                        .add(usage);
            }
            factions.forEach(faction -> seatedGames.merge(faction, 1, Integer::sum));
        }
        System.out.println("FACTION COMPONENTS over " + summaries.size() + " games");
        for (Map.Entry<String, Map<String, List<Usage>>> faction : byFaction.entrySet()) {
            int games = seatedGames.getOrDefault(faction.getKey(), 0);
            System.out.println("FACTIONCOMP " + faction.getKey() + " seated in " + games + " games");
            faction.getValue().values().stream()
                    .sorted(Comparator.comparing(
                            list -> list.getFirst().component.kind()))
                    .forEach(list -> printComponent(list, games));
        }
    }

    private static void printComponent(List<Usage> games, int seated) {
        Component component = games.getFirst().component;
        long usedIn = games.stream().filter(Usage::used).count();
        long offeredIn = games.stream().filter(usage -> usage.offered > 0).count();
        int offers = games.stream().mapToInt(usage -> usage.offered).sum();
        int presses = games.stream().mapToInt(usage -> usage.pressed).sum();
        int decisions = games.stream().mapToInt(usage -> usage.decided).sum();
        int stateUses = games.stream().mapToInt(usage -> usage.state).sum();
        int messages = games.stream().mapToInt(usage -> usage.messages).sum();
        int researched = games.stream().mapToInt(usage -> usage.researched).sum();
        int researchOffers =
                games.stream().mapToInt(usage -> usage.researchOffers).sum();
        int giveMenus = games.stream().mapToInt(usage -> usage.giveMenus).sum();
        int followThrough =
                games.stream().mapToInt(usage -> usage.followThrough).sum();
        double firstUse = games.stream()
                .filter(usage -> usage.firstUse >= 0)
                .mapToInt(usage -> usage.firstUse)
                .average()
                .orElse(Double.NaN);
        Map<String, Integer> events = new TreeMap<>();
        games.forEach(usage -> usage.stateEvents.forEach((event, count) -> events.merge(event, count, Integer::sum)));
        Map<Integer, Integer> rounds = new TreeMap<>();
        games.forEach(usage -> usage.rounds.forEach(round -> rounds.merge(round, 1, Integer::sum)));
        System.out.printf(
                "FACTIONCOMP   %s %-14s %-34s used in %d/%d games, offered in %d (%d offers), AI presses %d, AI reasons %d,"
                        + " state uses %d, bot mentions %d, first use r%s, rounds used %s, state events %s, researched %d"
                        + " (research menus %d), give menus %d, follow-through %s, passive %s%n",
                component.faction(),
                component.kind(),
                component.name() + " (" + component.id() + ")",
                usedIn,
                seated,
                offeredIn,
                offers,
                presses,
                decisions,
                stateUses,
                messages,
                Double.isNaN(firstUse) ? "-" : String.format("%.1f", firstUse),
                rounds,
                events,
                researched,
                researchOffers,
                giveMenus,
                FOLLOW_THROUGH.containsKey(component.id()) ? followThrough + " of " + stateUses + " uses" : "-",
                component.passive() ? "yes" : "no");
        games.stream()
                .flatMap(usage -> usage.reasons.stream())
                .limit(REASONS_KEPT)
                .forEach(reason -> System.out.println("FACTIONCOMP       reason " + reason));
    }
}
