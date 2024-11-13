package ti4.commands.bothelper;

import java.text.NumberFormat;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ListCategoryChannelCounts extends Subcommand {

    public ListCategoryChannelCounts(){
        super(Constants.CATEGORY_CHANNEL_COUNT, "List all categories and their channel counts.");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        StringBuilder sb = new StringBuilder("**__Categories:__**\n");

        int maxChannels = 50;
        List<Category> categories = guild.getCategories();
        for (Category category : categories) {
            int channelCount = category.getChannels().size();
            sb.append("> **").append(category.getName()).append("**: ").append(channelCount).append("/").append(maxChannels).append(getPercentage(channelCount, maxChannels)).append("\n");
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

    private String getPercentage(double numerator, double denominator) {
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        // formatPercent.setMinimumFractionDigits(1);
        String formatted = formatPercent.format(denominator == 0 ? 0.0 : (numerator / denominator));
        formatted = " *(" + formatted + ")* ";
        return formatted;
    }
}
