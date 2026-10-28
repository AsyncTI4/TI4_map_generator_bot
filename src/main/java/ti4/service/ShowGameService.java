package ti4.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.image.map.MapRenderPipeline;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.FogViewer;
import ti4.service.fow.MapOverviewService;
import ti4.service.fow.MapSegmentService;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.service.testbed.TestBedService;
import ti4.spring.api.image.GameImageService;
import ti4.spring.context.SpringContext;

@UtilityClass
public class ShowGameService {

    enum FogMapAccess {
        AS_ASKED,
        FULL_MAP,
        PLAYER_VIEW,
        REFUSED
    }

    public static void simpleEphemeralShowGame(Game game, GenericInteractionCreateEvent event) {
        if (!hasGameToShow(game, event) || !mayShowMap(game, event)) {
            return;
        }
        ephemeralShowGame(game, event, DisplayType.map);
    }

    public static boolean mayShowMap(Game game, GenericInteractionCreateEvent event) {
        if (fogMapAccess(game, event) != FogMapAccess.REFUSED) {
            return true;
        }
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Until this Fog of War game ends, its map can only be viewed in your private channel.");
        return false;
    }

    static FogMapAccess fogMapAccess(Game game, GenericInteractionCreateEvent event) {
        if (!game.isFowMode()
                || game.isHasEnded()
                || event instanceof UserOverridenGenericInteractionCreateEvent
                || FoWHelper.isGmRoom(game, event.getChannel())) {
            return FogMapAccess.AS_ASKED;
        }
        boolean gameMaster = FoWHelper.isGameMaster(event.getUser().getId(), game);
        if (!isGameChannel(game, event.getChannel())) {
            return gameMaster ? FogMapAccess.FULL_MAP : FogMapAccess.REFUSED;
        }
        if (gameMaster || MapSegmentService.isFoggedView(game, event)) {
            return FogMapAccess.AS_ASKED;
        }
        Player player = actingPlayer(game, event);
        boolean seatedWithPrivateChannel = player != null
                && event.getMember() != null
                && game.getRealPlayers().contains(player)
                && player.getPrivateChannel() != null;
        return seatedWithPrivateChannel ? FogMapAccess.PLAYER_VIEW : FogMapAccess.REFUSED;
    }

    @Nullable
    private static GenericInteractionCreateEvent renderEvent(FogMapAccess access, GenericInteractionCreateEvent event) {
        return switch (access) {
            case FULL_MAP -> null;
            case PLAYER_VIEW -> new UserOverridenGenericInteractionCreateEvent(event, event.getMember());
            case AS_ASKED, REFUSED -> event;
        };
    }

    private static boolean isGameChannel(Game game, @Nullable Channel channel) {
        if (channel instanceof ThreadChannel thread) {
            channel = thread.getParentChannel();
        }
        if (channel == null) {
            return false;
        }
        String channelId = channel.getId();
        return channel.getName().startsWith(game.getName() + "-")
                || gameChannelIds(game).anyMatch(id -> id.equals(channelId));
    }

    private static Stream<String> gameChannelIds(Game game) {
        Stream<String> shared = Stream.of(game.getMainChannelID(), game.getTableTalkChannelID());
        Stream<String> privateChannels = game.getPlayers().values().stream().map(Player::getPrivateChannelID);
        return Stream.concat(shared, privateChannels).filter(Objects::nonNull);
    }

    private static boolean isFoggedGameChannel(Game game, GenericInteractionCreateEvent event) {
        return isGameChannel(game, event.getChannel()) && MapSegmentService.isFoggedView(game, event);
    }

    @Nullable
    private static Player actingPlayer(Game game, GenericInteractionCreateEvent event) {
        return TestBedService.resolveActingPlayer(
                game, event, game.getPlayer(event.getUser().getId()));
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        simpleShowGame(game, event, DisplayType.all);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        simpleShowGame(game, event, displayType, null);
    }

