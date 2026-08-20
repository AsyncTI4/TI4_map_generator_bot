package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAbilities.PillageGainMode;
import ti4.helpers.ButtonHelperCommanders;
import ti4.message.MessageHelper;
import ti4.service.map.AddTileService;
import ti4.service.option.FOWOptionService.FOWOption;

/**
 * TEMPORARY live-testing harness for {@link FOWOption#OPTIONAL_PILLAGABLE_TG}. Posts a button panel that seeds a
 * Pillage-capable neighbor (a synthetic Mentak player sharing a system with you), lets a developer toggle every
 * input to {@link ButtonHelperAbilities#resolveOptionalTgGainMode} (FoW mode, the option, trade good count, and
 * whether Rear Admiral Farran / Magmus are unlocked), and fires the exact same production entry points -
 * {@link ButtonHelperCommanders#resolveLetnevCommanderCheck} and
 * {@link ButtonHelperCommanders#resolveMuaatCommanderCheck} - that a real Rear Admiral Farran / Magmus trigger uses,
 * so the resulting prompt (or auto-grant) can be checked on a live bot instead of only in unit tests.
 *
 * <p>To remove: delete this class and its entry in {@link DeveloperCommand}. Nothing else references it.
 */
class TestPillageTg extends GameStateSubcommand {

    private static final String PREFIX = "devPillageTg_";
    private static final String PILLAGER_ID = "devPillageTgPillager";
    private static final String LETNEV_COMMANDER = "letnevcommander";
    private static final String MUAAT_COMMANDER = "muaatcommander";
    private static final String SHARED_TILE_ID = "18"; // Mecatol Rex - single planet, simplest shared system
    private static final String SHARED_POSITION = "000";
    private static final String SHARED_PLANET = "mecatolrex";

