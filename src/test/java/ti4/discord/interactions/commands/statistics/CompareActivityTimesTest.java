package ti4.discord.interactions.commands.statistics;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class CompareActivityTimesTest {

    private final CompareActivityTimes command = new CompareActivityTimes();
    private final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    private final MessageChannelUnion channel = mock(MessageChannelUnion.class);

    @Test
    void registersOptionalRoleSelection() {
        assertTrue(command.getOptions().stream()
                .anyMatch(option -> option.getName().equals(Constants.ROLE1)
                        && option.getType() == OptionType.ROLE
                        && !option.isRequired()));
    }

    @Test
    void rejectsRolesOver300BeforeGeneratingStatistics() {
        selectRoleWithMembers(members(301));

        try (var generator = mockStatic(CreateGameButtonHandler.class);
                var messages = mockStatic(MessageHelper.class)) {
            command.execute(event);

            generator.verifyNoInteractions();
            messages.verify(() -> MessageHelper.sendMessageToEventChannel(
                    event, "That role has more than 300 members. Please choose a smaller role to compare."));
            messages.verifyNoMoreInteractions();
        }
    }

    @Test
    void accepts300MembersAndSendsResultsToThread() {
        List<Member> members = members(300);
        selectRoleWithMembers(members);
        when(event.getChannel()).thenReturn(channel);

        try (var generator = mockStatic(CreateGameButtonHandler.class);
                var messages = mockStatic(MessageHelper.class)) {
            generator.when(() -> CreateGameButtonHandler.generateMemberListMessage(members, "Activity Times", false))
                    .thenReturn("comparison");

            command.execute(event);

            generator.verify(() -> CreateGameButtonHandler.generateMemberListMessage(members, "Activity Times", false));
            messages.verify(() -> MessageHelper.sendMessageToThread(channel, "Compare Activity Times", "comparison"));
            messages.verifyNoMoreInteractions();
        }
    }

    @Test
    void combinesRoleAndIndividualPlayersWithoutDuplicates() {
        List<Member> members = members(2);
        selectRoleWithMembers(List.of(members.getFirst()));
        selectPlayer(Constants.PLAYER1, members.getFirst());
        selectPlayer(Constants.PLAYER2, members.getLast());
        selectPlayer(Constants.PLAYER3, members.getLast());
        verifyChannelComparison(members);
    }

    @Test
    void stillComparesIndividualPlayersWithoutARole() {
        List<Member> members = members(2);
        selectPlayer(Constants.PLAYER1, members.getFirst());
        selectPlayer(Constants.PLAYER2, members.getLast());
        verifyChannelComparison(members);
    }

    @Test
    void reportsEmptyRoleWithoutGeneratingStatistics() {
        selectRoleWithMembers(List.of());

        try (var generator = mockStatic(CreateGameButtonHandler.class);
                var messages = mockStatic(MessageHelper.class)) {
            command.execute(event);

            generator.verifyNoInteractions();
            messages.verify(() -> MessageHelper.sendMessageToEventChannel(
                    event, "No valid players or roles provided to compare."));
        }
    }

    private void selectRoleWithMembers(List<Member> members) {
        Role role = mock(Role.class);
        Guild guild = mock(Guild.class);
        doReturn(role).when(event).getOption(eq(Constants.ROLE1), isNull(), any());
        when(event.getGuild()).thenReturn(guild);
        when(guild.getMembersWithRoles(role)).thenReturn(members);
    }

    private void selectPlayer(String option, Member member) {
        doReturn(member).when(event).getOption(eq(option), isNull(), any());
    }

    private void verifyChannelComparison(List<Member> members) {
        when(event.getChannel()).thenReturn(channel);
        try (var generator = mockStatic(CreateGameButtonHandler.class);
                var messages = mockStatic(MessageHelper.class)) {
            generator.when(() -> CreateGameButtonHandler.generateMemberListMessage(members, "Activity Times", false))
                    .thenReturn("comparison");

            command.execute(event);

            generator.verify(() -> CreateGameButtonHandler.generateMemberListMessage(members, "Activity Times", false));
            messages.verify(() -> MessageHelper.sendMessageToChannel(channel, "comparison"));
            messages.verifyNoMoreInteractions();
        }
    }

    private static List<Member> members(int count) {
        return IntStream.range(0, count).mapToObj(index -> mock(Member.class)).toList();
    }
}
