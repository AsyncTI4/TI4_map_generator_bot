package ti4.commands.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

class SurveyResults extends Subcommand {

    SurveyResults() {
        super("survey_results", "See overall results of bots survey");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<UserSettings> users = UserSettingsManager.getAllUserSettings();
        Map<String, Integer> question1Data = new HashMap<>();
        Map<String, Integer> question2Data = new HashMap<>();
        Map<String, Integer> question3Data = new HashMap<>();
        Map<String, Integer> question4Data = new HashMap<>();
        Map<String, Integer> question5Data = new HashMap<>();

        users.stream().filter(UserSettings::isHasAnsweredSurvey).forEach(user -> {
            incrementCount(question1Data, user.getWhisperPref());
            incrementCount(question2Data, user.getSupportPref());
            incrementCount(question3Data, user.getTakebackPref());
            incrementCount(question4Data, user.getWinmakingPref());
            incrementCount(question5Data, user.getMetaPref());
        });

        String result = "# __Survey Results__:\n" + generateQuestionResult("## Question #1: Whispers\n", question1Data)
                + generateQuestionResult("## Question #2: Supports\n", question2Data)
                + generateQuestionResult("## Question #3: How To Handle Rollback Disputes\n", question3Data)
                + generateQuestionResult("## Question #4: Winmaking\n", question4Data)
                + generateQuestionResult("## Question #5: Meta Preferences\n", question5Data);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);
    }

    private void incrementCount(Map<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private String generateQuestionResult(String questionHeader, Map<String, Integer> data) {
        return questionHeader
                + data.entrySet().stream()
                        .map(entry -> "* " + entry.getKey() + ": " + entry.getValue() + "\n")
                        .collect(Collectors.joining());
    }
}
