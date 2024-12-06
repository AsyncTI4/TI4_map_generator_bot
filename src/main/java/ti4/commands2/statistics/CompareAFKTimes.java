package ti4.commands2.statistics;

import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.users.UserSettingsManager;

class CompareAFKTimes extends Subcommand {

    private static final List<String> PLAYER_OPTIONS_TO_CHECK = List.of(
        Constants.PLAYER1, Constants.PLAYER2, Constants.PLAYER3, Constants.PLAYER4,
        Constants.PLAYER5, Constants.PLAYER6, Constants.PLAYER7, Constants.PLAYER8);

    public CompareAFKTimes() {
        super(Constants.COMPARE_AFK_TIMES, "Compare different players set AFK Times");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player @playerName").setRequired(true));
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
        StringBuilder stringBuilder = new StringBuilder();

        PLAYER_OPTIONS_TO_CHECK.stream()
            .map(playerOptionName -> event.getOption(playerOptionName, null, OptionMapping::getAsUser))
            .filter(Objects::nonNull)
            .map(User::getId)
            .map(GameManager::getManagedPlayer)
            .forEach(player -> {
                var afkTime = UserSettingsManager.get(player.getId()).getAfkHours();
                if (afkTime != null) {
                    stringBuilder.append(player.getName()).append(" afk hours are: ").append(afkTime.replace(";", ", ")).append("\n");
                } else {
                    stringBuilder.append("AFK hours are not set for: ").append(player.getName()).append("\n");
                }
            });

        MessageHelper.sendMessageToChannel(event.getChannel(), stringBuilder.toString());
    }
}