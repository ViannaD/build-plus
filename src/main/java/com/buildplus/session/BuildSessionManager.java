package com.buildplus.session;

import com.buildplus.block.BuildingBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia todas as sessões ativas de "Modo Construção" no servidor: quem está
 * voando, dentro de qual área, contagens regressivas de quem saiu dos limites,
 * e restauração de habilidades ao finalizar.
 *
 * É um singleton simples por processo de servidor (cobre o caso comum de um
 * único MinecraftServer rodando por JVM, que é o padrão em Fabric).
 */
public final class BuildSessionManager {

	/** 5 segundos, conforme o spec, em ticks (20 ticks/segundo). */
	public static final int LEAVE_GRACE_TICKS = 20 * 5;

	private static final BuildSessionManager INSTANCE = new BuildSessionManager();

	private final Map<String, BuildSession> sessionsByBlockKey = new ConcurrentHashMap<>();
	private final Map<UUID, BuildSession> sessionByPlayer = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> outOfBoundsTicks = new ConcurrentHashMap<>();
	private final Map<String, ServerWorld> worldBySession = new ConcurrentHashMap<>();

	private BuildSessionManager() {
	}

	public static BuildSessionManager get(ServerWorld world) {
		return INSTANCE;
	}

	public static BuildSessionManager getGlobal() {
		return INSTANCE;
	}

	private static String key(BlockPos pos, World world) {
		return world.getRegistryKey().getValue() + "@" + pos.asLong();
	}

	// ---------------------------------------------------------------
	// Início / fim de sessão
	// ---------------------------------------------------------------

	public boolean isPlayerInAnySession(UUID uuid) {
		return sessionByPlayer.containsKey(uuid);
	}

	public BuildSession getSessionForPlayer(UUID uuid) {
		return sessionByPlayer.get(uuid);
	}

	public boolean startSession(ServerWorld world, BlockPos pos, BuildingBlockEntity entity, ServerPlayerEntity starter) {
		if (entity.getOwner() == null || !entity.getOwner().equals(starter.getUuid())) {
			starter.sendMessage(Text.translatable("buildplus.msg.not_owner"), true);
			return false;
		}
		if (isPlayerInAnySession(starter.getUuid())) {
			starter.sendMessage(Text.translatable("buildplus.msg.already_in_session"), true);
			return false;
		}

		String k = key(pos, world);
		BuildSession session = new BuildSession(pos, entity.getAreaSize(), entity.getOwner(), entity.getAllowedPlayers());
		sessionsByBlockKey.put(k, session);
		worldBySession.put(k, world);
		entity.setActive(true);

		MinecraftServer server = world.getServer();
		for (UUID allowed : session.allowedPlayers) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(allowed);
			if (p != null && p.world == world && !isPlayerInAnySession(allowed)) {
				addParticipant(p, session);
			}
		}

