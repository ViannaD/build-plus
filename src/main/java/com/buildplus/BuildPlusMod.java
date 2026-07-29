package com.buildplus;

import com.buildplus.block.ModBlocks;
import com.buildplus.network.NetworkHandler;
import com.buildplus.session.BuildSessionManager;
import com.buildplus.session.SafetyEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build+ — voo temporário limitado a uma área configurável, pensado para
 * facilitar construções grandes no Survival sem transformar o jogo em Criativo
 * permanente. Veja README.md para detalhes do fluxo completo.
 */
public class BuildPlusMod implements ModInitializer {

	public static final String MOD_ID = "buildplus";
	public static final Logger LOGGER = LoggerFactory.getLogger("Build+");

	@Override
	public void onInitialize() {
		LOGGER.info("Inicializando Build+");

		ModBlocks.register();
		NetworkHandler.registerServerReceivers();
		SafetyEvents.register();

		ServerTickEvents.END_SERVER_TICK.register(server -> BuildSessionManager.getGlobal().tick(server));
	}
}
