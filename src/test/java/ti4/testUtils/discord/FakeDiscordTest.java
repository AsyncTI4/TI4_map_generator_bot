package ti4.testUtils.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class FakeDiscordTest extends BaseTi4Test {

    private final AtomicLong clock = new AtomicLong(1_800_000_000_000L);
    private FakeDiscord discord;
    private TextChannel actions;

    @BeforeEach
    void installFakeDiscord() {
        discord = new FakeDiscord(clock::get);
        JdaService.jda = discord.jda();
        JdaService.guildPrimary = discord.guild();
        actions = discord.createTextChannel("fakegame-actions");
    }

    @AfterEach
    void reportUnsupportedCalls() {
        System.out.println("Unsupported calls: " + discord.unsupportedCalls());
    }

    // The bot's own helper splits, sanitises and sends; the fake must receive the result like Discord would.
    @Test
    void receivesButtonsSentThroughMessageHelper() {
        MessageHelper.sendMessageToChannelWithButtons(
                actions, "Pick one", List.of(Button.success("a_1", "One"), Button.danger("b_2", "Two")));

        FakeMessage sent = discord.channel(actions.getIdLong()).liveMessages().getLast();
        assertThat(sent.content()).isEqualTo("Pick one");
        assertThat(sent.buttons()).extracting(Button::getCustomId).contains("a_1", "b_2");
        assertThat(sent.proxy().getAuthor().getIdLong()).isEqualTo(FakeDiscord.BOT_ID);
    }

    // Discord gives every component a unique id, including buttons nested in action rows; deleteTheOneButton
    // relies on that to remove just the pressed button.
    @Test
    void assignsUniqueIdsToNestedButtons() {
        Message message = actions.sendMessage("Buttons")
                .setComponents(ActionRow.of(Button.primary("x", "X"), Button.primary("y", "Y")))
                .complete();

        List<Button> buttons = message.getComponentTree().findAll(Button.class);
        assertThat(buttons)
                .extracting(Button::getUniqueId)
                .allMatch(id -> id > 0)
                .doesNotHaveDuplicates();
    }

    @Test
    void historyIsNewestFirstAndStampedByTheClock() {
        actions.sendMessage("first").queue();
        clock.addAndGet(5000);
        actions.sendMessage("second").queue();

        List<Message> history = actions.getHistory().retrievePast(10).complete();

        assertThat(history).extracting(Message::getContentRaw).containsExactly("second", "first");
        assertThat(history.getFirst().getTimeCreated().toInstant().toEpochMilli())
                .isEqualTo(clock.get());
    }

    // An edit that only replaces the components keeps the text, as on Discord.
    @Test
    void partialEditsKeepUntouchedFields() {
        Message message = actions.sendMessage("Keep me")
                .setComponents(ActionRow.of(Button.primary("old", "Old")))
                .complete();

        message.editMessageComponents(ActionRow.of(Button.primary("new", "New")))
                .complete();

        Message reread = actions.retrieveMessageById(message.getId()).complete();
        assertThat(reread.getContentRaw()).isEqualTo("Keep me");
        assertThat(reread.getComponentTree().findAll(Button.class))
                .extracting(Button::getCustomId)
                .containsExactly("new");
    }

    @Test
    void deletedMessagesAreUnknown() {
        Message message = actions.sendMessage("Gone soon").complete();
        message.delete().complete();

        assertThatThrownBy(() -> actions.retrieveMessageById(message.getId()).complete())
                .isInstanceOfSatisfying(
                        ErrorResponseException.class,
                        error -> assertThat(error.getErrorResponse()).isEqualTo(ErrorResponse.UNKNOWN_MESSAGE));
    }

    // A thread started from a message shares the message's id, which the strategy card code relies on.
    @Test
    void threadsStartedFromAMessageShareItsId() {
        Message message = actions.sendMessage("Strategy card").complete();

        ThreadChannel thread = actions.createThreadChannel("fakegame-round-1-sc", message.getIdLong())
                .complete();

        assertThat(thread.getIdLong()).isEqualTo(message.getIdLong());
        assertThat(actions.getThreadChannels()).contains(thread);
        assertThat(JdaService.jda.getThreadChannelById(thread.getId())).isSameAs(thread);
        assertThat(thread.getParentChannel()).isSameAs(actions);
    }

    @Test
    void rejectsMessagesWithDuplicateCustomIds() {
        actions.sendMessage("Twice")
                .setComponents(ActionRow.of(Button.primary("same", "A"), Button.primary("same", "B")))
                .queue();

        assertThat(discord.channel(actions.getIdLong()).liveMessages()).isEmpty();
        assertThat(discord.actionFailures()).anyMatch(failure -> failure.contains("duplicate custom_id same"));
    }
}
