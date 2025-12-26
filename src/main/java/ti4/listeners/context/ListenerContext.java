package ti4.listeners.context;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.event.EventAuditService;
import ti4.service.game.GameNameService;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.jda.JdaService;

@Getter
public abstract class ListenerContext {

    protected boolean contextIsValid = true;
    protected final String origComponentID;
    protected String componentID;
    protected boolean factionChecked;
    protected final Game game;
    protected Player player;
    protected MessageChannel privateChannel, mainGameChannel, actionsChannel;
    final GenericInteractionCreateEvent event;

    @Setter
    protected boolean shouldSave = true;

    public abstract GenericInteractionCreateEvent getEvent();

    protected abstract String getContextType();

    public boolean isValid() {
        return contextIsValid;
    }

    ListenerContext(GenericInteractionCreateEvent event, String compID) {
        this.event = event;
        componentID = origComponentID = compID;

        String gameName = GameNameService.getGameNameFromChannel(event);
        game = GameManager.isValid(gameName)
                ? GameManager.getManagedGame(gameName).getGame()
                : null;
        player = null;
        privateChannel = event.getMessageChannel();
        mainGameChannel = event.getMessageChannel();

        if (game != null) {
            String userID = event.getUser().getId();
            player = CommandHelper.getPlayerFromGame(game, event.getMember(), userID);

            if (player == null && !"showGameAgain".equalsIgnoreCase(componentID)) {
                String message = event.getUser().getAsMention() + " is not a player of the game";
                if (event instanceof IReplyCallback replyCallback) {
                    if (replyCallback.isAcknowledged()) {
                        replyCallback
                                .getHook()
                                .sendMessage(message)
                                .setEphemeral(true)
                                .queue(Consumers.nop(), BotLogger::catchRestError);
                    } else {
                        replyCallback
                                .reply(message)
                                .setEphemeral(true)
                                .queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                } else {
                    event.getMessageChannel().sendMessage(message).queue(Consumers.nop(), BotLogger::catchRestError);
                }
                contextIsValid = false;
                return;
            }

            if ("button".equals(getContextType())) {
                componentID = componentID.replace("delete_buttons_", "resolveAgendaVote_");
                game.increaseButtonPressCount();
            }

            if (game.isFowMode()) {
                if (player != null && player.isRealPlayer() && player.getPrivateChannel() == null) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Private channels are not set up for this game. Messages will be suppressed.");
                    privateChannel = null;
                } else if (player != null) {
                    privateChannel = player.getPrivateChannel();
                }
            }

            if (game.getMainGameChannel() != null) {
                mainGameChannel = game.getMainGameChannel();
            }

            if (componentID.contains("dummyPlayerSpoof")) {
                String identity = StringUtils.substringBefore(componentID, "_").replace("dummyPlayerSpoof", "");
                player = game.getPlayerFromColorOrFaction(identity);
                componentID = componentID.replace("dummyPlayerSpoof" + identity + "_", "");
            }

            if (player != null
                    && game.getActivePlayerID() != null
                    && player.getUserID().equalsIgnoreCase(game.getActivePlayerID())) {
                AutoPingMetadataManager.delayPing(gameName);
            }
        }

        if (!checkFinsFactionChecker()) {
            contextIsValid = false;
            return;
        }

        actionsChannel = null;
        for (TextChannel textChannel_ : JdaService.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }
    }

    private boolean checkFinsFactionChecker() {
        GenericInteractionCreateEvent event = getEvent();
        if (factionChecked || componentID == null || !componentID.startsWith("FFCC_")) {
            return true;
        }
        componentID = componentID.replace("FFCC_", "");
        String factionWhoPressedButton = player == null ? "nullPlayer" : player.getFaction();

        if (player != null
                && !componentID.startsWith(factionWhoPressedButton + "_")
                && (!componentID.contains("firmament_") || !factionWhoPressedButton.contains("obsidian"))) {
            handlePlayerHittingButtonTheyDoNotOwn(event);
            return false;
        }
        if (componentID.contains("firmament_") && factionWhoPressedButton.contains("obsidian")) {
            factionWhoPressedButton = "firmament";
        }
        componentID = componentID.replaceFirst(factionWhoPressedButton + "_", "");
        factionChecked = true;
        return true;
    }

    private void handlePlayerHittingButtonTheyDoNotOwn(Interaction event) {
        var userSettings = UserSettingsManager.get(player.getUserID());
        Boolean prefersEphemeral = userSettings.getPrefersWrongButtonEphemeral();
        boolean shouldAskPreference = prefersEphemeral == null;
        boolean sendEphemeral = prefersEphemeral == null || prefersEphemeral;
        String message = "To " + player.fogSafeEmoji() + ": these buttons are for someone else";
        List<Button> buttons = Collections.emptyList();
        if (shouldAskPreference) {
            message += "\nWould you like this warning to be ephemeral in the future?";
            buttons = List.of(
                Buttons.green("wrongButtonEphemeral_true", "Yes"),
                Buttons.red("wrongButtonEphemeral_false", "No"));
        }
        if (event instanceof IReplyCallback replyCallback) {
            if (replyCallback.isAcknowledged()) {
                var messageAction = replyCallback.getHook().sendMessage(message).setEphemeral(sendEphemeral);
                if (shouldAskPreference) {
                    messageAction.setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons));
                }
                messageAction.queue(Consumers.nop(), BotLogger::catchRestError);
            } else {
                var replyAction = replyCallback.reply(message).setEphemeral(sendEphemeral);
                if (shouldAskPreference) {
                    replyAction.setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons));
                }
                replyAction.queue(Consumers.nop(), BotLogger::catchRestError);
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
    }

    public void save() {
        if (game != null) {
            GameManager.save(game, EventAuditService.getReason(getEvent(), game.isFowMode()));
        }
    }
}
