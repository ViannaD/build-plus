package com.buildplus.client;

import com.buildplus.client.gui.BuildingBlockScreen;
import com.buildplus.client.render.WireframeRenderer;
import com.buildplus.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class BuildPlusClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(WireframeRenderer::onRenderWorld);

		ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.OPEN_GUI, (client, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			UUID owner = buf.readUuid();
			int size = buf.readInt();
			boolean active = buf.readBoolean();
			int count = buf.readInt();
			Set<UUID> players = new LinkedHashSet<>();
			for (int i = 0; i < count; i++) {
				players.add(buf.readUuid());
			}
			client.execute(() -> client.setScreen(new BuildingBlockScreen(pos, owner, size, active, players)));
		});

		ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.SYNC_GUI, (client, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			UUID owner = buf.readUuid();
			int size = buf.readInt();
			boolean active = buf.readBoolean();
			int count = buf.readInt();
			Set<UUID> players = new LinkedHashSet<>();
			for (int i = 0; i < count; i++) {
				players.add(buf.readUuid());
			}
			client.execute(() -> {
				if (MinecraftClient.getInstance().currentScreen instanceof BuildingBlockScreen screen && screen.getBlockPos().equals(pos)) {
					screen.applySync(owner, size, active, players);
				}
			});
		});
	}
}
