package ti4.service.option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.franken.ban.BanService;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.draft.TwilightsFallFrankenDraft;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.Source.ComponentSource;
import ti4.model.SourceModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.emoji.SourceEmojis;
import ti4.service.fow.GMService;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.game.MonumentsService;

@UtilityClass
public class TEOptionService {

    /**
     * These homebrew-toggle confirmations were hardcoded to the public main channel regardless of where the
     * GM clicked from - in a FoW game that leaks setup chatter to every player, so route to the GM room instead.
     */
    private static MessageChannel homebrewChannel(Game game) {
        return game.isFowMode() ? GMService.getGMChannel(game) : game.getMainGameChannel();
    }

    @ButtonHandler("startTFGame")
    public static void startTFGame(Game game, ButtonInteractionEvent event) {
        // ButtonHelper.deleteMessage(event);
        String msg = """
            There are currently two draft options for Twilight's Fall.

            There is a bag draft option, \
            where you draft everything (tiles, mahact king faction, speaker position, starting fleet, starting HS initial abilities, etc), \
            and then there is a milty draft option where you draft slice, mahact king faction, and a pack of 3 faction cards (from which you \
            get speaker position, starting home system, and starting fleet.) After you finish the milty draft option, you'll do a bag draft (called in the rules an Inaugurul Splice) where \
            you draft abilities/units/genomes.

            The second option is closer to Rules As Written, the first is closer to a classic franken draft.""";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("startTFDraft_bag", "Use Bag Draft of Everything"));
        // Milty/Nucleus draft slices, tiles and speaker order - in FoW the GM hand-builds the map and sets
        // seat/speaker order via `/fow setup` instead, and tile/draft-order categories are already forced to
        // 0 for FoW (FrankenDraft.isFowExcludedCategory), so those two drafts have nothing left to draft.
        if (!game.isFowMode()) {
            buttons.add(
                    Buttons.gray("startDraftSystem_andcatPresetMilty", "Start Milty Draft + Later Inaugural Splice"));
            buttons.add(Buttons.gray(
                    "startDraftSystem_andcatPresetNucleus", "Start Nucleus Draft + Later Inaugural Splice"));
        } else {
            // The splice is normally phase 2 after a milty/nucleus draft. In FoW the wizard already does what
            // those drafts would (map, factions, positions, seat/speaker order), so the splice on its own is
            // the RAW-style option here - players still draft their abilities/units/genomes.
            buttons.add(Buttons.gray("startTFDraft_splice", "Inaugural Splice Only (abilities/units/genomes)"));
            msg += "\n\n-# Fog of War: Milty/Nucleus aren't offered - they draft slices, tiles and speaker "
                    + "order, which the `/fow setup` wizard handles itself. Use **Inaugural Splice Only** for the "
                    + "RAW-style flow once the wizard has assigned factions and positions.";
        }
        buttons.add(Buttons.red("editTFHomebrew", "Enable TF Homebrew options"));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("startTFDraft")
    public static void startTFDraft(ButtonInteractionEvent event, Game game) {
        game.setupTwilightsFallMode(event);
        if (event.getButton().getCustomId().endsWith("_splice")) {
            // force=false so any player the GM already set up through the wizard keeps their faction, colour
            // and (crucially) their assigned home position - setUpFrankenFactions with force=true re-parks
            // everyone at the temporary off-map 50x anchors, which would undo the wizard's placements.
            FrankenDraftBagService.setUpFrankenFactions(game, event, false);
            FrankenDraftBagService.clearPlayerHands(game);
            // Same entry point the automatic post-milty splice uses; it deliberately skips seat-order
            // assignment in FoW, since the wizard owns that.
            ButtonHelperTwilightsFall.startInauguralSplice(game);
            ButtonHelper.deleteMessage(event);
            return;
        }
        FrankenDraftBagService.setUpFrankenFactions(game, event, true);
        FrankenDraftBagService.clearPlayerHands(game);
        game.setBagDraft(new TwilightsFallFrankenDraft(game));
        FrankenDraftBagService.startDraft(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "editTFHomebrew", save = false)
    private static void postTwilightFallHomebrewOptions(ButtonInteractionEvent event, Game game) {
        String msg = "Use the buttons to enable or disable various homebrew options:";
        List<ContainerChildComponent> sections = getTFHomebrewInfo(game);
        MessageV2Builder builder = new MessageV2Builder(homebrewChannel(game));
        builder.append(msg);
        builder.append(Container.of(sections));
        builder.append(Buttons.DONE_DELETE_BUTTONS);
        builder.send();

        // MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), msg, buttons);
    }

    public static List<ContainerChildComponent> getTFHomebrewInfo(Game game) {
        String idPre = "toggleTFHomebrew_";
        List<ContainerChildComponent> sections = new ArrayList<>();

        SourceModel tk = Mapper.getSource("twilight_kart");
        String tkId = idPre + "twilightkart";
        Button button = Buttons.rgToggle(game.isTwilightKart(), tkId, "Twilight Kart", SourceEmojis.TwilightKart);
        sections.add(Section.of(button, tk.getRepresentationTextDisplays()));

        SourceModel teds = Mapper.getSource("twilight_ds");
        String tedsID = idPre + "twilightds";
        Button button2 =
                Buttons.rgToggle(game.isTwilightDS(), tedsID, "Discordant Stars", SourceEmojis.DiscordantStars);
        sections.add(Section.of(button2, teds.getRepresentationTextDisplays()));

        SourceModel monuments = Mapper.getSource("monuments");
        String monumentsId = idPre + "monuments";
        Button monumentsButton =
                Buttons.rgToggle(game.isMonumentsMode(), monumentsId, "Monuments+", SourceEmojis.Monuments);
        sections.add(Section.of(monumentsButton, monuments.getRepresentationTextDisplays()));
        // sections.add(Separator.create(true, Spacing.LARGE));

        return sections;
    }

    @ButtonHandler("toggleTFHomebrew")
    private static void toggleTFHomebrew(ButtonInteractionEvent event, Game game, String buttonID) {
        String homebrew = buttonID.split("_")[1];
        switch (homebrew) {
            case "twilightkart" -> {
                game.setTwilightKart(!game.isTwilightKart());
                if (game.isTwilightKart()) {
                    game.setupTwilightsFallMode(event);
                    List<Button> buttons = new ArrayList<>();
                    game.removeStoredValue("bannedUnits");
                    buttons.add(Buttons.green("twilightDSSetup_pruned", "Just 4 units of each type"));
                    buttons.add(Buttons.blue("deleteButtons", "All the Units"));
                    MessageHelper.sendMessageToChannel(
                            homebrewChannel(game),
                            "Some people find there's too many units and would prefer to prune the deck to just 4 random units of each type (normal deck has 31 units, TK + normal is 60 units, pruned is 43 units)",
                            buttons);
                }
            }
            case "twilightds" -> {
                game.setTwilightDS(!game.isTwilightDS());
                if (game.isTwilightDS()) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("twilightDSSetup_justds", "Just DS Abilities"));
                    buttons.add(Buttons.blue("twilightDSSetup_mixture", "Mixture of Normal and DS abilities"));
                    MessageHelper.sendMessageToChannel(
                            homebrewChannel(game),
                            "Do you want to use just DS abilities or a mixture of Normal and DS abilities?",
                            buttons);
                }
            }
            case "monuments" -> {
                game.setMonumentsMode(!game.isMonumentsMode());
                if (game.isMonumentsMode()) {
                    MonumentsService.applyTwilightsFallMonuments(game);
                    MessageHelper.sendMessageToChannel(
                            homebrewChannel(game),
                            "Added Monuments+ secret objectives and the Monuments+ Twilight's Fall strategy card set.");
                }
            }
        }
        postTwilightFallHomebrewOptions(event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("twilightDSSetup_")
    public static void twilightDSSetup(ButtonInteractionEvent event, Game game, String buttonID) {
        String choice = buttonID.split("_")[1];
        switch (choice.toLowerCase()) {
            case "justds" -> {
                MessageHelper.sendMessageToChannel(homebrewChannel(game), "Chose to just use DS abilities");
                List<String> allCards = Mapper.getDeck("techs_tf").getNewShuffledDeck();
                game.removeStoredValue("bannedTechs");
                for (String tech : allCards) {
                    BanService.appendStoredValue(game, "bannedTechs", tech);
                }
            }
            case "mixture" -> {
                MessageHelper.sendMessageToChannel(
                        homebrewChannel(game), "Chose to just use a mixture of DS and normal abilities");
                List<String> allCards = Mapper.getDeck("techs_tf").getNewShuffledDeck();
                game.removeStoredValue("bannedTechs");
                for (TechnologyModel tech : Mapper.getTechs().values()) {
                    if (tech.getSource() == ComponentSource.twilight_ds) {
                        allCards.add(tech.getID());
                    }
                }
                Collections.shuffle(allCards);
                String msg = "The following abilities have been banned:\n";
                for (int x = 0; x < allCards.size() / 2; x++) {
                    BanService.appendStoredValue(game, "bannedTechs", allCards.get(x));
                    msg += Mapper.getTech(allCards.get(x)).getName() + "\n";
                }
                MessageHelper.sendMessageToChannel(homebrewChannel(game), msg);
            }
            case "pruned" -> {
                MessageHelper.sendMessageToChannel(homebrewChannel(game), "Chose to just use a pruned deck of units.");
                List<String> allCards = Mapper.getDeck("twilight_kart_units").getNewShuffledDeck();
                game.removeStoredValue("bannedUnits");
                String msg = "The following units have been banned:\n";
                Map<UnitType, Integer> unitCount = new HashMap<>();

                for (String unit : allCards) {
                    UnitModel un = Mapper.getUnit(unit);
                    UnitType type = un.getUnitType();
                    unitCount.put(type, unitCount.getOrDefault(type, 0) + 1);
                    if (unitCount.get(type) > 4) {
                        BanService.appendStoredValue(game, "bannedUnits", unit);
                        msg += un.getName() + "\n";
                    }
                }
                MessageHelper.sendMessageToChannel(homebrewChannel(game), msg);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("chooseExp_")
    public static void chooseExp(ButtonInteractionEvent event, Game game, String buttonID) {
        String choice = buttonID.split("_")[1];
        switch (choice.toLowerCase()) {
            case "newpok" -> {
                game.removeStoredValue("useOldPok");
                game.setThundersEdge(false);
                game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_pok"));
            }
            case "oldpok" -> {
                game.setStoredValue("useOldPok", "true");
                game.setThundersEdge(false);
            }
            case "te" -> {
                game.setThundersEdge(true);
                game.removeStoredValue("useOldPok");
            }
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Set game to use "
                        + ("newPok".equalsIgnoreCase(choice)
                                ? "New PoK"
                                : "oldPok".equalsIgnoreCase(choice) ? "Old PoK" : "Thunder's Edge + New PoK")
                        + " components.");
    }

    @ButtonHandler(value = "offerTEOptionButtons", save = false)
    public static void offerTEOptionButtons(Game game, MessageChannel channel) {
        List<Button> galacticEventButtons = getGalacticEventButtons(game);
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel,
                "Enable or Disable Galactic Events\n-# See [here](https://twilight-imperium.fandom.com/wiki/Galactic_Events) for details",
                galacticEventButtons);

        // String msg =
        //         "Thunder's Edge contains a new version of mecatol rex, which is legendary (it's ability allows you to
        // discard and then draw a secret objective). If you want to play with this new version of mecatol rex in your
        // game, press this button and it will be added to the map when secrets are dealt.";
        // List<Button> buttons = new ArrayList<>();
        // buttons.add(Buttons.green("addLegendaryMecatol", "Use Legendary Mecatol Rex"));
        // buttons.add(Buttons.red("deleteButtons", "Decline"));
        // MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);

        // msg =
        //         "Thunder's Edge contains a new anomaly, called an entropic scar, which gives faction tech in status
        // phase at the cost of a strategy command token. If you want the bot's milty to potentially include this scar
        // (and other TE tiles), press this button.";
        // buttons = new ArrayList<>();
        // buttons.add(Buttons.green("addEntropicScar", "Use Entropic Scar & Other Tiles"));
        // buttons.add(Buttons.red("deleteButtons", "Decline"));
        // MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);

        // msg =
        //         "Thunder's Edge contains two new strategy cards, Construction and Warfare. You can use them in this
        // game by pressing the button below.";

    }

    public static List<Button> getGalacticEventButtons(Game game) {
        List<Button> galacticEventButtons = new ArrayList<>();

        if (game.isMinorFactionsMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_minorFactions_disable", "Disable Minor Factions"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_minorFactions_enable", "Enable Minor Factions"));
        }
        if (game.isHiddenAgendaMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_hiddenAgenda_disable", "Disable Hidden Agenda"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_hiddenAgenda_enable", "Enable Hidden Agenda"));
        }
        if (game.isAgeOfExplorationMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_ageOfExploration_disable", "Disable Age of Exploration"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_ageOfExploration_enable", "Enable Age of Exploration"));
        }
        if (game.isTotalWarMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_totalWar_disable", "Disable Total War"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_totalWar_enable", "Enable Total War"));
        }
        if (game.isDangerousWildsMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_DangerousWilds_disable", "Disable Dangerous Wilds"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_DangerousWilds_enable", "Enable Dangerous Wilds"));
        }
        if (game.isAgeOfFightersMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_AgeOfFighters_disable", "Disable Age Of Fighters"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_AgeOfFighters_enable", "Enable Age Of Fighters"));
        }
        if (game.isMercenariesForHireMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_MercenariesForHire_disable", "Disable Mercenaries For Hire"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_MercenariesForHire_enable", "Enable Mercenaries For Hire"));
        }
        if (game.isZealousOrthodoxyMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_ZealousOrthodoxy_disable", "Disable Zealous Orthodoxy"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_ZealousOrthodoxy_enable", "Enable Zealous Orthodoxy"));
        }
        if (game.isCulturalExchangeProgramMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_CulturalExchangeProgram_disable", "Disable Cultural Exchange Program"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_CulturalExchangeProgram_enable", "Enable Cultural Exchange Program"));
        }
        if (game.isRapidMobilizationMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_RapidMobilization_disable", "Disable Rapid Mobilization"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_RapidMobilization_enable", "Enable Rapid Mobilization"));
        }
        if (game.isCosmicPhenomenaeMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_Cosmic_disable", "Disable Cosmic Phenomenae"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_Cosmic_enable", "Enable Cosmic Phenomenae"));
        }
        if (game.isMonumentToTheAgesMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_Monument_disable", "Disable Monuments to the Ages"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_Monument_enable", "Enable Monuments to the Ages"));
        }
        if (game.isWeirdWormholesMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_WeirdWormholes_disable", "Disable Weird Wormholes"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_WeirdWormholes_enable", "Enable Weird Wormholes"));
        }
        if (game.isCosmicConvergenceMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_CosmicConvergence_disable", "Disable Cosmic Convergence"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_CosmicConvergence_enable", "Enable Cosmic Convergence"));
        }
        if (game.isWildWildGalaxyMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_WildGalaxy_disable", "Disable Wild, Wild Galaxy"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_WildGalaxy_enable", "Enable Wild, Wild Galaxy"));
        }
        if (game.isCallOfTheVoidMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_CallOfTheVoid_disable", "Disable Call of the Void"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_CallOfTheVoid_enable", "Enable Call of the Void"));
        }
        if (game.isConventionsOfWarAbandonedMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_Conventions_disable", "Disable Conventions of War Abandoned"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_Conventions_enable", "Enable Conventions of War Abandoned"));
        }
        if (game.isAdventOfTheWarsunMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_AdventOfTheWarsun_disable", "Disable Advent of the Warsun"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_AdventOfTheWarsun_enable", "Enable Advent of the Warsun"));
        }
        if (game.isCivilizedSocietyMode()) {
            galacticEventButtons.add(
                    Buttons.red("enableDaneMode_CivilizedSociety_disable", "Disable Civilized Society"));
        } else {
            galacticEventButtons.add(
                    Buttons.green("enableDaneMode_CivilizedSociety_enable", "Enable Civilized Society"));
        }
        if (game.isStellarAtomicsMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_StellarAtomics_disable", "Disable Stellar Atomics"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_StellarAtomics_enable", "Enable Stellar Atomics"));
        }
        if (game.isAgeOfCommerceMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_ageOfCommerce_disable", "Disable Age of Commerce"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_ageOfCommerce_enable", "Enable Age of Commerce"));
        }

        galacticEventButtons.add(Buttons.gray("deleteButtons", "Done"));

        return galacticEventButtons;
    }
}
