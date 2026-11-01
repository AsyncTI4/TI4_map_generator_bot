# `ti4.image`

Everything the bot draws: the game map, player areas, single tiles, draft boards,
banners and transaction cards. All drawing is plain Java2D (`BufferedImage` +
`Graphics2D`). The output is encoded and handed to Discord as a `FileUpload`, and
the full map is also pushed to the asyncti4 website.

## Package layout

| Package | What lives there |
| --- | --- |
| `map` | The full map render: pipeline, request/context, canvas layout, and the sections (board, header, score track, objectives, laws, side stats, player areas). Also the compact/overview generators and map segments (sectors). |
| `playerarea` | One player's area under the map: identity block, supply, card strip, planets, techs, leaders, secrets, homebrew token supplies. |
| `tile` | One hex: tile artwork and the `TileStep` painters (units, tokens, command tokens, fog, display-type highlights). Also `SystemContextImage` and hyperlane tiles. |
| `draft` | Draft images: slice sheets, Mantis and Nucleus boards, map template previews. |
| `card` | Banners (faction, agenda, phase) and the transaction/trade-offer images. |
| `draw` | Reusable drawing helpers: text wrapping and outlines, strokes, colours, faction icons, image grids, website overlays. |
| `io` | Reading, caching, encoding and uploading images: `ImageHelper`, `ImageCache`, `RemoteImages`, `ImageResources` (resource paths), `Fonts`, `ImageEncoder`, `ImageUploads`. |

### Layering

The packages are layered, and
[`ImagePackageLayeringTest`](../../../../test/java/ti4/image/ImagePackageLayeringTest.java)
fails the build when an import breaks the layering. `playerarea`, `tile` and `card`
never import each other, and nothing below `map` imports `map`. `ti4.game` and
`ti4.model` never import `ti4.image`.

```mermaid
flowchart TD
    map --> playerarea
    map --> draft
    map --> tile
    map --> card
    draft --> tile
    playerarea --> draw
    tile --> draw
    card --> draw
    draft --> draw
    draw --> io
    map -.-> draw
    map -.-> io
    playerarea -.-> io
    tile -.-> io
    card -.-> io
    draft -.-> io
```

Dotted edges are allowed direct dependencies that skip a layer.

## Full map render

Callers go through `MapRenderPipeline.queue(...)` (or `queueImage(...)` for
one-off images that should share the render threads). The pipeline:

1. Returns at once if the `CircuitBreaker` is open.
2. Copies the game with `GameSnapshots.copy`, so the render does not read the
   live `Game` while commands keep changing it. If the copy fails it logs and
   renders the live game.
3. Runs the render on a fixed pool of **2** `ti4-map-renderer-*` threads, wrapped
   in a `TimedRunnable` that warns after 10 seconds.

```mermaid
sequenceDiagram
    autonumber
    participant Caller as Command / button handler
    participant Pipeline as MapRenderPipeline
    participant Pool as Render thread pool (2)
    participant Renderer as MapRenderer
    participant Uploads as MapUploads
    participant Discord
    participant Website as asyncti4 website

    Caller->>Pipeline: queue(game, event, displayType, segment, callback)
    Pipeline->>Pipeline: CircuitBreaker open? stop
    Pipeline->>Pipeline: GameSnapshots.copy(game)
    Pipeline->>Pool: submit TimedRunnable
    Pool->>Renderer: render(MapRenderRequest, RenderTimings)
    Renderer-->>Pool: MapRenderResult(canvas, overlays, eligibleForWebsite)
    Pool->>Uploads: toDiscord(snapshot, result)
    Uploads->>Uploads: ImageEncoder.forDiscord (WebP, or JPG above 16383 px)
    Uploads-->>Caller: callback(FileUpload)
    Caller->>Discord: send message with image
    opt uploadToWebsite and eligibleForWebsite
        Pool->>Uploads: toWebsite(snapshot, result)
        Uploads->>Website: MapOverlayService.saveOverlays + putPlayerData
    end
    Pool->>Pool: timings.log
```

A result is eligible for the website only for the `all` display type when the
viewer is not a private Fog of War viewer.

### Resolving the render context

`MapRenderer.resolve` turns a request into a `RenderContext` before any pixel is
drawn.

```mermaid
flowchart LR
    Req[MapRenderRequest<br/>game, displayType, event, segment]
    Req --> Viewer[FogViewer.resolve<br/>who is looking]
    Viewer --> Prep[MapRenderPreparationService.prepare]
    Prep --> Build[RenderContext.build]
    Build --> Ctx

    subgraph Ctx[RenderContext]
        DT[displayType + basicType<br/>forced type wins]
        Fog[FogView<br/>visible tiles]
        Nb[NeighbourMap]
        Stat[MapSideStatLayout]
        Board[BoardLayout<br/>shown region, frame, segment]
        Canvas[MapCanvasLayout<br/>width, height, landscape]
        Ov[WebsiteOverlays]
    end

    Ctx --> Draws{drawsBoard?}
    Draws -- yes --> PrepBoard[MapRenderPreparationService.prepareBoard<br/>shown + connected tiles]
    Draws -- no --> Done[ready to paint]
    PrepBoard --> Done
```

