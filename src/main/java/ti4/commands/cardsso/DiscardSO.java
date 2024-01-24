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
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DiscardSO extends SOCardsSubcommandData {
    public DiscardSO() {
        super(Constants.DISCARD_SO, "Discard Secret Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame,"Please select what Secret Objective to discard");
            return;
        }
        discardSO(event, player, option.getAsInt(), activeGame);
    }
    public void discardSO(GenericInteractionCreateEvent event, Player player, int SOID, Game activeGame) {
        String soIDString = "";
        for (Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(SOID)) {
                soIDString = so.getKey();
            }
        }
        boolean removed = activeGame.discardSecretObjective(player.getUserID(), SOID);
        if (!removed) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame,"No such Secret Objective ID found, please retry");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame,"SO Discarded");
        
        SOInfo.sendSecretObjectiveInfo(activeGame, player);
        if(!soIDString.isEmpty()){
            String msg = "You discarded the SO "+Mapper.getSecretObjective(soIDString).getName()+". If this was an accident, you can get it back with the below button. This will tell everyone that you made a mistake discarding and are picking back up the secret.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.secondary("drawSpecificSO_"+soIDString, "Retrieve "+Mapper.getSecretObjective(soIDString).getName()));
            buttons.add(Button.danger("deleteButtons","Delete These Buttons"));
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),msg, buttons);
        }

        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        if(activeGame.getFactionsThatReactedToThis(key2).contains(player.getFaction()+"*")){
            activeGame.setCurrentReacts(key2, activeGame.getFactionsThatReactedToThis(key2).replace(player.getFaction()+"*",""));
        }
        if(!activeGame.getFactionsThatReactedToThis(key).contains(player.getFaction()+"*")){
            activeGame.setCurrentReacts(key, activeGame.getFactionsThatReactedToThis(key)+player.getFaction()+"*");
        }
        if(activeGame.getFactionsThatReactedToThis(key3).contains(player.getFaction()+"*")){
            activeGame.setCurrentReacts(key3, activeGame.getFactionsThatReactedToThis(key3).replace(player.getFaction()+"*",""));
            Helper.resolveQueue(activeGame, event);
        }
        
        
    }
    public static void drawSpecificSO(ButtonInteractionEvent event, Player player, String soID, Game activeGame){
        String publicMsg = activeGame.getPing() + " this is a public notice that "+ButtonHelper.getIdentOrColor(player, activeGame)+" is picking up a secret that they accidentally discarded.";
        Map<String, Integer> secrets = activeGame.drawSpecificSecretObjective(soID, player.getUserID());
        if (secrets == null){
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "SO not retrieved, most likely because someone else has it in hand. Ping a bothelper to help.");
            return;
        }
        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), publicMsg);
        event.getMessage().delete().queue();
        SOInfo.sendSecretObjectiveInfo(activeGame, player);
    }
}
