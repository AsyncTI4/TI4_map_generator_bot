package ti4.commands.bothelper;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

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
        if (!event.getUser().getId().equals("228999251328368640")) {
            String jazz = MapGenerator.jda.getUserById("228999251328368640").getAsMention();
            if (event.getUser().getId().equals("150809002974904321")) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz + ", but you are an honorary jazz so you may proceed");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
                return;
            }
        }

        Game activeGame = getActiveMap();
        String agendaID = activeGame.getNextAgenda(false);
        AgendaModel agenda = Mapper.getAgenda(agendaID);
        String image = Emoji.fromFormatted(Emojis.Scout).asCustom().getImageUrl();

        // Make an embed
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(Emojis.Agenda + " " + agenda.getName());
        // eb.setThumbnail(image);

        StringBuilder desc = new StringBuilder("**").append(agenda.getType()).append(":** *").append(agenda.getTarget()).append("*\n");
        desc.append("> ").append(agenda.getText1().replace("For:", "**For:**")).append("\n");
        desc.append("> ").append(agenda.getText2().replace("Against:", "**Against:**"));
        eb.setDescription(desc.toString());
        eb.setFooter(agenda.footnote());

        for (Player p : activeGame.getPlayers().values()) {
            String title = Helper.getFactionIconFromDiscord(p.getFaction()) + " " + p.getUserName();
            eb.addField(title, AgendaHelper.getPlayerVoteText(activeGame, p), false);
        }

        MessageCreateBuilder mcb = new MessageCreateBuilder();
        mcb.addEmbeds(eb.build());

        event.getChannel().sendMessage(mcb.build()).queue();
    }
}

/*
 * public String getRepresentation(@Nullable Integer uniqueID) {
 * StringBuilder sb = new StringBuilder();
 * 
 * sb.append("**__");
 * if (uniqueID != null) {
 * sb.append("(").append(uniqueID).append(") - ");
 * }
 * sb.append(name).append("__** ");
 * switch (source) {
 * case "absol" -> sb.append(Emojis.Absol);
 * case "PoK" -> sb.append(Emojis.AgendaWhite);
 * default -> sb.append(Emojis.AsyncTI4Logo);
 * }
 * sb.append("\n");
 * 
 * sb.append("> **").append(type).append(":** *").append(target).append("*\n");
 * if (text1.length() > 0) {
 * String arg = text1.replace("For:", "**For:**");
 * sb.append("> ").append(arg).append("\n");
 * }
 * if (text2.length() > 0) {
 * String arg = text2.replace("Against:", "**Against:**");
 * sb.append("> ").append(arg).append("\n");
 * }
 * 
 * switch (alias) {
 * case ("mutiny") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Mutiny public_vp_worth:1`").append("\n");
 * case ("seed_empire") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Seed of an Empire public_vp_worth:1`").append("\n");
 * case ("censure") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Political Censure public_vp_worth:1`").append("\n");
 * }
 * 
 * return sb.toString();
 * }
 */