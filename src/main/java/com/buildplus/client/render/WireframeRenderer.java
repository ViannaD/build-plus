package com.buildplus.client.render;

import com.buildplus.client.gui.BuildingBlockScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Desenha o holograma (wireframe) da área de construção enquanto a GUI do
 * Building Block está aberta, com cor mudando conforme a distância da borda:
 * azul (longe), amarelo (perto) e vermelho (fora/na borda).
 *
 * Desenha com um Tessellator/BufferBuilder próprio e teste de profundidade
 * desligado, em vez de usar o VertexConsumerProvider.Immediate compartilhado
 * com entidades - assim o contorno não depende do momento em que esse buffer
 * compartilhado é esvaziado no frame e fica visível mesmo atrás de blocos.
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

		// Desenhamos com um Tessellator próprio (em vez de pegar emprestado o
		// VertexConsumerProvider.Immediate compartilhado com entidades/blocos).
		// Isso evita depender de quando esse buffer compartilhado é "flushado"
		// no frame e é o motivo pelo qual o holograma podia simplesmente não
		// aparecer antes. Também desligamos o teste de profundidade de propósito,
		// para o contorno da área ficar visível mesmo atrás de terreno/blocos -
		// como uma seleção do WorldEdit.
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.lineWidth(2.5f);

		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

		drawBoxEdges(buffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.9f);

		BufferRenderer.drawWithGlobalProgram(buffer.end());

		RenderSystem.lineWidth(1.0f);
		RenderSystem.enableDepthTest();
		RenderSystem.disableBlend();

		matrices.pop();
	}

	private static void drawBoxEdges(BufferBuilder buffer, Matrix4f matrix,
									  double minX, double minY, double minZ,
									  double maxX, double maxY, double maxZ,
									  float r, float g, float b, float a) {
		// 12 arestas de um paralelepípedo.
		double[][] edges = {
				{minX, minY, minZ, maxX, minY, minZ}, {maxX, minY, minZ, maxX, minY, maxZ},
				{maxX, minY, maxZ, minX, minY, maxZ}, {minX, minY, maxZ, minX, minY, minZ},
				{minX, maxY, minZ, maxX, maxY, minZ}, {maxX, maxY, minZ, maxX, maxY, maxZ},
				{maxX, maxY, maxZ, minX, maxY, maxZ}, {minX, maxY, maxZ, minX, maxY, minZ},
				{minX, minY, minZ, minX, maxY, minZ}, {maxX, minY, minZ, maxX, maxY, minZ},
				{maxX, minY, maxZ, maxX, maxY, maxZ}, {minX, minY, maxZ, minX, maxY, maxZ}
		};
		for (double[] e : edges) {
			buffer.vertex(matrix, (float) e[0], (float) e[1], (float) e[2]).color(r, g, b, a).next();
			buffer.vertex(matrix, (float) e[3], (float) e[4], (float) e[5]).color(r, g, b, a).next();
		}
	}
}
