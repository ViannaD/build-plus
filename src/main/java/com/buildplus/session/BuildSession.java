package com.buildplus.session;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Estado em memória (não persistido em NBT diretamente aqui - os dados "fonte
 * da verdade" ficam no BuildingBlockEntity) de uma sessão de construção ativa:
 * centro, tamanho, dono, participantes e estatísticas para o resumo final.
 */
public class BuildSession {

	public final BlockPos blockPos;
	public final BlockPos center;
	public final int size;
	public final UUID owner;
	public final Set<UUID> allowedPlayers;

	public final long startTimeMillis;
	public int blocksPlaced = 0;

	public BuildSession(BlockPos blockPos, int size, UUID owner, Set<UUID> allowedPlayers) {
		this.blockPos = blockPos;
		this.center = blockPos;
		this.size = size;
		this.owner = owner;
		this.allowedPlayers = new LinkedHashSet<>(allowedPlayers);
		this.allowedPlayers.add(owner);
		this.startTimeMillis = System.currentTimeMillis();
	}

	/** Cubo de tamanho {@code size} centrado no Building Block, conforme o spec. */
	public Box getBounds() {
		double half = size / 2.0;
		return new Box(
				center.getX() + 0.5 - half, center.getY() + 0.5 - half, center.getZ() + 0.5 - half,
				center.getX() + 0.5 + half, center.getY() + 0.5 + half, center.getZ() + 0.5 + half
		);
	}

	public boolean isAllowed(UUID uuid) {
		return allowedPlayers.contains(uuid);
	}

	public long getElapsedMillis() {
		return System.currentTimeMillis() - startTimeMillis;
	}
}
