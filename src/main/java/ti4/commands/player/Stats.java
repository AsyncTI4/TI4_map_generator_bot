package ti4.commands.player;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.player.PlayerStatsService;
import ti4.settings.users.UserSettingsManager;

class Stats extends GameStateSubcommand {

    public Stats() {
        super(Constants.STATS, "Player Stats: Command tokens, trade goods, commodities", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.CC, "Command token - example: 3/3/2 or +1/-1/+0"))
                .addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.TACTICAL,
                        "Tactic pool command token count - can use +1/-1 etc. to add/subtract"))
                .addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.FLEET,
                        "Fleet pool command token count - can use +1/-1 etc. to add/subtract"))
                .addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.STRATEGY,
                        "Strategy pool command token count - can use +1/-1 etc. to add/subtract"))
                .addOptions(new OptionData(
                        OptionType.STRING, Constants.TG, "Trade good count - can use +1/-1 etc. to add/subtract"))
                .addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.COMMODITIES,
                        "Commodity count - can use +1/-1 etc. to add/subtract"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES_BASE, "Base commodity value"))
                .addOptions(
                        new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy card initiative number"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.TURN_COUNT, "Number of turns this round"))
                .addOptions(new OptionData(
                        OptionType.INTEGER,
                        Constants.SC_PLAYED,
                        "Flip a strategy card's played status; enter the initiative number"))
                .addOptions(new OptionData(OptionType.STRING, Constants.PASSED, "Set whether player has passed y/n"))
                .addOptions(new OptionData(OptionType.STRING, Constants.SPEAKER, "Set whether player is speaker y/n"))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.DUMMY, "Player is a placeholder"))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NPC, "Player is an NPC"))
                .addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"))
                .addOptions(new OptionData(
                                OptionType.STRING, Constants.FACTION_COLOR, "Set stats for another Faction or Color")
                        .setAutoComplete(true));
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
                MessageHelper.sendMessageToChannel(
                        player.getPrivateChannel(), PlayerStatsService.getPlayersCurrentStatsText(player, game));
            } else {
                MessageHelper.sendMessageToEventChannel(
                        event, PlayerStatsService.getPlayersCurrentStatsText(player, game));
            }
            return;
        }

        // DO CCs FIRST
        OptionMapping optionCC = event.getOption(Constants.CC);
        OptionMapping optionT = event.getOption(Constants.TACTICAL);
        OptionMapping optionF = event.getOption(Constants.FLEET);
        OptionMapping optionS = event.getOption(Constants.STRATEGY);
        if (optionCC != null && (optionT != null || optionF != null || optionS != null)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Use format 3/3/3 for command counters or individual values, not both");
        } else {
            String originalCCString =
                    player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
            if (optionCC != null) {
                String cc = AliasHandler.resolveFaction(optionCC.getAsString().toLowerCase());
                StringTokenizer tokenizer = new StringTokenizer(cc, "/");
                if (tokenizer.countTokens() != 3) {
                    MessageHelper.sendMessageToEventChannel(event, "Wrong format for tokens count. Must be 3/3/3.");
                } else {
                    try {
                        PlayerStatsService.setValue(
                                event,
                                game,
                                player,
                                "Tactic Token",
                                player::setTacticalCC,
                                player::getTacticalCC,
                                tokenizer.nextToken(),
                                true);
                        PlayerStatsService.setValue(
                                event,
                                game,
                                player,
                                "Fleet Token",
                                player::setFleetCC,
                                player::getFleetCC,
                                tokenizer.nextToken(),
                                true);
                        PlayerStatsService.setValue(
                                event,
                                game,
                                player,
                                "Strategy Token",
                                player::setStrategicCC,
                                player::getStrategicCC,
                                tokenizer.nextToken(),
                                true);
                    } catch (Exception e) {
                        MessageHelper.sendMessageToEventChannel(
                                event, "Not number entered, check command token count again.");
                    }
                }
                Helper.isCCCountCorrect(player);
            }
            if (optionT != null) {
                PlayerStatsService.setValue(
                        event, game, player, optionT, player::setTacticalCC, player::getTacticalCC, true);
            }
            if (optionF != null) {
                PlayerStatsService.setValue(event, game, player, optionF, player::setFleetCC, player::getFleetCC, true);
            }
            if (optionS != null) {
                PlayerStatsService.setValue(
                        event, game, player, optionS, player::setStrategicCC, player::getStrategicCC, true);
            }
            if (optionT != null || optionF != null || optionS != null || optionCC != null) {
                String newCCString = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                MessageHelper.sendMessageToEventChannel(
                        event,
                        player.getRepresentation() + " updated command tokens: " + originalCCString + " -> "
                                + newCCString + ".");
            }
            if (optionT != null || optionF != null || optionS != null) {
                Helper.isCCCountCorrect(player);
            }
        }
        optionMappings.remove(optionCC);
        optionMappings.remove(optionT);
        optionMappings.remove(optionF);
        optionMappings.remove(optionS);
        if (optionMappings.isEmpty()) return;

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
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged() + ", you have the opportunity to buy _Axis Orders_.",
                        ButtonHelperAbilities.getBuyableAxisOrders(player, game));
            }
            CommanderUnlockCheckService.checkPlayer(player, "mykomentori");
        }

        OptionMapping optionMedian = event.getOption(Constants.AUTO_SABO_PASS_MEDIAN);
        if (optionMedian != null) {
            player.setAutoSaboPassMedian(optionMedian.getAsInt());
        }

        OptionMapping prefersDistanceOption = event.getOption(Constants.PREFERS_DISTANCE);
        if (prefersDistanceOption != null) {
            var userSettings = UserSettingsManager.get(getPlayer().getUserID());
            userSettings.setPrefersDistanceBasedTacticalActions(prefersDistanceOption.getAsBoolean());
            UserSettingsManager.save(userSettings);
        }

        Integer commoditiesTotalCount = event.getOption(Constants.COMMODITIES_BASE, null, OptionMapping::getAsInt);
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
                    message.append("> flipped ")
                            .append(CardEmojis.getSCFrontFromInteger(sc))
                            .append(" to ")
                            .append(CardEmojis.getSCBackFromInteger(sc))
                            .append(" (played)");
                } else {
                    game.setSCPlayed(sc, false);
                    for (Player player_ : game.getPlayers().values()) {
                        if (!player_.isRealPlayer()) {
                            continue;
                        }
                        String faction = player_.getFaction();
                        if (faction == null || faction.isEmpty() || "null".equals(faction)) continue;
                        player_.addFollowedSC(sc);
                    }
                    message.append("> flipped ")
                            .append(CardEmojis.getSCBackFromInteger(sc))
                            .append(" to ")
                            .append(CardEmojis.getSCFrontFromInteger(sc))
                            .append(" (unplayed)");
                }
            } else {
                message.append("> attempted to change " + Constants.SC_PLAYED
                        + ", but player has not picked an strategy card (SC = 0).");
            }
            MessageHelper.sendMessageToEventChannel(event, message.toString());
        }

        OptionMapping optionDummy = event.getOption(Constants.DUMMY);
        if (optionDummy != null) {
            boolean value = optionDummy.getAsBoolean();
            player.setDummy(value);
            MessageHelper.sendMessageToEventChannel(event, getGeneralMessage(optionDummy));
        }

        OptionMapping optionNPC = event.getOption(Constants.NPC);
        if (optionNPC != null) {
            boolean value = optionNPC.getAsBoolean();
            player.setNpc(value);
            MessageHelper.sendMessageToEventChannel(event, getGeneralMessage(optionNPC));
            if (value) {
                if (!game.isFowMode()) {
                    Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), game.getName());
                }

                Guild guild = event.getGuild();
                Member removedMember = guild.getMemberById(player.getUserID());
                List<Role> roles = guild.getRolesByName(game.getName(), true);
                if (removedMember != null && roles.size() == 1) {
                    guild.removeRoleFromMember(removedMember, roles.getFirst()).queue();
                }
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.gray(
                        player.getFinsFactionCheckerPrefix() + "removePlayerPermissions_" + player.getFaction(),
                        "Remove View Permissions " + player.getDisplayName()));
                buttons.add(Buttons.red("deleteButtons", "Stay in channels"));
                String msg = player.getRepresentation()
                        + " do you want to remove yourself from the game channels? If so, press this button.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
            }
        }
    }

    private static String getGeneralMessage(OptionMapping option) {
        return ">  set **" + option.getName() + "** to " + option.getAsString();
    }
}
