package ti4.service.leader.agent.modules.homebrew;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.turn.StartTurnService;

public final class EdynAgent extends AgentModule<EdynAgent.PresetAction> {

    static final String ID = "edynagent";
    static final String PRESET_KEY = "edynAgentPreset";
    static final String IN_ACTION_KEY = "edynAgentInAction";
    private static final String CHOOSE_ACTOR = "presetEdynAgentStep1";
    private static final String CHOOSE_TRIGGER = "presetEdynAgentStep2_";
    private static final String CONFIRM_PRESET = "presetEdynAgentStep3_";
    private static final String SEPARATOR = "|";
    private static final String LEGACY_SEPARATOR = "_";

    public record PresetAction(Player actor, Player upNext) {}

    public EdynAgent() {
        super(ID, "Allant, the Edyn");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(holder.factionButtonChecker() + CHOOSE_ACTOR, "Preset Edyn Agent", FactionEmojis.edyn);
    }

    @ButtonHandler(CHOOSE_ACTOR)
    static void chooseActor(Game game, Player holder) {
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                holder.getRepresentationUnfogged()
                        + ", please choose the player who you wish to take the action when the time comes (probably yourself).",
                playerButtons(game, holder, CHOOSE_TRIGGER));
    }

    @ButtonHandler(CHOOSE_TRIGGER)
    static void chooseTrigger(Game game, Player holder, ButtonInteractionEvent event, String buttonID) {
        Player actor = game.getPlayerFromColorOrFaction(buttonID.substring(CHOOSE_TRIGGER.length()));
        if (actor == null) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                holder.getRepresentationUnfogged()
                        + ", please choose the passing player who will set off the trigger. When this player passes, the player you chose in the last step will get an action.",
                playerButtons(game, holder, CONFIRM_PRESET + actor.getFaction() + SEPARATOR));
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> playerButtons(Game game, Player holder, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            buttons.add(Buttons.blue(
                    holder.factionButtonChecker() + prefix + player.getFaction(),
                    player.getColorDisplayName(),
                    player.fogSafeEmoji()));
        }
        return buttons;
    }

    @ButtonHandler(CONFIRM_PRESET)
    static void confirmPreset(Game game, Player holder, ButtonInteractionEvent event, String buttonID) {
        List<Player> players = players(game, buttonID.substring(CONFIRM_PRESET.length()), 2);
        if (players.isEmpty()) {
            return;
        }
        Player actor = players.get(0);
        Player trigger = players.get(1);
        MessageHelper.sendMessageToChannel(
                holder.getCardsInfoThread(),
                holder.getRepresentationUnfogged() + " you set " + actor.getRepresentationNoPing()
                        + " up to take an action once " + trigger.getRepresentationNoPing() + " passes.");
        game.setStoredValue(
                PRESET_KEY, String.join(SEPARATOR, actor.getFaction(), trigger.getFaction(), holder.getFaction()));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                holder.getRepresentation() + " you may use this button to undo the preset. Ignore it otherwise.",
                List.of(Buttons.red("removePreset_" + PRESET_KEY, "Remove The Preset")));
    }

    public static boolean triggerPresetOnPass(
            Game game, Player passedPlayer, Player upNextPlayer, GenericInteractionCreateEvent event) {
        List<Player> preset = players(game, game.getStoredValue(PRESET_KEY), 3);
        if (preset.isEmpty() || preset.get(1) != passedPlayer) {
            return false;
        }
        game.setStoredValue(PRESET_KEY, "");
        Player actor = preset.get(0);
        Player edyn = preset.get(2);
        return AgentLifecycle.use(
                game, edyn, ID, String.join(SEPARATOR, actor.getFaction(), upNextPlayer.getFaction()), event);
    }

    public static boolean endPresetAction(Game game, GenericInteractionCreateEvent event) {
        String inAction = game.getStoredValue(IN_ACTION_KEY);
        if (inAction.isEmpty()) {
            return false;
        }
        String[] factions = split(inAction);
        Player edyn = factions.length == 3 ? game.getPlayerFromColorOrFaction(factions[1]) : null;
        if (edyn == null) {
            return false;
        }
        AgendaHelper.drawAgenda(1, false, game, edyn, true);
        Player upNext = game.getPlayerFromColorOrFaction(factions[2]);
        game.setStoredValue(IN_ACTION_KEY, "");
        if (upNext != null) {
            StartTurnService.turnStart(event, game, upNext);
        }
        return true;
    }

    @Override
    public Optional<PresetAction> decode(Game game, Player user, String payload) {
        List<Player> players = players(game, payload, 2);
        if (players.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PresetAction(players.get(0), players.get(1)));
    }

    @Override
    public AgentOutcome resolve(AgentUse<PresetAction> use) {
        Game game = use.game();
        Player edyn = use.user();
        Player actor = use.payload().actor();
        game.setStoredValue(
                IN_ACTION_KEY,
                String.join(
                        SEPARATOR,
                        actor.getFaction(),
                        edyn.getFaction(),
                        use.payload().upNext().getFaction()));
        List<Button> buttons = StartTurnService.getStartOfTurnButtons(actor, game, true, use.event());
        MessageHelper.sendMessageToChannelWithButtons(
                actor.getCorrectChannel(),
                actor.getRepresentationUnfogged() + " you may take 1 action now due to " + use.agentNameFor(actor)
                        + ".",
                buttons);
        game.updateActivePlayer(actor);
        return AgentOutcome.none();
    }

    static List<Player> players(Game game, String joinedFactions, int count) {
        String[] factions = split(joinedFactions);
        if (factions.length != count) {
            return List.of();
        }
        List<Player> players = new ArrayList<>();
        for (String faction : factions) {
            Player player = game.getPlayerFromColorOrFaction(faction);
            if (player == null) {
                return List.of();
            }
            players.add(player);
        }
        return players;
    }

    private static String[] split(String joinedFactions) {
        if (joinedFactions == null || joinedFactions.isEmpty()) {
            return new String[0];
        }
        if (joinedFactions.contains(SEPARATOR)) {
            return joinedFactions.split("\\|");
        }
        return joinedFactions.split(LEGACY_SEPARATOR);
    }
}
