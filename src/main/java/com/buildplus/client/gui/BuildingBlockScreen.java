package com.buildplus.client.gui;

import com.buildplus.block.BuildingBlockEntity;
import com.buildplus.network.NetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
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
 *
 * Layout desenhado à mão (painel escuro, chips de tamanho, lista de
 * jogadores com botões de remover/adicionar e barra Iniciar/Finalizar)
 * em vez dos botões padrão do Minecraft.
 */
public class BuildingBlockScreen extends Screen {

	// --- Cores do painel ---
	private static final int COLOR_PANEL_BG = 0xF0161616;
	private static final int COLOR_PANEL_BORDER = 0xFF3A3A3A;
	private static final int COLOR_DIVIDER = 0xFF2E2E2E;
	private static final int COLOR_HEADER = 0xFFB0B0B0;
	private static final int COLOR_ROW_BG = 0xFF232323;
	private static final int COLOR_CHIP_BG = 0xFF262626;
	private static final int COLOR_CHIP_HOVER = 0xFF333333;
	private static final int COLOR_CHIP_SELECTED = 0xFF2E5B4F;
	private static final int COLOR_CHIP_SELECTED_BORDER = 0xFF5FD1A8;
	private static final int COLOR_RED = 0xFFB33A3A;
	private static final int COLOR_RED_HOVER = 0xFFD14B4B;
	private static final int COLOR_GREEN = 0xFF3F8F5C;
	private static final int COLOR_GREEN_HOVER = 0xFF4FAE71;
	private static final int COLOR_GOLD = 0xFFE8C55A;

	private static final int PANEL_W = 300;
	private static final int PANEL_H = 210;

	private final BlockPos pos;
	private UUID owner;
	private int selectedSize;
	private boolean active;
	private final Set<UUID> players = new LinkedHashSet<>();

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

	private int panelX() {
		return width / 2 - PANEL_W / 2;
	}

	private int panelY() {
		return height / 2 - PANEL_H / 2;
	}

	@Override
	protected void init() {
		int panelX = panelX();
		int panelY = panelY();

		// --- Botão fechar (X) ---
		addDrawableChild(new ColorButton(panelX + PANEL_W - 24, panelY + 6, 16, 16, Text.literal("x"),
				0x00000000, 0x33FFFFFF, 0, 0xFFDDDDDD,
				b -> { if (client != null) client.setScreen(null); }));

		int colTop = panelY + 40;
		int leftX = panelX + 14;
		int leftW = 128;
		int rightX = panelX + 158;
		int rightW = 128;

		// --- Coluna esquerda: tamanho da área ---
		int sizeRowY = colTop + 12;
		addDrawableChild(new ColorButton(leftX, sizeRowY, 20, 18, Text.literal("-"),
				COLOR_CHIP_BG, COLOR_CHIP_HOVER, COLOR_PANEL_BORDER, 0xFFFFFFFF,
				b -> changeSize(-1)));
		addDrawableChild(new ColorButton(leftX + leftW - 20, sizeRowY, 20, 18, Text.literal("+"),
				COLOR_CHIP_BG, COLOR_CHIP_HOVER, COLOR_PANEL_BORDER, 0xFFFFFFFF,
				b -> changeSize(1)));

		int chipsY = sizeRowY + 26;
		int[] valid = BuildingBlockEntity.VALID_SIZES;
		int chipW = 22;
		int chipGap = 3;
		int totalChipsW = valid.length * chipW + (valid.length - 1) * chipGap;
		int chipsX = leftX + (leftW - totalChipsW) / 2;
		for (int i = 0; i < valid.length; i++) {
			int value = valid[i];
			boolean selected = value == selectedSize;
			int bg = selected ? COLOR_CHIP_SELECTED : COLOR_CHIP_BG;
			int hover = selected ? COLOR_CHIP_SELECTED : COLOR_CHIP_HOVER;
			int border = selected ? COLOR_CHIP_SELECTED_BORDER : COLOR_PANEL_BORDER;
			ColorButton chip = new ColorButton(chipsX + i * (chipW + chipGap), chipsY, chipW, 16,
					Text.literal(String.valueOf(value)), bg, hover, border, 0xFFEFEFEF,
					b -> setSize(value));
			chip.active = isOwner() && !active;
			addDrawableChild(chip);
		}

		// --- Coluna direita: jogadores permitidos ---
		int rowH = 20;
		int rowY = colTop + 12;
		int shown = 0;
		for (UUID uuid : players) {
			if (shown >= 4) break;
			boolean isOwnerRow = uuid.equals(owner);
			if (!isOwnerRow) {
				ColorButton removeBtn = new ColorButton(rightX + rightW - 16, rowY + 2, 14, 14, Text.literal("x"),
						COLOR_RED, COLOR_RED_HOVER, 0, 0xFFFFFFFF,
						b -> removePlayer(uuid));
				removeBtn.active = isOwner();
				addDrawableChild(removeBtn);
			}
			rowY += rowH;
			shown++;
		}

		int addBtnY = colTop + 12 + Math.max(shown, 1) * rowH + 2;
		ColorButton addBtn = new ColorButton(rightX, addBtnY, rightW, 18, Text.translatable("buildplus.gui.add_player"),
				COLOR_GREEN, COLOR_GREEN_HOVER, 0, 0xFFFFFFFF,
				b -> openAddPlayerList());
		addBtn.active = isOwner() && !active;
		addDrawableChild(addBtn);

		// --- Barra inferior: Iniciar / Finalizar ---
		int bottomY = panelY + PANEL_H - 32;
		int btnW = (PANEL_W - 28 - 8) / 2;
		ColorButton startButton = new ColorButton(panelX + 14, bottomY, btnW, 22, Text.translatable("buildplus.gui.start"),
				COLOR_GREEN, COLOR_GREEN_HOVER, 0, 0xFFFFFFFF, b -> start());
		ColorButton finishButton = new ColorButton(panelX + 14 + btnW + 8, bottomY, btnW, 22, Text.translatable("buildplus.gui.finish"),
				COLOR_RED, COLOR_RED_HOVER, 0, 0xFFFFFFFF, b -> finish());
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
		setSize(valid[idx]);
	}

