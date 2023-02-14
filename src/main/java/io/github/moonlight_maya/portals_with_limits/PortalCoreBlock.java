package io.github.moonlight_maya.portals_with_limits;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PortalCoreBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

    public PortalCoreBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(AXIS, Direction.Axis.X));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (world instanceof ServerWorld serverWorld) {
            PortalsWithLimits.placeCore(serverWorld, pos, state);
        }
        super.onBlockAdded(state, world, pos, oldState, notify);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (world instanceof ServerWorld serverWorld) {
            PortalsWithLimits.removeCore(serverWorld, pos);
            if (world.getBlockState(pos.add(0, 2, 0)).isOf(PortalsWithLimits.PORTAL_BLOCK))
                world.setBlockState(pos.add(0, 2, 0), Blocks.AIR.getDefaultState());
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(AXIS, ctx.getPlayerFacing().getAxis());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld) {
            if (player instanceof ServerPlayerEntity spe) {
                PortalsWithLimits.CoreData coreData = PortalsWithLimits.PORTAL_CORES.get(serverWorld).get(pos);
                if (coreData == null) {
                    //Internal error occurred, ask them to report this
                    spe.sendMessage(new TranslatableText("portals_with_limits.no_core"), false);
                    return ActionResult.CONSUME;
                }
                BlockState twoAbove = world.getBlockState(pos.add(0, 2, 0));
                if (!twoAbove.isAir()) {
                    spe.sendMessage(new TranslatableText("portals_with_limits.blocked"), true);
                    return ActionResult.CONSUME;
                }
                //Finally, begin filling
                PortalsWithLimits.beginFillingPortal(serverWorld, pos.add(0, 2, 0), state.get(AXIS), spe);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.CONSUME;
    }
}
