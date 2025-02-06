package ti4.listeners.context;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.event.EventAuditService;
import ti4.service.game.GameNameService;

@Getter
public abstract class ListenerContext {

    protected boolean contextIsValid = true;
    protected final String origComponentID;
    protected String componentID;
    protected boolean factionChecked;
    protected final Game game;
    protected Player player;
    protected MessageChannel privateChannel, mainGameChannel, actionsChannel;
    protected final GenericInteractionCreateEvent event;

    public abstract GenericInteractionCreateEvent getEvent();

    public abstract String getContextType();

    public boolean isValid() {
        return contextIsValid;
    }

    public ListenerContext(GenericInteractionCreateEvent event, String compID) {
        this.event = event;
        this.componentID = this.origComponentID = compID;

        String gameName = GameNameService.getGameNameFromChannel(event);
        game = GameManager.isValid(gameName) ? GameManager.getManagedGame(gameName).getGame() : null;
        player = null;
        privateChannel = event.getMessageChannel();
        mainGameChannel = event.getMessageChannel();

        if (game != null) {
            String userID = event.getUser().getId();
            player = CommandHelper.getPlayerFromGame(game, event.getMember(), userID);

            if (player == null && !"showGameAgain".equalsIgnoreCase(componentID)) {
                event.getMessageChannel().sendMessage(event.getUser().getAsMention()+" is not a player of the game").queue();
                contextIsValid = false;
                return;
            }

            if (getContextType().equals("button")) {
                componentID = componentID.replace("delete_buttons_", "resolveAgendaVote_");
                game.increaseButtonPressCount();
            }

            if (game.isFowMode()) {
                if (player != null && player.isRealPlayer() && player.getPrivateChannel() == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Private channels are not set up for this game. Messages will be suppressed.");
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

            if (player != null && game.getActivePlayerID() != null && player.getUserID().equalsIgnoreCase(game.getActivePlayerID())) {
                AutoPingMetadataManager.delayPing(gameName);
            }
        }

        if (!checkFinsFactionChecker()) {
            contextIsValid = false;
            return;
        }

        actionsChannel = null;
        for (TextChannel textChannel_ : AsyncTI4DiscordBot.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }
    }

    public boolean checkFinsFactionChecker() {
        GenericInteractionCreateEvent event = getEvent();
        if (factionChecked || componentID == null || !componentID.startsWith("FFCC_")) {
            return true;
        }
        componentID = componentID.replace("FFCC_", "");
        String factionWhoPressedButton = player == null ? "nullPlayer" : player.getFaction();
        if (player != null && !componentID.startsWith(factionWhoPressedButton + "_")) {
            String message = "To " + player.getFactionEmoji() + ": these buttons are for someone else";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return false;
        }
        componentID = componentID.replaceFirst(factionWhoPressedButton + "_", "");
        factionChecked = true;
        return true;
    }

    public void save() {
        if (game != null) {
            GameManager.save(game, EventAuditService.getReason(getEvent(), game.isFowMode()));
        }
    }
}
