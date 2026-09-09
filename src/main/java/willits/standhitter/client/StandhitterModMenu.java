package willits.standhitter.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import me.shedaniel.autoconfig.AutoConfig;

public class StandhitterModMenu implements ModMenuApi {
	@Override
	@SuppressWarnings("removal")
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> AutoConfig.getConfigScreen(StandhitterConfig.class, parent).get();
	}
}
