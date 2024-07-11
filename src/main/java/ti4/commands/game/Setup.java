package ti4.commands.game;

import java.util.ArrayList;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Setup extends GameSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Game Setup");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLAYER_COUNT_FOR_MAP, "Number of players between 1 or 30. Default 6"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.VP_COUNT, "Game VP count. Default is 10"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC_COUNT_FOR_MAP, "Number of strategy cards each player gets. Default 1"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MAX_SO_COUNT, "Max Number of SO's per player. Default 3"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_CUSTOM_NAME, "Custom description"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TIGL_GAME, "True to mark the game as TIGL"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.AUTO_PING, "Hours between auto pings. Min 1. Enter 0 to turn off."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        OptionMapping playerCount = event.getOption(Constants.PLAYER_COUNT_FOR_MAP);
        if (playerCount != null) {
            int count = playerCount.getAsInt();
            if (count < 1 || count > 30) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify between 1 or 30 players.");
            } else {
                game.setPlayerCountForMap(count);
            }

        }

        OptionMapping vpOption = event.getOption(Constants.VP_COUNT);
        if (vpOption != null) {
            int count = vpOption.getAsInt();
            if (count < 1) {
                count = 1;
            } else if (count > 20) {
                count = 20;
            }
            game.setVp(count);
        }

        Integer maxSOCount = event.getOption(Constants.MAX_SO_COUNT, null, OptionMapping::getAsInt);
        if (maxSOCount != null && maxSOCount >= 0) {
            game.setMaxSOCountPerPlayer(maxSOCount);

            String key = "factionsThatAreNotDiscardingSOs";
            String key2 = "queueToDrawSOs";
            String key3 = "potentialBlockers";
            game.setStoredValue(key, "");
            game.setStoredValue(key2, "");
            game.setStoredValue(key3, "");
            if (game.getRound() > 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Any SO queue has been erased due to the change in SO count. You can resolve the previously queued draws by just pressing draw again.");
            }

        }

        Integer scCountPerPlayer = event.getOption(Constants.SC_COUNT_FOR_MAP, null, OptionMapping::getAsInt);
        if (scCountPerPlayer != null) {
            int maxSCsPerPlayer;
            if (game.getRealPlayers().isEmpty()) {
                maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getPlayers().size());
            } else {
                maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getRealPlayers().size());
            }

            if (maxSCsPerPlayer == 0) maxSCsPerPlayer = 1;

            if (scCountPerPlayer < 1) {
                scCountPerPlayer = 1;
            } else if (scCountPerPlayer > maxSCsPerPlayer) {
                scCountPerPlayer = maxSCsPerPlayer;
            }
            game.setStrategyCardsPerPlayer(scCountPerPlayer);
        }

        Integer pingHours = event.getOption(Constants.AUTO_PING, null, OptionMapping::getAsInt);
        if (pingHours != null) {
            if (pingHours == 0) {
                game.setAutoPing(false);
                game.setAutoPingSpacer(pingHours);
            } else {
                game.setAutoPing(true);
                if (pingHours < 1) {
                    pingHours = 1;
                }
                game.setAutoPingSpacer(pingHours);
            }
        }

        String customGameName = event.getOption(Constants.GAME_CUSTOM_NAME, null, OptionMapping::getAsString);
        if (customGameName != null) {
            game.setCustomName(customGameName);
        }

        if (!setGameMode(event, game)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong and the game modes could not be set, please see error above.");
        }
    }

    public static boolean setGameMode(SlashCommandInteractionEvent event, Game game) {
        if (event.getOption(Constants.TIGL_GAME) == null && event.getOption(Constants.ABSOL_MODE) == null && event.getOption(Constants.DISCORDANT_STARS_MODE) == null
            && event.getOption(Constants.BASE_GAME_MODE) == null && event.getOption(Constants.MILTYMOD_MODE) == null) {
            return true; //no changes were made
        }
        boolean isTIGLGame = event.getOption(Constants.TIGL_GAME, game.isCompetitiveTIGLGame(), OptionMapping::getAsBoolean);
        boolean absolMode = event.getOption(Constants.ABSOL_MODE, game.isAbsolMode(), OptionMapping::getAsBoolean);
        boolean miltyModMode = event.getOption(Constants.MILTYMOD_MODE, game.isMiltyModMode(), OptionMapping::getAsBoolean);
        boolean discordantStarsMode = event.getOption(Constants.DISCORDANT_STARS_MODE, game.isDiscordantStarsMode(), OptionMapping::getAsBoolean);
        boolean baseGameMode = event.getOption(Constants.BASE_GAME_MODE, game.isBaseGameMode(), OptionMapping::getAsBoolean);
        return setGameMode(event, game, baseGameMode, absolMode, miltyModMode, discordantStarsMode, isTIGLGame);
    }

    // TODO: find a better way to handle this - this is annoying
    public static boolean setGameMode(GenericInteractionCreateEvent event, Game game, boolean baseGameMode, boolean absolMode, boolean miltyModMode, boolean discordantStarsMode,
        boolean isTIGLGame) {
        if (isTIGLGame
            && (baseGameMode || absolMode || discordantStarsMode || game.isHomebrewSCMode() || game.isFowMode() || game.isAllianceMode() || game.isCommunityMode())) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "TIGL Games can not be mixed with other game modes.");
            return false;
        } else if (isTIGLGame) {
            game.setCompetitiveTIGLGame(true);
            sendTIGLSetupText(game);
            return true;
        }

        if (miltyModMode && !baseGameMode) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Milty Mod Mode can only be combined with No Expansion Mode. Please set the game to No Expansion Mode first.");
            return false;
        }

        if (baseGameMode && (absolMode || discordantStarsMode)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No Expansion Mode is not supported with Discordant Stars or Absol Mode");
            return false;
        } else if (baseGameMode && miltyModMode) {
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_miltymod"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_miltymod"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_miltymod"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_miltymod"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_miltymod"))) return false;
            if (!game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_base"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_base"))) return false;

            for (Player player : game.getPlayers().values()) {
                player.setLeaders(new ArrayList<>());
                if (player.getUnitByBaseType("mech") != null) player.removeOwnedUnitByID(player.getUnitByBaseType("mech").getId());
            }

            game.setScSetID("miltymod");

            game.setTechnologyDeckID("techs_miltymod");
            game.swapInVariantTechs();
            game.swapInVariantUnits("miltymod");
            game.setBaseGameMode(true);
            game.setMiltyModMode(true);
            game.setAbsolMode(false);
            game.setDiscordantStarsMode(false);
            return true;
        } else if (baseGameMode) {
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_base_game"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_base"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_base"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_base"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_basegame_and_codex1"))) return false;
            if (!game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_base"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_base"))) return false;

            for (Player player : game.getPlayers().values()) {
                player.setLeaders(new ArrayList<>());
                if (player.getUnitByBaseType("mech") != null) player.removeOwnedUnitByID(player.getUnitByBaseType("mech").getId());
            }

            game.setScSetID("base_game");

            game.setTechnologyDeckID("techs_base");
            game.setBaseGameMode(true);
            game.setAbsolMode(false);
            game.setDiscordantStarsMode(false);
            return true;
        }
        game.setBaseGameMode(false);
        game.setMiltyModMode(false);

        // BOTH ABSOL & DS, and/or if either was set before the other
        if (absolMode && discordantStarsMode) {
            game.setDiscordantStarsMode(true);
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"))) return false;
            if (!game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol_ds"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_DS"))) return false;
            game.setTechnologyDeckID("techs_ds_absol");
            game.setAbsolMode(true);

            game.setBaseGameMode(false);
            game.swapInVariantUnits("absol");
            game.swapInVariantTechs();
            return true;
        }

        // JUST DS
        if (discordantStarsMode) {
            game.setDiscordantStarsMode(discordantStarsMode);
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"))) return false;
            if (!game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_ds"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_DS"))) return false;
            game.setTechnologyDeckID("techs_ds");
            game.setAbsolMode(false);
            game.swapOutVariantTechs();
        }
        game.setDiscordantStarsMode(discordantStarsMode);

        // JUST ABSOL
        if (absolMode) {
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_pok"))) return false;
            if (!game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_pok"))) return false;
            game.setTechnologyDeckID("techs_absol");
            game.setDiscordantStarsMode(false);
            game.swapInVariantUnits("absol");
            game.swapInVariantTechs();
        }
        game.setAbsolMode(absolMode);

        // JUST PoK
        if (!absolMode && !discordantStarsMode) {
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_pok"))) return false;
            if (!game.validateAndSetRelicDeck(event, Mapper.getDeck("relics_pok"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_pok"))) return false;
            game.setTechnologyDeckID("techs_pok");
            game.setBaseGameMode(false);
            game.setAbsolMode(false);
            game.setDiscordantStarsMode(false);
            game.swapOutVariantTechs();
            game.swapInVariantUnits("pok");
        }

        return true;
    }

    private static void sendTIGLSetupText(Game game) {
        WeirdGameSetup.sendTIGLSetupText(game);
    }
}
