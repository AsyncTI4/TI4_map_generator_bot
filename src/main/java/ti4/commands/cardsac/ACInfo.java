package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.uncategorized.InfoThreadCommand;
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class ACInfo extends ACCardsSubcommandData implements InfoThreadCommand {
    public ACInfo() {
        super(Constants.INFO, "Send Action Cards to your Cards Info thread");
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendActionCardInfo(activeGame, player, event);
        MessageHelper.sendMessageToEventChannel(event, "AC Info Sent");
    }

    private static void sendTrapCardInfo(Game activeGame, Player player) {
        if (player.hasAbility("cunning") || player.hasAbility("subterfuge")) { // Lih-zo trap abilities
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getTrapCardInfo(activeGame, player));
        }
    }

    private static String getTrapCardInfo(Game activeGame, Player player) {
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
        Map<String, String> dsHandcards = Mapper.getDSHandcards();
        String info = dsHandcards.get(trapID);
        if (info == null) {
            return "";
        }
        String[] split = info.split(";");
        // String trapType = split[0];
        String trapName = split[1];
        String trapText = split[2];
        String planet = trapCardsPlanets.get(trapID);
        sb.append("__**").append(trapName).append("**__").append(" - ").append(trapText);
        if (planet != null) {
            Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            String representation = planetRepresentations.get(planet);
            if (representation == null) {
                representation = planet;
            }
            sb.append("__**");
            sb.append(" Planet: ");
            sb.append(representation);
            sb.append("**__");
        }
        sb.append("\n");
        return sb.toString();
    }

    public static void sendActionCardInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendActionCardInfo(activeGame, player);
    }

    public static void sendActionCardInfo(Game activeGame, Player player) {
        // AC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getActionCardInfo(activeGame, player));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                "_ _\nClick a button below to play an Action Card",
                getPlayActionCardButtons(activeGame, player));

        sendTrapCardInfo(activeGame, player);
    }

    private static String getActionCardInfo(Game activeGame, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");

        // ACTION CARDS
        sb.append("**Action Cards:**").append("\n");
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

    private static List<Button> getPlayActionCardButtons(Game activeGame, Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();

        if (actionCards != null && !actionCards.isEmpty()
                && !ButtonHelper.isPlayerElected(activeGame, player, "censure")
                && !ButtonHelper.isPlayerElected(activeGame, player, "absol_censure")) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Button.danger(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name)
                            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        acButtons.add(Button.primary("getDiscardButtonsACs", "Discard an AC"));
        if (player.hasUnexhaustedLeader("nekroagent")) {
            Button nekroButton = Button.secondary("exhaustAgent_nekroagent",
                                                  "Use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Nekro Malleon (Nekro Agent)")
                    .withEmoji(Emoji.fromFormatted(Emojis.Nekro));
            acButtons.add(nekroButton);
        }
        if (ButtonHelper.isPlayerElected(activeGame, player, "minister_peace")) {
            Button hacanButton = Button.secondary("ministerOfPeace", "Use Minister of Peace")
                    .withEmoji(Emoji.fromFormatted(Emojis.Agenda));
            acButtons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("vaylerianagent")) {
            Button nekroButton = Button.secondary("exhaustAgent_vaylerianagent",
                                                  "Use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Yvin Korduul (Vaylerian Agent)")
                    .withEmoji(Emoji.fromFormatted(Emojis.vaylerian));
            acButtons.add(nekroButton);
        }
        if (player.ownsUnit("ghost_mech")
                && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech", false) > 0
                && !activeGame.getLaws().containsKey("articles_war")) {
            Button ghostButton = Button.secondary("creussMechStep1_", "Use Ghost Mech")
                    .withEmoji(Emoji.fromFormatted(Emojis.Ghost));
            acButtons.add(ghostButton);
        }
        if (player.ownsUnit("nivyn_mech2")
                && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech", false) > 0
                && !activeGame.getLaws().containsKey("articles_war")) {
            Button ghostButton = Button.secondary("nivynMechStep1_", "Use Nivyn Mech")
                    .withEmoji(Emoji.fromFormatted(Emojis.nivyn));
            acButtons.add(ghostButton);
        }
        if (player.hasUnexhaustedLeader("kolleccagent")) {
            Button nekroButton = Button.secondary("exhaustAgent_kolleccagent",
                                                  "Use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Captain Dust (Kollecc Agent)")
                    .withEmoji(Emoji.fromFormatted(Emojis.kollecc));
            acButtons.add(nekroButton);
        }
        if (player.hasUnexhaustedLeader("mykomentoriagent")) {
            Button nekroButton = Button.secondary("exhaustAgent_mykomentoriagent",
                                                  "Use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Lactarius Indigo (Myko Agent)")
                    .withEmoji(Emoji.fromFormatted(Emojis.mykomentori));
            acButtons.add(nekroButton);
        }
        if (player.hasAbility("cunning")) {
            acButtons.add(Button.success("setTrapStep1", "Set a Trap"));
            acButtons.add(Button.danger("revealTrapStep1", "Reveal a Trap"));
            acButtons.add(Button.secondary("removeTrapStep1", "Remove a Trap"));
        }

        if (player.hasAbility("divination") && ButtonHelperAbilities.getAllOmenDie(activeGame).size() > 0) {
            StringBuilder omenDice = new StringBuilder();
            for (int omenDie : ButtonHelperAbilities.getAllOmenDie(activeGame)) {
                omenDice.append(" ").append(omenDie);
            }
            omenDice = new StringBuilder(omenDice.toString().trim());
            Button augers = Button.secondary("getOmenDice", "Use an omen die (" + omenDice + ")")
                    .withEmoji(Emoji.fromFormatted(Emojis.mykomentori));
            acButtons.add(augers);
        }
        if (actionCards != null && !actionCards.isEmpty()
                && !ButtonHelper.isPlayerElected(activeGame, player, "censure")
                && (actionCards.containsKey("coup") || actionCards.containsKey("disgrace")
                        || actionCards.containsKey("investments") || actionCards.containsKey("summit"))) {
            acButtons.add(Button.secondary("checkForAllACAssignments", "Pre assign ACs"));
        }

        return acButtons;
    }

    public static List<Button> getActionPlayActionCardButtons(Game activeGame, Player player) {
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
                    acButtons.add(Button.danger(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name)
                            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getDiscardActionCardButtons(Game activeGame, Player player, boolean doingAction) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();
        String stall = "";
        if (doingAction) {
            stall = "stall";
        }
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Button.primary("ac_discard_from_hand_" + value + stall, "(" + value + ") " + ac_name)
                            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getYssarilHeroActionCardButtons(Game activeGame, Player yssaril, Player notYssaril) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = notYssaril.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Button
                            .secondary("yssarilHeroInitialOffering_" + value + "_" + yssaril.getFaction(), ac_name)
                            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }

    public static List<Button> getToBeStolenActionCardButtons(Game activeGame, Player player) {
        List<Button> acButtons = new ArrayList<>();
        Map<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCard(key).getName();
                if (ac_name != null) {
                    acButtons.add(Button.danger("takeAC_" + value + "_" + player.getFaction(), ac_name)
                            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }
}
