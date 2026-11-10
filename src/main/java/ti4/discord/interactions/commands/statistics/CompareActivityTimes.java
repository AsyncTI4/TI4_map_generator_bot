package ti4.discord.interactions.commands.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class CompareActivityTimes extends Subcommand {

    private static final int MAX_ROLE_MEMBERS = 300;

    private static final List<String> PLAYER_OPTIONS_TO_CHECK = List.of(
            Constants.PLAYER1,
            Constants.PLAYER2,
            Constants.PLAYER3,
            Constants.PLAYER4,
            Constants.PLAYER5,
            Constants.PLAYER6,
            Constants.PLAYER7,
            Constants.PLAYER8);

    CompareActivityTimes() {
        super(Constants.COMPARE_ACTIVITY_TIMES, "Compare different players set Activity Times");
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE1, "The role to compare (maximum 300 members)"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player @playerName"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Member> members = new ArrayList<>();
        Role role = event.getOption(Constants.ROLE1, null, OptionMapping::getAsRole);
        if (role != null) {
            List<Member> roleMembers = event.getGuild().getMembersWithRoles(role);
            if (roleMembers.size() > MAX_ROLE_MEMBERS) {
                MessageHelper.sendMessageToEventChannel(
                        event, "That role has more than 300 members. Please choose a smaller role to compare.");
                return;
            }
            members.addAll(roleMembers);
        }
        PLAYER_OPTIONS_TO_CHECK.stream()
                .map(playerOptionName -> event.getOption(playerOptionName, null, OptionMapping::getAsMember))
                .filter(Objects::nonNull)
                .forEach(members::add);
        members = members.stream().distinct().toList();
        if (members.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No valid players or roles provided to compare.");
            return;
        }

        String message = CreateGameButtonHandler.generateMemberListMessage(members, "Activity Times", false);
        if (members.size() > 10) {
            MessageHelper.sendMessageToThread(event.getChannel(), "Compare Activity Times", message);
            return;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }
}
