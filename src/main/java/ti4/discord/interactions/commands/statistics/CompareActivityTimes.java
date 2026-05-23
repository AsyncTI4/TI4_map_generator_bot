package ti4.discord.interactions.commands.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.service.statistics.StatisticsThreadHelper;

class CompareActivityTimes extends Subcommand {

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
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE1, "The role you want to compare"));
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
        if (event.getOption(Constants.ROLE1) != null) {
            Role role = event.getOption(Constants.ROLE1).getAsRole();
            if (role != null) {
                members.addAll(event.getGuild().getMembersWithRoles(role));
            }
        }
        PLAYER_OPTIONS_TO_CHECK.stream()
                .map(playerOptionName -> event.getOption(playerOptionName, null, OptionMapping::getAsUser))
                .filter(Objects::nonNull)
                .map(User::getId)
                .map(GameManager::getManagedPlayer)
                .forEach(player -> {
                    members.add(event.getGuild().getMemberById(player.getId()));
                });
        if (members.isEmpty()) {
            StatisticsThreadHelper.sendMessage(event, "No valid players or roles provided to compare.");
            return;
        }

        StatisticsThreadHelper.sendMessage(
                event,
                CreateGameButtonHandler.generateMemberListMessage(members, "Activity Times", false));
    }
}
