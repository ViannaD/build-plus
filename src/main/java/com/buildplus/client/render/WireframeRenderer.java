package com.buildplus.client.render;

import com.buildplus.client.gui.BuildingBlockScreen;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Desenha o holograma (wireframe) da área de construção enquanto a GUI do
 * Building Block está aberta, com cor mudando conforme a distância da borda:
 * azul (longe), amarelo (perto) e vermelho (fora/na borda).
 *
 * Usa a API de alto nível do Minecraft (RenderLayer/VertexConsumer) em vez de
 * chamar com.mojang.blaze3d.vertex diretamente - é a forma estável e
 * recomendada de desenhar geometria custom a partir de eventos do Fabric API,
 * e evita quebrar por causa de detalhes internos do renderer que mudam entre
 * builds da Fabric API/Minecraft.
 */
public final class WireframeRenderer {

	private WireframeRenderer() {
	}

	// Distância (em blocos) considerada "perto da borda" para virar amarelo.
	private static final double NEAR_EDGE_THRESHOLD = 8.0;

	public static void onRenderWorld(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(client.currentScreen instanceof BuildingBlockScreen screen)) {
			return;
		}

		BlockPos center = screen.getBlockPos();
		int size = screen.getSelectedSize();
		double half = size / 2.0;

		Vec3d camera = context.camera().getPos();
		double minX = center.getX() + 0.5 - half;
		double maxX = center.getX() + 0.5 + half;
		double minY = center.getY() + 0.5 - half;
		double maxY = center.getY() + 0.5 + half;
		double minZ = center.getZ() + 0.5 - half;
		double maxZ = center.getZ() + 0.5 + half;

		double distToEdge = Math.min(
				Math.min(Math.abs(camera.x - minX), Math.abs(camera.x - maxX)),
				Math.min(Math.abs(camera.z - minZ), Math.abs(camera.z - maxZ))
		);
		boolean outside = camera.x < minX || camera.x > maxX || camera.z < minZ || camera.z > maxZ
				|| camera.y < minY || camera.y > maxY;

		float r, g, b;
		if (outside) {
			r = 1f; g = 0.2f; b = 0.2f; // vermelho
		} else if (distToEdge < NEAR_EDGE_THRESHOLD) {
			r = 1f; g = 0.85f; b = 0.1f; // amarelo
		} else {
			r = 0.25f; g = 0.55f; b = 1f; // azul
		}

		MatrixStack matrices = context.matrixStack();
		if (matrices == null) return;

		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);

		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

		// Provider "imediato" padrão do cliente: desenhamos e fazemos flush na hora,
		// já que estamos fora do ciclo normal de batching de entidades/blocos.
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

		drawBoxEdges(buffer, matrix, normalMatrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.9f);

		consumers.draw(RenderLayer.getLines());

		matrices.pop();
	}

	private static void drawBoxEdges(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix,
									  double minX, double minY, double minZ,
									  double maxX, double maxY, double maxZ,
									  float r, float g, float b, float a) {
		// 12 arestas de um paralelepípedo. O "normal" de cada aresta é só a
		// direção da própria linha - RenderLayer.getLines() exige um normal no
		// formato de vértice, mas ele não afeta a cor (sem iluminação nas linhas).
		double[][] edges = {
				{minX, minY, minZ, maxX, minY, minZ}, {maxX, minY, minZ, maxX, minY, maxZ},
				{maxX, minY, maxZ, minX, minY, maxZ}, {minX, minY, maxZ, minX, minY, minZ},
				{minX, maxY, minZ, maxX, maxY, minZ}, {maxX, maxY, minZ, maxX, maxY, maxZ},
				{maxX, maxY, maxZ, minX, maxY, maxZ}, {minX, maxY, maxZ, minX, maxY, minZ},
				{minX, minY, minZ, minX, maxY, minZ}, {maxX, minY, minZ, maxX, maxY, minZ},
				{maxX, minY, maxZ, maxX, maxY, maxZ}, {minX, minY, maxZ, minX, maxY, maxZ}
		};
		for (double[] e : edges) {
			float nx = (float) (e[3] - e[0]);
			float ny = (float) (e[4] - e[1]);
			float nz = (float) (e[5] - e[2]);
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 0) {
				nx /= len; ny /= len; nz /= len;
			}
			buffer.vertex(matrix, (float) e[0], (float) e[1], (float) e[2])
					.color(r, g, b, a).normal(normalMatrix, nx, ny, nz).next();
			buffer.vertex(matrix, (float) e[3], (float) e[4], (float) e[5])
					.color(r, g, b, a).normal(normalMatrix, nx, ny, nz).next();
		}
	}
}
