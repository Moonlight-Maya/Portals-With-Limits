package io.github.moonlight_maya.portals_with_limits;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;

public class PortalBlockEntityRenderer implements BlockEntityRenderer<PortalBlockEntity> {

    public PortalBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    public void render(PortalBlockEntity portalBlockEntity, float f, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, int j) {
        Matrix4f matrix4f = matrixStack.peek().getPositionMatrix();
        this.renderSides(portalBlockEntity, matrix4f, vertexConsumerProvider.getBuffer(RenderLayer.getEndPortal()));
    }

    private void renderSides(PortalBlockEntity entity, Matrix4f matrix, VertexConsumer vertexConsumer) {
        float depth = 0.25f;
        if (entity.xAxis) {
            vertexConsumer.vertex(matrix, 0.5f-depth, 0, 0).next();
            vertexConsumer.vertex(matrix, 0.5f-depth, 0, 1).next();
            vertexConsumer.vertex(matrix, 0.5f-depth, 1, 1).next();
            vertexConsumer.vertex(matrix, 0.5f-depth, 1, 0).next();

            vertexConsumer.vertex(matrix, 0.5f+depth, 1, 0).next();
            vertexConsumer.vertex(matrix, 0.5f+depth, 1, 1).next();
            vertexConsumer.vertex(matrix, 0.5f+depth, 0, 1).next();
            vertexConsumer.vertex(matrix, 0.5f+depth, 0, 0).next();
        } else {
            vertexConsumer.vertex(matrix, 0, 0, 0.5f+depth).next();
            vertexConsumer.vertex(matrix, 1, 0, 0.5f+depth).next();
            vertexConsumer.vertex(matrix, 1, 1, 0.5f+depth).next();
            vertexConsumer.vertex(matrix, 0, 1, 0.5f+depth).next();

            vertexConsumer.vertex(matrix, 0, 1, 0.5f-depth).next();
            vertexConsumer.vertex(matrix, 1, 1, 0.5f-depth).next();
            vertexConsumer.vertex(matrix, 1, 0, 0.5f-depth).next();
            vertexConsumer.vertex(matrix, 0, 0, 0.5f-depth).next();
        }
    }
}