		Text startMsg = Text.translatable("buildplus.msg.started", session.size);
		for (UUID allowed : session.allowedPlayers) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(allowed);
			if (p != null) {
				p.sendMessage(startMsg, false);
			}
		}
		return true;
	}

	private void addParticipant(ServerPlayerEntity player, BuildSession session) {
		sessionByPlayer.put(player.getUuid(), session);
		grantFlight(player);
	}

	/** Dono adicionou um jogador enquanto a sessão já está ativa. */
	public void addPlayerToActiveSession(BuildSession session, ServerPlayerEntity player) {
		if (!isPlayerInAnySession(player.getUuid())) {
			session.allowedPlayers.add(player.getUuid());
			addParticipant(player, session);
		}
	}

	/** Dono removeu um jogador; se ele estava participando, encerra a participação dele. */
	public void removePlayerFromSession(BuildSession session, UUID uuid) {
		session.allowedPlayers.remove(uuid);
		if (session.owner.equals(uuid)) {
			return; // dono não pode remover a si mesmo
		}
		BuildSession current = sessionByPlayer.get(uuid);
		if (current == session) {
			endParticipation(uuid, session, true);
		}
	}

	/** Encerra manualmente (botão "Finalizar") toda a sessão do bloco. */
	public void finishSession(ServerWorld world, BlockPos pos, BuildingBlockEntity entity, ServerPlayerEntity requester) {
		if (entity.getOwner() == null || !entity.getOwner().equals(requester.getUuid())) {
			requester.sendMessage(Text.translatable("buildplus.msg.not_owner"), true);
			return;
		}
		endSessionByBlock(pos, world, false);
	}

	public void endSessionByBlock(BlockPos pos, boolean silent) {
		// Sobrecarga usada quando não temos a ServerWorld à mão (ex: onStateReplaced).
		for (Map.Entry<String, BuildSession> e : sessionsByBlockKey.entrySet()) {
			if (e.getValue().blockPos.equals(pos)) {
				ServerWorld w = worldBySession.get(e.getKey());
				endSessionByBlock(pos, w, silent);
				return;
			}
		}
	}

	public void endSessionByBlock(BlockPos pos, ServerWorld world, boolean silent) {
		if (world == null) return;
		String k = key(pos, world);
		BuildSession session = sessionsByBlockKey.remove(k);
		worldBySession.remove(k);
		if (session == null) return;

		for (UUID uuid : new HashSet<>(session.allowedPlayers)) {
			endParticipation(uuid, session, silent);
		}

		if (!silent) {
			sendSummary(world, session);
		}
	}

	/** Um jogador específico sai da sessão (não voltou a tempo, ou foi removido). O bloco continua ativo para os demais. */
	private void endParticipation(UUID uuid, BuildSession session, boolean silent) {
		sessionByPlayer.remove(uuid, session);
		outOfBoundsTicks.remove(uuid);

		MinecraftServer server = findServer(session);
		if (server == null) return;
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
		if (player != null) {
			revokeFlight(player);
			if (!silent) {
				player.sendMessage(Text.translatable("buildplus.msg.finished"), false);
				player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.6f, 1f);
			}
		}
	}

	private MinecraftServer findServer(BuildSession session) {
		for (Map.Entry<String, ServerWorld> e : worldBySession.entrySet()) {
			if (e.getValue() != null) {
				return e.getValue().getServer();
			}
		}
		return null;
	}

	private void sendSummary(ServerWorld world, BuildSession session) {
		long seconds = session.getElapsedMillis() / 1000;
		String time = String.format("%dm%02ds", seconds / 60, seconds % 60);
		MinecraftServer server = world.getServer();
		for (UUID uuid : session.allowedPlayers) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
			if (p == null) continue;
			p.sendMessage(Text.translatable("buildplus.summary.title"), false);
			p.sendMessage(Text.translatable("buildplus.summary.time", time), false);
			p.sendMessage(Text.translatable("buildplus.summary.players", session.allowedPlayers.size()), false);
			p.sendMessage(Text.translatable("buildplus.summary.area", session.size), false);
			p.sendMessage(Text.translatable("buildplus.summary.blocks_placed", session.blocksPlaced), false);
		}
	}

	// ---------------------------------------------------------------
	// Voo / habilidades
	// ---------------------------------------------------------------

	private void grantFlight(ServerPlayerEntity player) {
		if (player.interactionManager.getGameMode() == GameMode.CREATIVE
				|| player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
			return; // já pode voar, nada a fazer / restaurar depois
		}
		player.getAbilities().allowFlying = true;
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
	}

	private void revokeFlight(ServerPlayerEntity player) {
		if (player.interactionManager.getGameMode() == GameMode.CREATIVE
				|| player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
			return;
		}
		player.getAbilities().allowFlying = false;
		player.getAbilities().flying = false;
		player.sendAbilitiesUpdate();
	}

	// ---------------------------------------------------------------
	// Tick do servidor: checa limites, contagem regressiva, segurança
	// ---------------------------------------------------------------

	public void tick(MinecraftServer server) {
		if (sessionByPlayer.isEmpty()) return;

		for (Map.Entry<UUID, BuildSession> entry : new ArrayList<>(sessionByPlayer.entrySet())) {
			UUID uuid = entry.getKey();
			BuildSession session = entry.getValue();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player == null) continue; // desconectou; mantemos a sessão dele até voltar

			ServerWorld sessionWorld = worldForSession(session);
			boolean inBounds = sessionWorld != null && player.world == sessionWorld
					&& session.getBounds().contains(player.getX(), player.getY(), player.getZ());

			if (inBounds) {
				if (outOfBoundsTicks.remove(uuid) != null) {
					player.sendMessage(Text.translatable("buildplus.msg.area_recovered"), true);
				}
				// Segurança: sem dormir, sem elytra, sem fogos de artifício enquanto voando.
				enforceSafety(player);
				continue;
			}

			int ticks = outOfBoundsTicks.merge(uuid, 1, Integer::sum);
			if (ticks == 1) {
				player.sendMessage(Text.translatable("buildplus.msg.left_area"), false);
				player.sendMessage(Text.translatable("buildplus.msg.return_warning"), false);
			}
			int remainingSeconds = Math.max(0, (LEAVE_GRACE_TICKS - ticks) / 20 + 1);
			player.sendMessage(Text.literal("§c" + remainingSeconds + "..."), true);

			if (ticks >= LEAVE_GRACE_TICKS) {
				outOfBoundsTicks.remove(uuid);
				endParticipation(uuid, session, false);
			}
		}
	}

	private void enforceSafety(ServerPlayerEntity player) {
		if (player.isSleeping()) {
			player.wakeUp();
		}
		if (player.isGliding()) {
			player.setNoGravity(false);
			player.stopGliding();
		}
	}

	private ServerWorld worldForSession(BuildSession session) {
		for (Map.Entry<String, BuildSession> e : sessionsByBlockKey.entrySet()) {
			if (e.getValue() == session) {
				return worldBySession.get(e.getKey());
			}
		}
		return null;
	}

	// ---------------------------------------------------------------
	// Consultas usadas por outros sistemas (dano de queda, quebra de bloco, etc.)
	// ---------------------------------------------------------------

	public boolean shouldPreventFallDamage(UUID uuid) {
		return sessionByPlayer.containsKey(uuid);
	}

	public boolean isBuildingBlockActiveAt(BlockPos pos) {
		for (BuildSession s : sessionsByBlockKey.values()) {
			if (s.blockPos.equals(pos)) return true;
		}
		return false;
	}
}
