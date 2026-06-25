package ti4.service.fow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.service.fow.LoreService.LoreEntry;
import ti4.service.fow.LoreService.RECEIVER;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.map.CustomHyperlaneService;

/**
 * Effect-processing engine for lore entries.
 * Handles parsing, applying, and validating "!"-prefixed effect lines in lore footer text.
 * To add a new effect, register a handler in the static block — no other code changes needed.
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

    record EffectDescription(String text, boolean isMapChange, String tilePosition) {
        EffectDescription(String text, boolean isMapChange) {
            this(text, isMapChange, null);
        }
    }

    /** Effect verbs that mutate shared board state — these must apply exactly once per lore trigger,
     *  never repeated per recipient (see {@link #applyLoreEffectsPlayerOnly}). */
    private static final Set<String> MAP_CHANGE_VERBS =
            Set.of("unit", "plastic", "token", "removeunit", "removetoken", "swap");

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

    private static EffectResults applyLoreEffects(
            Player player, Game game, LoreEntry lore, boolean isSystemLore, String branch, List<String> lines) {
        if (lines.isEmpty()) return new EffectResults(List.of(), List.of());

        // Use lore.target (the original system position or planet name) rather than the caller's
        // "position" — for planet lore, that position has already been converted to the planet's
        // *system* position by the time it reaches here, which would otherwise resolve the wrong tile.
        Tile tile = isSystemLore ? game.getTileByPosition(lore.target) : game.getTileFromPlanet(lore.target);
        String holder = (!isSystemLore && tile != null && tile.getUnitHolders().containsKey(lore.target))
                ? lore.target
                : Constants.SPACE;

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
            if (p.branch() != null && !lore.isChoiceGated()) {
                problems.add(
                        "`" + p.branch() + ":` tag has no effect without a `!choice` marker in the footer" + where);
            }
            problems.addAll(validateOperands(p, where));
        }
        if (lore.isChoiceGated()) {
            if (lore.receiver == RECEIVER.WINNER || lore.receiver == RECEIVER.LOSER) {
                problems.add(
                        "`!choice` has no effect when the receiver is Battle winner/loser — that receiver already gates on the player's win/loss self-report");
            } else if (lore.receiver == RECEIVER.GM) {
                problems.add(
                        "`!choice` has no effect when the receiver is GM — GMs are never offered the choice or its reward");
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

    /**
     * Tokenises a single effect line into verb, operands, an optional "@target", and an optional
     * "accept:"/"reject:" branch tag (added by {@link LoreEntry#getEffectLines()}); null if blank.
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
        }

        List<String> tokens = new ArrayList<>(Arrays.asList(body.split("\\s+")));
        tokens.removeIf(String::isEmpty);
        if (tokens.isEmpty()) return null;

        String verb = tokens.remove(0).toLowerCase();
        String targetRef = null;
        List<String> args = new ArrayList<>();
        for (String token : tokens) {
            if (token.startsWith("@")) {
                targetRef = token.substring(1);
            } else {
                args.add(token);
            }
        }
        return new ParsedEffect(verb, args, targetRef, branch);
    }

    private record ParsedEffect(String verb, List<String> args, String targetRef, String branch) {}

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
        register(LoreEffects::effectVp, "vp");
        register(LoreEffects::effectSo, "so", "secretobjective");
    }

    private static EffectDescription playerChange(EffectContext ctx, int n, String resource) {
        if (n == 0) return null;
        String verb = n > 0 ? "gained" : "lost";
        int abs = Math.abs(n);
        String plural = (abs != 1 && "trade good".equals(resource)) ? "s" : "";
        // In FoW use color emoji + name so the player's identity stays hidden from non-sheet-viewers
        String who = ctx.game.isFowMode()
                ? ctx.player.getFactionEmojiOrColor()
                : ctx.player.getRepresentationUnfoggedNoPing();
        return new EffectDescription(who + " " + verb + " " + abs + " " + resource + plural + ".", false);
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
            if ("neutral".equalsIgnoreCase(ctx.arg(0))) {
                Player neutral = ctx.game.getPlayer(Constants.dicecordId);
                if (neutral != null) color = neutral.getColor();
            } else {
                color = ctx.arg(0).toLowerCase();
            }
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
                ctx.tile.getPosition());
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
        String who = ctx.game.isFowMode()
                ? ctx.player.getFactionEmojiOrColor()
                : ctx.player.getRepresentationUnfoggedNoPing();
        return new EffectDescription(who + " drew " + StringHelper.pluralize(n, "action card") + ".", false);
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
            if ("neutral".equalsIgnoreCase(ctx.arg(0))) {
                Player neutral = ctx.game.getPlayer(Constants.dicecordId);
                if (neutral != null) color = neutral.getColor();
            } else {
                color = ctx.arg(0).toLowerCase();
            }
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
                ctx.tile.getPosition());
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
        String who = ctx.game.isFowMode()
                ? ctx.player.getFactionEmojiOrColor()
                : ctx.player.getRepresentationUnfoggedNoPing();
        return new EffectDescription(who + " drew " + StringHelper.pluralize(n, "secret objective") + ".", false);
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
