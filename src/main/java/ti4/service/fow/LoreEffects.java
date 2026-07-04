package ti4.service.fow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.model.TileModel;
import ti4.service.fow.LoreService.LoreEntry;
import ti4.service.fow.LoreService.RECEIVER;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.map.AddTileService;
import ti4.service.map.CustomHyperlaneService;

/**
 * Effect-processing engine for lore entries.
 * Handles parsing, applying, and validating "!"-prefixed effect lines in lore footer text.
 * To add a new effect, register a handler in the static block — no other code changes needed.
 *
 * An effect line may end in "?condition" tokens ({@code ?red}, {@code ?!red}, {@code ?faction:winnu},
 * {@code ?round:3-6}); all must hold for the receiving player or the line is skipped for them — see
 * {@link #conditionHolds}.
 *
 * A footer's {@code !choice} marker gates the whole entry behind an Accept/Reject button per recipient;
 * lines tagged {@code accept:}/{@code reject:} only fire for the matching pick. A {@code !roll NdM} marker
 * gates the entry behind a single "Roll" button instead; lines tagged with a bare numeric range like
 * {@code 2-10:} or a single value like {@code 5:} only fire when the rolled total lands in that range — see
 * {@link #resolveRollBranch}. An entry may use {@code !choice} or {@code !roll}, never both.
 *
 * Phase entries (target strategy/action/status/agenda with a PHASE_START/PHASE_END trigger) fire with no
 * triggering player and no target tile: map lines apply once via {@link #applyLoreEffectsMapOnly} and must
 * name colors and {@code @targets} explicitly ({@link #validatePhaseEntry} warns otherwise); player-stat
 * lines fan out to every real player.
 *
 * Known limitations (not yet fixed):
 * - !removeunit removes whatever exists if fewer units are present than requested — silent partial removal by design.
 * - !token with an unresolvable name falls back to the raw arg as a filename; a typo passes validation but may
 *   corrupt tile state at trigger time. validateEffects only checks that an arg is present.
 * - !vp with PERSISTANCE.ALWAYS: scorePublicObjective prevents a player from scoring the same PO twice, so
 *   repeat triggers for the same player are silently ignored. Use ONCE if only one VP grant is intended.
 * - LORECACHE (in LoreService) is a plain HashMap — not thread-safe under concurrent event handlers. Evicted via
 *   LoreService.evictGameLore when GameManager removes a game, so it no longer grows unbounded.
 * - getEffectLines regex split (?<=\s)(?=!) only splits on '!' preceded by whitespace; "!tg +2!fleet +1"
 *   (no space) is parsed as one malformed effect and silently fails.
 */
final class LoreEffects {

    record EffectResults(List<String> playerChanges, List<EffectDescription> mapChanges) {}

    /** @param unitColor the color a unit-placing/removing effect acted on, or null for effects that
     *                    aren't about a specific color's units (token, swap, etc). Lets callers tell a
     *                    foreign-color change from one to the triggering player's own units. */
    record EffectDescription(String text, boolean isMapChange, String tilePosition, String unitColor) {
        EffectDescription(String text, boolean isMapChange) {
            this(text, isMapChange, null, null);
        }

        EffectDescription(String text, boolean isMapChange, String tilePosition) {
            this(text, isMapChange, tilePosition, null);
        }
    }

    /** Effect verbs that mutate shared board state — these must apply exactly once per lore trigger,
     *  never repeated per recipient (see {@link #applyLoreEffectsPlayerOnly}). */
    private static final Set<String> MAP_CHANGE_VERBS = Set.of(
            "unit",
            "plastic",
            "token",
            "removeunit",
            "removetoken",
            "swap",
            "cc",
            "removecc",
            "clearunits",
            "settile",
            "rotatehyperlane",
            "sethyperlane");

    static EffectResults applyLoreEffects(Player player, Game game, LoreEntry lore, boolean isSystemLore) {
        return applyLoreEffects(player, game, lore, isSystemLore, null);
    }

    /**
     * @param branch which tagged effect lines to include: {@code "accept"`/`"reject"`, or {@code null} to
     *               apply only untagged (always-fire) lines — used for {@code !choice}-gated entries where
     *               the resolving player's pick determines which tagged lines run.
     */
    static EffectResults applyLoreEffects(
            Player player, Game game, LoreEntry lore, boolean isSystemLore, String branch) {
        return applyLoreEffects(player, game, lore, isSystemLore, branch, lore.getEffectLines());
    }

    /**
     * Applies only the player-stat effect lines (skips anything in {@link #MAP_CHANGE_VERBS}). Used to give
     * each additional ADJACENT/ALL recipient their own copy of the per-player reward without re-running
     * board-mutating effects, which must fire exactly once for the whole lore trigger.
     */
    static EffectResults applyLoreEffectsPlayerOnly(
            Player player, Game game, LoreEntry lore, boolean isSystemLore, String branch) {
        List<String> lines = lore.getEffectLines().stream()
                .filter(line -> {
                    ParsedEffect p = parseLine(line);
                    return p == null || !MAP_CHANGE_VERBS.contains(p.verb());
                })
                .toList();
        return applyLoreEffects(player, game, lore, isSystemLore, branch, lines);
    }

    /**
     * The complement of {@link #applyLoreEffectsPlayerOnly}: applies only the board-mutating lines. Used
     * by phase lore, which has no triggering player — map effects fire exactly once with a context player
     * whose identity shouldn't matter (validation requires phase entries to give explicit colors and
     * {@code @targets}), while player-stat lines fan out separately per recipient.
     */
    static EffectResults applyLoreEffectsMapOnly(
            Player player, Game game, LoreEntry lore, boolean isSystemLore, String branch) {
        List<String> lines = lore.getEffectLines().stream()
                .filter(line -> {
                    ParsedEffect p = parseLine(line);
                    return p != null && MAP_CHANGE_VERBS.contains(p.verb());
                })
                .toList();
        return applyLoreEffects(player, game, lore, isSystemLore, branch, lines);
    }

    private static EffectResults applyLoreEffects(
            Player player, Game game, LoreEntry lore, boolean isSystemLore, String branch, List<String> lines) {
        if (lines.isEmpty()) return new EffectResults(List.of(), List.of());

        // Use lore.target (the original system position or planet name) rather than the caller's
        // "position" — for planet lore, that position has already been converted to the planet's
        // *system* position by the time it reaches here, which would otherwise resolve the wrong tile.
        // The entry's target may carry a "#tag" disambiguator (see LoreService.splitTargetKey) when more
        // than one entry is tagged onto the same system/planet — strip it before resolving the tile, or
        // the lookup below would silently fail to find anything and every effect line would be a no-op.
        String base = LoreService.splitTargetKey(lore.target).base();
        Tile tile = isSystemLore ? game.getTileByPosition(base) : game.getTileFromPlanet(base);
        String holder =
                (!isSystemLore && tile != null && tile.getUnitHolders().containsKey(base)) ? base : Constants.SPACE;

        return applyEffectLines(player, game, tile, holder, lines, branch);
    }

