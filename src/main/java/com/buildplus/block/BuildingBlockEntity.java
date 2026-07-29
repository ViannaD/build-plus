package com.buildplus.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda os dados persistentes de um Building Block: dono, tamanho da área,
 * lista de jogadores autorizados e se está ativo no momento.
 *
 * A lógica de voo/sessão em si fica em {@link com.buildplus.session.BuildSessionManager};
 * esta classe é só o "disco" onde os dados moram no mundo salvo.
 */
public class BuildingBlockEntity extends BlockEntity {

	public static final int DEFAULT_SIZE = 100;
	public static final int[] VALID_SIZES = {100, 200, 300, 400, 500};

	private UUID owner;
	private int areaSize = DEFAULT_SIZE;
	private final Set<UUID> allowedPlayers = new LinkedHashSet<>();
	private boolean active = false;

	public BuildingBlockEntity(BlockPos pos, BlockState state) {
		super(com.buildplus.block.ModBlocks.BUILDING_BLOCK_ENTITY_TYPE, pos, state);
	}

	public UUID getOwner() {
		return owner;
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		markDirtyAndSync();
	}

	public int getAreaSize() {
		return areaSize;
	}

	public void setAreaSize(int areaSize) {
		for (int valid : VALID_SIZES) {
			if (valid == areaSize) {
				this.areaSize = areaSize;
				markDirtyAndSync();
				return;
			}
		}
	}

	public Set<UUID> getAllowedPlayers() {
		return allowedPlayers;
	}

	public void addAllowedPlayer(UUID uuid) {
		allowedPlayers.add(uuid);
		markDirtyAndSync();
	}

	public void removeAllowedPlayer(UUID uuid) {
		allowedPlayers.remove(uuid);
		markDirtyAndSync();
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
		markDirtyAndSync();
	}

	private void markDirtyAndSync() {
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		nbt.putInt("AreaSize", areaSize);
		nbt.putBoolean("Active", active);
		NbtList list = new NbtList();
		for (UUID uuid : allowedPlayers) {
			list.add(NbtString.of(uuid.toString()));
		}
		nbt.put("AllowedPlayers", list);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		areaSize = nbt.contains("AreaSize") ? nbt.getInt("AreaSize") : DEFAULT_SIZE;
		active = nbt.getBoolean("Active");
		allowedPlayers.clear();
		if (nbt.contains("AllowedPlayers", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("AllowedPlayers", NbtElement.STRING_TYPE);
			for (int i = 0; i < list.size(); i++) {
				try {
					allowedPlayers.add(UUID.fromString(list.getString(i)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		return createNbt();
	}
}
