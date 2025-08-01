package ti4.commands.game;

import java.util.ArrayList;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.TIGLHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService.FOWOption;

class WeirdGameSetup extends GameStateSubcommand {

    public WeirdGameSetup() {
        super(Constants.WEIRD_GAME_SETUP, "Game Setup for Weird Games", true, false);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.COMMUNITY_MODE, "True to enable Community mode"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FOW_MODE, "True to enable FoW mode"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.BASE_GAME_MODE, "True to switch to No Expansion (base game) mode."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.MILTYMOD_MODE, "True to switch to MiltyMod mode (only compatabile with No Expansion Mode)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to switch out the PoK Agendas & Relics for Absol's "));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to add the Discordant Stars factions to the pool."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.UNCHARTED_SPACE_STUFF, "True to add the Uncharted Space Stuff to the draft pool."));
        //addOptions(new OptionData(OptionType.BOOLEAN, Constants.BETA_TEST_MODE, "True to test new features that may not be released to all games yet."));
        addOptions(new OptionData(OptionType.INTEGER, Constants.CC_LIMIT, "Command token limit each player should have, default 16."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.EXTRA_SECRET_MODE, "True to allow each player to start with 2 secret objectives. Great for SftT-less games!"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.VOTC_MODE, "True to enable Voices of the Council homebrew mod."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FACILITIES_MODE, "True to enable Cacotopos Facilities"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_SWAP_MODE, "True to enable No Support Swaps"));
        addOptions(new OptionData(OptionType.STRING, Constants.PRIORITY_TRACK, "Enable the Priority Track for this game").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Boolean communityMode = event.getOption(Constants.COMMUNITY_MODE, null, OptionMapping::getAsBoolean);
        if (communityMode != null) game.setCommunityMode(communityMode);

        Boolean fowMode = event.getOption(Constants.FOW_MODE, null, OptionMapping::getAsBoolean);
        if (fowMode != null) game.setFowMode(fowMode);

        if (!setGameMode(event, game)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong and the game modes could not be set, please see error above.");
        }

        // Boolean betaTestMode = event.getOption(Constants.BETA_TEST_MODE, null, OptionMapping::getAsBoolean);
        // if (betaTestMode != null) game.setTestBetaFeaturesMode(betaTestMode);

        Boolean uncharted = event.getOption(Constants.UNCHARTED_SPACE_STUFF, null, OptionMapping::getAsBoolean);
        if (uncharted != null) {
            game.setUnchartedSpaceStuff(uncharted);
            if (uncharted) {
                game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_DS"));
                game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"));
                if (game.isAbsolMode()) {
                    if (game.getTechnologyDeckID().contains("absol")) {
                        game.setTechnologyDeckID("techs_ds_absol");
                    }
                    if (game.getRelicDeckID().contains("absol")) {
                        game.validateAndSetRelicDeck(Mapper.getDeck("relics_absol_ds"));
                    }

                } else {
                    game.validateAndSetRelicDeck(Mapper.getDeck("relics_ds"));
                    game.setTechnologyDeckID("techs_ds");

                }
            }
        }

        Boolean extraSecretMode = event.getOption("extra_secret_mode", null, OptionMapping::getAsBoolean);
        if (extraSecretMode != null) game.setExtraSecretMode(extraSecretMode);

        Boolean facilitiesMode = event.getOption(Constants.FACILITIES_MODE, null, OptionMapping::getAsBoolean);
        if (facilitiesMode != null) game.setFacilitiesMode(facilitiesMode);

        Boolean noSwapMode = event.getOption(Constants.NO_SWAP_MODE, null, OptionMapping::getAsBoolean);
        if (noSwapMode != null) game.setNoSwapMode(noSwapMode);

        Integer cclimit = event.getOption(Constants.CC_LIMIT, null, OptionMapping::getAsInt);
        if (cclimit != null) game.setStoredValue("ccLimit", cclimit + "");

        String priorityTrackModeString = event.getOption(Constants.PRIORITY_TRACK, null, OptionMapping::getAsString);
        if (priorityTrackModeString != null) {
            PriorityTrackMode priorityTrackMode = PriorityTrackMode.parse(priorityTrackModeString);
            if (game.isCompetitiveTIGLGame()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "TIGL Games can not be mixed with other game modes. Priority Track is unchanged.");
            } else if (game.isOmegaPhaseMode() && priorityTrackMode != PriorityTrackMode.FULL) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Omega Phase requires the __full__ Priority Track. Priority Track is unchanged.");
            } else if (game.getPhaseOfGame().equals("strategy")) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot add or remove the Priority Track during the Strategy Phase. Either UNDO out of it or wait for after it. Priority Track is unchanged.");
            } else {
                game.setPriorityTrackMode(priorityTrackMode);
            }
        }
    }

    public static boolean setGameMode(SlashCommandInteractionEvent event, Game game) {
        if (event.getOption(Constants.TIGL_GAME) == null && event.getOption(Constants.ABSOL_MODE) == null && event.getOption(Constants.DISCORDANT_STARS_MODE) == null
            && event.getOption(Constants.BASE_GAME_MODE) == null && event.getOption(Constants.MILTYMOD_MODE) == null && event.getOption(Constants.VOTC_MODE) == null
            && event.getOption(FOWOption.RIFTSET_MODE.toString()) == null) {
            return true; //no changes were made
        }
        boolean isTIGLGame = event.getOption(Constants.TIGL_GAME, game.isCompetitiveTIGLGame(), OptionMapping::getAsBoolean);
        boolean absolMode = event.getOption(Constants.ABSOL_MODE, game.isAbsolMode(), OptionMapping::getAsBoolean);
        boolean miltyModMode = event.getOption(Constants.MILTYMOD_MODE, game.isMiltyModMode(), OptionMapping::getAsBoolean);
        boolean discordantStarsMode = event.getOption(Constants.DISCORDANT_STARS_MODE, game.isDiscordantStarsMode(), OptionMapping::getAsBoolean);
        boolean baseGameMode = event.getOption(Constants.BASE_GAME_MODE, game.isBaseGameMode(), OptionMapping::getAsBoolean);
        boolean votcMode = event.getOption(Constants.VOTC_MODE, game.isVotcMode(), OptionMapping::getAsBoolean);
        return setGameMode(event, game, baseGameMode, absolMode, miltyModMode, discordantStarsMode, isTIGLGame, votcMode);
    }

    // TODO: find a better way to handle this - this is annoying
    // NOTE: (Jazz) This seems okay. Could use improvements to reduce manual handling, but it's fine for now.
    public static boolean setGameMode(GenericInteractionCreateEvent event, Game game, boolean baseGameMode, boolean absolMode, boolean miltyModMode, boolean discordantStarsMode, boolean isTIGLGame, boolean votcMode) {
        if (isTIGLGame && (baseGameMode || absolMode || discordantStarsMode || game.isHomebrewSCMode() || game.isFowMode() || game.isAllianceMode() || game.isCommunityMode() || votcMode)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "TIGL Games can not be mixed with other game modes.");
            return false;
        } else if (isTIGLGame) {
            TIGLHelper.initializeTIGLGame(game);
            return true;
        }
        if (!isTIGLGame) {
            game.setCompetitiveTIGLGame(false);
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
            if (!game.validateAndSetRelicDeck(Mapper.getDeck("relics_base"))) return false;
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
            if (!game.validateAndSetRelicDeck(Mapper.getDeck("relics_base"))) return false;
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
            if (!game.validateAndSetRelicDeck(Mapper.getDeck("relics_absol_ds"))) return false;
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
            game.setDiscordantStarsMode(true);
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"))) return false;
            if (!game.validateAndSetRelicDeck(Mapper.getDeck("relics_ds"))) return false;
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
            if (!game.validateAndSetRelicDeck(Mapper.getDeck("relics_absol"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_pok"))) return false;
            game.setTechnologyDeckID("techs_absol");
            game.setDiscordantStarsMode(false);
            game.swapInVariantUnits("absol");
            game.swapInVariantTechs();
        }
        game.setAbsolMode(absolMode);

        // JUST PoK
        if (!absolMode && !discordantStarsMode && !votcMode) {
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_pok"))) return false;
            if (!game.validateAndSetRelicDeck(Mapper.getDeck("relics_pok"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_pok"))) return false;
            game.setTechnologyDeckID("techs_pok_c4");
            game.setBaseGameMode(false);
            game.setAbsolMode(false);
            game.setDiscordantStarsMode(false);
            game.swapOutVariantTechs();
            game.swapInVariantUnits("pok");
        }

        if (votcMode) {
            if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_cryypter"))) return false;
            if (!game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_pok"))) return false;
            if (!game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_pok"))) return false;
            if (!game.validateAndSetSecretObjectiveDeck(event, Mapper.getDeck("secret_objectives_pok"))) return false;
            if (!game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_cryypter"))) return false;
            if (!game.validateAndSetRelicDeck(Mapper.getDeck("relics_pok"))) return false;
            if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_pok"))) return false;
            game.setTechnologyDeckID("techs_cryypter");
            game.setBaseGameMode(false);
            game.setAbsolMode(false);
            game.setDiscordantStarsMode(false);
            game.swapInVariantTechs();
            game.swapInVariantUnits("pok");
            game.setScSetID("votc");
            game.setVotcMode(true);

            // Add envoys to players
            for (Player player : game.getPlayers().values()) {
                String faction = player.getFaction();

                if (player.hasLeader("xxchahero")) {
                    player.removeLeader("xxchahero");
                    player.addLeader("votc_xxchahero");
                }
                if (player.hasLeader("keleresheroodlynn")) {
                    player.removeLeader("keleresheroodlynn");
                    player.addLeader("votc_keleresheroxxcha");
                }

                if (faction.startsWith("keleres")) {
                    faction = "keleres";
                }

                String leaderID = faction + "envoy";
                if (Mapper.isValidLeader(leaderID)) {
                    player.addLeader(leaderID);
                }
            }
        }

        return true;
    }
}
