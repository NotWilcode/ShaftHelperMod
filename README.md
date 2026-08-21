# Shaft Helper (Fabric mod)

Hypixel Skyblock gemstone mining math, in-game. This is the Fabric mod port of
[ShaftHelperDCBot](https://github.com/williamnoblejones-prog/ShaftHelperDCBot): the same `/shaft`
command, run from Minecraft chat instead of Discord.

- **Minecraft 26.1.x** (The mod is built against `26.1.2` and runs on any `26.1` patch)
- **Fabric Loader** ≥ 0.19.3 with **Fabric API**
- Client-side only — works on any server, since it only reads your chat command and talks to the
  Bazaar APIs.

Run `/shaft config` in chat to open the mod settings and get started.
Also check out: `/shaft waypoints`, `/shaft ping`, `/shaft options`, or `/shaft`

## Auto-read stats, HUD and config

On Hypixel the mod reads **Mining Speed, Mining Fortune and Pristine straight from the tab list**
(the Stats widget), so `/shaft` works with no arguments — anything you type pulls from a pre-saved config.
The **HUD overlay** shows what `/shaft` answers, always on screen: each gemstone (in its chat
color) ranked by profit with the lapis corpses it needs to out-earn the benchmark (Jasper by
default) or "skip", using Bazaar prices refreshed every 10 minutes. It also spots the mineshaft
you are in from the tab list shaft code (the first four letters of the gemstone plus a variant,
e.g. `JASP_C`, `PERI_1`, `AMBE_2`) and lists the shafts visited this session below the ranking:

```
Shaft Helper (vs Jasper)
Peridot: 2 lapis, 1.4m/hr
Jasper: benchmark, 1.2m/hr
Jade: skip
...
Tracker: 12 procs in 8m — ~1.3m/hr
This session:
Shaft 1 JASP_C: jasper benchmark
Shaft 2 PERI_1: 2 lapis
```

The **profit tracker** counts the "PRISTINE!" proc messages in chat and works out the coins/hr you
are actually earning: the flawed gems from the procs at Bazaar prices, plus the rough gems mined
alongside them extrapolated from your Pristine chance, over the time since the first proc. Compare
it to the theoretical /hr in the ranking above. It resets when you leave the server.

`/shaft config` opens the **settings screen**: the auto-read stats (tweak them or turn auto-read
off), Cold Resistance, efficiency, the benchmark gemstone, the HUD toggle and its X/Y position
sliders (percent across the screen, 0% = top-left, 100% = bottom-right). Everything saves to
`config/shafthelper.json`, so it survives leaving and rejoining Minecraft.

## Math

```
ticks       = floor(block strength × 30 ÷ mining speed)   (minimum 1 tick)
blocks/hour = 72000 ÷ ticks                                (20 ticks per second)
gems/block  = 4 (avg of 3-5) × (1 + mining fortune ÷ 100)
gems/hour   = blocks/hour × gems/block
coins/hr(P) = gems/hour × [ (1 − P/100)·rough price + (P/100)·flawed price ]
```

`P` is Pristine: each point is a 1% chance for a block's whole 3-5 drop to come out flawed instead
of rough (80 rough = 1 flawed). Drop prices come from the Flawless tier, see below. `coins/hr(P)` is
a straight line in `P`, which is what makes the corpse math below exact rather than a search.

## Lapis corpses

Each lapis corpse inside a shaft grants +1 Pristine **for that shaft only**, and a shaft holds at
most 4. A Jasper shaft is always worth mining, so it is the yardstick: it runs at your base Pristine
while the challenger runs at `base + bonus`. Solving for the bonus:

```
bonus = (jasper coins/hr − gem coins/hr) ÷ (gem coins/hr gained per Pristine)
corpses = ceil(bonus)
```

Each gemstone row shows the ticks per block, the coins/hr of that shaft with the corpses it needs
already in it (capped at 4), the exact bonus Pristine needed to pass the benchmark (`none` = already
better, `never` = its flawed price is no better than its rough price), and that bonus rounded up to
whole corpses; `13 (>4)` means more than a shaft can ever hold, so skip it.

Mining Fortune multiplies every gemstone equally, so it never changes the corpse counts; Mining
Speed only shifts them slightly through tick rounding.

Block strengths: Ruby 2300 · Jade/Amber/Amethyst/Sapphire/Opal 3000 · Topaz 3800 · Jasper 4800 ·
Onyx/Aquamarine/Citrine/Peridot 5200.

Throughput is a theoretical ceiling: it assumes you never stop mining and never miss a block.

## Cold

```
seconds per Cold = 5 × (1 + cold resistance ÷ 100)
minutes in shaft = seconds per Cold × 100 ÷ 60      (100 Cold kicks you out)
shaft profit     = coins/hr × hours in shaft × efficiency
```

Cold Resistance caps at 138.5, which is 11.9s per Cold and 19.875 minutes in a shaft. `efficiency`
(default 70%) is the share of that time actually spent breaking blocks rather than walking, looting
corpses or opening the next room.

## Prices

`prices:sell_offer` (the default) is `quick_status.buyPrice`, what a filled sell offer pays;
`prices:insta_sell` is `quick_status.sellPrice`, what selling to the Bazaar pays right now. The two
modes can rank gemstones very differently, so check both.

Drops are valued from the **Flawless** gem they craft into, because that is the tier players
actually craft up to and buy, and it takes half a million rough gems to move one:

```
80 rough = 1 flawed · 80 flawed = 1 fine · 80 fine = 1 flawless
rough price  = flawless price ÷ 80³   (512,000)
flawed price = flawless price ÷ 80²   (6,400)
```

`price_basis:listed` values the drops at their own Bazaar price instead. Any listing trading at more
than double its Flawless value is named under "Listings ignored as inflated".

Two further defences against Bazaar manipulation:

- `price_data` — **3-day average** by default, from `https://sky.coflnet.com/api/bazaar/<id>/history`
  (cached 30 minutes, live Hypixel prices fill in anything it cannot serve). A pump that lasts a day
  moves a 3-day mean by a third of what it does to the live price. `price_data:live` reads the
  current snapshot from `https://api.hypixel.net/v2/skyblock/bazaar` instead (no API key, cached
  60 seconds).
- `prices` — which side of the Flawless order book to read, sell offer or instant sell.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.1.x.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) and the [Shaft Helper](https://github.com/NotWilcode/ShaftHelperMod/releases/) jar into your
   `mods/` folder.
3. Join Hypixel and run `/shaft` in chat.