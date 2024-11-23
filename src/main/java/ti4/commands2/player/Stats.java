package ti4.commands2.player;

import java.util.List;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.ManagedGame;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.player.PlayerStatsService;

class Stats extends GameStateSubcommand {

    public Stats() {
        super(Constants.STATS, "Player Stats: CC,TG,Commodities", true, true);
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
        Player player = getPlayer();

        List<OptionMapping> optionMappings = event.getOptions();
        optionMappings.remove(event.getOption(Constants.PLAYER));
        optionMappings.remove(event.getOption(Constants.FACTION_COLOR));
        // NO OPTIONS SELECTED, JUST DISPLAY STATS
        if (optionMappings.isEmpty()) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                    PlayerStatsService.getPlayersCurrentStatsText(player, game));
            } else {
                MessageHelper.sendMessageToEventChannel(event, PlayerStatsService.getPlayersCurrentStatsText(player, game));
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
                        PlayerStatsService.setValue(event, game, player, "Tactics CC", player::setTacticalCC, player::getTacticalCC,
                            tokenizer.nextToken(), true);
                        PlayerStatsService.setValue(event, game, player, "Fleet CC", player::setFleetCC, player::getFleetCC,
                            tokenizer.nextToken(), true);
                        PlayerStatsService.setValue(event, game, player, "Strategy CC", player::setStrategicCC,
                            player::getStrategicCC, tokenizer.nextToken(), true);
                    } catch (Exception e) {
                        MessageHelper.sendMessageToEventChannel(event, "Not number entered, check CC count again");
                    }
                }
                Helper.isCCCountCorrect(event, game, player.getColor());
            }
            if (optionT != null) {
                PlayerStatsService.setValue(event, game, player, optionT, player::setTacticalCC, player::getTacticalCC, true);
            }
            if (optionF != null) {
                PlayerStatsService.setValue(event, game, player, optionF, player::setFleetCC, player::getFleetCC, true);
            }
            if (optionS != null) {
                PlayerStatsService.setValue(event, game, player, optionS, player::setStrategicCC, player::getStrategicCC, true);
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
            PlayerStatsService.setValue(event, game, player, optionTG, player::setTg, player::getTg);
            if (optionTG.getAsString().contains("+")) {
                ButtonHelperAbilities.pillageCheck(player, game);
            } else if (player.getTg() > oldTg) {
                ButtonHelperAbilities.pillageCheck(player, game);
            }
        }

        OptionMapping optionC = event.getOption(Constants.COMMODITIES);
        if (optionC != null) {
            PlayerStatsService.setValue(event, game, player, optionC, player::setCommodities, player::getCommodities);
            if (player.hasAbility("military_industrial_complex")
                && ButtonHelperAbilities.getBuyableAxisOrders(player, game).size() > 1) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you have the opportunity to buy axis orders",
                    ButtonHelperAbilities.getBuyableAxisOrders(player, game));
            }
            CommanderUnlockCheckService.checkPlayer(player, "mykomentori");
        }

        OptionMapping optionMedian = event.getOption(Constants.AUTO_SABO_PASS_MEDIAN);
        if (optionMedian != null) {
            player.setAutoSaboPassMedian(optionMedian.getAsInt());
        }

        OptionMapping optionPref = event.getOption(Constants.PREFERS_DISTANCE);
        if (optionPref != null) {
            player.setPreferenceForDistanceBasedTacticalActions(optionPref.getAsBoolean());
            for (ManagedGame managedGame : GameManager.getManagedGames()) {
                if (!managedGame.isHasEnded()) {
                    var gameToUpdate = GameManager.getGame(managedGame.getName());
                    for (Player playerToUpdate : gameToUpdate.getRealPlayers()) {
                        if (playerToUpdate.getUserID().equalsIgnoreCase(player.getUserID())) {
                            playerToUpdate.setPreferenceForDistanceBasedTacticalActions(optionPref.getAsBoolean());
                        }
                    }
                }
            }
        }

        Integer commoditiesTotalCount = event.getOption(Constants.COMMODITIES_TOTAL, null, OptionMapping::getAsInt);
        if (commoditiesTotalCount != null) {
            PlayerStatsService.setTotalCommodities(event, player, commoditiesTotalCount);
        }

        Integer turnCount = event.getOption(Constants.TURN_COUNT, null, OptionMapping::getAsInt);
        if (turnCount != null) {
            player.setInRoundTurnCount(turnCount);
            String message = ">  set **Turn Count** to " + turnCount;
            MessageHelper.sendMessageToEventChannel(event, message);
        }

        OptionMapping optionSpeaker = event.getOption(Constants.SPEAKER);
        if (optionSpeaker != null) {
            StringBuilder message = new StringBuilder(getGeneralMessage(optionSpeaker));
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
            StringBuilder message = new StringBuilder(getGeneralMessage(optionPassed));
            String value = optionPassed.getAsString().toLowerCase();
            if ("y".equals(value) || "yes".equals(value)) {
                player.setPassed(true);
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

        PlayerStatsService.pickSC(event, game, player, event.getOption(Constants.STRATEGY_CARD));

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
            MessageHelper.sendMessageToEventChannel(event, getGeneralMessage(optionDummy));
        }
    }

    private static String getGeneralMessage(OptionMapping option) {
        return ">  set **" + option.getName() + "** to " + option.getAsString();
    }
}
