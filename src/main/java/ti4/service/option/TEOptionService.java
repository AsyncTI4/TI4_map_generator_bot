package ti4.service.option;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.draft.TwilightsFallFrankenDraft;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftBagService;

@UtilityClass
public class TEOptionService {

    @ButtonHandler("startTFGame")
    public static void startTFGame(Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
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
        buttons.add(Buttons.gray("startDraftSystem_andcatPresetMilty", "Start Milty Draft + Later Inaugural Splice"));
        buttons.add(
                Buttons.gray("startDraftSystem_andcatPresetNucleus", "Start Nucleus Draft + Later Inaugural Splice"));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("startTFDraft")
    public static void startTFDraft(Game game, ButtonInteractionEvent event) {
        game.setupTwilightsFallMode(event);
        FrankenDraftBagService.setUpFrankenFactions(game, event, true);
        FrankenDraftBagService.clearPlayerHands(game);
        game.setBagDraft(new TwilightsFallFrankenDraft(game));
        FrankenDraftBagService.startDraft(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("chooseExp_")
    public static void chooseExp(Game game, ButtonInteractionEvent event, String buttonID) {
        String choice = buttonID.split("_")[1];
        switch (choice.toLowerCase()) {
            case "newpok" -> {
                game.removeStoredValue("useOldPok");
                game.setThundersEdge(false);
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

    @ButtonHandler("offerTEOptionButtons")
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
        // buttons = new ArrayList<>();
        // buttons.add(Buttons.green("addNewSCs", "Use New Strategy Cards"));
        // buttons.add(Buttons.red("deleteButtons", "Decline"));
        // MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);

        // msg = "Thunder's Edge contains 7 new relics. You can use them in this game by pressing the button below.";
        // buttons = new ArrayList<>();
        // buttons.add(Buttons.green("addNewRelics", "Use New Relics"));
        // buttons.add(Buttons.red("deleteButtons", "Decline"));
        // MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);
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
        // if (game.isMonumentToTheAgesMode()) {
        //     galacticEventButtons.add(Buttons.red("enableDaneMode_Monument_disable", "Disable Monuments to the
        // Ages"));
        // } else {
        //     galacticEventButtons.add(Buttons.green("enableDaneMode_Monument_enable", "Enable Monuments to the
        // Ages"));
        // }
        if (game.isWeirdWormholesMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_WeirdWormholes_disable", "Disable Weird Wormholes"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_WeirdWormholes_enable", "Enable Weird Wormholes"));
        }
        if (game.isWildWildGalaxyMode()) {
            galacticEventButtons.add(Buttons.red("enableDaneMode_WildGalaxy_disable", "Disable Wild Wild Galaxy"));
        } else {
            galacticEventButtons.add(Buttons.green("enableDaneMode_WildGalaxy_enable", "Enable Wild Wild Galaxy"));
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
