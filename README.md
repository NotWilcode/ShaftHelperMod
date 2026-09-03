# Shaft Helper

Shaft Helper is a client-side Fabric mod for Hypixel SkyBlock gemstone mineshafts. It combines
shaft profitability math, live mining information, session tracking, and waypoint tools in one
in-game interface.

## Features

- Reads Mining Speed, Mining Fortune, Gemstone Fortune, Pristine, and corpse counts from the tab list.
- Compares gemstone shafts against a Jasper benchmark and calculates the lapis corpses needed to
  make each shaft worthwhile.
- Estimates ticks per block, ping-adjusted timing, mining speed, TPS, and network ping while looking
  at a tracked block.
- Tracks actual Pristine drops and displays estimated profit per hour.
- Tracks shafts visited during the current server session, including lapis, umber, and tungsten corpses.
- Provides HUD elements for the shaft calculator, profit, session log, network stats, efficiency,
  mining timers, and pickaxe ability alerts.
- Includes editable waypoints with island and shaft grouping, ordered waypoint navigation, themed
  Current/Next/Passed colors, and distance-based labels.
- Includes bundled corpse-location waypoints. They are loaded from the mod and can be enabled or
  disabled in the Waypoints settings; they are not added to the user-editable waypoint list.
- Offers configurable HUD positions, scales, colors, themes, price source, price data, and mining
  assumptions.

## Requirements

- Minecraft 26.1.x
- Fabric Loader 0.19.3 or newer
- Fabric API
- Iris 1.11.3 or newer
- Java 25 or newer

The mod is client-side and does not require installation on the server.

## Installation

1. Install Fabric Loader for Minecraft 26.1.x.
2. Put Fabric API, Iris, and the Shaft Helper jar in your `mods` folder.
3. Join Hypixel and run `/shaft`.

## In-game controls

Run `/shaft` to open the main menu. The menu provides:

- **Config**: stats, HUDs, themes, prices, mining settings, and waypoint settings.
- **Help**: the calculation guide and command examples.
- **Info**: a quick overview of the mod's tools and data sources.
- **Waypoints**: create, edit, import, export, and group personal waypoints.

Useful commands include:

```text
/shaft
/shaft config
/shaft waypoints
/shaft ping 50
/shaft miningspeed 1500
/shaft mining_speed:1500 mining_fortune:400 pristine:3
```

## Waypoints

Personal waypoints are saved in `config/shafthelper.json`. Preset waypoint files are stored in
`config/PresavedWaypoints` and can be imported from the Waypoints screen.

Bundled corpse waypoints live in the mod resources at
`assets/shafthelper/corpse_waypoints/`. The resource `index.json` lists the files loaded at startup.
They are intentionally separate from personal waypoints, so importing or exporting personal data
cannot modify the built-in corpse locations.

## HUD and themes

Open **Config > HUD** to enable elements and position or scale them. Open **Config > Options** to
choose a visual theme and configure network, timer, particle, and debug behavior. The debug block
panel shows the tracked block name, break timing, ping offset, TPS, and mining speed.

## Price and profit model

Prices are read from the Hypixel Bazaar. The default uses three-day historical data where available,
with live Hypixel data as a fallback. You can choose sell offers or instant sells, and value drops
from the Flawless tier or their listed item prices.

The calculator models 3-5 drops per block, mining fortune, Pristine, corpse bonuses, block strength,
Cold Resistance, and configurable mining efficiency. Throughput is an estimate assuming continuous
mining; actual profit is tracked separately from Pristine chat messages.

## Building

```powershell
.\gradlew.bat build
```

The project uses Fabric Loom and Java 25.

## Links

- [Source and releases](https://github.com/NotWilcode/ShaftHelperMod)
- [Fabric](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
