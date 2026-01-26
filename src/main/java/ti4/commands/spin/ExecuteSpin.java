package ti4.commands.spin;

import io.micrometer.common.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;
import ti4.service.map.SpinService;
import ti4.service.map.SpinService.AutoTrigger;
import ti4.service.map.SpinService.SpinSetting;

class ExecuteSpin extends GameStateSubcommand {
    private static final String TRIGGER = "trigger";

    private static final String ALL = "ALL";
    private static final String RND = "RND";

    public ExecuteSpin() {
        super(Constants.SPIN_EXECUTE, "Execute saved spin settings or use custom.", true, true);
        addOptions(new OptionData(
                OptionType.STRING, TRIGGER, "ID of spin setting, specific trigger, ALL or RND for random"));
        addOptions(new OptionData(OptionType.STRING, Constants.CUSTOM, "Custom spin string (1:CW:1)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String trigger = event.getOption(TRIGGER, "", OptionMapping::getAsString);
        String custom = event.getOption(Constants.CUSTOM, "", OptionMapping::getAsString);

        if (StringUtils.isBlank(trigger) && StringUtils.isBlank(custom)) {
            MessageHelper.replyToMessage(event, "Need to specify trigger or custom spin setting.");
            return;
        }

        if (game.isFowMode()
                && !getPlayer().isGM()
                && (StringUtils.isNotBlank(custom)
                        || List.of(ALL, RND, AutoTrigger.valuesAsStringList()).contains(trigger.toUpperCase()))) {
            MessageHelper.replyToMessage(event, "You are not authorized to use this command.");
            return;
        }

        List<SpinSetting> spinSettings = SpinService.getSpinSettings(game);
        List<SpinSetting> settingsToExecute = new ArrayList<>();
        if (ALL.equals(trigger)) {
            settingsToExecute.addAll(spinSettings);
        } else if (RND.equalsIgnoreCase(trigger)) {
            settingsToExecute.add(RandomHelper.pickRandomFromList(spinSettings));
        } else if (StringUtils.isNotBlank(trigger)) {
            AutoTrigger autoTrigger = AutoTrigger.fromString(trigger);
            if (autoTrigger != null) {
                spinSettings.stream()
                        .filter(setting -> autoTrigger == setting.trigger())
                        .forEach(settingsToExecute::add);
            } else {
                spinSettings.stream()
                        .filter(setting -> setting.id().equals(trigger))
                        .forEach(settingsToExecute::add);
                if (spinSettings.isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid trigger `" + trigger + "`");
                    return;
                }
            }
        }

        if (StringUtils.isNotBlank(custom)) {
            for (String customString : custom.split(" ")) {
                SpinSetting customSetting = SpinSetting.fromString(customString, null);
                if (!customSetting.isValid()) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(), "Invalid custom settings: `" + customString + "`");
                    return;
                }
                settingsToExecute.add(customSetting);
            }
        }

        if (settingsToExecute.isEmpty()) {
            MessageHelper.replyToMessage(event, "No valid spin setting found");
            return;
        }

        SpinService.executeSpinSettings(game, settingsToExecute);

        StringBuilder sb = new StringBuilder("Executed spin" + (settingsToExecute.size() > 1 ? "s" : "") + ":\n");
        for (SpinSetting s : settingsToExecute) {
            sb.append(s.getRepresentation(true)).append("\n");
        }
        if (game.isFowMode()
                && !event.getChannelId().equals(GMService.getGMChannel(game).getId())) {
            sb = new StringBuilder("Spin executed.");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return event.getChannel().getName().contains("-private");
    }
}
