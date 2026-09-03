# Bundled corpse waypoints

Place corpse waypoint JSON files in this folder and list each filename in `index.json`.
The client loads them automatically at startup. They are kept separate from the user's editable waypoints.

Each file can contain a waypoint array or one waypoint object. Plain waypoint format is:

```json
[
  {
    "name": "Lapis Corpse",
    "x": 0,
    "y": 0,
    "z": 0,
    "island": "Mineshafts",
    "enabled": true,
    "color": 16777215
  }
]
```

A first object containing `group` or `area` without coordinates is treated as a file header.
SkyBlock waypoint objects using `options.name`, `r`, `g`, and `b` are also supported.
