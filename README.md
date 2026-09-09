# standhitter

Client-side Fabric mod. Automatically simulates a left click on a timer while you look at an armor stand wearing armor (or an interaction entity).

## Requirements

- Minecraft 1.21.11
- Fabric Loader
- Fabric API
- Cloth Config (Fabric)
- Mod Menu (optional, but easy to open settings)

## Usage

1. Install all mods in `mods/`.
2. In game, open Mods > standhitter > config.
3. Turn **Enabled** on (or press `keybind` to toggle).
4. Look at an armor stand wearing armor. It gets hit automatically.

While on, a small green indicator shows bottom-left. If it switches off automatically from low hunger, it shows an orange notice instead.

## Settings

- **Enabled**: master switch.
- **Hit armor stands**: hit armor stands that wear armor.
- **Hit interaction entities**: also hit invisible interaction entities you aim at.
- **Click regardless of target**: click on the interval no matter what you look at, even air. Overrides the entity options above.
- **Disable at low hunger**: auto-off at 3 shanks of hunger (orange HUD notice).
- **Keep active while tabbed out**: keeps clicking when the window is unfocused.
- **Scale intervals with server tick rate**: widen delays when the server runs below 20 TPS.
- **Min / Max interval (milliseconds)**: random delay range between hits.

## Keybinds (Controls > Standhitter)

- `keybind`: toggle on/off
- *(unbound)*: open settings

## Notes

- Everything happens through vanilla input and packets; nothing extra is sent to the server.
- Repeated empty-hand hits will eventually break the armor stand and pop its armor.
