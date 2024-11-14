package ti4.commands.agenda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;
import ti4.buttons.Buttons;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

public class DrawAgenda extends AgendaSubcommandData {

    public DrawAgenda() {
        super(Constants.DRAW, "Draw Agenda");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
        addOptions(new OptionData(OptionType.BOOLEAN, "from_bottom", "Whether to draw from bottom, default false"));
    }

    public static void drawAgenda(int count, Game game, @NotNull Player player) {
        drawAgenda(count, false, game, player, false);
    }

    public static void drawAgenda(int count, boolean fromBottom, Game game, @NotNull Player player, boolean discard) {
        String sb = player.getRepresentationUnfogged() + " here " + (count == 1 ? "is" : "are") + " the agenda" + (count == 1 ? "" : "s") + " you have drawn:";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb);
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = fromBottom ? game.drawBottomAgenda() : game.drawAgenda();
            if (entry != null) {
                AgendaModel agenda = Mapper.getAgenda(entry.getKey());
                List<MessageEmbed> agendaEmbed = Collections.singletonList(agenda.getRepresentationEmbed());

                List<Button> buttons = agendaButtons(agenda, entry.getValue(), discard);
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, agendaEmbed, buttons);
            }
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "__Note: if you put both agendas on top, the second one you put will be revealed first!__");
    }

    public static void drawSpecificAgenda(String agendaID, Game game, @NotNull Player player) {
        String sb = player.getRepresentationUnfogged() + " here is the agenda you have drawn:";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb);

        Map.Entry<String, Integer> entry = game.drawSpecificAgenda(agendaID);
        if (entry != null) {
            AgendaModel agenda = Mapper.getAgenda(entry.getKey());
            List<MessageEmbed> agendaEmbed = Collections.singletonList(agenda.getRepresentationEmbed());

            List<Button> buttons = agendaButtons(agenda, entry.getValue(), false);
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, agendaEmbed, buttons);
        }

    }

    private static List<Button> agendaButtons(AgendaModel agenda, Integer id, boolean discard) {
        List<Button> buttons = new ArrayList<>();
        Button topButton = Buttons.green("topAgenda_" + id, "Put " + agenda.getName() + " on the top of the agenda deck.").withEmoji(Emoji.fromUnicode("🔼"));
        Button bottomButton = Buttons.red("bottomAgenda_" + id, "Put " + agenda.getName() + " on the bottom of the agenda deck.").withEmoji(Emoji.fromUnicode("🔽"));
        Button discardButton = Buttons.red("discardAgenda_" + id, "Discard " + agenda.getName()).withEmoji(Emoji.fromUnicode("🗑️"));

        buttons.add(topButton);
        if (!discard) {
            buttons.add(bottomButton);
        } else {
            buttons.add(discardButton);
        }
        return buttons;
    }

    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }
        OptionMapping fromBottomOption = event.getOption("from_bottom");
        boolean fromBottom = fromBottomOption != null && fromBottomOption.getAsBoolean();
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "You are not a player of this game");
            return;
        }
        drawAgenda(count, fromBottom, game, player, false);
    }
}