Every `DisplayType` collapses to a **basic type**:

| Basic type | Display types | Draws |
| --- | --- | --- |
| `all` | `all` and anything not listed below | board, info area, player areas |
| `map` | `map`, `wormholes`, `anomalies`, `legendaries`, `empties`, `aetherstream`, `spacecannon`, `traits`, `techskips`, `attachments`, `shipless`, `unlocked` | board and info area |
| `stats` | `stats` | info area and player areas |

### Painting the canvas

`MapRenderer.paint` draws top to bottom onto one `TYPE_INT_RGB` canvas.

```mermaid
flowchart TD
    Start([paint]) --> Alloc[Allocate canvas<br/>layout width x allocated height]
    Alloc --> Board[BoardSection.draw<br/>hex tiles, clipped to map frame]
    Board --> Strip[ConnectionStripSection.draw]
    Strip --> Header[HeaderSection]
    Header --> Score[ScoreTrackSection]
    Score --> Prio{PriorityTrackSection<br/>shown?}
    Prio -- yes --> PrioDraw[PriorityTrackSection.draw]
    Prio -- no --> Obj
    PrioDraw --> Obj[ObjectivesSection]
    Obj --> Laws[LawsAndEventsSection<br/>laws, then events]
    Laws --> Side{MapSideStatsSection<br/>drawn?}
    Side -- yes --> SideDraw[MapSideStatsSection.draw]
    Side -- no --> PA
    SideDraw --> PA[PlayerAreasSection.draw]
    PA --> Follows{height follows<br/>content?}
    Follows -- no --> Out([canvas])
    Follows -- yes --> Fit{content taller than<br/>allocated height?}
    Fit -- no --> Crop[Crop to drawn bottom] --> Out
    Fit -- "yes, landscape or<br/>info area overflowed" --> Again[Repaint everything once<br/>at the measured height] --> Out
    Fit -- "yes, portrait" --> Reuse[Copy info area rows,<br/>redraw only player areas<br/>on a taller canvas] --> Out
```

The canvas height is an estimate (`PlayerAreasHeightEstimate`). When the player
areas turn out taller, the renderer keeps the already drawn top of the image and
redraws only the player areas, restoring the `Graphics2D` state and truncating the
website overlays recorded after that point. It repaints at most once.

## Board and tile layers

`BoardSection` paints every visible tile once per `TileStep`, in passes, so that
for example units of one system are never covered by the artwork of a neighbour.

```mermaid
flowchart TD
    Tiles[Tiles in FogView<br/>filtered to shown region] --> Setup{map setup shown<br/>or no tiles?}
    Setup -- yes --> Gray[Setup hexes: 0gray / 0border<br/>Tile + TileNumber]
    Setup -- no --> P1
    Gray --> P1
    P1[Pass 1: TileStep.Tile<br/>every tile, sorted by position] --> Ghost[GhostHexes]
    Ghost --> P2[Pass 2: TileStep.Extras<br/>tiles with border anomalies<br/>or adjacency overrides]
    P2 --> P3[Pass 3: TileStep.Units]
    P3 --> D{tile distances set?}
    D -- yes --> P4[Pass 4: TileStep.Distance]
    D -- no --> H
    P4 --> H{display type has<br/>a highlight step?}
    H -- yes --> P5[Pass 5: Wormholes / Anomalies / Traits / ...]
    H -- no --> P6
    P5 --> P6[Pass 6: TileStep.TileNumber]
```

Each pass calls `TileLayers.paint(tile, step, g2, x, y)`:

```mermaid
flowchart LR
    TL[TileLayers<br/>one per render, holds TileScene] --> Scratch[Reuse scratch layer image<br/>clear only last dirty rect]
    Scratch --> Painter[PAINTERS.get step<br/>TileStepPainter.paint scene, layer]
    Painter --> Bounds[PaintedBounds.of layer]
    Bounds --> Blit[drawImage only the painted part<br/>onto the map canvas]
```

`TileScene` carries the game, display type, `FogViewer` and `RenderWarnings`
shared by all painters. The painters are registered in `TileLayers.painters()`:

| `TileStep` | Painter |
| --- | --- |
| `Tile` / `TileUnannotated` | `TileFaceStep` (artwork, fog overlay, with or without annotations) |
| `Extras` | `BorderDecorationsStep` |
| `Units` | `UnitsStep` |
| `Distance` | `DistanceStep` |
| `Wormholes`, `Anomalies`, `Aetherstream`, `Exile`, `Legendaries`, `Empties`, `SpaceCannon`, `Traits`, `TechSkips`, `Attachments` | the matching `*Step` highlight |
| `TileNumber` | `TileNumberStep` |
| `Nucleus` | `NucleusStep` |

### Units step

