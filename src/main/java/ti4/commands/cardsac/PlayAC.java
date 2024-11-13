package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.commands.player.TurnStart;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.TemporaryCombatModifierModel;

class PlayAC extends GameStateSubcommand {

    public PlayAC() {
        super(Constants.PLAY_AC, "Play an Action Card", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION_CARD_ID, "Action Card ID that is sent between () or Name/Part of Name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String acId = event.getOption(Constants.ACTION_CARD_ID).getAsString().toLowerCase();

        String reply = playAC(event, game, player, acId, event.getChannel());
        if (reply != null) {
            MessageHelper.sendMessageToEventChannel(event, reply);
        }
    }

    public static String playAC(GenericInteractionCreateEvent event, Game game, Player player, String value, MessageChannel channel) {
        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(acIndex)) {
                    acID = so.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
                String actionCardName = Mapper.getActionCard(ac.getKey()).getName();
                if (actionCardName != null) {
                    actionCardName = actionCardName.toLowerCase();
                    if (actionCardName.contains(value)) {
                        if (foundSimilarName && !cardName.equals(actionCardName)) {
                            return "Multiple cards with similar name founds, please use ID";
                        }
                        acID = ac.getKey();
                        acIndex = ac.getValue();
                        foundSimilarName = true;
                        cardName = actionCardName;
                    }
                }
            }
        }
        if (acID == null) {
            return "No such Action Card ID found, please retry";
        }
        return resolveActionCard(event, game, player, acID, acIndex, channel);
    }

    public static String resolveActionCard(GenericInteractionCreateEvent event, Game game, Player player, String acID, int acIndex, MessageChannel channel) {
        MessageChannel mainGameChannel = game.getMainGameChannel() == null ? channel : game.getMainGameChannel();
        ActionCardModel actionCard = Mapper.getActionCard(acID);
        String actionCardTitle = actionCard.getName();
        String actionCardWindow = actionCard.getWindow();

        String activePlayerID = game.getActivePlayerID();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = game.getPlayer(activePlayerID);
            if (activePlayer != null && activePlayer.hasTech("tp")) {
                return "You are passed and the active player has researched Transparasteel Plating. AC Play command cancelled.";
            }
        }
        if ("Action".equalsIgnoreCase(actionCardWindow) && game.getPlayer(activePlayerID) != player) {
            return "You are trying to play a component action AC and the game does not think you are the active player. You may fix this with /player turn_start. Until then, you are #denied.";
        }
        if (ButtonHelper.isPlayerOverLimit(game, player)) {
            return player.getRepresentationUnfogged()
                + " The bot thinks you are over the limit and thus will not allow you to play ACs at this time. You may discard the AC and manually resolve if you need to.";
        }

        if (player.hasAbility("cybernetic_madness")) {
            game.purgedActionCard(player.getUserID(), acIndex);
        } else {
            game.discardActionCard(player.getUserID(), acIndex);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(game.getPing()).append(" ").append(game.getName()).append("\n");
        if (game.isFowMode()) {
            sb.append("Someone played the Action Card ").append(actionCardTitle).append(":\n");
        } else {
            sb.append(player.getRepresentation()).append(" played the Action Card ").append(actionCardTitle).append(":\n");
        }

        List<Button> buttons = new ArrayList<>();
        Button sabotageButton = Buttons.red("sabotage_ac_" + actionCardTitle, "Cancel AC With Sabotage", Emojis.Sabotage);
        buttons.add(sabotageButton);
        Player empy = Helper.getPlayerFromUnit(game, "empyrean_mech");
        if (empy != null && ButtonHelperFactionSpecific.isNextToEmpyMechs(game, player, empy) && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            Button empyButton = Buttons.gray("sabotage_empy_" + actionCardTitle, "Cancel " + actionCardTitle + " With Empyrean Mech ", Emojis.mech);
            List<Button> empyButtons = new ArrayList<>();
            empyButtons.add(empyButton);
            Button refuse = Buttons.red("deleteButtons", "Delete These Buttons");
            empyButtons.add(refuse);
            MessageHelper.sendMessageToChannelWithButtons(empy.getCardsInfoThread(),
                empy.getRepresentationUnfogged()
                    + "You have mech(s) adjacent to the player who played the AC. Use buttons to decide whether to cancel.",
                empyButtons);
        }
        String instinctTrainingID = "it";
        for (Player player2 : game.getPlayers().values()) {
            if (!player.equals(player2) && player2.hasTechReady(instinctTrainingID) && player2.getStrategicCC() > 0) {
                Button instinctButton = Buttons.gray("sabotage_xxcha_" + actionCardTitle, "Cancel " + actionCardTitle + " With Instinct Training", Emojis.Xxcha);
                List<Button> xxchaButtons = new ArrayList<>();
                xxchaButtons.add(instinctButton);
                Button refuse = Buttons.red("deleteButtons", "Delete These Buttons");
                xxchaButtons.add(refuse);
                MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), player2.getRepresentationUnfogged() + "You have Instinct Training unexhausted and a CC available. Use Buttons to decide whether to cancel", xxchaButtons);
            }

        }
        MessageEmbed acEmbed = actionCard.getRepresentationEmbed();
        Button noSabotageButton = Buttons.blue("no_sabotage", "No Sabotage", Emojis.NoSabotage);
        buttons.add(noSabotageButton);
        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "moveAlongAfterAllHaveReactedToAC_" + actionCardTitle, "Pause Timer While Waiting For Sabo"));
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannelWithEmbed(mainGameChannel, sb.toString(), acEmbed);
        } else {
            String buttonLabel = "Resolve " + actionCardTitle;

            if (Helper.isSaboAllowed(game, player)) {
                MessageHelper.sendMessageToChannelWithEmbedsAndFactionReact(mainGameChannel, sb.toString(), game, player, Collections.singletonList(acEmbed), buttons, true);
            } else {
                MessageHelper.sendMessageToChannelWithEmbed(mainGameChannel, sb.toString(), acEmbed);
                StringBuilder noSabosMessage = new StringBuilder("> " + Helper.noSaboReason(game, player));
                boolean it = false, watcher = false;
                for (Player p : game.getRealPlayers()) {
                    if (p == player) continue;
                    if (game.isFowMode() || (!it && p.hasTechReady("it"))) {
                        noSabosMessage.append("\n> A player may have access to " + Emojis.Xxcha + "**Instinct Training**, watch out");
                        it = true;
                    }
                    if (game.isFowMode() || (!watcher && Helper.getPlayerFromUnit(game, "empyrean_mech") != null)) {
                        noSabosMessage.append("\n> A player may have access to " + Emojis.Empyrean + Emojis.mech + "**Watcher**, watch out");
                        watcher = true;
                    }
                }
                MessageHelper.sendMessageToChannel(mainGameChannel, noSabosMessage.toString());
            }
            MessageChannel channel2 = player.getCorrectChannel();
            if (actionCardTitle.contains("Manipulate Investments")) {
                List<Button> scButtons = new ArrayList<>();
                for (int sc : game.getSCList()) {
                    Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
                    Button button;
                    if (scEmoji != null && scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")) {
                        button = Buttons.gray(player.getFinsFactionCheckerPrefix() + "increaseTGonSC_" + sc, " ").withEmoji(scEmoji);
                    } else {
                        button = Buttons.gray(player.getFinsFactionCheckerPrefix() + "increaseTGonSC_" + sc, sc + " " + Helper.getSCName(sc, game));
                    }
                    scButtons.add(button);
                }
                scButtons.add(Buttons.red("deleteButtons", "Done adding TG"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, player.getRepresentation() + " Use buttons to increase TGs on SCs. Each press adds 1TG.", scButtons);
            }
            if (actionCardTitle.contains("Deflection")) {
                List<Button> scButtons = new ArrayList<>();
                for (int sc : game.getSCList()) {
                    Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
                    Button button;
                    if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")) {
                        button = Buttons.gray(player.getFinsFactionCheckerPrefix() + "deflectSC_" + sc, " ")
                            .withEmoji(scEmoji);
                    } else {
                        button = Buttons.gray(player.getFinsFactionCheckerPrefix() + "deflectSC_" + sc,
                            sc + " " + Helper.getSCName(sc, game));
                    }
                    scButtons.add(button);
                }
                MessageHelper.sendMessageToChannelWithButtons(channel2,
                    player.getRepresentation() + " Use buttons to choose which SC will be deflected.",
                    scButtons);
            }

            if (actionCardTitle.contains("Archaeological Expedition")) {
                List<Button> scButtons = ButtonHelperActionCards.getArcExpButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(channel2, player.getRepresentation() + " After checking for Sabos, use buttons to explore a planet type thrice and gain any fragments.", scButtons);
            }

            if (actionCardTitle.contains("Planetary Rigs")) {
                List<Button> acbuttons = ButtonHelperHeroes.getAttachmentSearchButtons(game, player);
                String msg = player.getRepresentation() + " After checking for Sabos, first declare what planet you mean to put an attachment on, then hit the button to resolve.";
                if (acbuttons.isEmpty()) {
                    msg = player.getRepresentation() + " there were no attachments found in the applicable exploration decks.";
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, acbuttons);
            }

            String codedMessage = player.getRepresentation() + " After checking for Sabos, use buttons to resolve. Reminder that all card targets (besides tech RESEARCH) should be declared now, before people decide on sabos. Resolve ";
            String codedMsg = codedMessage + actionCardTitle;

            List<Button> codedButtons = new ArrayList<>();
            if (actionCardTitle.contains("Plagiarize")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "getPlagiarizeButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Mining Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "miningInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Revolution")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "willRevolution", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Last Minute Deliberation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "lastMinuteDeliberation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Special Session")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveVeto", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("War Machine")) {
                player.addSpentThing("warmachine");
            }

            if (actionCardTitle.contains("Economic Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "economicInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Confounding Legal Text")) {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(codedMsg, codedButtons, player, game);
            }
            if (actionCardTitle.contains("Confusing Legal Text")) {
                codedButtons.add(Buttons.green("autoresolve_manual", buttonLabel));
                sendResolveMsgToMainChannel(codedMsg, codedButtons, player, game);
            }

            if (actionCardTitle.contains("Reveal Prototype")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Spatial Collapse")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "spatialCollapseStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Side Project")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "sideProject", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Brutal Occupation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "brutalOccupation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Stolen Prototype")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Skilled Retreat")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "retreat_" + game.getActiveSystem() + "_skilled", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Reparations")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveReparationsStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Distinguished Councilor")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDistinguished", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Uprising")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUprisingStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Tomb Raiders")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveTombRaiders", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Technological Breakthrough")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "technologicalBreakthrough", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Assassinate Representative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveAssRepsStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Signal Jamming")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSignalJammingStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Spy")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSpyStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Political Stability")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePSStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Plague")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePlagueStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Experimental Battlestation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveEBSStep1_" + game.getActiveSystem(), buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Blitz")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveBlitz_" + game.getActiveSystem(), buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Shrapnel Turrets")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveShrapnelTurrets_" + game.getActiveSystem(), buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Micrometeoroid Storm")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveMicrometeoroidStormStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Upgrade")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUpgrade_" + game.getActiveSystem(), buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(channel2, "The active system is currently non-existant, so this card cannot be automated");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
                }
            }
            if (actionCardTitle.contains("Infiltrate")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveInfiltrateStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg + ". Warning, this will not work if the player has already removed their structures", codedButtons);
            }
            if (actionCardTitle.contains("Emergency Repairs")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveEmergencyRepairs_" + game.getActiveSystem(), buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(channel2, "The active system is currently non-existant, so this card cannot be automated");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
                }
            }
            if (actionCardTitle.contains("Insider Information")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveInsiderInformation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Cripple Defenses")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveCrippleDefensesStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Impersonation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveImpersonation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Ancient Burial Sites")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveABSStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Salvage")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSalvageStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Insubordination")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveInsubStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Frontline Deployment")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveFrontline", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Unexpected Action")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUnexpected", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Data Archive")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDataArchive", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Ancient Trade Routes")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveAncientTradeRoutes", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Flank Speed")) {
                game.setStoredValue("flankspeedBoost", "1");
            }
            if (actionCardTitle.contains("Sister Ship")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSisterShip", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Boarding Party")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveBoardingParty", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCard.getAlias().equals("mercenary_contract")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveMercenaryContract", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Chain Reaction")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveChainReaction", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Rendezvous Point")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveRendezvousPoint", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Flawless Strategy")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveFlawlessStrategy", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Arms Deal")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveArmsDeal", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Defense Installation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDefenseInstallation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Harness Energy")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveHarness", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("War Effort")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveWarEffort", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Free Trade Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveFreeTrade", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Preparation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePreparation", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Summit")) {
                codedButtons.add(Buttons.green("resolveSummit", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Scuttle")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "startToScuttleAUnit_0", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Lucky Shot")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "startToLuckyShotAUnit_0", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Refit Troops")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveRefitTroops", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Seize Artifact")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveSeizeArtifactStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Diplomatic Pressure")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDiplomaticPressureStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Renegotiation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDiplomaticPressureStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Decoy Operation")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveDecoyOperationStep1_" + game.getActiveSystem(), buttonLabel));
                if (game.getActiveSystem().isEmpty()) {
                    MessageHelper.sendMessageToChannel(channel2, "The active system is currently non-existant, so this card cannot be automated");
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
                }
            }
            if (actionCardTitle.contains("Reactor Meltdown")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveReactorMeltdownStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Unstable Planet")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveUnstableStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Ghost Ship")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveGhostShipStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Stranded Ship")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "strandedShipStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Tactical Bombardment")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveTacticalBombardmentStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Exploration Probe")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveProbeStep1", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Rally")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveRally", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Industrial Initiative")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "industrialInitiative", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Repeal Law")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "getRepealLawButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Divert Funding")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "getDivertFundingButtons", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }
            if (actionCardTitle.contains("Emergency Meeting")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveEmergencyMeeting", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Focused Research")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "focusedResearch", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Forward Supply Base")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "forwardSupplyBase", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Rise of a Messiah")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "riseOfAMessiah", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            if (actionCardTitle.contains("Veto")) {
                codedButtons.add(
                    Buttons.blue(player.getFinsFactionCheckerPrefix() + "resolveVeto", "Reveal next Agenda"));
                sendResolveMsgToMainChannel(codedMsg, codedButtons, player, game);
            }

            if (actionCardTitle.contains("Fighter Conscription")) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "fighterConscription", buttonLabel));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMsg, codedButtons);
            }

            TemporaryCombatModifierModel combatModAC = CombatTempModHelper.GetPossibleTempModifier(Constants.AC, actionCard.getAlias(), player.getNumberTurns());
            if (combatModAC != null) {
                codedButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "applytempcombatmod__" + Constants.AC + "__" + actionCard.getAlias(), "Resolve " + actionCard.getName()));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage + actionCard.getName(), codedButtons);
            }

            if (actionCardWindow.contains("After an agenda is revealed")) {
                List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", game, afterButtons, "after");
                Date newTime = new Date();
                game.setLastActivePlayerPing(newTime);

                String finChecker = "FFCC_" + player.getFaction() + "_";
                if (actionCard.getText().toLowerCase().contains("predict aloud")) {
                    List<Button> riderButtons = AgendaHelper.getAgendaButtons(actionCardTitle, game, finChecker);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your prediction target", game, player, riderButtons);
                }
                if (actionCardTitle.contains("Hack Election")) {
                    game.setHasHackElectionBeenPlayed(true);
                    Button setHack = Buttons.red("hack_election", "Set the voting order as normal");
                    List<Button> hackButtons = List.of(setHack);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Voting order reversed. Please hit this button if Hack Election is Sabo'd", game, player, hackButtons);
                }

            }
            if (actionCardWindow.contains("When an agenda is revealed") && !actionCardTitle.contains("Veto")) {
                Date newTime = new Date();
                game.setLastActivePlayerPing(newTime);
                List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", game, whenButtons, "when");
                List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                    "Please indicate no afters again.", game, afterButtons, "after");
            }
            if ("Action".equalsIgnoreCase(actionCardWindow)) {
                String message = "Use buttons to end turn or do another action.";
                game.setJustPlayedComponentAC(true);
                List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                if (player.getLeaderIDs().contains("kelerescommander") && !player.hasLeaderUnlocked("kelerescommander")) {
                    String message2 = player.getRepresentationUnfogged() + " you may unlock Suffi An, your commander, by paying 1TG (if the AC isn't Sabo'd).";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green("pay1tgforKeleres", "Pay 1TG to Unlock Suffi An", Emojis.MentakAgent));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message2, buttons2);
                }
                serveReverseEngineerButtons(game, player, List.of(acID));
            }
        }

        // Fog of war ping
        if (game.isFowMode()) {
            String fowMessage = player.getRepresentation() + " played an Action Card: " + actionCardTitle;
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, fowMessage);
            MessageHelper.sendPrivateMessageToPlayer(player, game, "Played action card: " + actionCardTitle);
        }
        if (player.hasUnexhaustedLeader("cymiaeagent") && player.getStrategicCC() > 0) {
            List<Button> buttons2 = new ArrayList<>();
            Button cymiaeButton = Buttons.gray("exhaustAgent_cymiaeagent_" + player.getFaction(), "Use Cymiae Agent", Emojis.cymiae);
            buttons2.add(cymiaeButton);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Skhot Unit X-12, the Cymiae" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to draw 1AC.", buttons2);
        }

        ACInfo.sendActionCardInfo(game, player);
        return null;
    }

    public static void serveReverseEngineerButtons(Game game, Player discardingPlayer, List<String> actionCards) {
        for (Player player : game.getRealPlayers()) {
            if (player == discardingPlayer) continue;
            if (ButtonHelper.isPlayerElected(game, player, "censure")) continue;
            if (ButtonHelper.isPlayerElected(game, player, "absol_censure")) continue;

            String reverseEngineerID = "reverse_engineer";
            if (player.getActionCards().containsKey(reverseEngineerID)) {
                StringBuilder msg = new StringBuilder(player.getRepresentationUnfogged() + " you can use Reverse Engineer on ");
                if (actionCards.size() > 1) msg.append("one of the following cards:");

                List<Button> reverseButtons = new ArrayList<>();
                String reversePrefix = Constants.AC_PLAY_FROM_HAND + player.getActionCards().get(reverseEngineerID) + "_reverse_";

                for (String acID : actionCards) {
                    ActionCardModel model = Mapper.getActionCard(acID);
                    if (!model.getWindow().toLowerCase().startsWith("action")) {
                        continue;
                    }

                    String id = reversePrefix + model.getName();
                    String label = "Reverse Engineer " + model.getName();
                    reverseButtons.add(Buttons.green(id, label, Emojis.ActionCard));
                    if (actionCards.size() == 1) msg.append(model.getName()).append(".");
                }

                if (!reverseButtons.isEmpty()) {
                    reverseButtons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg.toString(), reverseButtons);
                }
            }
        }
    }

    private static void sendResolveMsgToMainChannel(String message, List<Button> buttons, Player player, Game game) {
        if (game.isFowMode()) {
            message = message.replace(player.getRepresentation(), "");
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message, buttons);
    }
}
