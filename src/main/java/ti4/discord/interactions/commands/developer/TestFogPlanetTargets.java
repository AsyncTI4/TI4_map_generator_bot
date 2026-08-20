package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.fow.BlindSelectionService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;

/**
 * TEMPORARY live-testing harness for fog-of-war planet targeting. Reaching the interesting states by playing
 * the game is impractical: it needs a fog game, the right card in hand, an agenda phase with an Elect Planet
 * agenda, specific factions, and above all a <i>specific fog memory</i> - a system scouted earlier but not
 * currently visible - which cannot be arranged on demand.
 *
 * <p>Every action button carries the <b>real production custom ID</b>, so pressing one dispatches through the
 * normal button router into the real handler rather than a copy of its logic. Nothing here reimplements the
 * behaviour under test.
 *
 * <p>Three kinds of button:
 * <ul>
 *   <li><b>Entry points</b> - open a card's real target list, so you can eyeball what fog offers.
 *   <li><b>Fizzle probes</b> - jump straight to a card's resolution step with a deliberately bad target.
 *       Each must produce a line from the shared pool and leave nothing in the error log.
 *   <li><b>Self-check</b> - runs every card's resolve() guard in-process and prints a pass/fail table, so the
 *       guards can be verified without clicking thirty buttons.
 * </ul>
 *
 * <p>To remove: delete this class and its entry in {@link DeveloperCommand}. Nothing else references it.
 */
class TestFogPlanetTargets extends GameStateSubcommand {

    private static final String PREFIX = "devFogPT_";

    /** Card label -> the resolution-step button prefix its targets carry. */
    private static final Map<String, String> PLANET_CARDS = new LinkedHashMap<>();

    static {
        PLANET_CARDS.put("Cripple Defenses", "crippleStep3_");
        PLANET_CARDS.put("Uprising", "uprisingStep3_");
        PLANET_CARDS.put("Plague", "plagueStep3_");
        PLANET_CARDS.put("Unstable Planet", "unstableStep3_");
        PLANET_CARDS.put("Infiltrate", "infiltrateStep3_");
        PLANET_CARDS.put("Reparations", "reparationsStep3_");
        PLANET_CARDS.put("Stellar Atomics", "atomicsStep3_");
        PLANET_CARDS.put("Khrask (ready)", "khraskHeroStep4Ready_");
        PLANET_CARDS.put("Khrask (exhaust)", "khraskHeroStep4Exhaust_");
    }

