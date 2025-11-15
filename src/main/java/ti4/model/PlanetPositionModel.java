package ti4.model;

import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class PlanetPositionModel {
    public enum TagPosition {
        TopLeft,
        TopRight,
        BottomLeft,
        BottomRight
    }

    public enum PlanetPosition {
        // HS Types
        TYPE01_N,
        TYPE01_L, // 1 planet (normal/large)
        TYPE02_1,
        TYPE02_2, // 2 planets
        TYPE03_1,
        TYPE03_2,
        TYPE03_3, // 3 planets

        // Non-HS types
        TYPE04_1,
        TYPE04_L, // 1 planet, centered, (normal/large)
        TYPE05_1,
        TYPE05_2, // 2 planet, starpoint style. OR 1 planet quann-style
        TYPE06_1,
        TYPE06_2,
        TYPE06_3, // 3 planet, devil/rigel style

        // Old Types
        TYPE01,
        TYPE02,
        TYPE03,
        TYPE04,
        TYPE05,
        TYPE06,
        TYPE07,
        TYPE08,
        TYPE09,
        TYPE10,
        TYPE11,
        TYPE12,
        TYPE13,
        TYPE14,
        TYPE15,
        TYPE16,
        TYPE17;

        public float getPlanetScale() {
            return switch (this) {
                case TYPE01_L -> 1.65f;
                default -> 1.0f;
            };
        }

        public TagPosition getTagPosition() {
            return switch (this) {
                // 1 p hs
                case TYPE01_N, TYPE01_L -> TagPosition.BottomLeft;
                // 2 p hs
                case TYPE02_1, TYPE02_2 -> TagPosition.TopLeft;
                // 3 p hs
                case TYPE03_1 -> TagPosition.BottomLeft;
                case TYPE03_2 -> TagPosition.TopRight;
                case TYPE03_3 -> TagPosition.TopLeft;

                // etc
                case TYPE04_1 -> TagPosition.BottomLeft;
                case TYPE05_1 -> TagPosition.TopLeft;
                case TYPE05_2 -> TagPosition.BottomRight;
                case TYPE06_1 -> TagPosition.BottomLeft;
                case TYPE06_2 -> TagPosition.TopRight;
                case TYPE06_3 -> TagPosition.TopLeft;

                default -> TagPosition.BottomLeft;
            };
        }

        public Point getPlanetCenter() {
            return switch (this) {
                default -> new Point(172, 150); // center of tile
            };
        }

        public String getName() {
            return switch (this) {
                // Home system planet types
                case TYPE01_N -> "1-planet HS (Jord-Style)";
                case TYPE01_L -> "1-planet LARGE HS (Elysium)";
                // 2 planet home system types
                case TYPE02_1 -> "2-planet HS, planet-1 (Retillion-style)";
                case TYPE02_2 -> "2-planet HS, planet-2 (Shalloq-style)";
                // 3 planet home system types
                case TYPE03_1 -> "3-planet HS, planet-1 (Hercant-style)";
                case TYPE03_2 -> "3-planet HS, planet-2 (Arretze-style)";
                case TYPE03_3 -> "3-planet HS, planet-3 (Kamdorn-style)";

                // Non-home, standard
                case TYPE04_1 -> "1-planet, (Wellon-style)";
                case TYPE04_L -> "1-planet large, (Mecatol-style)";
                case TYPE05_1 -> "2-planet, planet-1 (Lazar-style) OR 1-planet plus wormhole (Quann-style)";
                case TYPE05_2 -> "2-planet, planet-2 (Sakulag-style)";
                case TYPE06_1 -> "3-planet, planet-1 (Rigel 3/Loki)";
                case TYPE06_2 -> "3-planet, planet-2 (Rigel 2/Abaddon)";
                case TYPE06_3 -> "3-planet, planet-3 (Rigel 1/Ashtroth)";

                case TYPE07 -> "1 planet bottom left (Groose Mihsal, Albredaan)";
                case TYPE08 -> "Empties";
                case TYPE09 -> "Mallice and Creuss";
                case TYPE10 -> "Everra and Cormund";
                case TYPE11 -> "Phaeton (S08)";
                case TYPE12 -> "Ethan (C11)";
                case TYPE13 -> "Eko (C06)";
                case TYPE14 -> "Horace (C05)";
                case TYPE15 -> "Kwon (C10)";

                case TYPE17 -> "Single planet, centered";
                default -> "unknown";

                    // Non-HS system layouts
            };
        }

        public Point getPlanetCenterPos() {
            return switch (this) {
                // 1 planet hs
                case TYPE01_N, TYPE01_L -> new Point(172, 150);
                // 2 planet hs
                case TYPE02_1 -> new Point(135, 85);
                case TYPE02_2 -> new Point(225, 205);
                // 3 planet hs
                case TYPE03_1 -> new Point(90, 140);
                case TYPE03_2 -> new Point(210, 80);
                case TYPE03_3 -> new Point(230, 220);
                default -> null;
            };
        }

        public Point getControlMarkerPos() {
            Point p = getPlanetCenterPos();
            p.translate(-37, -25);
            return p;
        }

        public Point getSpaceDockPos() {
            Point p = getPlanetCenterPos();
            Point translate = null;
            switch (getTagPosition()) {
                case TopLeft -> translate = new Point(0, 0);
                case TopRight -> translate = new Point(0, 0);
                case BottomLeft -> translate = new Point(0, 0);
                case BottomRight -> translate = new Point(0, 0);
                default -> translate = new Point(0, 0);
            }
            p.translate(Math.round(translate.x * getPlanetScale()), Math.round(translate.y * getPlanetScale()));
            return p;
        }

        private static final Point offset = new Point(12, -7);
        private static final Point allianceOffset = new Point(8, -5);

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public String getTypeString() {
            return super.toString() + " - " + getName();
        }
    }

    public Point getOffset() {
        return PlanetPosition.offset;
    }

    public Point getAllianceOffset() {
        return PlanetPosition.allianceOffset;
    }

    public PlanetPosition getTypeFromString(String type) {
        type = type.substring(0, 6);
        Map<String, PlanetPosition> allTypes = Arrays.stream(PlanetPosition.values())
                .collect(Collectors.toMap(PlanetPosition::toString, (shipPositionModel -> shipPositionModel)));
        if (allTypes.containsKey(type.toLowerCase())) return allTypes.get(type.toLowerCase());
        return null;
    }
}
