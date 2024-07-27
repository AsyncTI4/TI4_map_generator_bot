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
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FOW_MODE, "True to enable fog of war mode"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BASE_GAME_MODE, "True to switch to No Expansion (base game) mode."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.MILTYMOD_MODE, "True to switch to MiltyMod mode (only compatible with No Expansion Mode)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to switch out the PoK Agendas & Relics for Absol's "));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to add the Discordant Stars factions to the pool."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.AGE_OF_EXPLORATION_MODE, "True to enable the Age of Exploration, per Dane Tweet."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.MINOR_FACTIONS_MODE, "True to enable the Minor Factions, per Dane Tweet."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BETA_TEST_MODE, "True to test new features that may not be released to all games yet."));
        addOptions(new OptionData(OptionType.BOOLEAN, "extra_secret_mode", "True to allow each player to start with 2 secrets. Great for SftT-less games!"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        Boolean communityMode = event.getOption(Constants.COMMUNITY_MODE, null, OptionMapping::getAsBoolean);
        if (communityMode != null) game.setCommunityMode(communityMode);

        Boolean fowMode = event.getOption(Constants.FOW_MODE, null, OptionMapping::getAsBoolean);
        if (fowMode != null) game.setFowMode(fowMode);

        String customGameName = event.getOption(Constants.GAME_CUSTOM_NAME, null, OptionMapping::getAsString);
        if (customGameName != null) {
            game.setCustomName(customGameName);
        }

        if (!setGameMode(event, game)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong and the game modes could not be set, please see error above.");
        }

        Boolean betaTestMode = event.getOption(Constants.BETA_TEST_MODE, null, OptionMapping::getAsBoolean);
        if (betaTestMode != null) game.setTestBetaFeaturesMode(betaTestMode);

        Boolean explorationMode = event.getOption(Constants.AGE_OF_EXPLORATION_MODE, null, OptionMapping::getAsBoolean);
        if (explorationMode != null) game.setAgeOfExplorationMode(explorationMode);

        Boolean minorMode = event.getOption(Constants.MINOR_FACTIONS_MODE, null, OptionMapping::getAsBoolean);
        if (minorMode != null) game.setMinorFactionsMode(minorMode);

        Boolean extraSecretMode = event.getOption("extra_secret_mode", null, OptionMapping::getAsBoolean);
        if (extraSecretMode != null) game.setExtraSecretMode(extraSecretMode);
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
    // NOTE: (Jazz) This seems okay. Could use improvements to reduce manual handling, but it's fine for now.
    public static boolean setGameMode(GenericInteractionCreateEvent event, Game game, boolean baseGameMode, boolean absolMode, boolean miltyModMode, boolean discordantStarsMode, boolean isTIGLGame) {
        if (isTIGLGame && (baseGameMode || absolMode || discordantStarsMode || game.isHomebrewSCMode() || game.isFowMode() || game.isAllianceMode() || game.isCommunityMode())) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "TIGL Games cannot be mixed with other game modes.");
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

    public static void sendTIGLSetupText(Game game) {
        String sb = "# " + Emojis.TIGL + "TIGL\nThis game has been flagged as a Twilight Imperium Global League (TIGL) Game!\n" +
            "Please ensure you have all:\n" +
            "- [Signed up for TIGL](https://forms.gle/QQKWraMyd373GsLN6)\n" +
            "- Read and accepted the TIGL [Code of Conduct](https://discord.com/channels/943410040369479690/1003741148017336360/1155173892734861402)\n" +
            "For more information, please see this channel: https://discord.com/channels/943410040369479690/1003741148017336360";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb);
    }
}
