package ti4.service.option;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.game.StartPhaseService;

@UtilityClass
public class GameOptionService {

    public static void offerGameOptionButtons(Game game, MessageChannel channel) {
        List<Button> factionReactButtons = new ArrayList<>();
        factionReactButtons.add(Buttons.green("enableAidReacts", "Enable Faction Reactions"));
        factionReactButtons.add(Buttons.red("disableAidReacts", "No Faction Reactions"));
        factionReactButtons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel,
                "Enable to have the bot react to player messages with their faction emoji.",
                factionReactButtons);

        List<Button> hexBorderButtons = new ArrayList<>();
        hexBorderButtons.add(Buttons.green("showHexBorders_dash", "Dashed line"));
        hexBorderButtons.add(Buttons.blue("showHexBorders_solid", "Solid line"));
        hexBorderButtons.add(Buttons.red("showHexBorders_off", "Off (default)"));
        hexBorderButtons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel, "Show borders around systems with player's ships.", hexBorderButtons);

        // sendShowOwnedPNsInPlayerAreaButton(game, channel);
        List<Button> daneLinkButtons = getDaneLeakModeButtons(game);
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel, "Enable or Disable Galactic Events.", daneLinkButtons);
        StartPhaseService.postSurveyResults(game);

        String msg =
                "Thunder's Edge contains a new version of mecatol rex, which is legendary (it's ability allows you to discard and then draw a secret objective). If you want to play with this new version of mecatol rex in your game, press this button and it will be added to the map when secrets are dealt.";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("addLegendaryMecatol", "Use Legendary Mecatol Rex"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);

        msg =
                "Thunder's Edge contains a new anomaly, called an entropic scar, which gives faction tech in status phase at the cost of a strategy command token. If you want the bot's milty to potentially include this scar (and other TE tiles), press this button.";
        buttons = new ArrayList<>();
        buttons.add(Buttons.green("addEntropicScar", "Use Entropic Scar & Other Tiles"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);

        msg =
                "Thunder's Edge contains two new strategy cards, Construction and Warfare. You can use them in this game by pressing the button below.";
        buttons = new ArrayList<>();
        buttons.add(Buttons.green("addNewSCs", "Use New Strategy Cards"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);

        msg = "Thunder's Edge contains 7 new relics. You can use them in this game by pressing the button below.";
        buttons = new ArrayList<>();
        buttons.add(Buttons.green("addNewRelics", "Use New Relics"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, msg, buttons);
    }

    public static final Button showOwnedPNs_ON = Buttons.green("showOwnedPNsInPlayerArea_turnOFF", "ON");
    public static final Button showOwnedPNs_OFF = Buttons.red("showOwnedPNsInPlayerArea_turnON", "OFF");

    public static List<Button> getDaneLeakModeButtons(Game game) {
        List<Button> daneLinkButtons = new ArrayList<>();

        if (game.isMinorFactionsMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_minorFactions_disable", "Disable Minor Factions"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_minorFactions_enable", "Enable Minor Factions"));
        }
        if (game.isHiddenAgendaMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_hiddenAgenda_disable", "Disable Hidden Agenda"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_hiddenAgenda_enable", "Enable Hidden Agenda"));
        }
        if (game.isAgeOfExplorationMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_ageOfExploration_disable", "Disable Age of Exploration"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_ageOfExploration_enable", "Enable Age of Exploration"));
        }
        if (game.isTotalWarMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_totalWar_disable", "Disable Total War"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_totalWar_enable", "Enable Total War"));
        }
        if (game.isDangerousWildsMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_DangerousWilds_disable", "Disable Dangerous Wilds"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_DangerousWilds_enable", "Enable Dangerous Wilds"));
        }
        if (game.isAgeOfFightersMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_AgeOfFighters_disable", "Disable Age Of Fighters"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_AgeOfFighters_enable", "Enable Age Of Fighters"));
        }
        if (game.isMercenariesForHireMode()) {
            daneLinkButtons.add(
                    Buttons.red("enableDaneMode_MercenariesForHire_disable", "Disable Mercenaries For Hire"));
        } else {
            daneLinkButtons.add(
                    Buttons.green("enableDaneMode_MercenariesForHire_enable", "Enable Mercenaries For Hire"));
        }
        if (game.isZealousOrthodoxyMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_ZealousOrthodoxy_disable", "Disable Zealous Orthodoxy"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_ZealousOrthodoxy_enable", "Enable Zealous Orthodoxy"));
        }
        if (game.isCulturalExchangeProgramMode()) {
            daneLinkButtons.add(
                    Buttons.red("enableDaneMode_CulturalExchangeProgram_disable", "Disable Cultural Exchange Program"));
        } else {
            daneLinkButtons.add(
                    Buttons.green("enableDaneMode_CulturalExchangeProgram_enable", "Enable Cultural Exchange Program"));
        }
        if (game.isRapidMobilizationMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_RapidMobilization_disable", "Disable Rapid Mobilization"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_RapidMobilization_enable", "Enable Rapid Mobilization"));
        }
        // if (game.isCosmicPhenomenaeMode()) {
        //     daneLinkButtons.add(Buttons.red("enableDaneMode_Cosmic_disable", "Disable Cosmic Phenomenae"));
        // } else {
        //     daneLinkButtons.add(Buttons.green("enableDaneMode_Cosmic_enable", "Enable Cosmic Phenomenae"));
        // }
        // if (game.isMonumentToTheAgesMode()) {
        //     daneLinkButtons.add(Buttons.red("enableDaneMode_Monument_disable", "Disable Monuments to the Ages"));
        // } else {
        //     daneLinkButtons.add(Buttons.green("enableDaneMode_Monument_enable", "Enable Monuments to the Ages"));
        // }
        if (game.isWeirdWormholesMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_WeirdWormholes_disable", "Disable Weird Wormholes"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_WeirdWormholes_enable", "Enable Weird Wormholes"));
        }
        // if (game.isWildWildGalaxyMode()) {
        //     daneLinkButtons.add(Buttons.red("enableDaneMode_WildGalaxy_disable", "Disable Wild Wild Galaxy"));
        // } else {
        //     daneLinkButtons.add(Buttons.green("enableDaneMode_WildGalaxy_enable", "Enable Wild Wild Galaxy"));
        // }
        if (game.isConventionsOfWarAbandonedMode()) {
            daneLinkButtons.add(
                    Buttons.red("enableDaneMode_Conventions_disable", "Disable Conventions of War Abandoned"));
        } else {
            daneLinkButtons.add(
                    Buttons.green("enableDaneMode_Conventions_enable", "Enable Conventions of War Abandoned"));
        }
        if (game.isAdventOfTheWarsunMode()) {
            daneLinkButtons.add(
                    Buttons.red("enableDaneMode_AdventOfTheWarsun_disable", "Disable Advent of the Warsun"));
        } else {
            daneLinkButtons.add(
                    Buttons.green("enableDaneMode_AdventOfTheWarsun_enable", "Enable Advent of the Warsun"));
        }
        if (game.isCivilizedSocietyMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_CivilizedSociety_disable", "Disable Civilized Society"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_CivilizedSociety_enable", "Enable Civilized Society"));
        }
        if (game.isStellarAtomicsMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_StellarAtomics_disable", "Disable Stellar Atomics"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_StellarAtomics_enable", "Enable Stellar Atomics"));
        }
        if (game.isAgeOfCommerceMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_ageOfCommerce_disable", "Disable Age of Commerce"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_ageOfCommerce_enable", "Enable Age of Commerce"));
        }

        daneLinkButtons.add(Buttons.gray("deleteButtons", "Done"));

        return daneLinkButtons;
    }

    public static void sendShowOwnedPNsInPlayerAreaButton(Game game, MessageChannel channel) {
        List<Button> buttons = new ArrayList<>();
        if (game.isShowOwnedPNsInPlayerArea()) { // button shows current status
            buttons.add(showOwnedPNs_ON);
        } else {
            buttons.add(showOwnedPNs_OFF);
        }
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel, "Show Owned Promissory Notes in Player Area?", buttons);
    }
}