	private void setSize(int size) {
		if (!isOwner() || active || size == selectedSize) return;
		selectedSize = size;

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
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);

		int panelX = panelX();
		int panelY = panelY();

		// --- Painel principal ---
		context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COLOR_PANEL_BG);
		context.drawBorder(panelX, panelY, PANEL_W, PANEL_H, COLOR_PANEL_BORDER);

		// --- Cabeçalho ---
		context.drawText(textRenderer, title, panelX + 14, panelY + 11, 0xFFFFFFFF, true);
		context.fill(panelX + 10, panelY + 28, panelX + PANEL_W - 10, panelY + 29, COLOR_DIVIDER);

		int colTop = panelY + 40;
		int leftX = panelX + 14;
		int leftW = 128;
		int rightX = panelX + 158;
		int rightW = 128;

		// --- Coluna esquerda ---
		context.drawText(textRenderer, Text.translatable("buildplus.gui.area_size").getString().toUpperCase(),
				leftX, colTop, COLOR_HEADER, false);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(selectedSize)),
				leftX + leftW / 2, colTop + 17, 0xFFFFFFFF);

		String areaLabel = Text.translatable("buildplus.gui.area_label",
				selectedSize, selectedSize, selectedSize).getString();
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(areaLabel),
				leftX + leftW / 2, colTop + 62, 0xFF9A9A9A);

		// --- Coluna direita ---
		context.drawText(textRenderer, Text.translatable("buildplus.gui.players").getString().toUpperCase(),
				rightX, colTop, COLOR_HEADER, false);

		int rowH = 20;
		int rowY = colTop + 12;
		int shown = 0;
		for (UUID uuid : players) {
			if (shown >= 4) break;
			boolean isOwnerRow = uuid.equals(owner);
			context.fill(rightX, rowY, rightX + rightW, rowY + rowH - 2, COLOR_ROW_BG);
			String name = resolveName(uuid);
			if (isOwnerRow) {
				context.drawText(textRenderer, name, rightX + 4, rowY + 5, COLOR_GOLD, false);
				String tag = Text.translatable("buildplus.gui.owner_tag").getString();
				int tagW = textRenderer.getWidth(tag);
				context.drawText(textRenderer, tag, rightX + rightW - tagW - 4, rowY + 5, 0xFF6FCF7A, false);
			} else {
				context.drawText(textRenderer, name, rightX + 4, rowY + 5, 0xFFE0E0E0, false);
			}
			rowY += rowH;
			shown++;
		}

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

	/**
	 * Botão simples com cor de fundo/borda customizável, para imitar os
	 * "chips" e barras coloridas do design em vez do botão cinza padrão.
	 */
	private static class ColorButton extends ButtonWidget {
		private final int bgColor;
		private final int bgHoverColor;
		private final int borderColor;
		private final int textColor;

		ColorButton(int x, int y, int width, int height, Text message,
					int bgColor, int bgHoverColor, int borderColor, int textColor, PressAction onPress) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
			this.bgColor = bgColor;
			this.bgHoverColor = bgHoverColor;
			this.borderColor = borderColor;
			this.textColor = textColor;
		}

		@Override
		public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			boolean hovered = isHovered() || isFocused();
			int bg = !active ? 0x552A2A2A : (hovered ? bgHoverColor : bgColor);
			if ((bg >>> 24) != 0) {
				context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
			}
			if (borderColor != 0) {
				context.drawBorder(getX(), getY(), getWidth(), getHeight(), borderColor);
			}
			int color = active ? textColor : 0xFF777777;
			net.minecraft.client.font.TextRenderer textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
			context.drawCenteredTextWithShadow(textRenderer,
					getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
		}
	}
}
