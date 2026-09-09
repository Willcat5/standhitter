package willits.standhitter.client;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.platform.InputConstants;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import willits.standhitter.Standhitter;

public class StandhitterClient implements ClientModInitializer {
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Standhitter.id("standhitter"));

	private ConfigHolder<StandhitterConfig> configHolder;
	private KeyMapping toggleEnabledKey;
	private KeyMapping openConfigKey;
	private long nextAttackTick;
	private boolean pauseOnLostFocusPatched;
	private boolean originalPauseOnLostFocus;
	private long tpsSampleGameTime;
	private int tpsSampleWindow;
	private double estimatedServerTps = 20.0;
	private boolean disabledByLowHunger;

	@Override
	public void onInitializeClient() {
		AutoConfig.register(StandhitterConfig.class, JanksonConfigSerializer::new);
		this.configHolder = AutoConfig.getConfigHolder(StandhitterConfig.class);

		this.toggleEnabledKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.standhitter.toggleEnabled",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_H,
				CATEGORY
		));
		this.openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.standhitter.openConfig",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		HudRenderCallback.EVENT.register(this::onHudRender);
	}

	@SuppressWarnings("removal")
	private void onEndTick(Minecraft mc) {
		if (this.toggleEnabledKey.consumeClick()) {
			StandhitterConfig config = this.configHolder.getConfig();
			config.enabled = !config.enabled;
			this.disabledByLowHunger = false;
			this.configHolder.save();
		}
		if (this.openConfigKey.consumeClick()) {
			mc.setScreen(AutoConfig.getConfigScreen(StandhitterConfig.class, mc.screen).get());
		}

		if (mc.player != null) {
			StandhitterConfig config = this.configHolder.getConfig();
			if (config.autoDisableOnLowHunger && config.enabled && mc.player.getFoodData().getFoodLevel() <= 6) {
				config.enabled = false;
				this.disabledByLowHunger = true;
				this.configHolder.save();
			}
		}

		this.handleUnfocused(mc);

		if (mc.level == null || mc.player == null || mc.gameMode == null || mc.screen != null || mc.isPaused()) {
			return;
		}
		if (mc.player.isSpectator()) {
			return;
		}

		StandhitterConfig config = this.configHolder.getConfig();
		long now = mc.player.tickCount;

		if (!config.enabled) {
			this.nextAttackTick = now + randomIntervalTicks(config);
			return;
		}
		long intervalTicks = scaledIntervalTicks(mc, config);
		if (config.clickRegardlessOfTarget) {
			if (now >= this.nextAttackTick) {
				KeyMapping.click(KeyBindingHelper.getBoundKeyOf(mc.options.keyAttack));
				this.nextAttackTick = now + intervalTicks;
			}
			return;
		}
		Entity target = findAimedTarget(mc, config);
		if (target == null) {
			this.nextAttackTick = now + intervalTicks;
			return;
		}
		if (now >= this.nextAttackTick) {
			attackTarget(mc, target);
			this.nextAttackTick = now + scaledIntervalTicks(mc, config);
		}
	}

	private long scaledIntervalTicks(Minecraft mc, StandhitterConfig config) {
		long base = randomIntervalTicks(config);
		if (!config.scaleIntervalWithTps) {
			return base;
		}
		double tps = sampleServerTps(mc);
		double factor = Math.max(0.25, Math.min(10.0, 20.0 / tps));
		return Math.max(4, Math.round(base * factor));
	}

	private double sampleServerTps(Minecraft mc) {
		long gameTime = mc.level.getLevelData().getGameTime();
		if (this.tpsSampleWindow == 0) {
			this.tpsSampleGameTime = gameTime;
		}
		this.tpsSampleWindow++;
		if (this.tpsSampleWindow >= 40) {
			double deltaTicks = gameTime - this.tpsSampleGameTime;
			double tps = deltaTicks / (this.tpsSampleWindow / 20.0);
			if (tps >= 1.0 && tps <= 20.05) {
				this.estimatedServerTps = tps;
			}
			this.tpsSampleWindow = 0;
		}
		return this.estimatedServerTps;
	}

	private void handleUnfocused(Minecraft mc) {
		StandhitterConfig config = this.configHolder.getConfig();
		boolean suppressPause = config.enabled && config.keepActiveWhenUnfocused && !mc.isWindowActive();
		if (suppressPause) {
			if (!this.pauseOnLostFocusPatched) {
				this.originalPauseOnLostFocus = mc.options.pauseOnLostFocus;
				this.pauseOnLostFocusPatched = true;
			}
			mc.options.pauseOnLostFocus = false;
			if (mc.screen instanceof PauseScreen) {
				mc.setScreen(null);
			}
		} else if (this.pauseOnLostFocusPatched) {
			mc.options.pauseOnLostFocus = this.originalPauseOnLostFocus;
			this.pauseOnLostFocusPatched = false;
		}
	}

	private static long randomIntervalTicks(StandhitterConfig config) {
		long minTicks = Math.max(4, (long) config.minIntervalMillis * 20L / 1000L);
		long maxTicks = Math.max(minTicks, (long) config.maxIntervalMillis * 20L / 1000L);
		return ThreadLocalRandom.current().nextLong(minTicks, maxTicks + 1);
	}

	private static void attackTarget(Minecraft mc, Entity target) {
		if (target instanceof ArmorStand) {
			KeyMapping.click(KeyBindingHelper.getBoundKeyOf(mc.options.keyAttack));
		} else {
			mc.player.swing(InteractionHand.MAIN_HAND);
			mc.gameMode.attack(mc.player, target);
		}
	}

	private static Entity findAimedTarget(Minecraft mc, StandhitterConfig config) {
		if (config.hitArmorStands) {
			Entity crosshairTarget = mc.crosshairPickEntity;
			if (isValidArmorStand(crosshairTarget)) {
				return crosshairTarget;
			}
		}
		if (config.hitInteractionEntities) {
			return findAimedInteraction(mc);
		}
		return null;
	}

	private static Entity findAimedInteraction(Minecraft mc) {
		Entity crosshairTarget = mc.crosshairPickEntity;
		if (crosshairTarget instanceof Interaction interaction && interaction.isAlive()) {
			return interaction;
		}
		Vec3 eye = mc.player.getEyePosition(1.0F);
		Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(mc.player.entityInteractionRange()));
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(mc.player.entityInteractionRange()))) {
			if (!(entity instanceof Interaction interaction) || !interaction.isAlive()) {
				continue;
			}
			Optional<Vec3> hit = entity.getBoundingBox().inflate(0.2).clip(eye, end);
			if (hit.isPresent()) {
				double distance = hit.get().distanceToSqr(eye);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = entity;
				}
			}
		}
		return best;
	}

	private static boolean isValidArmorStand(Object crosshairTarget) {
		if (!(crosshairTarget instanceof ArmorStand stand)) {
			return false;
		}
		if (!stand.isAlive() || stand.isMarker()) {
			return false;
		}
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (!stand.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.level == null) {
			return;
		}
		StandhitterConfig config = this.configHolder.getConfig();
		String key;
		int color;
		if (config.enabled) {
			key = "message.standhitter.hudEnabled";
			color = 0xFF55FF55;
		} else if (this.disabledByLowHunger) {
			key = "message.standhitter.hudLowHunger";
			color = 0xFFFFA500;
		} else {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().scale(0.5f, 0.5f);
		int x = 8;
		int y = (mc.getWindow().getGuiScaledHeight() - 6) * 2;
		graphics.drawString(mc.font, Component.translatable(key), x, y, color, true);
		graphics.pose().popMatrix();
	}
}
