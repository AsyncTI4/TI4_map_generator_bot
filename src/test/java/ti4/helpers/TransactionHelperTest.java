package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.discord.utility.DiscordChannelUtility;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.testUtils.BaseTi4Test;

class TransactionHelperTest extends BaseTi4Test {

    @Test
    void sendOfferOmitsPillageNoticeWhenNobodyCanPillage() {
        SendOfferHarness harness = createHarness();

        SentMessages messages = invokeSendOffer(harness);

        assertThat(messages.messagesFor(harness.mainChannel)).containsExactly(harness.mainChannelMessage());
        assertThat(messages.messagesFor(harness.tableTalkChannel))
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .startsWith(harness.publicPrefix())
                        .contains(harness.senderOfferLine())
                        .contains(harness.receiverOfferHeader())
                        .doesNotContain("NOTICE OF PROXIMITY SURCHARGE"));
        assertThat(messages.buttonMessagesFor(harness.senderThread))
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .startsWith(harness.senderThreadPrefix())
                        .contains(harness.senderOfferLine())
                        .contains(harness.receiverOfferHeader())
                        .doesNotContain("NOTICE OF PROXIMITY SURCHARGE"));
        assertThat(messages.buttonMessagesFor(harness.receiverThread))
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .startsWith(harness.receiverThreadPrefix())
                        .contains(harness.senderOfferLine())
                        .contains(harness.receiverOfferHeader())
                        .doesNotContain("NOTICE OF PROXIMITY SURCHARGE"));
        assertThat(messages.allText()).noneMatch(message -> message.contains("NOTICE OF PROXIMITY SURCHARGE"));
    }

    private static SentMessages invokeSendOffer(SendOfferHarness harness) {
        List<SentMessage> channelMessages = new ArrayList<>();
        List<SentMessage> buttonMessages = new ArrayList<>();

        try (MockedStatic<MessageHelper> mockedMessages = mockStatic(MessageHelper.class);
                MockedStatic<RandomHelper> mockedRandom = mockStatic(RandomHelper.class);
                MockedStatic<DiscordChannelUtility> mockedChannels = mockStatic(DiscordChannelUtility.class)) {
            mockedRandom.when(() -> RandomHelper.isOneInX(1000)).thenReturn(false);
            mockedMessages
                    .when(() -> MessageHelper.sendMessageToChannel(any(MessageChannel.class), anyString()))
                    .thenAnswer(invocation -> {
                        channelMessages.add(new SentMessage(
                                invocation.getArgument(0, MessageChannel.class),
                                invocation.getArgument(1, String.class)));
                        return null;
                    });
            mockedMessages
                    .when(() -> MessageHelper.sendMessageToChannelWithButtons(
                            any(MessageChannel.class), anyString(), anyList()))
                    .thenAnswer(invocation -> {
                        buttonMessages.add(new SentMessage(
                                invocation.getArgument(0, MessageChannel.class),
                                invocation.getArgument(1, String.class)));
                        return null;
                    });

            CacheRestAction<ThreadChannel> senderThreadLookup = mock(CacheRestAction.class);
            when(senderThreadLookup.complete()).thenReturn(harness.senderThread);
            CacheRestAction<ThreadChannel> receiverThreadLookup = mock(CacheRestAction.class);
            when(receiverThreadLookup.complete()).thenReturn(harness.receiverThread);
            mockedChannels
                    .when(() -> DiscordChannelUtility.retrieveThreadChannelById(harness.guild, 101L))
                    .thenReturn(senderThreadLookup);
            mockedChannels
                    .when(() -> DiscordChannelUtility.retrieveThreadChannelById(harness.guild, 202L))
                    .thenReturn(receiverThreadLookup);

            TransactionHelper.sendOffer(
                    harness.game, harness.sender, "sendOffer_" + harness.receiver.getFaction(), harness.event);
        }

        return new SentMessages(channelMessages, buttonMessages, harness);
    }

    private static SendOfferHarness createHarness() {
        JDA jda = mock(JDA.class);
        JdaService.jda = jda;
        when(jda.getPresence()).thenReturn(mock(Presence.class));

        Game game = new Game();
        game.setName("offer-game");
        game.setMainChannelID("main-channel");
        game.setTableTalkChannelID("table-talk");

        TextChannel mainChannel = mock(TextChannel.class);
        TextChannel tableTalkChannel = mock(TextChannel.class);
        Guild guild = mock(Guild.class);
        when(jda.getTextChannelById("main-channel")).thenReturn(mainChannel);
        when(jda.getTextChannelById("table-talk")).thenReturn(tableTalkChannel);
        when(mainChannel.getGuild()).thenReturn(guild);

        Player senderReal = addPlayer(game, "sender-id", "Sender", "arborec", "red");
        Player receiverReal = addPlayer(game, "receiver-id", "Receiver", "hacan", "blue");
        senderReal.setTg(3);
        senderReal.addTransactionItem(
                "sending" + senderReal.getFaction() + "_receiving" + receiverReal.getFaction() + "_TGs_3");
        senderReal.setCardsInfoThreadID("101");
        receiverReal.setCardsInfoThreadID("202");

        Player sender = spy(senderReal);
        Player receiver = spy(receiverReal);
        LinkedHashSet<Player> receiverNeighbors = new LinkedHashSet<>();
        receiverNeighbors.add(sender);
        doReturn(new LinkedHashSet<>(List.of(receiver))).when(sender).getNeighbouringPlayers(false);
        doReturn(new LinkedHashSet<>(List.of(receiver))).when(sender).getNeighbouringPlayers(true);
        doReturn(new LinkedHashSet<>(List.of(sender))).when(receiver).getNeighbouringPlayers(false);
        doReturn(receiverNeighbors).when(receiver).getNeighbouringPlayers(true);

        var players = new LinkedHashMap<String, Player>();
        players.put(sender.getUserID(), sender);
        players.put(receiver.getUserID(), receiver);
        game.setPlayers(players);

        ThreadChannel senderThread = mock(ThreadChannel.class);
        ThreadChannel receiverThread = mock(ThreadChannel.class);
        Message interactionMessage = mock(Message.class, RETURNS_DEEP_STUBS);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-05-09T06:58:03.416Z"));
        when(event.getMessage()).thenReturn(interactionMessage);

        return new SendOfferHarness(
                game,
                sender,
                receiver,
                receiverNeighbors,
                mainChannel,
                tableTalkChannel,
                guild,
                senderThread,
                receiverThread,
                event);
    }

    private static Player addPlayer(Game game, String userId, String userName, String faction, String color) {
        Player player = game.addPlayer(userId, userName);
        player.setFaction(game, faction);
        player.setColor(color);
        return player;
    }

    private record SendOfferHarness(
            Game game,
            Player sender,
            Player receiver,
            Set<Player> receiverNeighbors,
            TextChannel mainChannel,
            TextChannel tableTalkChannel,
            Guild guild,
            ThreadChannel senderThread,
            ThreadChannel receiverThread,
            ButtonInteractionEvent event) {

        private String senderOfferLine() {
            return "> " + sender.getRepresentation(false, false, true) + " gives:\n> - " + MiscEmojis.tg(3);
        }

        private String receiverOfferHeader() {
            return "> " + receiver.getRepresentation(false, false, true) + " gives:";
        }

        private String mainChannelMessage() {
            return sender.getRepresentationNoPing() + " sent a transaction offer to "
                    + receiver.getRepresentationNoPing() + ".";
        }

        private String publicPrefix() {
            return "Trade offer from " + sender.getRepresentationNoPing() + " to " + receiver.getRepresentationNoPing()
                    + ":\n";
        }

        private String senderThreadPrefix() {
            return sender.getRepresentationNoPing() + " you sent a transaction offer to "
                    + receiver.getRepresentationNoPing() + ":\n";
        }

        private String receiverThreadPrefix() {
            return receiver.getRepresentation() + " you have received a transaction offer from "
                    + sender.getRepresentationNoPing() + ":\n";
        }
    }

    private record SentMessage(MessageChannel channel, String message) {}

    private record SentMessages(
            List<SentMessage> channelMessages, List<SentMessage> buttonMessages, SendOfferHarness harness) {

        private List<String> messagesFor(MessageChannel channel) {
            return channelMessages.stream()
                    .filter(message -> message.channel() == channel)
                    .map(SentMessage::message)
                    .toList();
        }

        private List<String> buttonMessagesFor(MessageChannel channel) {
            return buttonMessages.stream()
                    .filter(message -> message.channel() == channel)
                    .map(SentMessage::message)
                    .toList();
        }

        private List<String> allText() {
            List<String> allText = new ArrayList<>();
            allText.addAll(channelMessages.stream().map(SentMessage::message).toList());
            allText.addAll(buttonMessages.stream().map(SentMessage::message).toList());
            return allText;
        }
    }
}
