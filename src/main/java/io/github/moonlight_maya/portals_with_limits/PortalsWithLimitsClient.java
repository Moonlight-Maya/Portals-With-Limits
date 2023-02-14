package io.github.moonlight_maya.portals_with_limits;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

public class PortalsWithLimitsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(PortalsWithLimits.PORTAL_BLOCK_ENTITY, PortalBlockEntityRenderer::new);
    }
}