    /**
     * Test-only entry point — bypasses the tile-from-position lookup so tests can
     * pass a pre-built Tile directly without needing valid lore target strings.
     * Returns all descriptions (player + map) combined for easy assertion.
     */
    static List<String> applyLoreEffectsForTest(
            Player player, Game game, LoreEntry lore, Tile tile, String holder, boolean isSystemLore) {
        return applyLoreEffectsForTest(player, game, lore, tile, holder, isSystemLore, null);
    }

    /** Same as above, but lets tests exercise {@code accept:}/{@code reject:} tagged effect lines. */
    static List<String> applyLoreEffectsForTest(
            Player player, Game game, LoreEntry lore, Tile tile, String holder, boolean isSystemLore, String branch) {
        EffectResults results = applyEffectLines(player, game, tile, holder, lore.getEffectLines(), branch);
        List<String> all = new ArrayList<>(results.playerChanges());
        results.mapChanges().forEach(d -> all.add(d.text()));
        return all;
    }

    /**
     * Best-effort validation of a lore entry's effect lines, run when lore is saved so the GM sees
     * problems immediately rather than at trigger time. Returns human-readable issues; empty if clean.
     */
    static List<String> validateEffects(LoreEntry lore, Game game) {
        List<String> problems = new ArrayList<>();
        for (String line : lore.getEffectLines()) {
            ParsedEffect p = parseLine(line);
            if (p == null) continue;
            String where = " (in `!" + line + "`)";
            if (!EFFECTS.containsKey(p.verb())) {
                problems.add("unknown effect `" + p.verb() + "`" + where);
                continue;
            }
            if (p.targetRef() != null && resolveTarget(game, p.targetRef()) == null) {
                problems.add("couldn't find target `@" + p.targetRef() + "`" + where);
            }
            if (p.branch() != null) {
                boolean isAcceptReject = "accept".equals(p.branch()) || "reject".equals(p.branch());
                if (isAcceptReject) {
                    if (!lore.isChoiceGated()) {
                        problems.add("`" + p.branch() + ":` tag has no effect without a `!choice` marker in the footer"
                                + where);
                    }
                } else if (!lore.isRollGated()) {
                    problems.add("`" + p.branch() + ":` tag has no effect without a `!roll NdM` marker in the footer"
                            + where);
                } else if (parseRoundBounds(p.branch()) == null) {
                    problems.add("invalid roll bin range `" + p.branch() + ":`" + where);
                }
            }
            for (String condition : p.conditions()) {
                String problem = validateCondition(condition);
                if (problem != null) problems.add(problem + where);
            }
            if (("addfogtile".equals(p.verb()) || "removefogtile".equals(p.verb())) && !game.isFowMode()) {
                problems.add("`" + p.verb() + "` only has an effect in Fog of War games" + where);
            }
            problems.addAll(validateOperands(p, where));
        }
        if (lore.isChoiceGated() && lore.isRollGated()) {
            problems.add("An entry can't use both `!choice` and `!roll` markers — pick one");
        }
        if (lore.isChoiceGated() || lore.isRollGated()) {
            String marker = lore.isChoiceGated() ? "!choice" : "!roll";
            if (lore.receiver == RECEIVER.WINNER || lore.receiver == RECEIVER.LOSER) {
                problems.add(
                        "`" + marker
                                + "` has no effect when the receiver is Battle winner/loser — that receiver already gates on the player's win/loss self-report");
            } else if (lore.receiver == RECEIVER.GM) {
                problems.add("`" + marker
                        + "` has no effect when the receiver is GM — GMs are never offered the choice or its reward");
            }
        }
        if (lore.isRollGated()) {
            int[] spec = lore.getRollSpec();
            if (spec[0] <= 0 || spec[1] <= 1) {
                problems.add("`!roll` needs a positive dice count and at least 2 sides, e.g. `!roll 2d10`");
            }
        }
        if (LoreService.isPhaseTarget(LoreService.splitTargetKey(lore.target).base())) {
            problems.addAll(validatePhaseEntry(lore));
        }
        return problems;
    }

    /** Effect verbs that act on the entry's own tile unless an {@code @target} redirects them. Phase
     *  entries have no tile of their own, so these lines silently no-op without an explicit target. */
    private static final Set<String> TILE_DEFAULT_VERBS = Set.of(
            "unit",
            "plastic",
            "token",
            "removeunit",
            "removetoken",
            "cc",
            "removecc",
            "clearunits",
            "addfogtile",
            "removefogtile");

    /**
     * Phase entries fire on phase transitions with no triggering player and no target tile, which breaks
     * two defaults every other lore entry can rely on: "the current player's color" and "the lore's own
     * system". These warnings catch lines that would silently no-op or act on an arbitrary player.
     */
    private static List<String> validatePhaseEntry(LoreEntry lore) {
        List<String> problems = new ArrayList<>();
        if (lore.receiver != RECEIVER.ALL && lore.receiver != RECEIVER.GM) {
            problems.add("phase lore has no single receiving player — receiver `" + lore.receiver.name
                    + "` is treated as All Players");
        }
        if (lore.persistance == LoreService.PERSISTANCE.ONCE_PER_PLAYER
                && !lore.isChoiceGated()
                && !lore.isRollGated()) {
            problems.add("`Once per player` behaves like `Once` for phase lore — there is no per-player delivery");
        }
        for (String line : lore.getEffectLines()) {
            ParsedEffect p = parseLine(line);
            if (p == null) continue;
            String where = " (in `!" + line + "`)";
            List<String> a = p.args();
            boolean colorImplicit =
                    switch (p.verb()) {
                        case "unit", "plastic", "removeunit" -> !a.isEmpty() && isSignedInt(a, 0);
                        case "cc", "removecc" -> a.isEmpty();
                        default -> false;
                    };
            if (colorImplicit) {
                problems.add("`" + p.verb() + "` needs an explicit color in phase lore — there is no triggering player"
                        + where);
            }
            if (TILE_DEFAULT_VERBS.contains(p.verb()) && p.targetRef() == null) {
                problems.add("`" + p.verb() + "` needs an `@target` in phase lore — there is no target system" + where);
            }
            if (lore.receiver == RECEIVER.GM && !MAP_CHANGE_VERBS.contains(p.verb())) {
                problems.add("player rewards are skipped for GM-receiver phase lore" + where);
            }
        }
        return problems;
    }

    private static EffectResults applyEffectLines(
            Player player, Game game, Tile defaultTile, String defaultHolder, List<String> lines, String branch) {
        List<String> playerChanges = new ArrayList<>();
        List<EffectDescription> mapChanges = new ArrayList<>();
        for (String line : lines) {
            try {
                EffectDescription desc = applyEffectLine(player, game, defaultTile, defaultHolder, line, branch);
                if (desc == null) continue;
                if (desc.isMapChange()) mapChanges.add(desc);
                else playerChanges.add(desc.text());
            } catch (Exception e) {
                BotLogger.warning("Lore effect failed to apply: '" + line + "'", e);
            }
        }
        return new EffectResults(playerChanges, mapChanges);
    }

