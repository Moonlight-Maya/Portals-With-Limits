package io.github.moonlight_maya.portals_with_limits;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

public class PortalFiller {

    public final ServerWorld world;
    public final BlockPos rootPos;
    public final boolean xAxis;
    public final ServerPlayerEntity toNotify;

    public static final int MAX_PER_TICK = 10;

    private final Queue<BlockPos> toFillOrdered = new LinkedList<>();
    private final HashSet<BlockPos> toFill = new HashSet<>();
    private boolean failed = false;
    private boolean completed = false;
    private final BlockState onCorrectAxis;
    private int remainingThisCycle;

    public PortalFiller(ServerWorld world, BlockPos rootPos, boolean xAxis, ServerPlayerEntity toNotify) {
        this.world = world;
        this.rootPos = rootPos;
        this.xAxis = xAxis;
        this.toNotify = toNotify;
        onCorrectAxis = xAxis ?
                PortalsWithLimits.PORTAL_BLOCK.getDefaultState() :
                PortalsWithLimits.PORTAL_BLOCK.getDefaultState().with(PortalBlock.AXIS, Direction.Axis.Z);
        toFill.add(rootPos);
        toFillOrdered.add(rootPos);
    }

    public void tick() {
        if (failed) {
            world.setBlockState(rootPos, Blocks.AIR.getDefaultState());
            if (toNotify != null)
                toNotify.sendMessage(new TranslatableText("portals_with_limits.too_big"), true);
            completed = true;
            return;
        }
        //Calculate how many to do this tick

        if (remainingThisCycle == 0) {
            remainingThisCycle = toFillOrdered.size();
        }
        int toDoThisTick = Math.min(remainingThisCycle, MAX_PER_TICK);
        remainingThisCycle -= toDoThisTick;

        //If there's nothing to do, then it's completed
        if (toDoThisTick == 0) {
            completed = true;
            return;
        }

        int xDiff = xAxis ? 0 : 1;
        int zDiff = xAxis ? 1 : 0;
        for (int i = 0; i < toDoThisTick; i++) {
            BlockPos newPos = toFillOrdered.poll();
            toFill.remove(newPos);
            BlockPos[] neighbors = new BlockPos[]{newPos.add(0, 1, 0), newPos.add(0, -1, 0), newPos.add(-xDiff, 0, -zDiff), newPos.add(xDiff, 0, zDiff)};
            int maxNeighborStrength = 0;
            for (BlockPos neighborPos : neighbors) {
                BlockState state = world.getBlockState(neighborPos);
                if (state.isOf(PortalsWithLimits.PORTAL_BLOCK)) {
                    maxNeighborStrength = Math.max(maxNeighborStrength, state.get(PortalBlock.STRENGTH));
                } else if (state.isAir() && !world.isOutOfHeightLimit(neighborPos)) {
                    //If the neighbor was not already added to queue, then add it to queue.
                    if (toFill.add(neighborPos))
                        toFillOrdered.add(neighborPos);
                }
            }
            //Check strength and see if we can fill this block or if we need to fail
            if (maxNeighborStrength == 0) {
                if (!newPos.equals(rootPos)) {
                    //If we have no portal neighbors and this block isn't the root, then the fill fails.
                    //Mark it as failed and return
                    failed = true;
                } else {
                    world.setBlockState(newPos, onCorrectAxis.with(PortalBlock.STRENGTH, PortalBlock.MAX_STRENGTH));
                }
                return;
            }
            world.setBlockState(newPos, onCorrectAxis.with(PortalBlock.STRENGTH, maxNeighborStrength - 1));
        }
    }

    public boolean isCompleted() {
        return completed;
    }

    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList xs = new NbtList(), ys = new NbtList(), zs = new NbtList();
        for (BlockPos pos : toFillOrdered) {
            xs.add(NbtInt.of(pos.getX()));
            ys.add(NbtInt.of(pos.getY()));
            zs.add(NbtInt.of(pos.getZ()));
        }
        tag.put("xs", xs);
        tag.put("ys", ys);
        tag.put("zs", zs);
        tag.putBoolean("xAxis", xAxis);
        tag.putInt("rootX", rootPos.getX());
        tag.putInt("rootY", rootPos.getY());
        tag.putInt("rootZ", rootPos.getZ());
        return tag;
    }

    public static PortalFiller fromNbt(ServerWorld world, NbtCompound tag) {
        BlockPos root = new BlockPos(tag.getInt("rootX"), tag.getInt("rootY"), tag.getInt("rootZ"));
        boolean xAxis = tag.getBoolean("xAxis");
        PortalFiller filler = new PortalFiller(world, root, xAxis, null);
        NbtList xs = tag.getList("xs", NbtElement.INT_TYPE);
        NbtList ys = tag.getList("ys", NbtElement.INT_TYPE);
        NbtList zs = tag.getList("zs", NbtElement.INT_TYPE);
        for (int i = 0; i < xs.size(); i++) {
            BlockPos pos = new BlockPos(xs.getInt(i), ys.getInt(i), zs.getInt(i));
            filler.toFillOrdered.add(pos);
            filler.toFill.add(pos);
        }
        return filler;
    }


}
