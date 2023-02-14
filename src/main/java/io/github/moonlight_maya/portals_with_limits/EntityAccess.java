package io.github.moonlight_maya.portals_with_limits;

import net.minecraft.util.math.BlockPos;

//Quack quack
public interface EntityAccess {
    boolean canUsePortal();
    void setUsedPortal(BlockPos destination, boolean xAxis);
}
