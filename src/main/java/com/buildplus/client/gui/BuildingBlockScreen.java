package com.buildplus.client.gui;

import com.buildplus.block.BuildingBlockEntity;
import com.buildplus.network.NetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GUI custom (não é um ScreenHandler de inventário - não há slots) para
 * configurar e iniciar/finalizar o Modo Construção de um Building Block.
 */
public class BuildingBlockScreen extends Screen {

	private final BlockPos pos;
	private UUID owner;
	private int selectedSize;
	private boolean active;
	private final Set<UUID> players = new LinkedHashSet<>();

	private ButtonWidget startButton;
	private ButtonWidget finishButton;
	private int playerListScroll = 0;

	public BuildingBlockScreen(BlockPos pos, UUID owner, int size, boolean active, Set<UUID> players) {
		super(Text.translatable("buildplus.gui.title"));
		this.pos = pos;
		this.owner = owner;
		this.selectedSize = size;
		this.active = active;
		this.players.addAll(players);
	}

	public BlockPos getBlockPos() {
		return pos;
	}

	public int getSelectedSize() {
		return selectedSize;
	}

	public void applySync(UUID owner, int size, boolean active, Set<UUID> players) {
		this.owner = owner;
		this.selectedSize = size;
		this.active = active;
		this.players.clear();
		this.players.addAll(players);
		clearAndInit();
	}

	private boolean isOwner() {
		return owner != null && client != null && client.player != null && owner.equals(client.player.getUuid());
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int top = height / 2 - 90;

		// --- Botões de tamanho [-] valor [+]
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeSize(-1))
				.dimensions(centerX - 80, top, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeSize(1))
				.dimensions(centerX + 60, top, 20, 20).build());

		// --- Botão adicionar jogador
		addDrawableChild(ButtonWidget.builder(Text.translatable("buildplus.gui.add_player"), b -> openAddPlayerList())
				.dimensions(centerX - 60, top + 40, 120, 20).build());

		int y = top + 65;
		int shown = 0;
		for (UUID uuid : players) {
			if (shown >= 5) break;
			String name = resolveName(uuid);
			boolean isOwnerRow = uuid.equals(owner);
			ButtonWidget.Builder builder = ButtonWidget.builder(
					Text.literal(name + (isOwnerRow ? " §7(você)§r" : "")),
					b -> {
						if (!isOwnerRow && isOwner()) removePlayer(uuid);
					}
			);
			ButtonWidget row = builder.dimensions(centerX - 60, y, 120, 18).build();
			row.active = isOwner() && !isOwnerRow;
			addDrawableChild(row);
			y += 20;
			shown++;
		}

		int bottom = top + 190;
		startButton = ButtonWidget.builder(Text.translatable("buildplus.gui.start"), b -> start())
				.dimensions(centerX - 80, bottom, 75, 20).build();
		finishButton = ButtonWidget.builder(Text.translatable("buildplus.gui.finish"), b -> finish())
				.dimensions(centerX + 5, bottom, 75, 20).build();
		startButton.active = isOwner() && !active;
		finishButton.active = isOwner() && active;
		addDrawableChild(startButton);
		addDrawableChild(finishButton);
	}

	private String resolveName(UUID uuid) {
		if (client == null || client.getNetworkHandler() == null) return uuid.toString().substring(0, 8);
		PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
		return entry != null ? entry.getProfile().getName() : uuid.toString().substring(0, 8);
	}

	private void changeSize(int direction) {
		if (!isOwner() || active) return;
		int[] valid = BuildingBlockEntity.VALID_SIZES;
		int idx = 0;
		for (int i = 0; i < valid.length; i++) {
			if (valid[i] == selectedSize) idx = i;
		}
		idx = Math.max(0, Math.min(valid.length - 1, idx + direction));
		selectedSize = valid[idx];

		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeInt(selectedSize);
		ClientPlayNetworking.send(NetworkHandler.SET_SIZE, buf);
		clearAndInit();
	}

	private void openAddPlayerList() {
		if (client == null) return;
		client.setScreen(new PlayerPickerScreen(this, players));
	}

	public void addPlayer(UUID uuid) {
		players.add(uuid);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeUuid(uuid);
		ClientPlayNetworking.send(NetworkHandler.ADD_PLAYER, buf);
	}

	private void removePlayer(UUID uuid) {
		players.remove(uuid);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeUuid(uuid);
		ClientPlayNetworking.send(NetworkHandler.REMOVE_PLAYER, buf);
		clearAndInit();
	}

	private void start() {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		ClientPlayNetworking.send(NetworkHandler.START, buf);
		if (client != null) client.setScreen(null);
	}

	private void finish() {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		ClientPlayNetworking.send(NetworkHandler.FINISH, buf);
		if (client != null) client.setScreen(null);
	}

	@Override
	public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		int centerX = width / 2;
		int top = height / 2 - 90;

		context.drawCenteredTextWithShadow(textRenderer, title, centerX, top - 40, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("buildplus.gui.area_size"), centerX, top - 22, 0xAAAAAA);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(selectedSize)), centerX, top + 5, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("buildplus.gui.players"), centerX, top + 55, 0xAAAAAA);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
