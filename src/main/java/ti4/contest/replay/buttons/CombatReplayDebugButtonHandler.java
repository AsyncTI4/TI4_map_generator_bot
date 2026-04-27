package ti4.contest.replay.buttons;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.unit.AddUnitService;
import ti4.spring.context.SpringContext;

@UtilityClass
public class CombatReplayDebugButtonHandler {

    private static final String PREFIX = "combatReplayDebug_";
    private static final String ENABLE_JOLNAR = PREFIX + "enable_jolnar_commander";
    private static final String ENABLE_WINNU = PREFIX + "enable_winnu_commander";
    private static final String DISABLE_JOLNAR = PREFIX + "disable_jolnar_commander";
    private static final String DISABLE_WINNU = PREFIX + "disable_winnu_commander";
    private static final String START_MR_FIGHT = PREFIX + "start_mecatol_rex_fight";
    private static final String WIPE_MR_SPACE = PREFIX + "wipe_mecatol_rex_space";
    private static final String DEBUG_FLEET = "carrier, dreadnought, cruiser, destroyer, 3 fighter";

    public static List<Button> buttons() {
        return List.of(
                Buttons.green(ENABLE_JOLNAR, "Enable Jol-Nar Commander"),
                Buttons.green(ENABLE_WINNU, "Enable Winnu Commander"),
                Buttons.red(DISABLE_JOLNAR, "Disable Jol-Nar Commander"),
                Buttons.red(DISABLE_WINNU, "Disable Winnu Commander"),
                Buttons.blue(START_MR_FIGHT, "Start a Fight at Mecatol Rex"),
                Buttons.red(WIPE_MR_SPACE, "Wipe Space Army on Rex"));
    }

    @ButtonHandler(PREFIX)
    public static void handleDebugButton(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        if (!isEnabled()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Combat replay debug controls are only available in combat dev mode.");
            return;
        }

        switch (buttonId) {
            case ENABLE_JOLNAR -> setCommanderLock(event, game, player, "jolnarcommander", false);
            case ENABLE_WINNU -> setCommanderLock(event, game, player, "winnucommander", false);
            case DISABLE_JOLNAR -> setCommanderLock(event, game, player, "jolnarcommander", true);
            case DISABLE_WINNU -> setCommanderLock(event, game, player, "winnucommander", true);
            case START_MR_FIGHT -> startMecatolRexFight(event, game, player);
            case WIPE_MR_SPACE -> wipeMecatolRexSpace(event, game);
            default -> MessageHelper.sendEphemeralMessageToEventChannel(event, "Unknown combat replay debug action.");
        }
    }

    private static boolean isEnabled() {
        return SpringContext.getBean(CombatContestSettings.class).getRuntime().isDevMode();
    }

    private static void setCommanderLock(
            ButtonInteractionEvent event, Game game, Player pressingPlayer, String commanderId, boolean locked) {
        Player target = debugPlayer(game, pressingPlayer);
        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find a player for this debug action.");
            return;
        }
        Leader commander = target.getLeader(commanderId).orElse(null);
        if (commander == null) {
            target.addLeader(commanderId);
            commander = target.unsafeGetLeader(commanderId);
        }

        game.addFakeCommander(commanderId);
        commander.setLocked(locked);
        MessageHelper.sendMessageToEventChannel(
                event, target.getRepresentation() + " " + commanderId + " " + (locked ? "disabled" : "enabled") + ".");
    }

    private static void startMecatolRexFight(ButtonInteractionEvent event, Game game, Player pressingPlayer) {
        if (game == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find a game for this debug action.");
            return;
        }
        Tile mecatolRex = game.getMecatolTile();
        if (mecatolRex == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find Mecatol Rex in this game.");
            return;
        }

        Player player = debugPlayer(game, pressingPlayer);
        if (player == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find a player for this debug action.");
            return;
        }
        Player opponent = debugOpponent(game, player);
        if (opponent == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not find a non-allied real player for the test fight.");
            return;
        }

        mecatolRex.getSpaceUnitHolder().getUnitsByState().clear();
        AddUnitService.addUnits(event, mecatolRex, game, player.getColor(), DEBUG_FLEET);
        AddUnitService.addUnits(event, mecatolRex, game, opponent.getColor(), DEBUG_FLEET);
        game.setActiveSystem(mecatolRex.getPosition());

        MessageHelper.sendMessageToEventChannel(
                event,
                "Added combat replay debug fleets to Mecatol Rex: " + player.getRepresentation() + " `" + DEBUG_FLEET
                        + "` vs " + opponent.getRepresentation() + " `" + DEBUG_FLEET + "`.");
        StartCombatService.combatCheck(game, event, mecatolRex);
    }

    private static void wipeMecatolRexSpace(ButtonInteractionEvent event, Game game) {
        if (game == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find a game for this debug action.");
            return;
        }
        Tile mecatolRex = game.getMecatolTile();
        if (mecatolRex == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find Mecatol Rex in this game.");
            return;
        }

        mecatolRex.getSpaceUnitHolder().getUnitsByState().clear();
        MessageHelper.sendMessageToEventChannel(event, "Removed all space units from Mecatol Rex.");
    }

    private static Player debugPlayer(Game game, Player pressingPlayer) {
        if (game == null) return pressingPlayer;
        for (Player player : game.getRealPlayers()) {
            if ("memephilosopher".equalsIgnoreCase(player.getFaction())) {
                return player;
            }
        }
        return pressingPlayer;
    }

    private static Player debugOpponent(Game game, Player player) {
        for (Player opponent : game.getRealPlayersExcludingThis(player)) {
            if (!player.isPlayerMemberOfAlliance(opponent) && !opponent.isPlayerMemberOfAlliance(player)) {
                return opponent;
            }
        }
        return null;
    }
}