```mermaid
flowchart TD
    U([UnitsStep.paint]) --> Hidden{tile fogged<br/>for viewer?}
    Hidden -- yes --> Stop([nothing])
    Hidden -- no --> SpaceTok[Space holder:<br/>custodians token, SpaceTokens]
    SpaceTok --> Ground[Each holder:<br/>ground tokens, ControlMarkers]
    Ground --> Gears{show gears<br/>and not fog?}
    Gears -- yes --> Badge[ProductionBadge]
    Gears -- no --> CC
    Badge --> CC[CommandTokens on space]
    CC --> Each[Each holder, space last:<br/>PlanetTokens, then UnitHolderRenderer]
    Each --> UHR[UnitHolderRenderer.render<br/>UnitOrder.sorted, UnitPlacement spiral,<br/>damage / galvanize badges, decals,<br/>jail and fog hiding]
```

Placement shares an `occupied` rectangle list across the tile so tokens and units
of different holders do not overlap.

## Player areas

`PlayerAreasSection` builds a `PlayerAreaContext` and calls
`PlayerAreas.drawAll`, which lays players out in columns and rows.

```mermaid
flowchart TD
    All([PlayerAreas.drawAll]) --> Filter[Real + eliminated players<br/>minus PlayerArea.isSkipped]
    Filter --> Cols[PlayerAreaColumns.split<br/>column count and width]
    Cols --> Row[For each row, for each column]
    Row --> One[PlayerArea.draw<br/>returns a PlayerAreaFrame]
    One --> Height[Row height = tallest frame]
    Height --> Frames[Draw every frame at row height]
    Frames --> Row
    Frames --> Spec[Spectators line]

    One --> Hdr[PlayerAreaHeader]
    Hdr --> Id[IdentityBlock<br/>faction icon, strategy cards, status]
    Id --> Elim{eliminated?}
    Elim -- yes --> Short[short frame]
    Elim -- no --> Rest[FollowAndNeighboursLine<br/>SupplyBlock<br/>ScoredSecrets<br/>PlanetRow<br/>RightBlocks<br/>CardStrip]
```

Players whose stats the viewer may not see in Fog of War are skipped
(`PlayerAreaContext.hidesStatsOf`), so other players' hidden state never reaches
the image.

## Other entry points

These render outside the full map pipeline but reuse the same lower layers.

```mermaid
flowchart LR
    subgraph map
        MOG[MapOverviewGenerator]
        COG[CompactOverviewGenerator<br/>GM and player overviews]
    end
    subgraph tile
        SCI[SystemContextImage<br/>one system and its neighbours]
        HTG[HyperlaneTileGenerator]
    end
    subgraph draft
        SS[SliceSheet]
        MI[MantisImage]
        NI[NucleusImage]
        TP[TemplatePreview]
    end
    subgraph card
        BG[BannerGenerator]
        TG[TransactionGenerator]
    end
    MOG --> TL[TileLayers]
    COG --> TL
    SCI --> TL
    MI --> TL
    NI --> TL
    TP --> TL
    TL --> IO[io: ImageHelper, ImageCache,<br/>ImageEncoder, ImageUploads]
    SS --> IO
    BG --> IO
    TG --> IO
```

Use `MapRenderPipeline.queueImage(game, label, image, callback)` when one of these
should render off the interaction thread on a snapshot of the game.

## Loading images

```mermaid
flowchart LR
    Path[ImageResources<br/>tile / unit / token path] --> Read[ImageHelper.read / readScaled]
    Read --> Cache{ImageCache<br/>file cache hit?}
    Cache -- yes --> Img[BufferedImage]
    Cache -- no --> Disk[ImageIO from resources] --> Store[store in cache] --> Img
    Url[Discord avatar / emoji URL] --> Remote[RemoteImages<br/>2 s timeout, one retry]
    Remote --> UrlCache[ImageCache url cache] --> Img
```

Both caches are Caffeine caches (default 2000 entries, 8 hours), registered with
`CacheManager`. Scaled reads are cached under their scale, so call
`readScaled` with a constant scale rather than scaling the result yourself.

## Testing

Tests live under [`src/test/java/ti4/image`](../../../../test/java/ti4/image):

- `ImagePackageLayeringTest`: the package layering above.
- `map/MapPaintPurityTest`: painting leaves the game unchanged (fog flags, tile
  distances) and sends nothing.
- `map/RenderThreadSafetyTest`: snapshots, two games rendering at once, and
  `Mapper` reloads during a render.
- `RenderStaticStateTest`: every static field the render path reads must be safe
  to share between the two render threads.
- `map/FogViewerRenderTest`, `playerarea/FogSafePlayerAreaTest`: Fog of War does
  not leak.
- `map/MapRenderGoldenHarness`, `ImageGeneratorsGoldenHarness`,
  `ScatteredImagesGoldenHarness`: golden-image comparisons, opt-in through the
  `golden.saves` system property.
- `*Bench` classes: timing benchmarks, opt-in through system properties such as
  `golden.timing.saves`.