    private static EffectDescription applyEffectLine(
            Player player, Game game, Tile defaultTile, String defaultHolder, String line, String branch) {
        ParsedEffect parsed = parseLine(line);
        if (parsed == null) return null;
        if (parsed.branch() != null && !parsed.branch().equals(branch)) return null;
        for (String condition : parsed.conditions()) {
            if (!conditionHolds(player, game, condition)) return null;
        }

        Tile tile = defaultTile;
        String holder = defaultHolder;
        if (parsed.targetRef() != null) {
            TargetRef ref = resolveTarget(game, parsed.targetRef());
            if (ref != null) {
                tile = ref.tile();
                holder = ref.holder();
            } else {
                BotLogger.warning("Lore effect: could not resolve target '@" + parsed.targetRef() + "'");
                return null;
            }
        }

        EffectHandler handler = EFFECTS.get(parsed.verb());
        if (handler == null) {
            BotLogger.warning("Unknown lore effect verb: " + parsed.verb());
            return null;
        }
        return handler.apply(
                new EffectContext(player, game, tile, holder, parsed.args().toArray(new String[0])));
    }

    /** Matches a "2-10:"/"5:"-style roll-bin branch tag at the start of a line (added by
     *  {@link LoreEntry#getEffectLines()}). */
    private static final Pattern BIN_BRANCH = Pattern.compile("^(\\d+(?:-\\d+)?):(.*)$", Pattern.DOTALL);

    /**
     * Tokenises a single effect line into verb, operands, an optional "@target", and an optional branch tag
     * (added by {@link LoreEntry#getEffectLines()}) — {@code "accept"}/{@code "reject"} for {@code !choice}
     * entries, or a bare numeric range like {@code "2-10"} for {@code !roll} entries; null if blank.
     */
    private static ParsedEffect parseLine(String line) {
        String branch = null;
        String body = line;
        if (line.regionMatches(true, 0, "accept:", 0, 7)) {
            branch = "accept";
            body = line.substring(7);
        } else if (line.regionMatches(true, 0, "reject:", 0, 7)) {
            branch = "reject";
            body = line.substring(7);
        } else {
            Matcher m = BIN_BRANCH.matcher(line);
            if (m.matches()) {
                branch = m.group(1);
                body = m.group(2);
            }
        }

        List<String> tokens = new ArrayList<>(Arrays.asList(body.split("\\s+")));
        tokens.removeIf(String::isEmpty);
        if (tokens.isEmpty()) return null;

        String verb = tokens.remove(0).toLowerCase();
        verb = VERB_ALIASES.getOrDefault(verb, verb);
        String targetRef = null;
        List<String> args = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        for (String token : tokens) {
            if (token.startsWith("@")) {
                targetRef = token.substring(1);
            } else if (token.startsWith("?") && token.length() > 1) {
                conditions.add(token.substring(1));
            } else {
                args.add(token);
            }
        }
        return new ParsedEffect(verb, args, targetRef, branch, conditions);
    }

    /** Short (max 4 char) aliases for effect verbs longer than that, so footer lines can be terser. Resolved
     *  to the canonical verb in {@link #parseLine} before anything else runs, so EFFECTS, MAP_CHANGE_VERBS,
     *  and validateOperands only ever need to know the canonical name. */
    private static final Map<String, String> VERB_ALIASES = Map.ofEntries(
            Map.entry("flt", "fleet"),
            Map.entry("tac", "tactic"),
            Map.entry("str", "strategy"),
            Map.entry("com", "comms"),
            Map.entry("tkn", "token"),
            Map.entry("runi", "removeunit"),
            Map.entry("rtkn", "removetoken"),
            Map.entry("rcc", "removecc"),
            Map.entry("clru", "clearunits"),
            Map.entry("rtec", "removetech"),
            Map.entry("afog", "addfogtile"),
            Map.entry("rfog", "removefogtile"),
            Map.entry("stl", "settile"),
            Map.entry("rhl", "rotatehyperlane"),
            Map.entry("shl", "sethyperlane"));

    private record ParsedEffect(
            String verb, List<String> args, String targetRef, String branch, List<String> conditions) {}

    /**
     * Evaluates one "?condition" token against the player receiving the effect. Supported forms:
     * {@code ?red} (player's color), {@code ?faction:winnu}, {@code ?round:3-6} (same range shorthand as the
     * entry-level Rounds field); a "!" right after the "?" negates ({@code ?!red}). All conditions on a line
     * must hold, or the line is skipped for that player — there is no if/else: "else" is just another line
     * with the negated condition. An unparseable round range fails closed (line skipped); save-time
     * validation flags it so the GM finds out before trigger time.
     */
    private static boolean conditionHolds(Player player, Game game, String condition) {
        boolean negated = condition.startsWith("!");
        String body = negated ? condition.substring(1) : condition;
        String lower = body.toLowerCase();
        boolean holds;
        if (lower.startsWith("faction:")) {
            holds = body.substring("faction:".length()).equalsIgnoreCase(player.getFaction());
        } else if (lower.startsWith("round:")) {
            int[] bounds = parseRoundBounds(body.substring("round:".length()));
            int round = game.getRound();
            holds = bounds != null && (bounds[0] <= 0 || round >= bounds[0]) && (bounds[1] <= 0 || round <= bounds[1]);
        } else {
            holds = body.equalsIgnoreCase(player.getColor());
        }
        return negated != holds;
    }

    /** Save-time syntax check for one "?condition" token; returns a problem description or null if fine. */
    private static String validateCondition(String condition) {
        String body = condition.startsWith("!") ? condition.substring(1) : condition;
        String lower = body.toLowerCase();
        if (lower.startsWith("faction:")) {
            String faction = lower.substring("faction:".length());
            if (!Mapper.isValidFaction(faction)) {
                return "unknown faction `" + faction + "` in condition `?" + condition + "`";
            }
        } else if (lower.startsWith("round:")) {
            if (parseRoundBounds(body.substring("round:".length())) == null) {
                return "invalid round range in condition `?" + condition + "` (e.g. `?round:3-6`, `?round:4`)";
            }
        } else if (!Mapper.isValidColor(lower)) {
            return "unknown color `" + body + "` in condition `?" + condition + "`";
        }
        return null;
    }

