package ti4.commands.player;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;

public class Stats extends PlayerSubcommandData {
    public Stats() {
        super(Constants.STATS, "Player Stats: CC,TG,Commodities");
        addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2 or +1/-1/+0"))
            .addOptions(new OptionData(OptionType.STRING, Constants.TACTICAL, "Tactical command counter count - can use +1/-1 etc. to add/subtract"))
            .addOptions(new OptionData(OptionType.STRING, Constants.FLEET, "Fleet command counter count - can use +1/-1 etc. to add/subtract"))
            .addOptions(new OptionData(OptionType.STRING, Constants.STRATEGY, "Strategy command counter count - can use +1/-1 etc. to add/subtract"))
            .addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count - can use +1/-1 etc. to add/subtract"))
            .addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count - can use +1/-1 etc. to add/subtract"))
            .addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES_TOTAL, "Commodity total count"))
            .addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card Number"))
            .addOptions(new OptionData(OptionType.INTEGER, Constants.TURN_COUNT, "# turns this round"))
            .addOptions(new OptionData(OptionType.INTEGER, Constants.SC_PLAYED, "Flip a Strategy Card's played status. Enter the SC #."))
            .addOptions(new OptionData(OptionType.STRING, Constants.PASSED, "Set whether player has passed y/n"))
            .addOptions(new OptionData(OptionType.STRING, Constants.SPEAKER, "Set whether player is speaker y/n"))
            .addOptions(new OptionData(OptionType.BOOLEAN, Constants.DUMMY, "Player is a placeholder"))
            .addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"))
            .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Set stats for another Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        List<OptionMapping> optionMappings = event.getOptions();
        optionMappings.remove(playerOption);
        optionMappings.remove(factionColorOption);
        // NO OPTIONS SELECTED, JUST DISPLAY STATS
        if (optionMappings.isEmpty()) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    getPlayersCurrentStatsText(player, game));
            } else {
                MessageHelper.sendMessageToEventChannel(event, getPlayersCurrentStatsText(player, game));
            }
            return;
        }

        // DO CCs FIRST
        OptionMapping optionCC = event.getOption(Constants.CC);
        OptionMapping optionT = event.getOption(Constants.TACTICAL);
        OptionMapping optionF = event.getOption(Constants.FLEET);
        OptionMapping optionS = event.getOption(Constants.STRATEGY);
        if (optionCC != null && (optionT != null || optionF != null || optionS != null)) {
            MessageHelper.sendMessageToEventChannel(event, "Use format 3/3/3 for command counters or individual values, not both");
        } else {
            String originalCCString = player.getTacticalCC() + "/" + player.getFleetCC() + "/"
                + player.getStrategicCC();
            if (optionCC != null) {
                String cc = AliasHandler.resolveFaction(optionCC.getAsString().toLowerCase());
                StringTokenizer tokenizer = new StringTokenizer(cc, "/");
                if (tokenizer.countTokens() != 3) {
                    MessageHelper.sendMessageToEventChannel(event, "Wrong format for tokens count. Must be 3/3/3");
                } else {
                    try {
                        setValue(event, game, player, "Tactics CC", player::setTacticalCC, player::getTacticalCC,
                            tokenizer.nextToken(), true);
                        setValue(event, game, player, "Fleet CC", player::setFleetCC, player::getFleetCC,
                            tokenizer.nextToken(), true);
                        setValue(event, game, player, "Strategy CC", player::setStrategicCC,
                            player::getStrategicCC, tokenizer.nextToken(), true);
                    } catch (Exception e) {
                        MessageHelper.sendMessageToEventChannel(event, "Not number entered, check CC count again");
                    }
                }
                Helper.isCCCountCorrect(event, game, player.getColor());
            }
            if (optionT != null) {
                setValue(event, game, player, optionT, player::setTacticalCC, player::getTacticalCC, true);
            }
            if (optionF != null) {
                setValue(event, game, player, optionF, player::setFleetCC, player::getFleetCC, true);
            }
            if (optionS != null) {
                setValue(event, game, player, optionS, player::setStrategicCC, player::getStrategicCC, true);
            }
            if (optionT != null || optionF != null || optionS != null || optionCC != null) {
                String newCCString = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " updated CCs: " + originalCCString + " -> " + newCCString);
            }
            if (optionT != null || optionF != null || optionS != null) {
                Helper.isCCCountCorrect(event, game, player.getColor());
            }
        }
        optionMappings.remove(optionCC);
        optionMappings.remove(optionT);
        optionMappings.remove(optionF);
        optionMappings.remove(optionS);
        if (optionMappings.isEmpty())
            return;

        MessageHelper.sendMessageToEventChannel(event, player.getRepresentationUnfogged() + " player stats changed:");

        OptionMapping optionTG = event.getOption(Constants.TG);
        if (optionTG != null) {
            int oldTg = player.getTg();
            setValue(event, game, player, optionTG, player::setTg, player::getTg);
            if (optionTG.getAsString().contains("+")) {
                ButtonHelperAbilities.pillageCheck(player, game);
            } else if (player.getTg() > oldTg) {
                ButtonHelperAbilities.pillageCheck(player, game);
            }

        }

        OptionMapping optionC = event.getOption(Constants.COMMODITIES);
        if (optionC != null) {
            setValue(event, game, player, optionC, player::setCommodities, player::getCommodities);
            if (player.hasAbility("military_industrial_complex")
                && ButtonHelperAbilities.getBuyableAxisOrders(player, game).size() > 1) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you have the opportunity to buy axis orders",
                    ButtonHelperAbilities.getBuyableAxisOrders(player, game));
            }
            CommanderUnlockCheck.checkPlayer(player, "mykomentori");
        }

        OptionMapping optionMedian = event.getOption(Constants.AUTO_SABO_PASS_MEDIAN);
        if (optionMedian != null) {
            player.setAutoSaboPassMedian(optionMedian.getAsInt());
        }

        OptionMapping optionPref = event.getOption(Constants.PREFERS_DISTANCE);
        if (optionPref != null) {
            player.setPreferenceForDistanceBasedTacticalActions(optionPref.getAsBoolean());
            for (var managedGame : GameManager.getGameNameToGame().values()) {
                if (!managedGame.isHasEnded()) {
                    var gameToUpdate = GameManager.getGame(managedGame.getName());
                    for (Player playerToUpdate : gameToUpdate.getRealPlayers()) {
                        if (playerToUpdate.getUserID().equalsIgnoreCase(player.getUserID())) {
                            playerToUpdate.setPreferenceForDistanceBasedTacticalActions(optionPref.getAsBoolean());
                            GameSaveLoadManager.saveGame(gameToUpdate, event);
                        }
                    }
                }
            }
        }

        Integer commoditiesTotalCount = event.getOption(Constants.COMMODITIES_TOTAL, null, OptionMapping::getAsInt);
        if (commoditiesTotalCount != null) {
            setTotalCommodities(event, player, commoditiesTotalCount);
        }

        Integer turnCount = event.getOption(Constants.TURN_COUNT, null, OptionMapping::getAsInt);
        if (turnCount != null) {
            player.setTurnCount(turnCount);
            String message = ">  set **Turn Count** to " + turnCount;
            MessageHelper.sendMessageToEventChannel(event, message);
        }

        OptionMapping optionSpeaker = event.getOption(Constants.SPEAKER);
        if (optionSpeaker != null) {
            StringBuilder message = new StringBuilder(getGeneralMessage(event, player, optionSpeaker));
            String value = optionSpeaker.getAsString().toLowerCase();
            if ("y".equals(value) || "yes".equals(value)) {
                game.setSpeakerUserID(player.getUserID());
            } else {
                message.append(", which is not a valid input. Please use one of: y/yes");
            }
            MessageHelper.sendMessageToEventChannel(event, message.toString());
        }

        OptionMapping optionPassed = event.getOption(Constants.PASSED);
        if (optionPassed != null) {
            StringBuilder message = new StringBuilder(getGeneralMessage(event, player, optionPassed));
            String value = optionPassed.getAsString().toLowerCase();
            if ("y".equals(value) || "yes".equals(value)) {
                player.setPassed(true);
                // Turn.pingNextPlayer(event, activeMap, player);
                if (game.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")) {
                    ButtonHelperCommanders.olradinCommanderStep1(player, game);
                }
            } else if ("n".equals(value) || "no".equals(value)) {
                player.setPassed(false);
            } else {
                message.append(", which is not a valid input. Please use one of: y/yes/n/no");
            }
            MessageHelper.sendMessageToEventChannel(event, message.toString());
        }

        pickSC(event, game, player, event.getOption(Constants.STRATEGY_CARD));

        OptionMapping optionSCPlayed = event.getOption(Constants.SC_PLAYED);
        if (optionSCPlayed != null) {
            StringBuilder message = new StringBuilder();
            int sc = optionSCPlayed.getAsInt();
            if (sc > 0) {
                Boolean scIsPlayed = game.getScPlayed().get(sc);
                if (scIsPlayed == null || !scIsPlayed) {
                    game.setSCPlayed(sc, true);
                    message.append("> flipped ").append(Emojis.getSCEmojiFromInteger(sc)).append(" to ")
                        .append(Emojis.getSCBackEmojiFromInteger(sc)).append(" (played)");
                } else {
                    game.setSCPlayed(sc, false);
                    for (Player player_ : game.getPlayers().values()) {
                        if (!player_.isRealPlayer()) {
                            continue;
                        }
                        String faction = player_.getFaction();
                        if (faction == null || faction.isEmpty() || "null".equals(faction))
                            continue;
                        player_.addFollowedSC(sc);
                    }
                    message.append("> flipped ").append(Emojis.getSCBackEmojiFromInteger(sc)).append(" to ")
                        .append(Emojis.getSCEmojiFromInteger(sc)).append(" (unplayed)");
                }
            } else {
                message.append(
                    "> attempted to change " + Constants.SC_PLAYED + ", but player has not picked an strategy card (SC = 0).");
            }
            MessageHelper.sendMessageToEventChannel(event, message.toString());
        }

        OptionMapping optionDummy = event.getOption(Constants.DUMMY);
        if (optionDummy != null) {
            boolean value = optionDummy.getAsBoolean();
            player.setDummy(value);
            MessageHelper.sendMessageToEventChannel(event, getGeneralMessage(event, player, optionDummy));
        }

    }

    public static void setTotalCommodities(GenericInteractionCreateEvent event, Player player, Integer commoditiesTotalCount) {
        if (commoditiesTotalCount < 1 || commoditiesTotalCount > 10) {
            MessageHelper.sendMessageToEventChannel(event, "**Warning:** Total Commodities count seems like a wrong value:");
        }
        player.setCommoditiesTotal(commoditiesTotalCount);
        String message = ">  set **Total Commodities** to " + commoditiesTotalCount + Emojis.comm;
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    public String getPlayersCurrentStatsText(Player player, Game game) {
        StringBuilder sb = new StringBuilder(player.getFactionEmoji() + " player's current stats:\n");

        sb.append("> VP: ").append(player.getTotalVictoryPoints());
        sb.append("      ").append(Emojis.getTGorNomadCoinEmoji(game)).append(player.getTg());
        sb.append("      ").append(Emojis.comm).append(player.getCommodities()).append("/").append(player.getCommoditiesTotal());
        sb.append("      ").append(Emojis.CFrag).append(player.getCrf());
        sb.append("   ").append(Emojis.IFrag).append(player.getIrf());
        sb.append("   ").append(Emojis.HFrag).append(player.getHrf());
        sb.append("   ").append(Emojis.UFrag).append(player.getUrf());

        sb.append("\n");

        sb.append("> CC: `").append(player.getCCRepresentation()).append("`\n");
        sb.append("> Strategy Cards: `").append(player.getSCs()).append("`\n");
        sb.append("> Unfollowed Strategy Cards: `").append(player.getUnfollowedSCs()).append("`\n");
        sb.append("> Debt: `").append(player.getDebtTokens()).append("`\n");
        sb.append("> Speaker: `").append(game.getSpeakerUserID().equals(player.getUserID())).append("`\n");
        sb.append("> Passed: `").append(player.isPassed()).append("`\n");
        sb.append("> Dummy: `").append(player.isDummy()).append("`\n");
        sb.append("> Raw Faction Emoji: `").append(player.getFactionEmoji()).append("`\n");
        sb.append("> Display Name: `").append(player.getDisplayName()).append("`\n");
        sb.append("> Stats Anchor: `").append(player.getPlayerStatsAnchorPosition()).append("`\n");

        Tile homeSystemTile = player.getHomeSystemTile();
        if (homeSystemTile != null) {
            sb.append("> Home System:  `").append(homeSystemTile.getPosition()).append("`\n");
        }

        sb.append("> Abilities: `").append(player.getAbilities()).append("`\n");
        sb.append("> Planets: `").append(player.getPlanets()).append("`\n");
        sb.append("> Techs: `").append(player.getTechs()).append("`\n");
        sb.append("> Faction Techs: `").append(player.getFactionTechs()).append("`\n");
        sb.append("> Fragments: `").append(player.getFragments()).append("`\n");
        sb.append("> Relics: `").append(player.getRelics()).append("`\n");
        sb.append("> Mahact CC: `").append(player.getMahactCC()).append("`\n");
        sb.append("> Leaders: `").append(player.getLeaderIDs()).append("`\n");
        sb.append("> Owned PNs: `").append(player.getPromissoryNotesOwned()).append("`\n");
        sb.append("> Owned Units: `").append(player.getUnitsOwned()).append("`\n");
        sb.append("> Alliance Members: ").append(player.getAllianceMembers().replace(player.getFaction(), "")).append("\n");
        sb.append("> Followed SCs: `").append(player.getFollowedSCs().toString()).append("`\n");
        sb.append("> Expected Number of Hits: `").append((player.getExpectedHitsTimes10() / 10.0)).append("`\n");
        sb.append("> Actual Hits: `").append(player.getActualHits()).append("`\n");
        sb.append("> Total Unit Resource Value: ").append(Emojis.resources).append("`").append(player.getTotalResourceValueOfUnits("both")).append("`\n");
        sb.append("> Total Unit Hit-point Value: ").append(Emojis.PinkHeart).append("`").append(player.getTotalHPValueOfUnits("both")).append("`\n");
        sb.append("> Total Unit Combat Expected Hits: ").append("💥").append("`").append(player.getTotalCombatValueOfUnits("both")).append("`\n");
        sb.append("> Total Unit Ability Expected Hits: ").append(Emojis.UnitUpgradeTech).append("`").append(player.getTotalUnitAbilityValueOfUnits()).append("`\n");
        sb.append("> Decal Set: `").append(player.getDecalName()).append("`\n");
        sb.append("\n");
        return sb.toString();
    }

    public static boolean pickSC(GenericInteractionCreateEvent event, Game game, Player player, OptionMapping optionSC) {
        if (optionSC == null) {
            return false;
        }
        int scNumber = optionSC.getAsInt();
        return secondHalfOfPickSC(event, game, player, scNumber);
    }

    public static boolean secondHalfOfPickSC(GenericInteractionCreateEvent event, Game game, Player player, int scNumber) {
        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        if (player.getColor() == null || "null".equals(player.getColor()) || player.getFaction() == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                "Can only pick strategy card if both faction and color have been picked.");
            return false;
        }
        if (!scTradeGoods.containsKey(scNumber)) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                "Strategy Card must be from possible ones in Game: " + scTradeGoods.keySet());
            return false;
        }

        Map<String, Player> players = game.getPlayers();
        for (Player playerStats : players.values()) {
            if (playerStats.getSCs().contains(scNumber)) {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                    Helper.getSCName(scNumber, game) + " is already picked.");
                return false;
            }
        }

        player.addSC(scNumber);
        if (game.isFowMode()) {
            String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " picked " + Helper.getSCName(scNumber, game);
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
        }

        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scNumber).orElse(null);

        // WARNING IF PICKING TRADE WHEN PLAYER DOES NOT HAVE THEIR TRADE AGREEMENT
        if (scModel.usesAutomationForSCID("pok5trade") && !player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
            String message = player.getRepresentationUnfogged() + " heads up, you just picked Trade but don't currently hold your Trade Agreement";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
        }

        Integer tgCount = scTradeGoods.get(scNumber);
        String msg = player.getRepresentationUnfogged() +
            "\n> Picked: " + Helper.getSCRepresentation(game, scNumber);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (tgCount != null && tgCount != 0) {
            int tg = player.getTg();
            tg += tgCount;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " gained " + tgCount + " TG" + (tgCount == 1 ? "" : "s") + " from picking " + Helper.getSCName(scNumber, game));
            if (game.isFowMode()) {
                String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " gained " + tgCount
                    + " TG" + (tgCount == 1 ? "" : "s") + " from picking " + Helper.getSCName(scNumber, game);
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
            }
            player.setTg(tg);
            CommanderUnlockCheck.checkPlayer(player, "hacan");
            ButtonHelperAbilities.pillageCheck(player, game);
            if (scNumber == 2 && game.isRedTapeMode()) {
                for (int x = 0; x < tgCount; x++) {
                    ButtonHelper.offerRedTapeButtons(game, player);
                }
            }
        }
        return true;
    }

    public void setValue(SlashCommandInteractionEvent event, Game game, Player player, OptionMapping option,
        Consumer<Integer> consumer, Supplier<Integer> supplier) {
        setValue(event, game, player, option.getName(), consumer, supplier, option.getAsString(), false);
    }

    public void setValue(SlashCommandInteractionEvent event, Game game, Player player, OptionMapping option,
        Consumer<Integer> consumer, Supplier<Integer> supplier, boolean suppressMessage) {
        setValue(event, game, player, option.getName(), consumer, supplier, option.getAsString(),
            suppressMessage);
    }

    public void setValue(SlashCommandInteractionEvent event, Game game, Player player, String optionName,
        Consumer<Integer> consumer, Supplier<Integer> supplier, String value, boolean suppressMessage) {
        try {
            boolean setValue = !value.startsWith("+") && !value.startsWith("-");
            String explanation = "";
            if (value.contains("?")) {
                explanation = value.substring(value.indexOf("?") + 1);
                value = value.substring(0, value.indexOf("?")).replace(" ", "");
            }

            int number = Integer.parseInt(value);
            int existingNumber = supplier.get();
            if (setValue) {
                consumer.accept(number);
                String messageToSend = getSetValueMessage(event, player, optionName, number, existingNumber,
                    explanation);
                if (!suppressMessage)
                    MessageHelper.sendMessageToEventChannel(event, messageToSend);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
                }
            } else {
                int newNumber = existingNumber + number;
                newNumber = Math.max(newNumber, 0);
                consumer.accept(newNumber);
                String messageToSend = getChangeValueMessage(event, player, optionName, number, existingNumber,
                    newNumber, explanation);
                if (!suppressMessage)
                    MessageHelper.sendMessageToEventChannel(event, messageToSend);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
                }
            }
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse number for: " + optionName);
        }
    }

    public static String getSetValueMessage(SlashCommandInteractionEvent event, Player player, String optionName,
        Integer setToNumber, Integer existingNumber, String explanation) {
        if (explanation == null || "".equalsIgnoreCase(explanation)) {
            return "> set **" + optionName + "** to **" + setToNumber + "**   _(was "
                + existingNumber + ", a change of " + (setToNumber - existingNumber)
                + ")_";
        } else {
            return "> set **" + optionName + "** to **" + setToNumber + "**   _(was "
                + existingNumber + ", a change of " + (setToNumber - existingNumber)
                + ")_ for the reason of: " + explanation;
        }

    }

    public static String getChangeValueMessage(SlashCommandInteractionEvent event, Player player, String optionName,
        Integer changeNumber, Integer existingNumber, Integer newNumber, String explanation) {
        String changeDescription = "changed";
        if (changeNumber > 0) {
            changeDescription = "increased";
        } else if (changeNumber < 0) {
            changeDescription = "decreased";
        }
        if (explanation == null || "".equalsIgnoreCase(explanation)) {
            return "> " + changeDescription + " **" + optionName + "** by " + changeNumber + "   _(was "
                + existingNumber + ", now **" + newNumber + "**)_";
        } else {
            return "> " + changeDescription + " **" + optionName + "** by " + changeNumber + "   _(was "
                + existingNumber + ", now **" + newNumber + "**)_ for the reason of: " + explanation;
        }

    }

    private static String getGeneralMessage(SlashCommandInteractionEvent event, Player player, OptionMapping option) {
        return ">  set **" + option.getName() + "** to " + option.getAsString();
    }
}
