package ti4.commands.agenda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
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

    public static void drawAgenda(GenericInteractionCreateEvent event, int count, Game activeGame, @NotNull Player player) {
        drawAgenda(event, count, false, activeGame, player);
    }

    public static void drawAgenda(GenericInteractionCreateEvent event, int count, boolean fromBottom, Game activeGame, @NotNull Player player) {
        drawAgenda(count, fromBottom, activeGame, player, false);
    }

    public static void drawAgenda(int count, Game activeGame, @NotNull Player player) {
        drawAgenda(count, false, activeGame, player, false);
    }

    public static void drawAgenda(int count, boolean fromBottom, Game activeGame, @NotNull Player player, boolean discard) {
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentation(true, true)).append(" here are the agenda(s) you have drawn:");
        Player realPlayer = Helper.getGamePlayer(activeGame, player, (Member) null, null);
        if (realPlayer == null || activeGame == null) return;

        MessageHelper.sendMessageToPlayerCardsInfoThread(realPlayer, activeGame, sb.toString());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = fromBottom ? activeGame.drawBottomAgenda() : activeGame.drawAgenda();
            if (entry != null) {
                AgendaModel agenda = Mapper.getAgenda(entry.getKey());
                List<MessageEmbed> agendaEmbed = Collections.singletonList(agenda.getRepresentationEmbed());

                List<Button> buttons = agendaButtons(agenda, entry.getValue(), discard);
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(realPlayer.getCardsInfoThread(), null, agendaEmbed, buttons);
            }
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(realPlayer, activeGame, "__Note: if you put both agendas on top, the second one you put will be revealed first!__");
    }

    private static List<Button> agendaButtons(AgendaModel agenda, Integer id, boolean discard) {
        List<Button> buttons = new ArrayList<>();
        Button topButton = Button.success("topAgenda_" + id, "Put " + agenda.getName() + " on the top of the agenda deck.").withEmoji(Emoji.fromUnicode("🔼"));
        Button bottomButton = Button.danger("bottomAgenda_" + id, "Put " + agenda.getName() + " on the bottom of the agenda deck.").withEmoji(Emoji.fromUnicode("🔽"));
        Button discardButton = Button.danger("discardAgenda_" + id, "Discard " + agenda.getName()).withEmoji(Emoji.fromUnicode("🗑️"));

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
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "You are not a player of this game");
            return;
        }
        drawAgenda(event, count, fromBottom, activeGame, player);
    }
}
