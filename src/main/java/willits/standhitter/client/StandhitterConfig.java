package willits.standhitter.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "standhitter")
public class StandhitterConfig implements ConfigData {
	@ConfigEntry.Gui.Tooltip
	public boolean enabled = false;

	@ConfigEntry.Gui.Tooltip
	public boolean hitArmorStands = true;

	@ConfigEntry.Gui.Tooltip
	public boolean hitInteractionEntities = false;

	@ConfigEntry.Gui.Tooltip
	public boolean autoDisableOnLowHunger = true;

	@ConfigEntry.Gui.Tooltip
	public boolean keepActiveWhenUnfocused = false;

	@ConfigEntry.Gui.Tooltip
	public boolean scaleIntervalWithTps = false;

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = 200, max = 10000)
	public int minIntervalMillis = 800;

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = 200, max = 10000)
	public int maxIntervalMillis = 1500;

	@Override
	public void validatePostLoad() {
		if (this.maxIntervalMillis < this.minIntervalMillis) {
			int temp = this.minIntervalMillis;
			this.minIntervalMillis = this.maxIntervalMillis;
			this.maxIntervalMillis = temp;
		}
	}
}
