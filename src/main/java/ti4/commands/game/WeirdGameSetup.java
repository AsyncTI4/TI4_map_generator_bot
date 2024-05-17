package ti4.commands.game;

import java.util.ArrayList;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class WeirdGameSetup extends GameSubcommandData {
    public WeirdGameSetup() {
        super(Constants.WEIRD_GAME_SETUP, "Game Setup for Weird Games");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.COMMUNITY_MODE, "True to enable Community mode"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FOW_MODE, "True to enable FoW mode"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BASE_GAME_MODE, "True to switch to base game mode."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.MILTYMOD_MODE, "True to switch to MiltyMod mode (only compatabile with Base Game Mode)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to switch out the PoK Agendas & Relics for Absol's "));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to add the Discordant Stars factions to the pool."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BETA_TEST_MODE, "True to test new features that may not be released to all games yet."));
        addOptions(new OptionData(OptionType.BOOLEAN, "extra_secret_mode", "True to allow each player to start with 2 secrets. Great for SftT-less games!"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

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
                if (pingHours < 1) {
                    pingHours = 1;
                }
                activeGame.setAutoPingSpacer(pingHours);
            }
        }

        String customGameName = event.getOption(Constants.GAME_CUSTOM_NAME, null, OptionMapping::getAsString);
        if (customGameName != null) {
            activeGame.setCustomName(customGameName);
        }

        if (!setGameMode(event, activeGame)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong and the game modes could not be set, please see error above.");
        }

        Boolean betaTestMode = event.getOption(Constants.BETA_TEST_MODE, null, OptionMapping::getAsBoolean);
        if (betaTestMode != null) activeGame.setTestBetaFeaturesMode(betaTestMode);

        Boolean extraSecretMode = event.getOption("extra_secret_mode", null, OptionMapping::getAsBoolean);
        if (extraSecretMode != null) activeGame.setExtraSecretMode(extraSecretMode);
    }

    public static boolean setGameMode(SlashCommandInteractionEvent event, Game activeGame) {
        if (event.getOption(Constants.TIGL_GAME) == null && event.getOption(Constants.ABSOL_MODE) == null && event.getOption(Constants.DISCORDANT_STARS_MODE) == null
            && event.getOption(Constants.BASE_GAME_MODE) == null && event.getOption(Constants.MILTYMOD_MODE) == null) {
            return true; //no changes were made
        }
        boolean isTIGLGame = event.getOption(Constants.TIGL_GAME, activeGame.isCompetitiveTIGLGame(), OptionMapping::getAsBoolean);
        boolean absolMode = event.getOption(Constants.ABSOL_MODE, activeGame.isAbsolMode(), OptionMapping::getAsBoolean);
        boolean miltyModMode = event.getOption(Constants.MILTYMOD_MODE, activeGame.isMiltyModMode(), OptionMapping::getAsBoolean);
        boolean discordantStarsMode = event.getOption(Constants.DISCORDANT_STARS_MODE, activeGame.isDiscordantStarsMode(), OptionMapping::getAsBoolean);
        boolean baseGameMode = event.getOption(Constants.BASE_GAME_MODE, activeGame.isBaseGameMode(), OptionMapping::getAsBoolean);
        return setGameMode(event, activeGame, baseGameMode, absolMode, miltyModMode, discordantStarsMode, isTIGLGame);
    }

    // TODO: find a better way to handle this - this is annoying
    public static boolean setGameMode(GenericInteractionCreateEvent event, Game activeGame, boolean baseGameMode, boolean absolMode, boolean miltyModMode, boolean discordantStarsMode,
        boolean isTIGLGame) {
        if (isTIGLGame
            && (baseGameMode || absolMode || discordantStarsMode || activeGame.isHomeBrewSCMode() || activeGame.isFoWMode() || activeGame.isAllianceMode() || activeGame.isCommunityMode())) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "TIGL Games can not be mixed with other game modes.");
            return false;
        } else if (isTIGLGame) {
            activeGame.setCompetitiveTIGLGame(true);
            sendTIGLSetupText(activeGame);
            return true;
        }

        if (miltyModMode && !baseGameMode) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Milty Mod Mode can only be combined with Base Game Mode. Please set the game to Base Game Mode first.");
            return false;
        }

        if (baseGameMode && (absolMode || discordantStarsMode)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Base Game Mode is not supported with Discordant Stars or Absol Mode");
            return false;
        } else if (baseGameMode && miltyModMode) {
            if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_miltymod"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_miltymod"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_miltymod"))) return false;
            if (!activeGame.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_miltymod"))) return false;
            if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_miltymod"))) return false;
            if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_base"))) return false;
            if (!activeGame.validateAndSetExploreDeck(event, Mapper.getDeck("explores_base"))) return false;

            for (Player player : activeGame.getPlayers().values()) {
                player.setLeaders(new ArrayList<>());
                if (player.getUnitByBaseType("mech") != null) player.removeOwnedUnitByID(player.getUnitByBaseType("mech").getId());
            }

            activeGame.setScSetID("miltymod");

            activeGame.setTechnologyDeckID("techs_miltymod");
            activeGame.swapInVariantTechs();
            activeGame.swapInVariantUnits("miltymod");
            activeGame.setBaseGameMode(true);
            activeGame.setMiltyModMode(true);
            activeGame.setAbsolMode(false);
            activeGame.setDiscordantStarsMode(false);
            return true;
        } else if (baseGameMode) {
            if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_base_game"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_base"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_base"))) return false;
            if (!activeGame.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_base"))) return false;
            if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_basegame_and_codex1"))) return false;
            if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_base"))) return false;
            if (!activeGame.validateAndSetExploreDeck(event, Mapper.getDeck("explores_base"))) return false;

            for (Player player : activeGame.getPlayers().values()) {
                player.setLeaders(new ArrayList<>());
                if (player.getUnitByBaseType("mech") != null) player.removeOwnedUnitByID(player.getUnitByBaseType("mech").getId());
            }

            activeGame.setScSetID("base_game");

            activeGame.setTechnologyDeckID("techs_base");
            activeGame.setBaseGameMode(true);
            activeGame.setAbsolMode(false);
            activeGame.setDiscordantStarsMode(false);
            return true;
        }
        activeGame.setBaseGameMode(false);
        activeGame.setMiltyModMode(false);

        // BOTH ABSOL & DS, and/or if either was set before the other
        if (absolMode && discordantStarsMode) {
            activeGame.setDiscordantStarsMode(true);
            if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!activeGame.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"))) return false;
            if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol_ds"))) return false;
            if (!activeGame.validateAndSetExploreDeck(event, Mapper.getDeck("explores_DS"))) return false;
            activeGame.setTechnologyDeckID("techs_ds_absol");
            activeGame.setAbsolMode(true);

            activeGame.setBaseGameMode(false);
            activeGame.swapInVariantUnits("absol");
            activeGame.swapInVariantTechs();
            return true;
        }

        // JUST DS
        if (discordantStarsMode) {
            activeGame.setDiscordantStarsMode(discordantStarsMode);
            if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_pok"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!activeGame.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"))) return false;
            if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_ds"))) return false;
            if (!activeGame.validateAndSetExploreDeck(event, Mapper.getDeck("explores_DS"))) return false;
            activeGame.setTechnologyDeckID("techs_ds");
            activeGame.setAbsolMode(false);
            activeGame.swapOutVariantTechs();
        }
        activeGame.setDiscordantStarsMode(discordantStarsMode);

        // JUST ABSOL
        if (absolMode) {
            if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!activeGame.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_pok"))) return false;
            if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_absol"))) return false;
            if (!activeGame.validateAndSetExploreDeck(event, Mapper.getDeck("explores_pok"))) return false;
            activeGame.setTechnologyDeckID("techs_absol");
            activeGame.setDiscordantStarsMode(false);
            activeGame.swapInVariantUnits("absol");
            activeGame.swapInVariantTechs();
        }
        activeGame.setAbsolMode(absolMode);

        // JUST PoK
        if (!absolMode && !discordantStarsMode) {
            if (!activeGame.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_pok"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!activeGame.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!activeGame.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!activeGame.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_pok"))) return false;
            if (!activeGame.validateAndSetRelicDeck(event, Mapper.getDeck("relics_pok"))) return false;
            if (!activeGame.validateAndSetExploreDeck(event, Mapper.getDeck("explores_pok"))) return false;
            activeGame.setTechnologyDeckID("techs_pok");
            activeGame.setBaseGameMode(false);
            activeGame.setAbsolMode(false);
            activeGame.setDiscordantStarsMode(false);
            activeGame.swapOutVariantTechs();
            activeGame.swapInVariantUnits("pok");
        }

        return true;
    }

    private static void sendTIGLSetupText(Game activeGame) {
        String sb = "# " + Emojis.TIGL + "TIGL\nThis game has been flagged as a Twilight Imperium Global League (TIGL) Game!\n" +
            "Please ensure you have all:\n" +
            "- [Signed up for TIGL](https://forms.gle/QQKWraMyd373GsLN6)\n" +
            "- Read and accepted the TIGL [Code of Conduct](https://discord.com/channels/943410040369479690/1003741148017336360/1155173892734861402)\n" +
            "For more information, please see this channel: https://discord.com/channels/943410040369479690/1003741148017336360";
        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), sb);
    }
}