    public static void simpleShowGame(
            Game game, GenericInteractionCreateEvent event, DisplayType displayType, @Nullable String segment) {
        if (game != null && MapOverviewService.showsOverlayAsOverview(game, displayType)) {
            showFeature(game, event, displayType);
            return;
        }
        if (!hasGameToShow(game, event) || !mayShowMap(game, event)) {
            return;
        }
        GenericInteractionCreateEvent renderEvent = renderEvent(fogMapAccess(game, event), event);
        boolean shouldPersistFullMapMessageId = displayType == DisplayType.all && !game.isFowMode();
        boolean shouldPersistFowMapMessageId = displayType == DisplayType.all && game.isFowMode();

        // For non-FoW games: persist the full map message ID
        Consumer<Message> persistMessageId = shouldPersistFullMapMessageId
                ? msg -> SpringContext.getBean(GameImageService.class).saveDiscordMessage(game.getName(), msg)
                : null;

        // For FoW games: persist the player-specific map message ID
        String playerId = event.getUser().getId();
        Consumer<Message> persistFowMessageId = shouldPersistFowMapMessageId
                ? msg -> SpringContext.getBean(GameImageService.class)
                        .savePlayerDiscordMessageId(
                                game.getName(),
                                playerId,
                                msg.getIdLong(),
                                msg.getChannel().getIdLong())
                : null;

        boolean foggedView = renderEvent != null && MapSegmentService.isFoggedView(game, renderEvent);
        MapRenderPipeline.queue(game, renderEvent, displayType, segment, fileUpload -> {
            if (includeButtons(displayType)) {
                List<Button> buttons = Buttons.mapImageButtons(game, segment);
                buttons.addAll(
                        MapSegmentService.switchButtons(game, MapSegmentService.viewerId(game, event), foggedView));
                MapOverviewService.overviewButton(game, event).ifPresent(buttons::add);

                // Divert map image to the botMapUpdatesThread event channel is actions channel is the same
                MessageChannel channel = sendMessage(game, event);
                MapOverviewService.gmOverviewButton(game, event, channel).ifPresent(buttons::add);
                // Use FoW callback when sending to player's private channel, otherwise use full map callback
                Consumer<Message> callback = (game.isFowMode() && isSendingToPrivateChannel(game, event))
                        ? persistFowMessageId
                        : persistMessageId;
                ButtonHelper.sendFileWithCorrectButtons(channel, fileUpload, null, buttons, game, callback);
            } else {
                MessageChannel channel = sendMessage(game, event);
                Consumer<Message> callback = (game.isFowMode() && isSendingToPrivateChannel(game, event))
                        ? persistFowMessageId
                        : persistMessageId;
                MessageHelper.sendFileUploadToChannel(channel, fileUpload, callback);
            }
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                buttonEvent.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
            }
        });
    }

    public static void showFeature(Game game, GenericInteractionCreateEvent event, DisplayType feature) {
        if (!hasGameToShow(game, event) || !mayShowMap(game, event)) {
            return;
        }
        GenericInteractionCreateEvent renderEvent = renderEvent(fogMapAccess(game, event), event);
        Consumer<FileUpload> post =
                fileUpload -> MessageHelper.sendFileUploadToChannel(sendMessage(game, event), fileUpload);
        if (MapOverviewService.showsOverlayAsOverview(game, feature)) {
            boolean foggedView = renderEvent != null && MapSegmentService.isFoggedView(game, renderEvent);
            Player viewer =
                    foggedView ? FogViewer.resolve(game, renderEvent, null).player() : null;
            if (!foggedView || viewer != null) {
                MapOverviewService.queueOverlayOverview(game, viewer, feature, post);
                return;
            }
        }
        MapRenderPipeline.queue(game, renderEvent, feature, post);
    }

    public static void showMapWithoutButtons(Game game, GenericInteractionCreateEvent event, @Nullable String segment) {
        if (!hasGameToShow(game, event) || !mayShowMap(game, event)) {
            return;
        }
        MessageChannel channel = redirectedPrivateChannel(game, event).orElse(event.getMessageChannel());
        MapRenderPipeline.queue(
                game,
                renderEvent(fogMapAccess(game, event), event),
                DisplayType.map,
                segment,
                fileUpload -> MessageHelper.sendFileUploadToChannel(channel, fileUpload));
    }

    /**
     * Check if the map is being sent to a player's private channel (for FoW games).
     */
    private static boolean isSendingToPrivateChannel(Game game, GenericInteractionCreateEvent event) {
        return redirectedPrivateChannel(game, event).isPresent();
    }

    private static Optional<MessageChannel> redirectedPrivateChannel(Game game, GenericInteractionCreateEvent event) {
        if (!game.isFowMode() || event.getClass().equals(UserOverridenGenericInteractionCreateEvent.class)) {
            return Optional.empty();
        }
        Player player = actingPlayer(game, event);
        MessageChannel privateChannel = player != null ? player.getPrivateChannel() : null;
        boolean redirects = game.getRealPlayers().contains(player)
                && (!game.getPlayersWithGMRole().contains(player) || isFoggedGameChannel(game, event))
                && privateChannel != null
                && !event.getMessageChannel().equals(privateChannel);
        return redirects ? Optional.of(privateChannel) : Optional.empty();
    }

    private static void ephemeralShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        MapRenderPipeline.queue(
                game,
                renderEvent(fogMapAccess(game, event), event),
                displayType,
                fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }

    private static MessageChannel sendMessage(Game game, GenericInteractionCreateEvent event) {
        MessageChannel channel = event.getMessageChannel();
        if (!game.isFowMode()
                && game.getActionsChannel() != null
                && game.getBotMapUpdatesThread() != null
                && channel.equals(game.getActionsChannel())) {
            channel = game.getBotMapUpdatesThread();
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Map Image sent to " + game.getBotMapUpdatesThread().getJumpUrl());
        } else {
            Optional<MessageChannel> privateChannel = redirectedPrivateChannel(game, event);
            if (privateChannel.isPresent()) {
                channel = privateChannel.get();
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Map Image sent to " + channel.getJumpUrl());
            }
        }
        return channel;
    }

    private static boolean includeButtons(DisplayType displayType) {
        return switch (displayType) {
            case wormholes,
                    anomalies,
                    legendaries,
                    empties,
                    aetherstream,
                    spacecannon,
                    traits,
                    techskips,
                    attachments,
                    shipless,
                    unlocked -> false;
            default -> true;
        };
    }

    private static boolean hasGameToShow(Game game, GenericInteractionCreateEvent event) {
        if (game != null) {
            return true;
        }
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Could not find a game to show. Please use this from a current game channel.");
        return false;
    }
}
