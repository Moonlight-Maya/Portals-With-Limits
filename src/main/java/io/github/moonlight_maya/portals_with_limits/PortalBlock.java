package io.github.moonlight_maya.portals_with_limits;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class PortalBlock extends BlockWithEntity {

    public static final int MAX_STRENGTH = 47;
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;
    public static final IntProperty STRENGTH = IntProperty.of("portals_with_limits_strength", 0, MAX_STRENGTH);

    protected static final VoxelShape X_SHAPE = Block.createCuboidShape(7.5, 0, 0, 8.5, 16, 16);
    protected static final VoxelShape Z_SHAPE = Block.createCuboidShape(0, 0, 7.5, 16, 16, 8.5);

    public PortalBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(AXIS, Direction.Axis.X).with(STRENGTH, MAX_STRENGTH));
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient) {
            super.onEntityCollision(state, world, pos, entity);
            return;
        }
        if (world instanceof ServerWorld serverWorld) {
            if (((EntityAccess) entity).canUsePortal()) {
                BlockPos rootPos = findRoot(serverWorld, pos);
                Vec3d entityToCenter = new Vec3d(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5).subtract(entity.getPos());
                boolean xAxis = state.get(AXIS) == Direction.Axis.X;
                double axisValue = xAxis ? entityToCenter.x : entityToCenter.z;
                PortalsWithLimits.CoreData coreData = PortalsWithLimits.PORTAL_CORES.get(serverWorld).get(rootPos);
                if (coreData != null) {
                    if (axisValue >= 0 && coreData.positiveTarget != null) {
                        Vec3d teleportPos = new Vec3d(coreData.positiveTarget.getX()+0.5+entityToCenter.x, coreData.positiveTarget.getY()+2, coreData.positiveTarget.getZ()+0.5+entityToCenter.z);
                        FabricDimensions.teleport(entity, serverWorld, new TeleportTarget(teleportPos, Vec3d.ZERO, entity.getYaw(), entity.getPitch()));
                        ((EntityAccess) entity).setUsedPortal(coreData.positiveTarget, xAxis);
                    } else if (axisValue < 0 && coreData.negativeTarget != null) {
                        Vec3d teleportPos = new Vec3d(coreData.negativeTarget.getX()+0.5+entityToCenter.x, coreData.negativeTarget.getY()+2, coreData.negativeTarget.getZ()+0.5+entityToCenter.z);
                        FabricDimensions.teleport(entity, serverWorld, new TeleportTarget(teleportPos, Vec3d.ZERO, entity.getYaw(), entity.getPitch()));
                        ((EntityAccess) entity).setUsedPortal(coreData.negativeTarget, xAxis);
                    }
                }
            }
        }
        super.onEntityCollision(state, world, pos, entity);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> switch (state.get(AXIS)) {
                case Z -> state.with(AXIS, Direction.Axis.X);
                case X -> state.with(AXIS, Direction.Axis.Z);
                default -> state;
            };
            default -> state;
        };
    }

    @Nullable
    private BlockPos findRoot(World world, BlockPos start) {
        boolean isX = world.getBlockState(start).get(AXIS) == Direction.Axis.X;
        int strength = world.getBlockState(start).get(STRENGTH);

        BlockPos.Mutable pos = new BlockPos.Mutable(start.getX(), start.getY(), start.getZ());
        int last = -1; //0 = down, 1 = up, 2 = left, 3 = right

        int xDiff = isX ? 0 : 1;
        int zDiff = isX ? 1 : 0;

        while (strength < MAX_STRENGTH) {
            //Always check y axis
            if (last != 0) { //If we didn't come from down, check down
                pos.move(0, -1, 0);
                BlockState newBlockState = world.getBlockState(pos);
                int newStrength = newBlockState.isOf(this) ? newBlockState.get(STRENGTH) : -1;
                if (newStrength == strength + 1) {
                    strength++;
                    last = 1;
                    continue;
                }
                pos.move(0, 1, 0);
            }
            if (last != 2) { //If we didn't come from left, check left
                pos.move(-xDiff, 0, -zDiff);
                BlockState newBlockState = world.getBlockState(pos);
                int newStrength = newBlockState.isOf(this) ? newBlockState.get(STRENGTH) : -1;
                if (newStrength == strength + 1) {
                    strength++;
                    last = 3;
                    continue;
                }
                pos.move(xDiff, 0, zDiff);
            }
            if (last != 3) { //If we didn't come from right, check right
                pos.move(xDiff, 0, zDiff);
                BlockState newBlockState = world.getBlockState(pos);
                int newStrength = newBlockState.isOf(this) ? newBlockState.get(STRENGTH) : -1;
                if (newStrength == strength + 1) {
                    strength++;
                    last = 2;
                    continue;
                }
                pos.move(-xDiff, 0, -zDiff);
            }
            if (last != 1) { //If we didn't come from up, check up
                pos.move(0, 1, 0);
                BlockState newBlockState = world.getBlockState(pos);
                int newStrength = newBlockState.isOf(this) ? newBlockState.get(STRENGTH) : -1;
                if (newStrength == strength + 1) {
                    strength++;
                    last = 0;
                    continue;
                }
                pos.move(0, -1, 0);
            }
            return null; //Failure
        }
        return pos.add(0, -2, 0);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        //If the update is irrelevant to the portal, ignore
        if (direction.getAxis() == state.get(AXIS))
            return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
        if (world.getBlockState(neighborPos).isAir()) {
            //If it was changed to air, schedule this to be destroyed
            world.createAndScheduleBlockTick(pos, this, 0);
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        super.scheduledTick(state, world, pos, random);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS).add(STRENGTH);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PortalBlockEntity(pos, state);
    }
}
