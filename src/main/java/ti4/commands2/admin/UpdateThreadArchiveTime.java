package ti4.commands2.admin;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class UpdateThreadArchiveTime extends Subcommand {

    public UpdateThreadArchiveTime() {
        super(Constants.UPDATE_THREAD_ARCHIVE_TIME, "Update the AutoArchiveDuration for all currently open threads that contain the search string");
        addOptions(new OptionData(OptionType.STRING, Constants.THREAD_SEARCH_STRING, "Any thread containing this string will be updated.").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.AUTO_ARCHIVE_DURATION, "The autoarchive duration to set. Must be picked from the list.").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.THREAD_SEARCH_STRING, null, OptionMapping::getAsString).toLowerCase();
        if (searchString.isEmpty() || searchString.isBlank()) {
            MessageHelper.sendMessageToEventChannel(event, "Please do better with the search string.");
            return;
        }

        String autoArchiveDurationString = event.getOption(Constants.AUTO_ARCHIVE_DURATION, null, OptionMapping::getAsString);

        AutoArchiveDuration autoArchiveDuration = null;

        switch (autoArchiveDurationString) {
            case "1_HOUR" -> autoArchiveDuration = AutoArchiveDuration.TIME_1_HOUR;
            case "24_HOURS" -> autoArchiveDuration = AutoArchiveDuration.TIME_24_HOURS;
            case "3_DAYS" -> autoArchiveDuration = AutoArchiveDuration.TIME_3_DAYS;
            case "1_WEEK" -> autoArchiveDuration = AutoArchiveDuration.TIME_1_WEEK;
        }

        if (autoArchiveDuration == null) {
            MessageHelper.sendMessageToEventChannel(event, "Must pick autoArchive duration from the list");
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(event, "What did you do wrong this time?");
            return;
        }

        AutoArchiveDuration autoArchiveDuration_ = autoArchiveDuration;
        List<ThreadChannel> threadChannels = guild.getThreadChannels().stream().filter(tc -> tc.getName().toLowerCase().contains(searchString) && tc.getAutoArchiveDuration() != autoArchiveDuration_).toList();

        StringBuilder sb = new StringBuilder("**__Threads Updated__**\n");
        for (ThreadChannel threadChannel : threadChannels) {
            threadChannel.getManager().setAutoArchiveDuration(autoArchiveDuration).queue();
            sb.append("> ").append(threadChannel.getAsMention()).append("\n");
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

}
