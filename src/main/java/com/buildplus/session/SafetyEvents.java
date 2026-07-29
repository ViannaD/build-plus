package com.buildplus.session;

import com.buildplus.block.ModBlocks;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;


/**
 * Regras de segurança pedidas no spec:
 * - não pode quebrar o Building Block durante a construção;
 * - sem dano de queda enquanto está no Modo Construção;
 * - contabiliza blocos colocados dentro da área (heurística, para o resumo final).
 *
 * Dormir e planar de elytra são tratados no tick de {@link BuildSessionManager}
 * (acordar/parar de planar ativamente), já que não há callback dedicado "antes"
 * no Fabric API para cancelar de forma limpa em todos os casos.
 */
public final class SafetyEvents {

	private SafetyEvents() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
			if (!state.isOf(ModBlocks.BUILDING_BLOCK)) return true;
			if (world.isClient) return true;
			if (BuildSessionManager.getGlobal().isBuildingBlockActiveAt(pos)) {
				player.sendMessage(Text.translatable("buildplus.msg.cannot_break"), true);
				return false;
			}
			return true;
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((livingEntity, damageSource, amount) -> {
			if (!damageSource.isOf(DamageTypes.FALL)) return true;
			if (!(livingEntity instanceof ServerPlayerEntity player)) return true;
			return !BuildSessionManager.getGlobal().shouldPreventFallDamage(player.getUuid());
		});

		registerBlockPlacementTracking();
	}

	/**
	 * Chamado a partir de um item-use mixin/callback dedicado para foguetes, caso deseje
	 * reforçar via mixin no futuro. Mantido como método utilitário público.
	 */
	public static boolean isRestricted(ServerPlayerEntity player) {
		return BuildSessionManager.getGlobal().isPlayerInAnySession(player.getUuid());
	}

	/** Heurística simples de contagem de blocos colocados dentro da área (para o resumo final). */
	public static void registerBlockPlacementTracking() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
			BuildSession session = BuildSessionManager.getGlobal().getSessionForPlayer(serverPlayer.getUuid());
			if (session == null) return ActionResult.PASS;

			ItemStack stack = player.getStackInHand(hand);
			if (!(stack.getItem() instanceof BlockItem)) return ActionResult.PASS;
			int before = stack.getCount();
			ServerWorld serverWorld = (ServerWorld) world;
			serverWorld.getServer().execute(() -> {
				ItemStack after = player.getStackInHand(hand);
				if (after.getCount() < before) {
					session.blocksPlaced++;
				}
			});
			return ActionResult.PASS;
		});
	}
}
