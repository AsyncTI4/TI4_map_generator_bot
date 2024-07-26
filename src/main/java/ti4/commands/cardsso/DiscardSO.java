package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DiscardSO extends SOCardsSubcommandData {
    public DiscardSO() {
        super(Constants.DISCARD_SO, "Discard a secret objective.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID to discard").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found.");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "Please select which secret objective to discard.");
            return;
        }
        discardSO(event, player, option.getAsInt(), game);
    }

    public static void discardSO(GenericInteractionCreateEvent event, Player player, int SOID, Game game) {
        String soIDString = "";
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(SOID)) {
                soIDString = so.getKey();
            }
        }
        boolean removed = game.discardSecretObjective(player.getUserID(), SOID);
        if (!removed) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "No such secret objective ID found, please retry.");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "Secret objective has been discarded.");

        SOInfo.sendSecretObjectiveInfo(game, player);
        if (!soIDString.isEmpty()) {
            String msg = "You discarded the secret objectives " + Mapper.getSecretObjective(soIDString).getName() + ". If this was an accident, you can get it back with the below button."
                + " This will tell everyone that you made a mistake discarding and are picking back up the secret.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.secondary("drawSpecificSO_" + soIDString, "Retrieve " + Mapper.getSecretObjective(soIDString).getName()));
            buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
        }

        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (!game.getStoredValue(key).contains(player.getFaction() + "*")) {
            game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            Helper.resolveQueue(game);
        }

    }

    public static void drawSpecificSO(ButtonInteractionEvent event, Player player, String soID, Game game) {
        String publicMsg = game.getPing() + " this is a public notice that " + ButtonHelper.getIdentOrColor(player, game) + " is picking up a secret objective that they accidentally discarded.";
        Map<String, Integer> secrets = game.drawSpecificSecretObjective(soID, player.getUserID());
        if (secrets == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Secret objectives not retrieved, most likely because someone else has it in hand. Ping a bothelper to help.");
            return;
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), publicMsg);
        ButtonHelper.deleteMessage(event);
        SOInfo.sendSecretObjectiveInfo(game, player);
    }
}