    /** Parses "N", "N-M", "N-", "-M" into {from, till} (0 = unbounded); null if malformed or backwards. */
    private static int[] parseRoundBounds(String range) {
        try {
            if (range.contains("-")) {
                String[] parts = range.split("-", 2);
                int from = parts[0].isBlank() ? 0 : Integer.parseInt(parts[0].trim());
                int till = parts[1].isBlank() ? 0 : Integer.parseInt(parts[1].trim());
                if (from > 0 && till > 0 && from > till) return null;
                return new int[] {from, till};
            }
            int round = Integer.parseInt(range.trim());
            return new int[] {round, round};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Finds which roll-bin branch tag ({@code "2-10"}, a bare {@code "5"}, etc.) the given dice total falls
     * inside, reusing the same "N"/"N-M" range parsing as {@code ?round:} conditions. The first matching
     * bin wins if a GM accidentally wrote overlapping ranges. Returns null if no bin covers the total — the
     * roll grants nothing beyond whatever untagged (always-fire) lines the entry has.
     */
    static String resolveRollBranch(List<String> effectLines, int total) {
        for (String line : effectLines) {
            ParsedEffect p = parseLine(line);
            if (p == null || p.branch() == null || "accept".equals(p.branch()) || "reject".equals(p.branch())) {
                continue;
            }
            int[] bounds = parseRoundBounds(p.branch());
            if (bounds != null && (bounds[0] <= 0 || total >= bounds[0]) && (bounds[1] <= 0 || total <= bounds[1])) {
                return p.branch();
            }
        }
        return null;
    }

    /** Resolves an "@" target to a tile + unit-holder; tries planet name first, then board position. */
    private static TargetRef resolveTarget(Game game, String ref) {
        Tile byPlanet = game.getTileFromPlanet(ref);
        if (byPlanet != null) {
            String holder = byPlanet.getUnitHolders().containsKey(ref) ? ref : Constants.SPACE;
            return new TargetRef(byPlanet, holder);
        }
        Tile byPosition = game.getTileByPosition(ref);
        if (byPosition != null) return new TargetRef(byPosition, Constants.SPACE);
        return null;
    }

    private record TargetRef(Tile tile, String holder) {}

    @FunctionalInterface
    private interface EffectHandler {
        /** Apply the effect and return a description of what changed, or null if nothing noteworthy. */
        EffectDescription apply(EffectContext ctx);
    }

    /** Context handed to every effect: the player who triggered it, the game, and the resolved board target. */
    private static final class EffectContext {
        final Player player;
        final Game game;
        final Tile tile;
        final String holder;
        final String[] args;

        EffectContext(Player player, Game game, Tile tile, String holder, String[] args) {
            this.player = player;
            this.game = game;
            this.tile = tile;
            this.holder = holder;
            this.args = args;
        }

        /** Operand at index i parsed as a signed int ("+2"/"-1"/"3"), or 0 if absent/blank. */
        int signed(int i) {
            if (args.length <= i || args[i].isEmpty()) return 0;
            return Integer.parseInt(args[i].replace("+", ""));
        }

        String arg(int i) {
            return args.length > i ? args[i] : null;
        }
    }

    // ---- Effect registry. To add an effect, register a handler here; no other code changes needed. ----
    private static final Map<String, EffectHandler> EFFECTS = new HashMap<>();

    static {
        register(
                ctx -> {
                    int n = ctx.signed(0);
                    ctx.player.gainTG(n);
                    return playerChange(ctx, n, "trade good");
                },
                "tg");
        register(
                ctx -> {
                    int n = ctx.signed(0);
                    ctx.player.setFleetCC(ctx.player.getFleetCC() + n);
                    return playerChange(ctx, n, "fleet CC");
                },
                "fleet");
        register(
                ctx -> {
                    int n = ctx.signed(0);
                    ctx.player.setTacticalCC(ctx.player.getTacticalCC() + n);
                    return playerChange(ctx, n, "tactic CC");
                },
                "tactic",
                "tactical");
        register(
                ctx -> {
                    int n = ctx.signed(0);
                    ctx.player.setStrategicCC(ctx.player.getStrategicCC() + n);
                    return playerChange(ctx, n, "strategy CC");
                },
                "strategy",
                "strategic");
        register(
                ctx -> {
                    int n = ctx.signed(0);
                    ctx.player.gainCommodities(n);
                    return playerChange(ctx, n, "commodity");
                },
                "comms",
                "commodities");
        register(LoreEffects::effectAc, "ac");
        register(LoreEffects::effectUnit, "unit", "plastic");
        register(LoreEffects::effectToken, "token");
        register(LoreEffects::effectRemoveUnit, "removeunit");
        register(LoreEffects::effectRemoveToken, "removetoken");
        register(LoreEffects::effectSwap, "swap");
        register(LoreEffects::effectAddCC, "cc");
        register(LoreEffects::effectRemoveCC, "removecc");
        register(LoreEffects::effectClearUnits, "clearunits");
        register(LoreEffects::effectVp, "vp");
        register(LoreEffects::effectSo, "so", "secretobjective");
        register(LoreEffects::effectTech, "tech");
        register(LoreEffects::effectRemoveTech, "removetech");
        register(LoreEffects::effectAddFogTile, "addfogtile");
        register(LoreEffects::effectRemoveFogTile, "removefogtile");
        register(LoreEffects::effectSetTile, "settile");
        register(LoreEffects::effectRotateHyperlane, "rotatehyperlane");
        register(LoreEffects::effectSetHyperlane, "sethyperlane");
    }

    /** FoW-aware player name for effect descriptions: color emoji + name in FoW so identity stays hidden. */
    private static String who(EffectContext ctx) {
        return ctx.game.isFowMode()
                ? ctx.player.getFactionEmojiOrColor()
                : ctx.player.getRepresentationUnfoggedNoPing();
    }

    private static EffectDescription playerChange(EffectContext ctx, int n, String resource) {
        if (n == 0) return null;
        String verb = n > 0 ? "gained" : "lost";
        int abs = Math.abs(n);
        String plural = (abs != 1 && "trade good".equals(resource)) ? "s" : "";
        return new EffectDescription(who(ctx) + " " + verb + " " + abs + " " + resource + plural + ".", false);
    }

    private static void register(EffectHandler handler, String... verbs) {
        for (String verb : verbs) EFFECTS.put(verb, handler);
    }

    // unit [neutral|<color>] <count> <unitType>  -> adds units in the given color (default: current player)
    private static EffectDescription effectUnit(EffectContext ctx) {
        if (ctx.tile == null || ctx.args.length < 2) return null;
        int idx = 0;
        String color = ctx.player.getColor();
        if (!isSignedInt(Arrays.asList(ctx.args), 0)) {
            color = resolveColorArg(ctx, ctx.arg(0));
            if (color == null) return null;
            idx = 1;
        }
        if (ctx.args.length < idx + 2) return null;
        int count = ctx.signed(idx);
        var key = Units.getUnitKey(ctx.arg(idx + 1), color);
        if (key == null || count <= 0) return null;

        // Optional planet argument: !unit [color] <count> <unit> [planet]
        String holder = ctx.holder;
        if (ctx.args.length >= idx + 3) {
            String planetArg = AliasHandler.resolvePlanet(ctx.arg(idx + 2));
            if (ctx.tile.getUnitHolders().containsKey(planetArg)) {
                holder = planetArg;
            }
        }

        ctx.tile.addUnit(holder, key, count);
        String location = ctx.tile.getPosition() + (Constants.SPACE.equals(holder) ? "" : " (" + holder + ")");
        return new EffectDescription(
                "Placed " + count + " " + color + " " + ctx.arg(idx + 1) + " in " + location + ".",
                true,
                ctx.tile.getPosition(),
                color);
    }

    // ac <count>  -> draws action cards; pure deck draw first, then best-effort notification to cards thread
    private static EffectDescription effectAc(EffectContext ctx) {
        int n = ctx.signed(0);
        if (n <= 0) return null;
        ctx.game.drawActionCard(ctx.player.getUserID(), n);
        try {
            ActionCardHelper.sendActionCardInfo(ctx.game, ctx.player);
        } catch (Exception e) {
            BotLogger.warning("AC info update failed during lore", e);
        }
        return new EffectDescription(who(ctx) + " drew " + StringHelper.pluralize(n, "action card") + ".", false);
    }

    // token <tokenId>  -> drops a token on the target holder (name resolved via Mapper, e.g. "gravityrift")
    private static EffectDescription effectToken(EffectContext ctx) {
        if (ctx.tile == null || ctx.args.length == 0) return null;
        String tokenId = Mapper.getTokenID(ctx.arg(0));
        if (tokenId == null) tokenId = ctx.arg(0);
        ctx.tile.addToken(tokenId, ctx.holder);
        String readableName = tokenId.replaceFirst("^token_", "").replaceFirst("\\.png$", "");
        String location = ctx.tile.getPosition() + (Constants.SPACE.equals(ctx.holder) ? "" : " (" + ctx.holder + ")");
        return new EffectDescription(
                "Placed " + readableName + " token in " + location + ".", true, ctx.tile.getPosition());
    }

    // removetoken <tokenId> [planet]  -> removes a token or planet attachment from the target holder
    private static EffectDescription effectRemoveToken(EffectContext ctx) {
        if (ctx.tile == null || ctx.args.length == 0) return null;
        String tokenId = Mapper.getTokenID(ctx.arg(0));
        if (tokenId == null) tokenId = ctx.arg(0);

        String holder = ctx.holder;
        if (ctx.args.length >= 2) {
            String planetArg = AliasHandler.resolvePlanet(ctx.arg(1));
            if (ctx.tile.getUnitHolders().containsKey(planetArg)) {
                holder = planetArg;
            }
        }

        String readableName = tokenId.replaceFirst("^token_", "").replaceFirst("\\.png$", "");
        String location = ctx.tile.getPosition() + (Constants.SPACE.equals(holder) ? "" : " (" + holder + ")");
        boolean removed = ctx.tile.removeToken(tokenId, holder);
        if (!removed) {
            return new EffectDescription(
                    "Token `" + readableName + "` not found in " + location + " — nothing removed.", false);
        }
        return new EffectDescription(
                "Removed " + readableName + " token from " + location + ".", true, ctx.tile.getPosition());
    }

    // removeunit [neutral|<color>] <count> <unitType> [planet]  -> removes units from the board
    private static EffectDescription effectRemoveUnit(EffectContext ctx) {
        if (ctx.tile == null || ctx.args.length < 2) return null;
        int idx = 0;
        String color = ctx.player.getColor();
        if (!isSignedInt(Arrays.asList(ctx.args), 0)) {
            color = resolveColorArg(ctx, ctx.arg(0));
            if (color == null) return null;
            idx = 1;
        }
        if (ctx.args.length < idx + 2) return null;
        int count = ctx.signed(idx);
        var key = Units.getUnitKey(ctx.arg(idx + 1), color);
        if (key == null || count <= 0) return null;

        String holder = ctx.holder;
        if (ctx.args.length >= idx + 3) {
            String planetArg = AliasHandler.resolvePlanet(ctx.arg(idx + 2));
            if (ctx.tile.getUnitHolders().containsKey(planetArg)) {
                holder = planetArg;
            }
        }

        ctx.tile.removeUnit(holder, key, count);
        String location = ctx.tile.getPosition() + (Constants.SPACE.equals(holder) ? "" : " (" + holder + ")");
        return new EffectDescription(
                "Removed " + count + " " + color + " " + ctx.arg(idx + 1) + " from " + location + ".",
                true,
                ctx.tile.getPosition(),
                color);
    }

    // cc [neutral|<color>]  -> places a command token in the target system (default: current player)
    private static EffectDescription effectAddCC(EffectContext ctx) {
        if (ctx.tile == null) return null;
        String color = ctx.args.length == 0 ? ctx.player.getColor() : resolveColorArg(ctx, ctx.arg(0));
        if (color == null || !Mapper.isValidColor(color)) return null;

        ctx.tile.addCC(Mapper.getCCID(color));
        return new EffectDescription(
                "Placed a " + color + " command token in " + ctx.tile.getPosition() + ".",
                true,
                ctx.tile.getPosition(),
                color);
    }

    // removecc [neutral|<color>]  -> removes a command token from the target system
    private static EffectDescription effectRemoveCC(EffectContext ctx) {
        if (ctx.tile == null) return null;
        String color = ctx.args.length == 0 ? ctx.player.getColor() : resolveColorArg(ctx, ctx.arg(0));
        if (color == null || !Mapper.isValidColor(color)) return null;

        ctx.tile.removeCC(Mapper.getCCID(color));
        return new EffectDescription(
                "Removed the " + color + " command token from " + ctx.tile.getPosition() + ".",
                true,
                ctx.tile.getPosition(),
                color);
    }

    // clearunits <neutral|color> [planet]  -> removes every unit of one color from a single holder
    private static EffectDescription effectClearUnits(EffectContext ctx) {
        if (ctx.tile == null || ctx.args.length == 0) return null;
        String color = resolveColorArg(ctx, ctx.arg(0));
        if (color == null) return null;

        String holder = ctx.holder;
        if (ctx.args.length >= 2) {
            String planetArg = AliasHandler.resolvePlanet(ctx.arg(1));
            if (ctx.tile.getUnitHolders().containsKey(planetArg)) {
                holder = planetArg;
            }
        }

        UnitHolder unitHolder = ctx.tile.getUnitHolders().get(holder);
        if (unitHolder == null) return null;
        unitHolder.removeAllUnits(color);

        String location = ctx.tile.getPosition() + (Constants.SPACE.equals(holder) ? "" : " (" + holder + ")");
        return new EffectDescription(
                "Cleared all " + color + " units from " + location + ".", true, ctx.tile.getPosition(), color);
    }

    /**
     * Resolves a color argument, expanding {@code neutral} to the game's neutral (Dicecord) player's
     * color. Returns null — after reporting the problem — if {@code neutral} was requested but no
     * neutral player is set up yet; without this, the lookup used to silently fall back to the
     * triggering player's own color and mutate their units instead of erroring.
     */
    private static String resolveColorArg(EffectContext ctx, String arg) {
        if ("neutral".equalsIgnoreCase(arg)) {
            Player neutral = ctx.game.getPlayer(Constants.dicecordId);
            if (neutral == null) {
                reportMissingNeutralPlayer(ctx);
                return null;
            }
            return neutral.getColor();
        }
        return arg.toLowerCase();
    }

    /**
     * Sends a short error when {@code neutral} was given as the color but the game has no neutral
     * (Dicecord) player set up.
     */
    private static void reportMissingNeutralPlayer(EffectContext ctx) {
        if (ctx.game.isFowMode()) {
            var playerChannel = ctx.player.getCorrectChannel();
            if (playerChannel != null) {
                MessageHelper.sendMessageToChannel(playerChannel, "❌ ERROR missing in action");
            }
            GMService.sendMessageToGMChannel(ctx.game, "❌ ERROR no neutral player is setup", true);
        } else {
            MessageHelper.sendMessageToChannel(ctx.game.getActionsChannel(), "❌ ERROR no neutral player is setup");
        }
    }

    // swap <pos1> <pos2>  -> swaps two systems on the board
    private static EffectDescription effectSwap(EffectContext ctx) {
        if (ctx.args.length < 2) return null;
        String pos1 = ctx.arg(0);
        String pos2 = ctx.arg(1);
        if (pos1.equals(pos2)) return null;
        Tile tile1 = ctx.game.getTileByPosition(pos1);
        Tile tile2 = ctx.game.getTileByPosition(pos2);
        if (tile1 == null || tile2 == null) return null;

        tile2.setPosition(pos1);
        ctx.game.setTile(tile2);
        tile1.setPosition(pos2);
        ctx.game.setTile(tile1);

        CustomHyperlaneService.moveCustomHyperlaneData(pos1, pos2, ctx.game, true);
        ctx.game.rebuildTilePositionAutoCompleteList();
        RiftSetModeService.swappedSystems(ctx.game);

        return new EffectDescription("Swapped systems at " + pos1 + " and " + pos2 + ".", true, pos2);
    }

    // vp <count> [label words...]  -> grants count VPs to the player under a named custom objective
    private static EffectDescription effectVp(EffectContext ctx) {
        if (ctx.args.length == 0) return null;
        int count = ctx.signed(0);
        if (count == 0) return null;
        String label = ctx.args.length > 1
                ? String.join(" ", Arrays.copyOfRange(ctx.args, 1, ctx.args.length))
                : "Lore Reward";
        // Reuse an existing PO with the same label so multiple triggers share one scoreboard entry
        Integer poIndex = ctx.game.getRevealedPublicObjectives().get(label);
        if (poIndex == null) {
            poIndex = ctx.game.addCustomPO(label, count);
        }
        ctx.game.scorePublicObjective(ctx.player.getUserID(), poIndex);
        return new EffectDescription(
                (count > 0 ? "Gained " : "Lost ") + Math.abs(count) + " VP (" + label + ").", false);
    }

    // so [count]  -> draws secret objectives (default 1); pure deck draw first, then best-effort notification
    private static EffectDescription effectSo(EffectContext ctx) {
        int n = ctx.args.length == 0 ? 1 : ctx.signed(0);
        if (n <= 0) return null;
        for (int i = 0; i < n; i++) {
            ctx.game.drawSecretObjective(ctx.player.getUserID());
        }
        try {
            SecretObjectiveInfoService.sendSecretObjectiveInfo(ctx.game, ctx.player);
        } catch (Exception e) {
            BotLogger.warning("SO info update failed during lore", e);
        }
        return new EffectDescription(who(ctx) + " drew " + StringHelper.pluralize(n, "secret objective") + ".", false);
    }

    /** Maps the color-word filters GMs know ("blue" tech) onto TechnologyType names; type names pass through. */
    private static String techTypeFilter(String word) {
        if (word == null) return null;
        return switch (word.toLowerCase()) {
            case "blue" -> "propulsion";
            case "green" -> "biotic";
            case "yellow" -> "cybernetic";
            case "red" -> "warfare";
            case "unit" -> "unitupgrade";
            default -> word.toLowerCase();
        };
    }

    // tech <techID|random> [blue|green|yellow|red|unit]  -> grants a technology (random draws from the
    // game's own technology deck, so homebrew deck settings are respected automatically)
    private static EffectDescription effectTech(EffectContext ctx) {
        if (ctx.args.length == 0) return null;
        String techID = AliasHandler.resolveTech(ctx.arg(0).toLowerCase());
        if ("random".equalsIgnoreCase(techID)) {
            String typeFilter = techTypeFilter(ctx.arg(1));
            List<String> pool = ctx.game.getTechnologyDeck().stream()
                    .filter(id -> !ctx.player.getTechs().contains(id))
                    .filter(id -> {
                        TechnologyModel model = Mapper.getTech(id);
                        if (model == null) return false;
                        // Faction techs are only drawable by their own faction
                        if (model.getFaction().isPresent()
                                && !model.getFaction().get().equalsIgnoreCase(ctx.player.getFaction())) {
                            return false;
                        }
                        return typeFilter == null || model.isType(typeFilter);
                    })
                    .toList();
            if (pool.isEmpty()) return null;
            techID = RandomHelper.pickRandomFromList(pool);
        } else if (Mapper.getTech(techID) == null || ctx.player.getTechs().contains(techID)) {
            return null;
        }

        ctx.player.addTech(techID);
        return new EffectDescription(
                who(ctx) + " gained the technology _" + Mapper.getTech(techID).getName() + "_.", false);
    }

    // removetech <techID>  -> removes a technology the player has researched; no-op if they don't have it
    private static EffectDescription effectRemoveTech(EffectContext ctx) {
        if (ctx.args.length == 0) return null;
        String techID = AliasHandler.resolveTech(ctx.arg(0).toLowerCase());
        if (Mapper.getTech(techID) == null || !ctx.player.getTechs().contains(techID)) return null;

        ctx.player.removeTech(techID);
        return new EffectDescription(
                who(ctx) + " lost the technology _" + Mapper.getTech(techID).getName() + "_.", false);
    }

    // addfogtile <tileId> [label words...]  -> plants what THIS player believes sits at the target position
    // when it's fogged (their fowSeenTiles override). Per-player state, so ADJACENT/ALL recipients each get
    // their own copy via the normal player-effect fan-out. Deliberately vague description: naming the
    // planted tile in the delivery message would spoil whether the sighting is genuine.
    private static EffectDescription effectAddFogTile(EffectContext ctx) {
        if (!ctx.game.isFowMode() || ctx.tile == null || ctx.args.length == 0) return null;
        String tileId = AliasHandler.resolveTile(ctx.arg(0).toLowerCase());
        if (!TileHelper.isValidTile(tileId)) return null;

        String label = ctx.args.length > 1 ? String.join(" ", Arrays.copyOfRange(ctx.args, 1, ctx.args.length)) : "";
        ctx.player.addFogTile(tileId, ctx.tile.getPosition(), label);
        return new EffectDescription(who(ctx) + "'s fog view of " + ctx.tile.getPosition() + " was updated.", false);
    }

    // removefogtile  -> wipes the player's remembered view of the target position back to blank fog
    private static EffectDescription effectRemoveFogTile(EffectContext ctx) {
        if (!ctx.game.isFowMode() || ctx.tile == null) return null;
        ctx.player.removeFogTile(ctx.tile.getPosition());
        return new EffectDescription(who(ctx) + "'s fog view of " + ctx.tile.getPosition() + " was wiped.", false);
    }

    private static final Set<String> SETTILE_FILTERS = Set.of("blue", "red", "wormhole", "anomaly", "empty");

    // settile <position> <tileId|random> [blue|red|wormhole|anomaly|empty]...  -> places (or replaces) the
    // system at a board position. Position is a plain argument (not @target) because it may currently be
    // empty. Random draws honor the game's enabled tile sources and exclude tiles already on the board
    // (except duplicate-able empty reds), reusing the /map add_tile_random pool logic.
    private static EffectDescription effectSetTile(EffectContext ctx) {
        if (ctx.args.length < 2) return null;
        String position = ctx.arg(0);
        if (!PositionMapper.isTilePositionValid(position)) return null;

        String tileId;
        if ("random".equalsIgnoreCase(ctx.arg(1))) {
            List<String> filters = new ArrayList<>();
            for (int i = 2; i < ctx.args.length; i++) filters.add(ctx.arg(i).toLowerCase());
            tileId = pickRandomTileId(ctx.game, filters);
            if (tileId == null) return null;
        } else {
            tileId = AliasHandler.resolveTile(ctx.arg(1).toLowerCase());
            if (!TileHelper.isValidTile(tileId)) return null;
        }

        // addTile removes any existing tile at the position first, which runs the cleanup that clears
        // planet ownership and custom hyperlane data — a raw setTile overwrite would leave those stale.
        AddTileService.addTile(ctx.game, new Tile(tileId, position));
        ctx.game.rebuildTilePositionAutoCompleteList();

        String tileName = TileHelper.getTileById(tileId).getNameNullSafe();
        return new EffectDescription(
                "A new system appeared at " + position + ": " + tileName + " (" + tileId + ").", true, position);
    }

    private static String pickRandomTileId(Game game, List<String> filters) {
        Set<ComponentSource> sources = AddTileService.getSources(game, false);
        Set<TileModel> onBoard = game.getTileMap().values().stream()
                .map(Tile::getTileModel)
                .filter(model -> model != null)
                .collect(Collectors.toSet());

        List<TileModel> pool = new ArrayList<>();
        if (!filters.contains("red")) {
            pool.addAll(AddTileService.availableTiles(sources, AddTileService.RandomOption.B, onBoard, List.of()));
        }
        if (!filters.contains("blue")) {
            pool.addAll(AddTileService.availableTiles(sources, AddTileService.RandomOption.R, onBoard, List.of()));
        }

        List<TileModel> filtered = pool.stream()
                .filter(model -> !filters.contains("wormhole")
                        || (model.getWormholes() != null
                                && !model.getWormholes().isEmpty()))
                .filter(model -> !filters.contains("anomaly") || model.isAnomaly())
                .filter(model -> !filters.contains("empty") || model.isEmpty())
                .toList();
        if (filtered.isEmpty()) return null;
        return RandomHelper.pickRandomFromList(filtered).getId();
    }

    // rotatehyperlane <position> [steps]  -> rotates a custom hyperlane's connection matrix by steps * 60°
    // (negative = counter-rotate). Only touches positions that currently hold a custom hyperlane tile with
    // data, so it can't orphan hyperlane data onto a normal system.
    private static EffectDescription effectRotateHyperlane(EffectContext ctx) {
        if (ctx.args.length == 0) return null;
        String position = ctx.arg(0);
        Tile tile = ctx.game.getTileByPosition(position);
        String matrix = ctx.game.getCustomHyperlaneData().get(position);
        if (tile == null || !CustomHyperlaneService.isCustomHyperlaneTile(tile) || matrix == null) return null;

        int steps = ctx.args.length > 1 ? ctx.signed(1) : 1;
        int normalized = ((steps % 6) + 6) % 6;
        if (normalized == 0) return null;

        for (int i = 0; i < normalized; i++) {
            matrix = CustomHyperlaneService.rotateMatrix60(matrix);
        }
        CustomHyperlaneService.insertData(ctx.game, position, matrix);
        return new EffectDescription(
                "The hyperlane at " + position + " rotated by " + (normalized * 60) + "°.", true, position);
    }

    // sethyperlane <position> <encodedMatrix>  -> overwrites a custom hyperlane's 6x6 connection matrix.
    // Takes the encoded form (9 hex chars, exactly what the hyperlane manager's Export button prints) —
    // the raw ';'-separated matrix can't be used here because LoreService.clean strips ';' from footers.
    private static EffectDescription effectSetHyperlane(EffectContext ctx) {
        if (ctx.args.length < 2) return null;
        String position = ctx.arg(0);
        Tile tile = ctx.game.getTileByPosition(position);
        String matrix = decodeHyperlaneMatrixArg(ctx.arg(1));
        if (tile == null || !CustomHyperlaneService.isCustomHyperlaneTile(tile) || matrix == null) return null;

        CustomHyperlaneService.insertData(ctx.game, position, matrix);
        return new EffectDescription("The hyperlane at " + position + " reconfigured.", true, position);
    }

    /** Decodes a footer-safe encoded hyperlane matrix (9 hex chars or legacy 36-bit binary); null if invalid. */
    private static String decodeHyperlaneMatrixArg(String arg) {
        try {
            String matrix = CustomHyperlaneService.decodeMatrix(arg.trim());
            return CustomHyperlaneService.isValidConnectionMatrix(matrix) ? matrix : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> validateOperands(ParsedEffect p, String where) {
        List<String> problems = new ArrayList<>();
        List<String> a = p.args();
        switch (p.verb()) {
            case "tg", "fleet", "tactic", "tactical", "strategy", "strategic", "comms", "commodities" -> {
                if (!isSignedInt(a, 0)) {
                    problems.add("`" + p.verb() + "` needs a number, e.g. `!" + p.verb() + " +2`" + where);
                }
            }
            case "ac" -> {
                if (!isSignedInt(a, 0)
                        || (isSignedInt(a, 0) && Integer.parseInt(a.get(0).replace("+", "")) <= 0)) {
                    problems.add("`ac` needs a positive number, e.g. `!ac 2`" + where);
                }
            }
            case "unit", "plastic" -> {
                int idx = (!a.isEmpty() && !isSignedInt(a, 0)) ? 1 : 0;
                if (a.size() < idx + 2) {
                    problems.add("`" + p.verb() + "` needs `[neutral|<color>] <count> <unit> [planet]`, e.g. `!"
                            + p.verb() + " 2 infantry` or `!" + p.verb() + " 2 infantry mr`"
                            + where);
                } else {
                    if (!isSignedInt(a, idx)) {
                        problems.add("`" + p.verb() + "` count `" + a.get(idx) + "` isn't a number" + where);
                    }
                    if (Units.findUnitType(a.get(idx + 1)) == null) {
                        problems.add("unknown unit `" + a.get(idx + 1) + "`" + where);
                    }
                }
            }
            case "token" -> {
                if (a.isEmpty()) problems.add("`token` needs a token id, e.g. `!token gravityrift`" + where);
            }
            case "removetoken" -> {
                if (a.isEmpty())
                    problems.add("`removetoken` needs a token id, e.g. `!removetoken gravityrift`" + where);
            }
            case "removeunit" -> {
                int idx = (!a.isEmpty() && !isSignedInt(a, 0)) ? 1 : 0;
                if (a.size() < idx + 2) {
                    problems.add(
                            "`removeunit` needs `[neutral|<color>] <count> <unit> [planet]`, e.g. `!removeunit 2 infantry` or `!removeunit 2 infantry mr`"
                                    + where);
                } else {
                    if (!isSignedInt(a, idx)) {
                        problems.add("`removeunit` count `" + a.get(idx) + "` isn't a number" + where);
                    }
                    if (Units.findUnitType(a.get(idx + 1)) == null) {
                        problems.add("unknown unit `" + a.get(idx + 1) + "`" + where);
                    }
                }
            }
            case "swap" -> {
                if (a.size() < 2) {
                    problems.add("`swap` needs two positions, e.g. `!swap 203 401`" + where);
                } else if (a.get(0).equals(a.get(1))) {
                    problems.add("`swap` positions must be different" + where);
                }
            }
            case "clearunits" -> {
                if (a.isEmpty()) {
                    problems.add(
                            "`clearunits` needs `<neutral|color> [planet]`, e.g. `!clearunits neutral` or `!clearunits red mr`"
                                    + where);
                }
            }
            case "tech" -> {
                if (a.isEmpty()) {
                    problems.add("`tech` needs a tech id or `random`, e.g. `!tech gd` or `!tech random blue`" + where);
                } else if ("random".equalsIgnoreCase(a.get(0))) {
                    if (a.size() > 1 && !isKnownTechType(a.get(1))) {
                        problems.add("unknown tech type `" + a.get(1) + "` — use blue/green/yellow/red/unit" + where);
                    }
                } else if (Mapper.getTech(AliasHandler.resolveTech(a.get(0).toLowerCase())) == null) {
                    problems.add("unknown tech `" + a.get(0) + "`" + where);
                }
            }
            case "removetech" -> {
                if (a.isEmpty()) {
                    problems.add("`removetech` needs a tech id, e.g. `!removetech gd`" + where);
                } else if (Mapper.getTech(AliasHandler.resolveTech(a.get(0).toLowerCase())) == null) {
                    problems.add("unknown tech `" + a.get(0) + "`" + where);
                }
            }
            case "addfogtile" -> {
                if (a.isEmpty()) {
                    problems.add("`addfogtile` needs a tile id, e.g. `!addfogtile 41 Decoy @305`" + where);
                } else if (!TileHelper.isValidTile(
                        AliasHandler.resolveTile(a.get(0).toLowerCase()))) {
                    problems.add("unknown tile `" + a.get(0) + "`" + where);
                }
            }
            case "removefogtile" -> {
                /* no operands: acts on the lore's own tile or the @target override */
            }
            case "settile" -> {
                if (a.size() < 2) {
                    problems.add(
                            "`settile` needs `<position> <tileId|random> [filters]`, e.g. `!settile 305 41` or `!settile 305 random red wormhole`"
                                    + where);
                } else {
                    if (!PositionMapper.isTilePositionValid(a.get(0))) {
                        problems.add("invalid position `" + a.get(0) + "`" + where);
                    }
                    if ("random".equalsIgnoreCase(a.get(1))) {
                        for (String filter : a.subList(2, a.size())) {
                            if (!SETTILE_FILTERS.contains(filter.toLowerCase())) {
                                problems.add("unknown `settile` filter `" + filter
                                        + "` — use blue/red/wormhole/anomaly/empty" + where);
                            }
                        }
                    } else if (!TileHelper.isValidTile(
                            AliasHandler.resolveTile(a.get(1).toLowerCase()))) {
                        problems.add("unknown tile `" + a.get(1) + "`" + where);
                    }
                }
            }
            case "rotatehyperlane" -> {
                if (a.isEmpty()) {
                    problems.add("`rotatehyperlane` needs a position, e.g. `!rotatehyperlane 305 2`" + where);
                } else {
                    if (!PositionMapper.isTilePositionValid(a.get(0))) {
                        problems.add("invalid position `" + a.get(0) + "`" + where);
                    }
                    if (a.size() > 1 && !isSignedInt(a, 1)) {
                        problems.add("`rotatehyperlane` steps `" + a.get(1) + "` isn't a number" + where);
                    }
                }
            }
            case "sethyperlane" -> {
                if (a.size() < 2) {
                    problems.add(
                            "`sethyperlane` needs `<position> <encodedMatrix>` — use the 9-hex-char form from the hyperlane manager's Export button"
                                    + where);
                } else {
                    if (!PositionMapper.isTilePositionValid(a.get(0))) {
                        problems.add("invalid position `" + a.get(0) + "`" + where);
                    }
                    if (decodeHyperlaneMatrixArg(a.get(1)) == null) {
                        problems.add("invalid encoded matrix `" + a.get(1)
                                + "` — use the 9-hex-char form from the hyperlane manager's Export button" + where);
                    }
                }
            }
            case "vp" -> {
                if (!isSignedInt(a, 0) || Integer.parseInt(a.get(0).replace("+", "")) == 0) {
                    problems.add("`vp` needs a non-zero number, e.g. `!vp 1 Ancient Relic`" + where);
                }
            }
            case "so", "secretobjective" -> {
                if (!a.isEmpty()
                        && (!isSignedInt(a, 0) || Integer.parseInt(a.get(0).replace("+", "")) <= 0)) {
                    problems.add("`" + p.verb() + "` needs a positive number, e.g. `!" + p.verb()
                            + " 2`, or no args for 1" + where);
                }
            }
            default -> {
                /* registered effect without an operand schema: verb + target checks already ran */
            }
        }
        return problems;
    }

    private static boolean isKnownTechType(String word) {
        return switch (techTypeFilter(word)) {
            case "propulsion", "biotic", "cybernetic", "warfare", "unitupgrade" -> true;
            default -> false;
        };
    }

    private static boolean isSignedInt(List<String> args, int i) {
        if (args.size() <= i) return false;
        try {
            Integer.parseInt(args.get(i).replace("+", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
