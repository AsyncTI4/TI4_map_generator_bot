package ti4.commands.game;

import java.util.Optional;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Setup extends GameSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Game Setup");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLAYER_COUNT_FOR_MAP, "Specify player map size between 2 or 30. Default 6").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.VP_COUNT, "Specify game VP count. Default is 10").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_CUSTOM_NAME, "Add Custom description to game").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TIGL_GAME, "True to mark the game as TIGL"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.COMMUNITY_MODE, "True if want Community Mode for map, False to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FOW_MODE, "True if want FoW Mode for map, False to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BASE_GAME_MODE, "True to "));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to switch out the PoK Agendas & Relics for Absol's "));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to add the Discordant Stars factions to the pool."));
        addOptions(new OptionData(OptionType.INTEGER, Constants.AUTO_PING, "Hours between auto pings. Min 1. Enter 0 to turn off."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BETA_TEST_MODE, "True to test new features that may not be released to all games yet."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping playerCount = event.getOption(Constants.PLAYER_COUNT_FOR_MAP);
        if (playerCount != null) {
            int count = playerCount.getAsInt();
            if (count < 2 || count > 30) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify between 2 or 30 players.");
            } else {
                activeGame.setPlayerCountForMap(count);
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
            activeGame.setVp(count);
        }

        Boolean communityMode = event.getOption(Constants.COMMUNITY_MODE, null, OptionMapping::getAsBoolean);
        if (communityMode != null) activeGame.setCommunityMode(communityMode);

        Boolean fowMode = event.getOption(Constants.FOW_MODE, null, OptionMapping::getAsBoolean);
        if (fowMode != null) activeGame.setFoWMode(fowMode);

        Integer pingHours = event.getOption(Constants.AUTO_PING, null, OptionMapping::getAsInt);
        if (pingHours != null) {
            if (pingHours == 0) {
                activeGame.setAutoPing(false);
                activeGame.setAutoPingSpacer(pingHours);
            } else {
                activeGame.setAutoPing(true);
                if (pingHours < 1){
                    pingHours = 1;
                }
                activeGame.setAutoPingSpacer(pingHours);
            }
        }

        String customGameName = event.getOption(Constants.GAME_CUSTOM_NAME, null, OptionMapping::getAsString);
        if (customGameName != null) {
            activeGame.setCustomName(customGameName);
        }

        // GAME MODES
        Boolean isTIGLGame = event.getOption(Constants.TIGL_GAME, null, OptionMapping::getAsBoolean);
        if (isTIGLGame != null && isTIGLGame) {
            getActiveGame().setCompetitiveTIGLGame(true);
        }

        Boolean absolMode = event.getOption(Constants.ABSOL_MODE, null, OptionMapping::getAsBoolean);
        Boolean discordantStarsMode = event.getOption(Constants.DISCORDANT_STARS_MODE, null, OptionMapping::getAsBoolean);
        Boolean baseGameMode = event.getOption(Constants.BASE_GAME_MODE, null, OptionMapping::getAsBoolean);

        if (!setGameMode(event, activeGame, baseGameMode, absolMode, discordantStarsMode, isTIGLGame)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong and the game modes could not be set, please see error above.");
        }

        Boolean betaTestMode = event.getOption(Constants.BETA_TEST_MODE, null, OptionMapping::getAsBoolean);
        if (betaTestMode != null) activeGame.setTestBetaFeaturesMode(betaTestMode);
    }

    public static boolean setGameMode(GenericInteractionCreateEvent event, Game activeGame, Boolean baseGameMode, Boolean absolMode, Boolean discordantStarsMode, Boolean tiglGame) {
        
        // TODO: Validate TIGL is not a homebrew game 

        // BOTH ABSOL & DS, and/or if either was set before the other
        if (Optional.ofNullable(absolMode).orElse(activeGame.isAbsolMode()) && Optional.ofNullable(discordantStarsMode).orElse(activeGame.isDiscordantStarsMode())) {
            if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"))) return false;
            if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"))) return false;
            if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol_ds"))) return false;
            activeGame.setTechnologyDeckID("techs_ds_absol");
            // SOMEHOW HANDLE MECHS AND STARTING/FACTION TECHS
            activeGame.setAbsolMode(absolMode);
            activeGame.setDiscordantStarsMode(discordantStarsMode);
            return true;
        }
    
        // JUST DS
        if (discordantStarsMode != null) {
            if (discordantStarsMode) {
                if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"))) return false;
                if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_ds"))) return false;
                activeGame.setTechnologyDeckID("techs_ds");
            }
            activeGame.setDiscordantStarsMode(discordantStarsMode);
        }

        // JUST ABSOL
        if (absolMode != null) {
            if (absolMode) {
                if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"))) return false;
                if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol"))) return false;
                activeGame.setTechnologyDeckID("techs_absol");
                // SOMEHOW HANDLE MECHS AND STARTING/FACTION TECHS
            }
            activeGame.setAbsolMode(absolMode);
        }

        if (baseGameMode != null && baseGameMode) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "For Base Game Mode, please run /custom change_to_base_game. This mode is still Work in Progress");
        }

        return true;
    }
}

