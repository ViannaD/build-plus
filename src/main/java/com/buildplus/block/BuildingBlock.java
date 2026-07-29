package com.buildplus.block;

import com.buildplus.network.NetworkHandler;
import com.buildplus.session.BuildSessionManager;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * O Building Block: usa um modelo 3D customizado (feito no Blockbench, com
 * detalhes decorativos que saem do cubo principal) e uma hitbox simplificada
 * (cubo 16x16x16). Ao ser clicado, abre a GUI de Modo Construção.
 */
public class BuildingBlock extends BlockWithEntity {

	// Hitbox = corpo principal do modelo (cubo 16x16x16). As peças decorativas
	// finas que saem pelas laterais no modelo customizado (building_block.json)
	// não recebem colisão própria - é o padrão para detalhes decorativos.
	private static final VoxelShape SHAPE = createCuboidShape(0, 0, 0, 16, 16, 16);

	public BuildingBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
		// Sem propriedades de estado por enquanto (orientação fixa).
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new BuildingBlockEntity(pos, state);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof BuildingBlockEntity entity)) {
			return ActionResult.PASS;
		}

		// Dono é definido no primeiro uso (quem colocou/primeiro clicar sem dono).
		if (entity.getOwner() == null) {
			entity.setOwner(player.getUuid());
		}

		if (player instanceof ServerPlayerEntity serverPlayer) {
			NetworkHandler.sendOpenGui(serverPlayer, pos, entity);
		}

		return ActionResult.CONSUME;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!world.isClient && !state.isOf(newState.getBlock())) {
			// Bloco foi quebrado/removido: encerra imediatamente qualquer sessão ativa.
			BuildSessionManager.get((net.minecraft.server.world.ServerWorld) world).endSessionByBlock(pos, true);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
