package com.buildplus.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.UUID;

/**
 * Lista simples dos jogadores online para o dono escolher quem autorizar a voar.
 */
public class PlayerPickerScreen extends Screen {

	private final BuildingBlockScreen parent;
	private final Set<UUID> alreadyAdded;

	protected PlayerPickerScreen(BuildingBlockScreen parent, Set<UUID> alreadyAdded) {
		super(Text.literal("Jogadores Online"));
		this.parent = parent;
		this.alreadyAdded = alreadyAdded;
	}

	@Override
	protected void init() {
		if (client == null || client.getNetworkHandler() == null) return;
		int y = height / 2 - 80;
		int centerX = width / 2;
		for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
			UUID uuid = entry.getProfile().getId();
			if (alreadyAdded.contains(uuid)) continue;
			String name = entry.getProfile().getName();
			addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> {
				parent.addPlayer(uuid);
				if (client != null) client.setScreen(parent);
			}).dimensions(centerX - 75, y, 150, 20).build());
			y += 22;
			if (y > height / 2 + 80) break;
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Cancelar"), b -> {
			if (client != null) client.setScreen(parent);
		}).dimensions(centerX - 40, height / 2 + 100, 80, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 100, 0xFFFFFF);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
