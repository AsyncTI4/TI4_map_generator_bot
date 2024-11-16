package ti4.commands2.cardsac;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PickACFromDiscard extends GameStateSubcommand {

    public PickACFromDiscard() {
        super(Constants.PICK_AC_FROM_DISCARD, "Pick an Action Card from discard pile into your hand", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();
        getActionCardFromDiscard(event, getGame(), getPlayer(), acIndex);
    }

    public static void getActionCardFromDiscard(GenericInteractionCreateEvent event, Game game, Player player, int acIndex) {
        String acId = null;
        for (Map.Entry<String, Integer> ac : game.getDiscardActionCards().entrySet()) {
            if (ac.getValue().equals(acIndex)) {
                acId = ac.getKey();
            }
        }

        if (acId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        boolean picked = game.pickActionCard(player.getUserID(), acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String sb = "Game: " + game.getName() + " " +
            "Player: " + player.getUserName() + "\n" +
            "Picked card from Discards: " +
            Mapper.getActionCard(acId).getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);

        ActionCardHelper.sendActionCardInfo(game, player);
    }

    @ButtonHandler("codexCardPick_")
    public static void pickACardFromDiscardStep1(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String acStringID : game.getDiscardActionCards().keySet()) {
            buttons.add(Buttons.green("pickFromDiscard_" + acStringID, Mapper.getActionCard(acStringID).getName()));
        }
        buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        if (buttons.size() > 25) {
            buttons.add(25, Buttons.red("deleteButtons_", "Delete These Buttons"));
        }
        if (buttons.size() > 50) {
            buttons.add(50, Buttons.red("deleteButtons_2", "Delete These Buttons"));
        }
        if (buttons.size() > 75) {
            buttons.add(75, Buttons.red("deleteButtons_3", "Delete These Buttons"));
        }
        String msg = player.getRepresentationUnfogged() + " use buttons to grab an AC from the discard";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("pickFromDiscard_")
    public static void pickACardFromDiscardStep2(Game game, Player player, ButtonInteractionEvent event,        String buttonID) {
        ButtonHelper.deleteMessage(event);
        String acID = buttonID.replace("pickFromDiscard_", "");
        boolean picked = game.pickActionCard(player.getUserID(), game.getDiscardActionCards().get(acID));
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String msg2 = player.getRepresentationUnfogged() + " grabbed " + Mapper.getActionCard(acID).getName() + " from the discard";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);

        ActionCardHelper.sendActionCardInfo(game, player, event);
        if (player.hasAbility("autonetic_memory")) {
            String message = player.getRepresentationUnfogged() + " if you did not just use the Codex to get that AC, please discard 1 AC due to your Cybernetic Madness ability";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, ActionCardHelper.getDiscardActionCardButtons(player, false));
        }
    }
}
