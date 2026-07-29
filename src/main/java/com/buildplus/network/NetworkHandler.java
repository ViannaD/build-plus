package com.buildplus.network;

import com.buildplus.BuildPlusMod;
import com.buildplus.block.BuildingBlockEntity;
import com.buildplus.session.BuildSession;
import com.buildplus.session.BuildSessionManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Define todos os identificadores de pacote e os handlers do servidor.
 * O registro dos handlers de cliente fica em com.buildplus.client.BuildPlusClient
 * (para não puxar classes de cliente no dedicated server).
 */
public final class NetworkHandler {

	private NetworkHandler() {
	}

	// Servidor -> Cliente: abre a GUI com o estado atual do Building Block
	public static final Identifier OPEN_GUI = new Identifier(BuildPlusMod.MOD_ID, "open_gui");
	// Servidor -> Cliente: atualiza a GUI aberta (ex: outro jogador foi adicionado)
	public static final Identifier SYNC_GUI = new Identifier(BuildPlusMod.MOD_ID, "sync_gui");

	// Cliente -> Servidor
	public static final Identifier SET_SIZE = new Identifier(BuildPlusMod.MOD_ID, "set_size");
	public static final Identifier ADD_PLAYER = new Identifier(BuildPlusMod.MOD_ID, "add_player");
	public static final Identifier REMOVE_PLAYER = new Identifier(BuildPlusMod.MOD_ID, "remove_player");
	public static final Identifier START = new Identifier(BuildPlusMod.MOD_ID, "start");
	public static final Identifier FINISH = new Identifier(BuildPlusMod.MOD_ID, "finish");

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SET_SIZE, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			int size = buf.readInt();
			server.execute(() -> withOwnedEntity(player, pos, entity -> entity.setAreaSize(size)));
		});

		ServerPlayNetworking.registerGlobalReceiver(ADD_PLAYER, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			UUID target = buf.readUuid();
			server.execute(() -> withOwnedEntity(player, pos, entity -> {
				entity.addAllowedPlayer(target);
				if (entity.isActive()) {
					BuildSession session = BuildSessionManager.getGlobal().getSessionForPlayer(entity.getOwner());
					ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(target);
					if (session != null && targetPlayer != null) {
						BuildSessionManager.getGlobal().addPlayerToActiveSession(session, targetPlayer);
					}
				}
			}));
		});

		ServerPlayNetworking.registerGlobalReceiver(REMOVE_PLAYER, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			UUID target = buf.readUuid();
			server.execute(() -> withOwnedEntity(player, pos, entity -> {
				entity.removeAllowedPlayer(target);
				BuildSession session = BuildSessionManager.getGlobal().getSessionForPlayer(target);
				if (session != null && session.blockPos.equals(pos)) {
					BuildSessionManager.getGlobal().removePlayerFromSession(session, target);
				}
			}));
		});

		ServerPlayNetworking.registerGlobalReceiver(START, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			server.execute(() -> withEntity(player, pos, (world, entity) -> {
				if (entity.isActive()) return;
				BuildSessionManager.get(world).startSession(world, pos, entity, player);
			}));
		});

		ServerPlayNetworking.registerGlobalReceiver(FINISH, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			server.execute(() -> withEntity(player, pos, (world, entity) ->
					BuildSessionManager.get(world).finishSession(world, pos, entity, player)));
		});
	}

	private interface EntityAction {
		void run(BuildingBlockEntity entity);
	}

	private interface WorldEntityAction {
		void run(ServerWorld world, BuildingBlockEntity entity);
	}

	private static void withOwnedEntity(ServerPlayerEntity player, BlockPos pos, EntityAction action) {
		withEntity(player, pos, (world, entity) -> {
			if (entity.getOwner() != null && entity.getOwner().equals(player.getUuid())) {
				action.run(entity);
			} else {
				player.sendMessage(Text.translatable("buildplus.msg.not_owner"), true);
			}
		});
	}

	private static void withEntity(ServerPlayerEntity player, BlockPos pos, WorldEntityAction action) {
		ServerWorld world = player.getServerWorld();
		if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
				&& world.getBlockEntity(pos) instanceof BuildingBlockEntity entity) {
			// Só permite agir se o jogador estiver razoavelmente perto do bloco (evita comandos "blind").
			if (player.getBlockPos().isWithinDistance(pos, 32)) {
				action.run(world, entity);
			}
		}
	}

	// ---------------------------------------------------------------
	// Envio Servidor -> Cliente
	// ---------------------------------------------------------------

	public static void sendOpenGui(ServerPlayerEntity player, BlockPos pos, BuildingBlockEntity entity) {
		PacketByteBuf buf = writeGuiState(pos, entity);
		ServerPlayNetworking.send(player, OPEN_GUI, buf);
	}

	public static void sendSyncGui(ServerPlayerEntity player, BlockPos pos, BuildingBlockEntity entity) {
		PacketByteBuf buf = writeGuiState(pos, entity);
		ServerPlayNetworking.send(player, SYNC_GUI, buf);
	}

	private static PacketByteBuf writeGuiState(BlockPos pos, BuildingBlockEntity entity) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeUuid(entity.getOwner() != null ? entity.getOwner() : new UUID(0, 0));
		buf.writeInt(entity.getAreaSize());
		buf.writeBoolean(entity.isActive());
		buf.writeInt(entity.getAllowedPlayers().size());
		for (UUID uuid : entity.getAllowedPlayers()) {
			buf.writeUuid(uuid);
		}
		return buf;
	}
}
