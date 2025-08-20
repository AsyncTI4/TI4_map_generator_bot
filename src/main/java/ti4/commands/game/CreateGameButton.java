package ti4.commands.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SearchGameHelper;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;

class CreateGameButton extends Subcommand {

    public CreateGameButton() {
        super(Constants.CREATE_GAME_BUTTON, "Create Game Creation Button");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Channel"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // GAME NAME
        String gameName = CreateGameService.getNextGameName();

        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = CreateGameService.getCategoryNameForGame(gameName);
        Category categoryChannel = null;
        List<Category> categories = CreateGameService.getAllAvailablePBDCategories();
        for (Category category : categories) {
            if (category.getName().toUpperCase().startsWith(categoryChannelName)) {
                categoryChannel = category;
                break;
            }
        }
        if (categoryChannel == null) categoryChannel = CreateGameService.createNewCategory(categoryChannelName);
        if (categoryChannel == null) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Could not automatically find a category that begins with **" + categoryChannelName
                            + "** - Please create this category.\n# Warning, this may mean all servers are at capacity.");
            return;
        }
        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();

        // PLAYERS
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null) members.add(member);
                else {
                    continue;
                }

                if (!member.getUser().isBot() && !CommandHelper.hasRole(event, AsyncTI4DiscordBot.developerRoles)) {
                    int ongoingAmount = SearchGameHelper.searchGames(
                            member.getUser(), event, false, false, false, true, false, true, true, true);
                    int completedAndOngoingAmount = SearchGameHelper.searchGames(
                            member.getUser(), event, false, true, false, true, false, true, true, true);
                    int completedGames = completedAndOngoingAmount - ongoingAmount;
                    if (ongoingAmount > completedGames + 2) {
                        MessageHelper.sendMessageToChannel(
                                event.getChannel(),
                                member.getUser().getAsMention()
                                        + " is at their game limit (# of ongoing games must be equal or less than # of completed games + 3) and so cannot join more games at the moment."
                                        + " Their number of ongoing games is " + ongoingAmount
                                        + " and their number of completed games is " + completedGames + ".");
                        return;
                    }
                    // Used for specific people we are limiting the amount of games of
                    // if (member.getId().equalsIgnoreCase("400038967744921612")) {
                    //     if (ongoingAmount > 6) {
                    //         MessageHelper.sendMessageToChannel(event.getChannel(), "One of the games proposed members
                    // is currently under a limit and cannot join more games at this time");
                    //         return;
                    //     }
                    // }
                }
                if (gameOwner == null) gameOwner = member;
            } else {
                break;
            }
        }

        // CHECK IF GUILD HAS ALL PLAYERS LISTED
        CreateGameService.inviteUsersToServer(guild, members, event.getMessageChannel());

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("createGameChannels", "Create Game"));
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME) == null
                ? null
                : event.getOption(Constants.GAME_FUN_NAME).getAsString();
        if (gameFunName == null) {
            // spotless:off
            // if these words are changed, please replace them in place, to avoid disrupting the generation algorithm
            // i.e. avoid deleting a word and putting a new word at the end, instead put the new word where the old word was
            List<String> words = new ArrayList<>(Arrays.asList(
                "Relativity", "Photon", "Crystalline", "Particle", "Lunar", "Ecosystem", "Hardlight", "Halogen",
                "Fluorescence", "Helium", "Tachyon", "Jetpack", "Pluto", "Interstellar", "Cryptography", "Blueprint",
                "Fission", "Disruptor", "Network", "Domino", "Doppelganger", "Freefall", "Zeta", "Hypocube",
                "Levitation", "Chemical", "Biohazard", "Frequency", "Equinox", "Extrapolate", "Nanocarbon", "Cygnus",
                "Labyrinth", "Zenith", "Acidic", "Oxygen", "Primordial", "Havoc", "Homoeostasis", "Vorpal",
                "Solstice", "Qubit", "Cephalopod", "Vertebrate", "Lattice", "Obelisk", "Yggdrasil", "Jargon",
                "Compass", "Machination", "Incorporeal", "Electron", "Maglev", "Radiant", "Cosmology", "Tensor",
                "Cryosleep", "Incandescent", "Vector", "Atomizer", "Retina", "Dragonfly", "Nanotube",  "Gloom",
                "Saturn", "Convex", "Nulldrive", "Distortion", "Equilibrium", "Abyss", "Hydra", "Friction",
                "Equatorial", "Incursion", "Solenoid", "Illusion", "Inhibitor", "Sundial", "Microchip", "Krypton",
                "Gravitational", "Entropy", "Taurus", "Hyperion", "Deuterium", "Voltage", "Viscosity", "Logarithm",
                "Centrifuge", "Mercury", "Ioniser", "Parabola", "Starlight", "Hydrocarbon", "Precursor", "Scorpius",
                "Covalent", "Paradox", "Chromosome", "Incognita", "Polarity", "Sigma", "Imprint", "Overclock",
                "Thermodynamics", "Zephyr", "Quadrant", "Cortex", "Luminance", "Irradiated", "Polymer", "Fluctuation",
                "Cryogenics", "Pegasus", "Ferrocore", "Quaternary", "Ultrasonic", "Pulsar", "Kinetic", "Chimera",
                "Turbine", "Transduction", "Isotope", "Quicksilver", "Jovian", "Lateral", "Lithium", "Neurotoxin",
                "Osmosis", "Thunderchild", "Electrical", "Ablation", "Gigawatt", "Leviathan", "Titration", "Emerald",
                "Toxicology", "Immaterial", "Disintegration", "Harmonics", "Android", "Constellation", "Parallax", "Cyborg",
                "Tesseract", "Jupiter", "Volatile", "Moebius", "Antimatter", "Phoenix", "Hardwired", "Uninhabitable",
                "Phosphorus", "Horizon", "Oscillation", "Waveform", "Banshee", "Dissonance", "Omicron", "Terraform",
                "Conduit", "Spacetime", "Eclipse", "Ultimatum", "Junkyard", "Inertia", "Hovercraft", "Symbiotic",
                "Cellular", "Celestial", "Instability", "Decontamination", "Valence", "Diffusion", "Fractal", "Radioactive",
                "Caduceus", "Quotient", "Atmosphere", "Apparatus", "Infosphere", "Juggernaut", "Pendulum", "Spectral",
                "Harbinger", "Venus", "Lambda", "Alkaline", "Voyage", "Ozone", "Iota", "Atomic",
                "Galactic", "Redshift", "Cerebral", "Fungi", "Cronus", "Dendrite", "Ziggurat", "Vermilion",
                "Neptune", "Pathology", "Orthogonal", "Yesteryear", "Dinosaur", "Andromeda", "Catalyst", "Fabricator",
                "Portal", "Molecular", "Encryption", "Hydrogen", "Theta", "Angstrom", "Epoch", "Digital",
                "Parasite", "Synchronisation", "Singularity", "Comet", "Resonance", "Topography", "Gargoyle", "Forcefield",
                "Citadel", "Hologram", "Circuitry", "Gemini", "Cyberspace", "Graphite", "Synthetic", "Trajectory",
                "Nitrogen", "Odyssey", "Wavelength", "Orbital", "Lightspeed", "Helix", "Photosynthesis", "Interface",
                "Nanite", "Glacier", "Astrolabe", "Ultraviolet", "Enthalpy", "Observatory", "Solar", "Vacuum",
                "Infrared", "Kaleidoscope", "Magnetosphere", "Gyroscope", "Diamond", "Optic", "Enzyme", "Energy"));
            // extra words: "Bioluminescence", "Uranium", "Wetware", "Moonstone"
            // spotless:on
            int gameNumber = CreateGameService.getNextGameNumber();
            int first = gameNumber & 0xFF;
            int second = (gameNumber >> 8) & 0xFF;
            int third = (gameNumber >> 16) & 0xFF;
            second ^= first;
            third ^= second;
            gameFunName = words.get(3 * first & 0xFF) + "-" + words.get(5 * second & 0xFF) + "-"
                    + words.get(7 * third & 0xFF);
        }
        if (!members.isEmpty()) {
            StringBuilder buttonMsg =
                    new StringBuilder("Game Fun Name: " + gameFunName.replace(":", "") + "\nPlayers:\n");
            int counter = 1;
            for (Member member : members) {
                buttonMsg
                        .append(counter)
                        .append(":")
                        .append(member.getId())
                        .append(".(")
                        .append(member.getEffectiveName().replace(":", ""))
                        .append(")\n");
                counter++;
            }
            buttonMsg
                    .append("\n\n")
                    .append(" Please hit this button after confirming that the members are the correct ones.");
            MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(buttonMsg.toString());
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), buttonMsg.toString(), buttons);
            ActionRow actionRow = ActionRow.of(buttons);
            baseMessageObject.addComponents(actionRow);
        }
    }
}