    TestPillageTg() {
        super("test_pillage_tg", "TEMPORARY: post buttons to live-test OPTIONAL_PILLAGABLE_TG", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), describeState(getGame(), getPlayer()), buttons());
    }

    private static List<Button> buttons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(PREFIX + "state", "Show State"));
        buttons.add(Buttons.blue(PREFIX + "seed", "Seed Pillage Neighbor"));
        buttons.add(Buttons.red(PREFIX + "wipe", "Wipe (reset)"));
        buttons.add(Buttons.gray(PREFIX + "toggleFow", "Toggle FoW Mode"));
        buttons.add(Buttons.gray(PREFIX + "toggleOption", "Toggle OPTIONAL_PILLAGABLE_TG"));
        buttons.add(Buttons.gray(PREFIX + "toggleTg", "Toggle My TG (0 / 3)"));
        buttons.add(Buttons.gray(PREFIX + "toggleLetnev", "Toggle Rear Admiral Farran Unlocked"));
        buttons.add(Buttons.gray(PREFIX + "toggleMuaat", "Toggle Magmus Unlocked"));
        buttons.add(Buttons.green(PREFIX + "triggerLetnev", "Trigger Letnev Commander Check (real handler)"));
        buttons.add(Buttons.green(PREFIX + "triggerMuaat", "Trigger Muaat Commander Check (real handler)"));
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler(PREFIX)
    public static void handleTestButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!CommandHelper.hasRole(event, JdaService.developerRoles)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "These test buttons are for developers only.");
            return;
        }

        String action = buttonID.replace(PREFIX, "");
        StringBuilder log =
                new StringBuilder("### `test_pillage_tg` → `").append(action).append("`\n");
        switch (action) {
            case "state" -> log.setLength(0);
            case "seed" -> log.append(seedPillager(game, player));
            case "wipe" -> log.append(wipe(game, player));
            case "toggleFow" -> {
                boolean newValue = !game.isFowMode();
                game.setFowMode(newValue);
                log.append("`fowMode` → **").append(newValue).append("**\n");
            }
            case "toggleOption" -> {
                boolean newValue = !game.getFowOption(FOWOption.OPTIONAL_PILLAGABLE_TG);
                game.setFowOption(FOWOption.OPTIONAL_PILLAGABLE_TG, newValue);
                log.append("`OPTIONAL_PILLAGABLE_TG` → **").append(newValue).append("**\n");
            }
            case "toggleTg" -> {
                int newValue = player.getTg() >= 3 ? 0 : 3;
                player.setTg(newValue);
                log.append("your `tg` → **").append(newValue).append("** (Pillage range needs > 2)\n");
            }
            case "toggleLetnev" -> log.append(toggleCommander(player, LETNEV_COMMANDER, "Rear Admiral Farran"));
            case "toggleMuaat" -> log.append(toggleCommander(player, MUAAT_COMMANDER, "Magmus"));
            case "triggerLetnev" -> {
                ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
                log.append("Called `resolveLetnevCommanderCheck` - see the message it posted above/below.\n");
            }
            case "triggerMuaat" -> {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(
                        player, game, event, "activated a system (devtest trigger)");
                log.append("Called `resolveMuaatCommanderCheck` - see the message it posted above/below.\n");
            }
            default -> log.append("Unknown action `").append(action).append("`.\n");
        }
        log.append('\n').append(describeState(game, player));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), log.toString());
    }

    /** Places a synthetic Mentak (has the `pillage` ability) sharing your system, so `canBePillaged` can go true. */
    private static String seedPillager(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        if (game.getTileByPosition(SHARED_POSITION) == null) {
            AddTileService.addTile(game, new Tile(SHARED_TILE_ID, SHARED_POSITION));
            sb.append("- placed Mecatol Rex at `").append(SHARED_POSITION).append("`\n");
        }

        Player pillager = game.getPlayer(PILLAGER_ID);
        if (pillager == null) {
            pillager = game.addPlayer(PILLAGER_ID, "DevPillager");
            pillager.setFaction(game, "mentak");
            pillager.setColor("orange");
            sb.append("- added synthetic Mentak neighbor (has the `pillage` ability)\n");
        }

        if (!player.getPlanets().contains(SHARED_PLANET)) {
            player.addPlanet(SHARED_PLANET);
            sb.append("- gave you `").append(SHARED_PLANET).append("` (shares a system with the pillager)\n");
        }
        if (!pillager.getPlanets().contains(SHARED_PLANET)) {
            pillager.addPlanet(SHARED_PLANET);
            sb.append("- gave the pillager `").append(SHARED_PLANET).append("` too\n");
        }
        if (sb.isEmpty()) sb.append("- nothing to do, already seeded\n");
        sb.append("Now use **Toggle My TG** to get above the Pillage threshold and check state.\n");
        return sb.toString();
    }

    private static String wipe(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        Player pillager = game.getPlayer(PILLAGER_ID);
        if (pillager != null) {
            pillager.getPlanets().remove(SHARED_PLANET);
            sb.append("- removed the pillager's planet (the synthetic player itself is left registered)\n");
        }
        player.getPlanets().remove(SHARED_PLANET);
        player.setTg(0);
        unlock(player, LETNEV_COMMANDER, false);
        unlock(player, MUAAT_COMMANDER, false);
        sb.append("- removed your seeded planet, reset `tg` to 0, and re-locked both commanders\n");
        return sb.toString();
    }

    private static String toggleCommander(Player player, String leaderID, String label) {
        boolean newlyUnlocked = !player.hasLeaderUnlocked(leaderID);
        unlock(player, leaderID, newlyUnlocked);
        return "`" + label + "` unlocked → **" + newlyUnlocked + "**\n";
    }

    private static void unlock(Player player, String leaderID, boolean unlocked) {
        player.addLeader(leaderID);
        Leader leader = player.unsafeGetLeader(leaderID);
        if (leader != null) leader.setLocked(!unlocked);
    }

    private static String describeState(Game game, Player player) {
        StringBuilder sb = new StringBuilder("**OPTIONAL_PILLAGABLE_TG test state — `")
                .append(game.getName())
                .append("`**\n");
        sb.append("- FoW mode: `").append(game.isFowMode()).append("`\n");
        sb.append("- OPTIONAL_PILLAGABLE_TG option: `")
                .append(game.getFowOption(FOWOption.OPTIONAL_PILLAGABLE_TG))
                .append("`\n");
        sb.append("- your `tg`: `").append(player.getTg()).append("`\n");
        sb.append("- Rear Admiral Farran unlocked: `")
                .append(player.hasLeaderUnlocked(LETNEV_COMMANDER))
                .append("`\n");
        sb.append("- Magmus unlocked: `")
                .append(player.hasLeaderUnlocked(MUAAT_COMMANDER))
                .append("`\n");

        List<String> pillageNeighbors = game.getRealPlayers().stream()
                .filter(p -> p != player)
                .filter(p -> p.hasAbility("pillage"))
                .filter(p -> player.getNeighbouringPlayers(true).contains(p))
                .map(Player::getFaction)
                .toList();
        sb.append("- neighboring players with the `pillage` ability: ")
                .append(pillageNeighbors.isEmpty() ? "none" : pillageNeighbors)
                .append('\n');

        boolean inRange = ButtonHelperAbilities.canBePillaged(player, game, player.getTg() + 1);
        sb.append("- `canBePillaged(tg+1)`: `").append(inRange).append("`\n");

        PillageGainMode mode = ButtonHelperAbilities.resolveOptionalTgGainMode(player, game);
        sb.append("- **`resolveOptionalTgGainMode` → ").append(mode).append("**\n");
        sb.append(
                switch (mode) {
                    case FOW_OPT_IN ->
                        "  (FoW + option ON: always privately prompts, regardless of actual range - the mere prompt never leaks proximity)\n";
                    case RANGE_OPT_IN ->
                        "  (non-FoW and actually in Pillage range: prompts, naming Pillage explicitly - that's public info here)\n";
                    case AUTO ->
                        "  (no Pillage risk worth prompting about, or FoW table hasn't opted in: auto-granted)\n";
                });
        return sb.toString();
    }
}
