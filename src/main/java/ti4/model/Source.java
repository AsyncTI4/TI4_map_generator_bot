package ti4.model;

import ti4.helpers.Emojis;

public class Source {

    public enum ComponentSource {

        // official
        base, pok, codex1, codex2, codex3,

        //big homebrew
        ds, absol, franken, uncharted_space, monuments,

        // lil homebrew
        lazax, action_deck_2, action_deck_2_old, keleresplus, little_omega, project_pi, neutral, lost_star_charts_of_ixth, flagshipping, promises_promises,

        // async homebrew
        draft, admins, pbd100, pbd500, pbd1000, testsource, pbd2000, fow, dane_leaks,

        // personal projs
        somno, ignis_aurora, asteroid, cryypter, voice_of_the_council, oath_of_kings, eronous, miltymod, luminous, holytispoon, salliance, nomadfalcon, unfulvio, andcat, sigma, byz_agendas, memephilosopher,

        // catchall
        other;

        public String toString() {
            return super.toString().toLowerCase();
        }

        /**
         * Converts a string identifier to the corresponding ComponentSource enum value.
         * 
         * @param id the string identifier
         * @return the ComponentSource enum value, or null if not found
         */
        public static ComponentSource fromString(String id) {
            for (ComponentSource source : values()) {
                if (source.toString().equals(id)) {
                    return source;
                }
            }
            return null;
        }

        public boolean isOfficial() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3 -> true;
                default -> false;
            };
        }

        public boolean isPok() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3 -> true;
                default -> false;
            };
        }

        public boolean isDs() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3, ds, uncharted_space -> true;
                default -> false;
            };
        }

        public String emoji() {
            return switch (this) {
                case absol -> Emojis.Absol;
                case ds -> Emojis.DiscordantStars;
                case uncharted_space -> Emojis.UnchartedSpace;
                case eronous -> Emojis.Eronous;
                case admins -> Emojis.AdminsFaction;
                case ignis_aurora, pbd2000 -> Emojis.IgnisAurora;
                case keleresplus -> Emojis.KeleresPlus;
                case project_pi -> Emojis.ProjectPi;
                case flagshipping -> Emojis.Flagshipping;
                case promises_promises -> Emojis.PromisesPromises;
                case miltymod -> Emojis.MiltyMod;
                case lazax -> Emojis.Lazax;
                case neutral -> Emojis.Neutral;
                case salliance -> Emojis.StrategicAlliance;
                case monuments -> Emojis.Monuments;
                default -> "";
            };
        }

        public String prettyName() {
            return switch (this) {
                case base -> "Twilight Imperium 4th Edition (Base Game)";
                case pok -> "Prophecy of Kings [Expansion]";
                case codex1 -> "Codex 1 - Omega Techs";
                case codex2 -> "Codex 2 - Relics";
                case codex3 -> "Codex 3 - Naalu, Yin, Keleres";
                case ds -> "Discordant Stars [Homebrew]";
                case absol -> "Absol's Mod [Homebrew]";
                case flagshipping -> "Flagshipping";
                case promises_promises -> "Promises Promises";
                case franken -> "Franken Draft [Homebrew Game Mode]";
                case monuments -> "Monuments+";
                default -> toString();
            };
        }
    }

}
