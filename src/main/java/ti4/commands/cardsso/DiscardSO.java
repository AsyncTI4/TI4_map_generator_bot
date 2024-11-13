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
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class DiscardSO extends GameStateSubcommand {

    public DiscardSO() {
        super(Constants.DISCARD_SO, "Discard Secret Objective", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        Game game = getGame();
        Player player = getPlayer();
        discardSO(event, player, option.getAsInt(), game);
    }

    @ButtonHandler("SODISCARD_")
    @ButtonHandler("discardSecret_")
    private static void discardSecretButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String soID = buttonID.replace("SODISCARD_", "");
        soID = soID.replace("discardSecret_", "");

        boolean drawReplacement = false;
        if (soID.endsWith("redraw")) {
            soID = soID.replace("redraw", "");
            drawReplacement = true;
        }

        try {
            int soIndex = Integer.parseInt(soID);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " discarded an SO");
            discardSO(event, player, soIndex, game);
            if (drawReplacement) {
                DrawSO.drawSO(event, game, player);
            }
        } catch (Exception e) {
            BotLogger.log(event, "Could not parse SO ID: " + soID, e);
            event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please discard manually.").queue();
            return;
        }
        ButtonHelper.deleteMessage(event);
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
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "No such Secret Objective ID found, please retry");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "SO Discarded");

        SOInfo.sendSecretObjectiveInfo(game, player);
        if (!soIDString.isEmpty()) {
            String msg = "You discarded the SO " + Mapper.getSecretObjective(soIDString).getName() + ". If this was an accident, you can get it back with the below button. This will tell everyone that you made a mistake discarding and are picking back up the secret.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray("drawSpecificSO_" + soIDString, "Retrieve " + Mapper.getSecretObjective(soIDString).getName()));
            buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
        }

        handleSecretObjectiveDrawOrder(game, player);
    }

    @ButtonHandler("drawSpecificSO_")
    public static void drawSpecificSO(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String soID = buttonID.split("_")[1];
        String publicMsg = game.getPing() + " this is a public notice that " + player.getFactionEmojiOrColor() + " is picking up a secret that they accidentally discarded.";
        Map<String, Integer> secrets = game.drawSpecificSecretObjective(soID, player.getUserID());
        if (secrets == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "SO not retrieved, most likely because someone else has it in hand. Ping a bothelper to help.");
            return;
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), publicMsg);
        ButtonHelper.deleteMessage(event);
        SOInfo.sendSecretObjectiveInfo(game, player);
    }

    private static void handleSecretObjectiveDrawOrder(Game game, Player player) {
        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        if (game.getStoredValue(key2)
                .contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                    game.getStoredValue(key2)
                            .replace(player.getFaction() + "*", ""));
        }
        if (!game.getStoredValue(key)
                .contains(player.getFaction() + "*")) {
            game.setStoredValue(key,
                    game.getStoredValue(key)
                            + player.getFaction() + "*");
        }
        if (game.getStoredValue(key3)
                .contains(player.getFaction() + "*")) {
            game.setStoredValue(key3,
                    game.getStoredValue(key3)
                            .replace(player.getFaction() + "*", ""));
            Helper.resolveQueue(game);
        }
    }
}
