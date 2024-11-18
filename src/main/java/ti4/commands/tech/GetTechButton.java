package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.player.TurnStart;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

public class GetTechButton extends TechSubcommandData {
    public GetTechButton() {
        super(Constants.BUTTON, "Force the add tech button to display");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", Buttons.GET_A_TECH);
    }

    @ButtonHandler("getTech_")
    public static void getTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String ident = player.getFactionEmoji();
        boolean paymentRequired = !buttonID.contains("__noPay");
        final String[] buttonIDComponents = buttonID.split("__");
        buttonID = buttonIDComponents[0];
        final String paymentType = buttonIDComponents.length > 1 ? buttonIDComponents[1] : "res";

        String techID = StringUtils.substringAfter(buttonID, "getTech_");
        techID = AliasHandler.resolveTech(techID);
        if (!Mapper.isValidTech(techID)) {
            BotLogger.log(event, "`ButtonHelper.getTech` Invalid TechID in 'getTech_' Button: " + techID);
            return;
        }
        TechnologyModel techM = Mapper.getTech(techID);
        StringBuilder message = new StringBuilder(ident).append(" acquired the technology: ")
            .append(techM.getRepresentation(false));

        if (techM.getRequirements().isPresent() && techM.getRequirements().get().length() > 1) {
            CommanderUnlockCheck.checkPlayer(player, "zealots");
        }
        player.addTech(techID);
        if (techM.isUnitUpgrade()) {
            if (player.hasUnexhaustedLeader("mirvedaagent") && player.getStrategicCC() > 0) {
                List<Button> buttons = new ArrayList<>();
                Button mirvedaButton = Buttons.gray("exhaustAgent_mirvedaagent_" + player.getFaction(), "Use Mirveda Agent", Emojis.mirveda);
                buttons.add(mirvedaButton);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged()
                    + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Logic Machina, the Mirveda"
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to spend 1 CC and research a tech of the same color as a prerequisite of the tech you just got.",
                    buttons);
            }
            if (player.hasAbility("obsessive_designs") && paymentRequired
                && "action".equalsIgnoreCase(game.getPhaseOfGame())) {
                String msg = player.getRepresentation()
                    + " due to your obsessive designs ability, you may use your space dock at home PRODUCTION ability to build units of the type you just upgraded, reducing the total cost by 2.";
                String generalMsg = player.getFactionEmojiOrColor()
                    + " has an opportunity to use their obsessive designs ability to build " + techM.getName()
                    + " at home";
                List<Button> buttons;
                Tile tile = game.getTile(AliasHandler.resolveTile(player.getFaction()));
                if (player.hasAbility("mobile_command")
                    && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship).isEmpty()) {
                    tile = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship).getFirst();
                }
                if (tile == null) {
                    tile = player.getHomeSystemTile();
                }
                if (tile == null) {
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find a HS, sorry bro");
                }
                buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "obsessivedesigns", "place");
                int val = Helper.getProductionValue(player, game, tile, true);
                String message2 = msg + ButtonHelper.getListOfStuffAvailableToSpend(player, game) + "\n"
                    + "You have " + val + " PRODUCTION value in this system";
                if (val > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
                    message2 = message2
                        + ". You also have the That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 fighters/infantry that don't count towards production limit";
                }
                if (val > 0 && ButtonHelper.isPlayerElected(game, player, "prophecy")) {
                    message2 = message2 + "Reminder that you have Prophecy of Ixth and should produce 2 fighters if you want to keep it. Its removal is not automated";
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), generalMsg);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Produce Units", buttons);
            }
        }

        if (player.hasUnexhaustedLeader("zealotsagent")) {
            List<Button> buttons = new ArrayList<>();
            Button zealotsButton = Buttons.gray("exhaustAgent_zealotsagent_" + player.getFaction(), "Use Zealots Agent", Emojis.zealots);
            buttons.add(zealotsButton);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                    + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Priestess Tuh, the Rhodun"
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to produce 1 ship at home or in a system where you have a tech skip planet.",
                buttons);
        }

        ButtonHelperFactionSpecific.resolveResearchAgreementCheck(player, techID, game);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        if ("iihq".equalsIgnoreCase(techID)) {
            message.append("\n Automatically added the Custodia Vigilia planet");
        }
        if ("cm".equalsIgnoreCase(techID) && game.getActivePlayer() != null
            && game.getActivePlayerID().equalsIgnoreCase(player.getUserID()) && !player.getSCs().contains(7)) {
            if (!game.isFowMode()) {
                try {
                    if (game.getLatestTransactionMsg() != null && !game.getLatestTransactionMsg().isEmpty()) {
                        game.getMainGameChannel().deleteMessageById(game.getLatestTransactionMsg()).queue();
                        game.setLatestTransactionMsg("");
                    }
                } catch (Exception e) {
                    // Block of code to handle errors
                }
            }
            String text = player.getRepresentationUnfogged() + " UP NEXT";
            String buttonText = "Use buttons to do your turn. ";
            if (game.getName().equalsIgnoreCase("pbd1000") || game.getName().equalsIgnoreCase("pbd100two")) {
                buttonText = buttonText + "Your SC number is #" + player.getSCs().toArray()[0];
            }
            List<Button> buttons = TurnStart.getStartOfTurnButtons(player, game, true, event);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), text);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), buttonText, buttons);
        }
        CommanderUnlockCheck.checkPlayer(player, "jolnar", "nekro", "mirveda", "dihmohn");

        if (game.isComponentAction() || !"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        } else {
            ButtonHelper.sendMessageToRightStratThread(player, game, message.toString(), "technology");
            String key = "TechForRound" + game.getRound() + player.getFaction();
            if (game.getStoredValue(key).isEmpty()) {
                game.setStoredValue(key, techID);
            } else {
                game.setStoredValue(key, game.getStoredValue(key) + "." + techID);
            }
            postTechSummary(game);
        }
        if (paymentRequired) {
            payForTech(game, player, event, techID, paymentType);
        } else {
            if (player.hasLeader("zealotshero") && player.getLeader("zealotshero").get().isActive()) {
                if (game.getStoredValue("zealotsHeroTechs").isEmpty()) {
                    game.setStoredValue("zealotsHeroTechs", techID);
                } else {
                    game.setStoredValue("zealotsHeroTechs",
                        game.getStoredValue("zealotsHeroTechs") + "-" + techID);
                }
            }
        }
        if (player.hasUnit("augers_mech") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " has the opportunity to deploy an Augur mech on a legendary planet or planet with a tech skip");
            String message2 = player.getRepresentationUnfogged() + " Use buttons to drop 1 mech on a legendary planet or planet with a tech skip";
            List<Button> buttons2 = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message2, buttons2);
        }

        ButtonHelper.deleteMessage(event);
    }

    public static void postTechSummary(Game game) {
        if (game.isFowMode() || game.getTableTalkChannel() == null
            || !game.getStoredValue("TechSummaryRound" + game.getRound()).isEmpty() || game.isHomebrewSCMode()) {
            return;
        }
        StringBuilder msg = new StringBuilder("**__Tech Summary For Round " + game.getRound() + "__**\n");
        for (Player player : game.getRealPlayers()) {
            if (!player.hasFollowedSC(7)) {
                return;
            }
            String key = "TechForRound" + game.getRound() + player.getFaction();
            msg.append(player.getFactionEmoji()).append(":");
            String key2 = "RAForRound" + game.getRound() + player.getFaction();
            if (!game.getStoredValue(key2).isEmpty()) {
                msg.append("(From RA: ");
                if (game.getStoredValue(key2).contains(".")) {
                    for (String tech : game.getStoredValue(key2).split("\\.")) {
                        msg.append(" ").append(Mapper.getTech(tech).getNameRepresentation());
                    }

                } else {
                    msg.append(" ").append(Mapper.getTech(game.getStoredValue(key2)).getNameRepresentation());
                }
                msg.append(")");
            }
            if (!game.getStoredValue(key).isEmpty()) {
                if (game.getStoredValue(key).contains(".")) {
                    String tech1 = StringUtils.substringBefore(game.getStoredValue(key), ".");
                    String tech2 = StringUtils.substringAfter(game.getStoredValue(key), ".");
                    msg.append(" ").append(Mapper.getTech(tech1).getNameRepresentation());
                    for (String tech2Plus : tech2.split("\\.")) {
                        msg.append("and ").append(Mapper.getTech(tech2Plus).getNameRepresentation());
                    }

                } else {
                    msg.append(" ").append(Mapper.getTech(game.getStoredValue(key)).getNameRepresentation());
                }
                msg.append("\n");
            } else {
                msg.append(" Did not follow for tech\n");
            }
        }
        String key2 = "TechForRound" + game.getRound() + "Counter";
        if (game.getStoredValue(key2).equalsIgnoreCase("0")) {
            MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), msg.toString());
            game.setStoredValue("TechSummaryRound" + game.getRound(), "yes");
            GameSaveLoadManager.saveGame(game, "Tech Summary Posted");
        } else {
            if (game.getStoredValue(key2).isEmpty()) {
                game.setStoredValue(key2, "6");
            }
        }
    }

    /**
     * Generate buttons to pay for tech.
     * 
     * @param game
     * @param player
     * @param event
     * @param tech
     * @param payWith Possible values: {@code ["res", "inf"]}
     */
    public static void payForTech(Game game, Player player, ButtonInteractionEvent event, String tech, final String payWith) {
        String trueIdentity = player.getRepresentationUnfogged();
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
        String payType = payWith != null ? payWith : "res";
        if (!payType.equals("res") && !payType.equals("inf")) {
            payType = "res";
        }
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, payType + "tech");
        TechnologyModel techM = Mapper.getTechs().get(AliasHandler.resolveTech(tech));
        if (techM.isUnitUpgrade() && player.hasTechReady("aida")) {
            Button aiDEVButton = Buttons.red("exhaustTech_aida", "Exhaust AI Development Algorithm");
            buttons.add(aiDEVButton);
        }
        if (techM.isUnitUpgrade() && player.hasTechReady("absol_aida")) {
            Button aiDEVButton = Buttons.red("exhaustTech_absol_aida", "Exhaust AI Development Algorithm");
            buttons.add(aiDEVButton);
        }
        if (!techM.isUnitUpgrade() && player.hasAbility("iconoclasm")) {

            for (int x = 1; x < player.getCrf() + 1; x++) {
                Button transact = Buttons.blue("purge_Frags_CRF_" + x, "Purge Cultural Fragments (" + x + ")", Emojis.CFrag);
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getIrf() + 1 && x < 4); x++) {
                Button transact = Buttons.green("purge_Frags_IRF_" + x, "Purge Industrial Fragments (" + x + ")", Emojis.IFrag);
                buttons.add(transact);
            }

            for (int x = 1; (x < player.getHrf() + 1 && x < 4); x++) {
                Button transact = Buttons.red("purge_Frags_HRF_" + x, "Purge Hazardous Fragments (" + x + ")", Emojis.HFrag);
                buttons.add(transact);
            }

            for (int x = 1; x < player.getUrf() + 1; x++) {
                Button transact = Buttons.gray("purge_Frags_URF_" + x, "Purge Frontier Fragments (" + x + ")", Emojis.UFrag);
                buttons.add(transact);
            }

        }
        if (player.hasTechReady("is")) {
            Button inheritanceSystemsButton = Buttons.gray("exhaustTech_is", "Exhaust Inheritance Systems");
            buttons.add(inheritanceSystemsButton);
        }
        if (player.hasRelicReady("prophetstears")) {
            Button pT1 = Buttons.red("prophetsTears_AC", "Exhaust Prophets Tears for AC");
            buttons.add(pT1);
            Button pT2 = Buttons.red("prophetsTears_TechSkip", "Exhaust Prophets Tears for Tech Skip");
            buttons.add(pT2);
        }
        if (player.hasExternalAccessToLeader("jolnaragent") || player.hasUnexhaustedLeader("jolnaragent")) {
            Button pT2 = Buttons.gray("exhaustAgent_jolnaragent", "Use Jol-Nar Agent", Emojis.Jolnar);
            buttons.add(pT2);
        }
        if (player.hasUnexhaustedLeader("veldyragent")) {
            Button winnuButton = Buttons.red("exhaustAgent_veldyragent_" + player.getFaction(), "Use Veldyr Agent", Emojis.veldyr);
            buttons.add(winnuButton);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            Button pT2 = Buttons.gray("yinCommanderStep1_", "Remove infantry via Yin Commander", Emojis.Yin);
            buttons.add(pT2);
        }
        Button doneExhausting = Buttons.red("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
        if (ButtonHelper.isLawInPlay(game, "revolution")) {
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                player.getRepresentation()
                    + " Due to the Anti-Intellectual Revolution law, you now have to kill a non-fighter ship if you researched the tech you just acquired",
                Buttons.gray("getModifyTiles", "Modify Units"));
        }
    }
}
