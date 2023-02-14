package io.github.moonlight_maya.portals_with_limits;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class PortalBlockEntity extends BlockEntity {

    public final boolean xAxis;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(PortalsWithLimits.PORTAL_BLOCK_ENTITY, pos, state);
        xAxis = state.get(PortalBlock.AXIS) == Direction.Axis.X;
    }
}
