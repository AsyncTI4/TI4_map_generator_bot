package ti4.commands.bothelper;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Emojis;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "Jazz's custom command");
        // addOptions(new OptionData(OptionType.INTEGER, "num_dice", "description", true).setRequiredRange(0, 1000));
        // addOptions(new OptionData(OptionType.INTEGER, "threshold", "description", true).setRequiredRange(1, 10));
        // addOptions(new OptionData(OptionType.INTEGER, "num_dice_2", "description", true).setRequiredRange(0, 1000));
        // addOptions(new OptionData(OptionType.INTEGER, "threshold_2", "description", true).setRequiredRange(1, 10));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!"228999251328368640".equals(event.getUser().getId())) {
            String jazz = MapGenerator.jda.getUserById("228999251328368640").getAsMention();
            if ("150809002974904321".equals(event.getUser().getId())) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz + ", but you are an honorary jazz so you may proceed");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
                return;
            }
        }

        Game activeGame = getActiveGame();
        String agendaID = activeGame.getNextAgenda(false);
        AgendaModel agenda = Mapper.getAgenda(agendaID);
        // Make an embed
        EmbedBuilder eb = AgendaHelper.buildAgendaEmbed(agenda);
        eb.addField(Emojis.nowhens + " __No Whens__", Emojis.Arborec + Emojis.Naaz + Emojis.Winnu + "\n", true);
        eb.addField(Emojis.noafters + " __No Afters__", Emojis.Arborec + Emojis.Naaz, true);

        MessageCreateBuilder mcb = new MessageCreateBuilder();
        mcb.addEmbeds(eb.build());

        event.getChannel().sendMessage(mcb.build()).queue();
    }
}