    TestFogPlanetTargets() {
        super("test_fog_planet_targets", "TEMPORARY: post buttons to live-test fog planet targeting", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        var channel = event.getMessageChannel();

        MessageHelper.sendMessageToChannelWithButtons(channel, describeState(game, player), setupButtons());
        MessageHelper.sendMessageToChannelWithButtons(
                channel, "**Action cards** - open the real target list.", actionCardButtons());
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "**Factions, relics and components** - open the real target list.",
                componentButtons(game, player));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "**Fizzle probes** - each must give a flavour line and no error in the log.",
                fizzleButtons(game, player));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                "**Agenda** - stage an Elect Planet vote without an agenda phase, then press Vote.",
                agendaButtons());
    }

    private static List<Button> setupButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(PREFIX + "knowledge", "Show Knowledge"));
        buttons.add(Buttons.blue(PREFIX + "selfcheck", "Run Self-Check"));
        buttons.add(Buttons.gray(PREFIX + "toggleFow", "Toggle FoW Mode"));
        buttons.add(Buttons.green(PREFIX + "scoutAll", "Scout Everything"));
        buttons.add(Buttons.red(PREFIX + "wipeMemory", "Wipe My Fog Memory"));
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        return buttons;
    }

    /** Real Step-1 ids: no card needs to be in hand, and no action phase is required. */
    private static List<Button> actionCardButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("resolveCrippleDefensesStep1", "Cripple Defenses"));
        buttons.add(Buttons.green("resolveUprisingStep1", "Uprising"));
        buttons.add(Buttons.green("resolvePlagueStep1", "Plague"));
        buttons.add(Buttons.green("resolveUnstableStep1", "Unstable Planet"));
        buttons.add(Buttons.green("resolveInfiltrateStep1", "Infiltrate"));
        buttons.add(Buttons.green("resolveReparationsStep1", "Reparations"));
        buttons.add(Buttons.green("resolveReactorMeltdownStep1", "Reactor Meltdown"));
        buttons.add(Buttons.green("resolveMicrometeoroidStormStep1", "Micrometeoroid Storm"));
        buttons.add(Buttons.green("resolveGhostShipStep1", "Ghost Ship"));
        buttons.add(Buttons.green("resolveProbeStep1", "Exploration Probe"));
        buttons.add(Buttons.green("resolveIxthianGift", "Ixthian Gift (ACD2)"));
        buttons.add(Buttons.green("resolveSettlements", "Settlements (ACD2)"));
        return buttons;
    }

    private static List<Button> componentButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("setTrapStep1", "Set a Trap (cunning)"));
        buttons.add(Buttons.green("yinHeroStart", "Yin hero"));
        buttons.add(Buttons.green("startAncientEmpire", "Ancient Empire (tomb)"));
        buttons.add(
                Buttons.green("khraskHeroStep3Exhaust_" + BlindSelectionService.TBD_FACTION, "Khrask hero: exhaust"));
        buttons.add(Buttons.green("khraskHeroStep3Ready_" + BlindSelectionService.TBD_FACTION, "Khrask hero: ready"));

        Player other = game.getRealPlayers().stream()
                .filter(p -> p != player)
                .findFirst()
                .orElse(null);
        if (other != null) {
            buttons.add(Buttons.green("addSleeperViaBt_" + other.getFaction(), "Sleeper (via BT)"));
            buttons.add(Buttons.green("revealSeethe_" + other.getFaction(), "Seethe (Firmament plot)"));
        }
        if (other != null) {
            // The coexistence family: five components build a list and all funnel into
            // exchangeProgramPart3_, which is where "is there anybody here to coexist with" is checked.
            buttons.add(Buttons.green("exchangeProgramPart2_" + other.getFaction(), "Cultural Exchange (TE)"));
        }
        String anyPlanet = findPlanet(game, player, true);
        if (anyPlanet != null) {
            buttons.add(Buttons.green("raghsCallStepOne_" + anyPlanet, "Ragh's Call (from " + anyPlanet + ")"));
        }
        // No standalone button id, so drive these through their normal flows: Vyserix hero, Kaltrim shrine,
        // Circlet of the Void, Creuss Envoy (VotC agenda resolution), Kairn agent, Xin/Deepwrought intrigue,
        // the two Bentor grants, and Galactic Movement.
        return buttons;
    }

    /**
     * One probe per card, each aimed at a planet that does not exist. The card should be spent and the actor
     * should get a flavour line - never a stack trace, and never a message naming the reason.
     */
    private static List<Button> fizzleButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, String> card : PLANET_CARDS.entrySet()) {
            buttons.add(Buttons.red(
                    card.getValue() + BlindSelectionService.TBD_FACTION + "_notaplanetatall",
                    card.getKey() + ": off-map"));
        }
        String unowned = findPlanet(game, player, false);
        if (unowned != null) {
            buttons.add(Buttons.red(
                    "crippleStep3_" + BlindSelectionService.TBD_FACTION + "_" + unowned, "Cripple: unowned"));
        }
        String owned = findPlanet(game, player, true);
        if (owned != null) {
            buttons.add(Buttons.green(
                    "crippleStep3_" + BlindSelectionService.TBD_FACTION + "_" + owned,
                    "Cripple: REAL (" + owned + ")"));
        }
        // Reactor Meltdown names a unit holder, not a planet, so its ids carry a fourth segment. A system
        // that does not exist and a holder that does not exist must both come to nothing.
        buttons.add(Buttons.red(
                "reactorMeltdownStep3_" + BlindSelectionService.TBD_FACTION + "_zzz_space",
                "Meltdown: no such system"));
        buttons.add(Buttons.red(
                "reactorMeltdownStep3_" + BlindSelectionService.TBD_FACTION + "_" + game.getActiveSystem()
                        + "_notaplanetatall",
                "Meltdown: no such holder"));
        // The shared coexistence sink: an off-map planet must not reach AddUnitService.
        buttons.add(Buttons.red(
                player.factionButtonChecker() + "exchangeProgramPart3_notaplanetatall", "Coexist: off-map"));
        return buttons;
    }

    /**
     * Staging an Elect Planet vote turns out to need almost nothing: the fog vote path reads only
     * {@code game.getCurrentAgendaInfo()}, so setting that string is enough to make the real Vote button
     * behave as if an agenda were live. No agenda phase, no deck, no turn order.
     */
    private static List<Button> agendaButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(PREFIX + "agendaPlanet", "Stage: Elect Planet"));
        buttons.add(Buttons.blue(PREFIX + "agendaNonHome", "Stage: Elect Non-Home Planet"));
        buttons.add(Buttons.green(PREFIX + "seedVote", "Seed a vote for a planet you cannot see"));
        buttons.add(Buttons.red(PREFIX + "clearAgenda", "Close the agenda window"));
        // The real vote entry point.
        buttons.add(Buttons.green("vote", "Vote (production)"));
        return buttons;
    }

    /** A planet in a system the player neither sees nor remembers, or null. */
    private static String findUnknownPlanet(Game game, Player player) {
        Set<String> known = FoWHelper.getKnownTilePositions(game, player);
        for (Tile tile : game.getTileMap().values()) {
            if (known.contains(tile.getPosition())) continue;
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                if (uh instanceof Planet) return uh.getName();
            }
        }
        return null;
    }

    /** First on-map planet that is (or is not) controlled by somebody other than the acting player. */
    private static String findPlanet(Game game, Player player, boolean owned) {
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                if (!(uh instanceof Planet)) continue;
                Player controller = game.getPlayerThatControlsPlanet(uh.getName(), true);
                if (owned && controller != null && controller != player) return uh.getName();
                if (!owned && controller == null) return uh.getName();
            }
        }
        return null;
    }

    @ButtonHandler(PREFIX)
    public static void handleTestButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!CommandHelper.hasRole(event, JdaService.developerRoles)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "These test buttons are for developers only.");
            return;
        }

        String action = buttonID.replace(PREFIX, "");
        StringBuilder log = new StringBuilder("### `test_fog_planet_targets` -> `")
                .append(action)
                .append("`\n");
        switch (action) {
            case "knowledge" -> log.setLength(0);
            case "selfcheck" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), selfCheck(game, player));
                return;
            }
            case "toggleFow" -> {
                boolean newValue = !game.isFowMode();
                game.setFowMode(newValue);
                log.append("`fowMode` -> **").append(newValue).append("**\n");
            }
            case "scoutAll" -> {
                int added = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (!FoWHelper.hasEverSeenTile(player, tile.getPosition())) added++;
                    player.updateFogTile(tile, null);
                }
                log.append("Remembered ")
                        .append(added)
                        .append(" additional system(s). Systems you cannot currently see are now ")
                        .append("\"scouted earlier\" - the state the reported bug made untargetable.\n");
            }
            case "agendaPlanet" -> {
                game.setCurrentAgendaInfo("agenda_Elect Planet_1_core_mining");
                game.resetCurrentAgendaVotes();
                log.append("Staged an **Elect Planet** agenda and cleared any votes. Press **Vote**.\n")
                        .append("In fog you should get planets you know about plus Blind Target - *not* a ")
                        .append("\"choose the player who controls it\" step.\n");
            }
            case "agendaNonHome" -> {
                game.setCurrentAgendaInfo("agenda_Elect Non-Home Planet Other Than Mecatol Rex_1_redistribution");
                game.resetCurrentAgendaVotes();
                log.append("Staged an **Elect Non-Home Planet Other Than Mecatol Rex** agenda. Press **Vote**.\n")
                        .append("Home planets and Mecatol must be absent from the list - those are public map ")
                        .append("facts, so filtering on them leaks nothing.\n");
            }
            case "seedVote" -> {
                String unknown = findUnknownPlanet(game, player);
                if (unknown == null) {
                    log.append("Every system is already known to you - press Wipe My Fog Memory first.\n");
                } else {
                    game.setCurrentAgendaVote(unknown, player.getFaction() + "_1");
                    log.append("Seeded a vote for `")
                            .append(unknown)
                            .append("`, which you cannot see. Press **Vote**: it must now be selectable, ")
                            .append("because a later voter has to be able to pile onto an outcome the summary ")
                            .append("already showed them.\n");
                }
            }
            case "clearAgenda" -> {
                game.setCurrentAgendaInfo("");
                log.append("Agenda window closed. Pressing **Vote** now must not throw - the fog builder ")
                        .append("guards the missing agenda info rather than parsing it blindly.\n");
            }
            case "wipeMemory" -> {
                int had = player.getFogTiles().size();
                player.getFogTiles().clear();
                log.append("Cleared ").append(had).append(" remembered system(s).\n");
                log.append("**Note:** `FoWHelper.updateFog` re-runs for every real player on *every save* in ")
                        .append("fog mode, so any system you can currently see will be remembered again ")
                        .append("immediately. Wiping only sticks for systems outside your current vision.\n");
            }
            default -> log.append("Unknown action `").append(action).append("`.\n");
        }
        log.append('\n').append(describeState(game, player));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), log.toString());
    }

    /**
     * Exercises every card's resolution guard in-process. This is the part that can actually be checked
     * without a human: {@code resolve} must refuse an off-map planet and an uncontrolled one for every card,
     * because those are the inputs that used to reach the handlers and throw.
     */
    private static String selfCheck(Game game, Player player) {
        String offMap = "notaplanetatall";
        String unowned = findPlanet(game, player, false);
        String owned = findPlanet(game, player, true);

        StringBuilder sb = new StringBuilder("**Resolve-guard self-check**\n");
        sb.append("Off-map target: `").append(offMap).append("`\n");
        sb.append("Uncontrolled target: `")
                .append(unowned == null ? "none on this map" : unowned)
                .append("`\n");
        sb.append("Controlled target: `")
                .append(owned == null ? "none on this map" : owned)
                .append("`\n\n");

        int failures = 0;
        for (Map.Entry<String, String> card : PLANET_CARDS.entrySet()) {
            String prefix = card.getValue() + BlindSelectionService.TBD_FACTION;
            boolean offMapRefused =
                    PlanetTargetService.resolve(game, player, prefix + "_" + offMap, null, null) == null;
            boolean unownedRefused = unowned == null
                    || PlanetTargetService.resolve(game, player, prefix + "_" + unowned, null, null) == null;
            boolean ok = offMapRefused && unownedRefused;
            if (!ok) failures++;
            sb.append(ok ? "✅ " : "❌ ")
                    .append(card.getKey())
                    .append(" - off-map refused: `")
                    .append(offMapRefused)
                    .append("`, uncontrolled refused: `")
                    .append(unownedRefused)
                    .append("`\n");
        }

        // A controlled planet must still resolve, or the cards would be unplayable rather than merely safe.
        if (owned != null) {
            boolean resolves = PlanetTargetService.resolve(
                            game, player, "crippleStep3_" + BlindSelectionService.TBD_FACTION + "_" + owned, null, null)
                    != null;
            if (!resolves) failures++;
            sb.append(resolves ? "✅ " : "❌ ")
                    .append("A controlled planet still resolves (guards are not over-eager)\n");
        }

        // Unit-holder targets go through a different resolver, so they need their own guard.
        boolean meltdownRefusesJunk = PlanetTargetService.resolveUnitHolder(
                        game,
                        player,
                        "reactorMeltdownStep3_" + BlindSelectionService.TBD_FACTION + "_zzz_space",
                        ButtonHelperActionCards.meltdownSpec(game))
                == null;
        if (!meltdownRefusesJunk) failures++;
        sb.append(meltdownRefusesJunk ? "✅ " : "❌ ").append("Reactor Meltdown refuses a system that does not exist\n");

        // The rule that fixed the Yin space-station regression: outside fog this API must be invisible,
        // because there is no Blind Target there and the component's own builder already applied its rules.
        boolean fogGated = true;
        if (owned != null) {
            boolean wasFow = game.isFowMode();
            game.setFowMode(false);
            fogGated = PlanetTargetService.resolve(
                            game,
                            player,
                            "crippleStep3_" + BlindSelectionService.TBD_FACTION + "_" + owned,
                            PlanetTargetSpec.of("crippleStep3_" + BlindSelectionService.TBD_FACTION)
                                    .where(p -> false),
                            null)
                    != null;
            game.setFowMode(wasFow);
        }
        if (!fogGated) failures++;
        sb.append(fogGated ? "✅ " : "❌ ")
                .append("Spec rules are not applied outside fog (non-fog behaviour unchanged)\n");

        boolean poolShared = PlanetTargetService.messagePool().contains(PlanetTargetService.fizzleMessage());
        if (!poolShared) failures++;
        sb.append(poolShared ? "✅ " : "❌ ").append("Fizzle messages come from the shared pool\n");

        sb.append("\n**")
                .append(failures == 0 ? "All checks passed." : failures + " check(s) FAILED.")
                .append("**\n");
        return sb.toString();
    }

    private static String describeState(Game game, Player player) {
        Set<String> visible = FoWHelper.getTilePositionsToShow(game, player);
        Set<String> known = FoWHelper.getKnownTilePositions(game, player);
        Set<String> remembered = player.getFogTiles().keySet();

        StringBuilder sb = new StringBuilder("**Fog planet-target state - `")
                .append(game.getName())
                .append("`**\n");
        sb.append("- FoW mode: `").append(game.isFowMode()).append("`\n");
        sb.append("- systems on the map: `").append(game.getTileMap().size()).append("`\n");
        sb.append("- you can see now: `").append(visible.size()).append("`\n");
        sb.append("- you have ever seen: `").append(remembered.size()).append("`\n");
        sb.append("- known (see now OR ever seen): `").append(known.size()).append("`\n");

        List<String> rememberedOnly = remembered.stream()
                .filter(pos -> !visible.contains(pos))
                .sorted()
                .toList();
        sb.append("- remembered but NOT currently visible: `")
                .append(rememberedOnly.size())
                .append("` ")
                .append(rememberedOnly.isEmpty() ? "(none - press Scout Everything)" : rememberedOnly)
                .append('\n');

        int knowable = 0;
        int total = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                if (!(uh instanceof Planet)) continue;
                total++;
                if (FoWHelper.knowsPlanetExists(game, player, uh.getName())) knowable++;
            }
        }
        sb.append("- planets you could know exist: `")
                .append(knowable)
                .append("` of `")
                .append(total)
                .append("`\n");
        sb.append("\nPress a card button to see its real target list. Before the fix, in fog these listed ")
                .append("only your own planets.\n");
        return sb.toString();
    }
}
