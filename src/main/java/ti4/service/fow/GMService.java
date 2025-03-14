package ti4.service.fow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.FoWHelper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.emoji.CardEmojis;

public class GMService {

    private static final List<Button> GMBUTTONS = Arrays.asList(
        Buttons.REFRESH_MAP,
        Buttons.SHOW_DECKS,
        Buttons.green("gmShowGameAs_", "Show Game As..."),
        Buttons.green("gmCheckPlayerHands_", "Check Player Hands for..."),
        Buttons.green("gmWhoCanSee~MDL", "Who Can See Position..."),
        Buttons.EDIT_NOTEPAD,
        Buttons.POST_NOTEPAD,
        Buttons.EDIT_SUMMARIES
    );

    private static final List<Button> HAND_CHECK_BUTTONS = Arrays.asList(
        Buttons.gray("gmCheckPlayerHands_sabotage", "Sabotages", CardEmojis.ActionCard), 
        Buttons.gray("gmCheckPlayerHands_whens", "Whens/Afters", CardEmojis.ActionCard), 
        Buttons.gray("gmCheckPlayerHands_deadly", "Deadly Plots/Briberies", CardEmojis.ActionCard), 
        Buttons.gray("gmCheckPlayerHands_confusing", "Confusing/Confounding", CardEmojis.ActionCard), 
        Buttons.DONE_DELETE_BUTTONS
    );

    public static void showGMButtons(Game game) {
        MessageHelper.sendMessageToChannelWithButtons(getGMChannel(game), "GM Buttons", GMBUTTONS);
    }

    public static TextChannel getGMChannel(Game game) {
        List<TextChannel> channels = game.getGuild().getTextChannelsByName(game.getName() + "-gm-room", true);
        return channels.isEmpty() ? null : channels.getFirst();
    }


    @ButtonHandler("gmShowGameAs_")
    public static void showGameAs(ButtonInteractionEvent event, String buttonID, Game game) {
        String faction = buttonID.replace("gmShowGameAs_", "");
        if (!"".equals(faction)) {
            Player showAs = game.getPlayerFromColorOrFaction(faction);
            ShowGameService.simpleShowGame(game, new UserOverridenGenericInteractionCreateEvent(event, showAs.getUser()));
        } else {
            List<Button> factionButtons = new ArrayList<>();
            for (Player player : game.getRealPlayers()) {
                factionButtons.add(Buttons.green("gmShowGameAs_" + player.getFaction(), player.getColor() + ", " + player.getUserName(), player.getFactionEmoji()));
            }
            factionButtons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(getGMChannel(game), "Select player who to view the game as:", factionButtons);
        }
    }

    @ButtonHandler("gmCheckPlayerHands_")
    public static void checkPlayerHands(ButtonInteractionEvent event, String buttonID, Game game) {
        String option = buttonID.replace("gmCheckPlayerHands_", "");
        switch (option) {
            case "sabotage" -> {
                checkWhoHas("sabo", game);
            }        
            case "deadly" -> {
                checkWhoHas("deadly_plot", game);
                checkWhoHas("bribery", game);
            }
            case "confusing" -> {
                checkWhoHas("confusing", game);
                checkWhoHas("confounding", game);
            }
            case "whens" -> {
              StringBuffer sbWhens = new StringBuffer("Following players have **whens** in hand:\n");
              StringBuffer sbAfters = new StringBuffer("Following players have **afters** in hand:\n");

              for (Player player : game.getRealPlayers()) {
                  List<String> whens = AgendaHelper.getPossibleWhenNames(player);
                  if (!whens.isEmpty()) {
                      sbWhens.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(": ").append(String.join(", ", whens)).append("\n");
                  }
                  List<String> afters = AgendaHelper.getPossibleAfterNames(player);
                  if (!afters.isEmpty()) {
                      sbAfters.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(": ").append(String.join(", ", afters)).append("\n");
                  }
              }
              MessageHelper.sendMessageToChannel(getGMChannel(game), sbWhens.toString());
              MessageHelper.sendMessageToChannel(getGMChannel(game), sbAfters.toString());
            }
            default -> {
                MessageHelper.sendMessageToChannelWithButtons(getGMChannel(game), "Select what to look for:", HAND_CHECK_BUTTONS);
            }
        }
    }

    @ButtonHandler("gmWhoCanSee~MDL")
    public static void whoCanSeePosition(ButtonInteractionEvent event) {
        String modalID = "whoCanSeePositionModal";
        String fieldID = "position";
        TextInput.Builder textInputBuilder = TextInput.create(fieldID, "Position", TextInputStyle.SHORT).setPlaceholder("000");
        Modal modal = Modal.create(modalID, "Who Can See Position").addActionRow(textInputBuilder.build()).build();
        event.replyModal(modal).queue();
    }

    @ModalHandler("whoCanSeePositionModal")
    public static void finishEditNotepad(ModalInteractionEvent event, Game game) {
        String position = event.getValue("position").getAsString();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(getGMChannel(game), "Position '" + position + "' is invalid.");
            return;
        }

        StringBuffer sb = new StringBuffer("Following players can see system **");
        sb.append(position).append("**:\n");
        for (Player player : FoWHelper.getAdjacentPlayers(game, position, false)) {
            sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append("\n");
        }
        MessageHelper.sendMessageToChannel(getGMChannel(game), sb.toString());
    }

    private static void checkWhoHas(String acId, Game game) {
        StringBuffer sb = new StringBuffer("Following players have **");
        sb.append(acId).append("** in hand:\n");
        for (Player player : game.getRealPlayers()) {
            for (String ac : player.getActionCards().keySet()) {
                if (ac.startsWith(acId)) {
                    sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append("\n");
                    break;
                }
            }
        }
        MessageHelper.sendMessageToChannel(getGMChannel(game), sb.toString());
    }

}
