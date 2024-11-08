package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.commands.uncategorized.InfoThreadCommand;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.GenericCardModel;

public class ACInfo extends ACCardsSubcommandData implements InfoThreadCommand {
    public ACInfo() {
        super(Constants.INFO, "Send Action Cards to your Cards Info thread");
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendActionCardInfo(game, player, event);
        MessageHelper.sendMessageToEventChannel(event, "AC Info Sent");
    }

    private static void sendTrapCardInfo(Game game, Player player) {
        if (player.hasAbility("cunning") || player.hasAbility("subterfuge")) { // Lih-zo trap abilities
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, getTrapCardInfo(game, player));
        }
    }

    private static String getTrapCardInfo(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");
        sb.append("**Trap Cards:**").append("\n");
        int index = 1;
        Map<String, Integer> trapCards = player.getTrapCards();
        Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
        if (trapCards != null) {
            if (trapCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> trapCard : trapCards.entrySet()) {
                    Integer value = trapCard.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                    sb.append(getTrapCardRepresentation(trapCard.getKey(), trapCardsPlanets));
                    index++;
                }
            }
        }
        return sb.toString();
    }

    public static String getTrapCardRepresentation(String trapID, Map<String, String> trapCardsPlanets) {
        StringBuilder sb = new StringBuilder();
        GenericCardModel trap = Mapper.getTrap(trapID);
        String planet = trapCardsPlanets.get(trapID);

        sb.append(trap.getRepresentation());
        if (planet != null) {
            Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            String representation = planetRepresentations.get(planet);
            if (representation == null) {
                representation = planet;
            }
            sb.append(" **__Planet: ").append(representation).append("**__");
        }
        sb.append("\n");
        return sb.toString();
    }

    @ButtonHandler("refreshACInfo")
    public static void sendActionCardInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendActionCardInfo(game, player);
    }

    public static void sendActionCardInfo(Game game, Player player) {
        // AC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, getActionCardInfo(game, player));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "_ _\nClick a button below to play an Action Card", getPlayActionCardButtons(game, player));

        sendTrapCardInfo(game, player);
    }

    private static String getActionCardInfo(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");

        // ACTION CARDS
        sb.append("**Action Cards (").append(player.getAc()).append("/").append(ButtonHelper.getACLimit(game, player)).append("):**").append("\n");
        int index = 1;

        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null) {
            if (actionCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                    Integer value = ac.getValue();
                    ActionCardModel actionCard = Mapper.getActionCard(ac.getKey());

                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                    if (actionCard == null) {
                        sb.append("Something broke here");
                    } else {
                        sb.append(actionCard.getRepresentation());
                    }

                    index++;
                }
            }
        }

        return sb.toString();
    }

    private static List<Button> getPlayActionCardButtons(Game game, Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();

        if (actionCards != null && !actionCards.isEmpty()
            && !ButtonHelper.isPlayerElected(game, player, "censure")
            && !ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.red(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name, Emojis.ActionCard));
                }
            }
        }
        if (ButtonHelper.isPlayerElected(game, player, "censure") || ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            acButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard an AC (You are politically censured)"));
        } else {
            acButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard an AC"));
        }
        if (actionCards != null && !actionCards.isEmpty()
            && !ButtonHelper.isPlayerElected(game, player, "censure")
            && (actionCards.containsKey("coup") || actionCards.containsKey("disgrace") || actionCards.containsKey("special_session")
                || actionCards.containsKey("investments") || actionCards.containsKey("last_minute_deliberation") || actionCards.containsKey("revolution") || actionCards.containsKey("deflection") || actionCards.containsKey("summit"))) {
            acButtons.add(Buttons.gray("checkForAllACAssignments", "Pre assign ACs"));
        }

        return acButtons;
    }

    public static List<Button> getActionPlayActionCardButtons(Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                ActionCardModel actionCard = Mapper.getActionCard(key);
                String actionCardWindow = actionCard.getWindow();
                if (ac_name != null && "Action".equalsIgnoreCase(actionCardWindow)) {
                    acButtons.add(Buttons.red(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name, Emojis.ActionCard));
                }
            }
        }
        return acButtons;
    }

    public static void sendDiscardActionCardButtons(Player player, boolean doingAction) {
        List<Button> buttons = getDiscardActionCardButtons(player, doingAction);
        String msg = player.getRepresentationUnfogged() + " use buttons to discard";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void sendDiscardAndDrawActionCardButtons(Player player) {
        List<Button> buttons = getDiscardActionCardButtonsWithSuffix(player, "redraw");
        String msg = player.getRepresentationUnfogged() + " use buttons to discard. A new action card will be automatically drawn afterwards.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static List<Button> getDiscardActionCardButtons(Player player, boolean doingAction) {
        return getDiscardActionCardButtonsWithSuffix(player, doingAction ? "stall" : "");
    }

    public static List<Button> getDiscardActionCardButtonsWithSuffix(Player player, String suffix) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();

        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.blue("ac_discard_from_hand_" + value + suffix, "(" + value + ") " + ac_name, Emojis.ActionCard));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getYssarilHeroActionCardButtons(Player yssaril, Player notYssaril) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = notYssaril.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.gray("yssarilHeroInitialOffering_" + value + "_" + yssaril.getFaction(), ac_name, Emojis.ActionCard));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getToBeStolenActionCardButtons(Game game, Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Buttons.red("takeAC_" + value + "_" + player.getFaction(), ac_name, Emojis.ActionCard));
                }
            }
        }
        return acButtons;
    }
}
