package io.github.moonlight_maya.portals_with_limits.mixin;

import io.github.moonlight_maya.portals_with_limits.EntityAccess;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityAccess {

    @Shadow public abstract Box getBoundingBox();

    //If null, the last portal destination is unknown or doesn't matter
    @Unique
    @Nullable
    private Double portals_with_limits$lastPortalDestination;
    @Unique
    @Nullable
    private Boolean portals_with_limits$lastPortalWasXAxis;

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tickNetherPortal()V", shift = At.Shift.AFTER))
    public void tickPortalsWithLimits(CallbackInfo ci) {
        if (portals_with_limits$lastPortalDestination != null && portals_with_limits$lastPortalWasXAxis != null) {
            Direction.Axis axis = portals_with_limits$lastPortalWasXAxis ? Direction.Axis.X : Direction.Axis.Z;
            if (Math.min(
                    Math.abs(getBoundingBox().getMin(axis) - portals_with_limits$lastPortalDestination),
                    Math.abs(getBoundingBox().getMax(axis) - portals_with_limits$lastPortalDestination)
            ) > 0.5) {
                portals_with_limits$lastPortalDestination = null;
                portals_with_limits$lastPortalWasXAxis = null;
            }
        }
    }

    @Override
    public boolean canUsePortal() {
        return portals_with_limits$lastPortalDestination == null;
    }

    @Override
    public void setUsedPortal(BlockPos destination, boolean xAxis) {
        portals_with_limits$lastPortalWasXAxis = xAxis;
        portals_with_limits$lastPortalDestination = (xAxis ? destination.getX() : destination.getZ()) + 0.5;
    }
}
