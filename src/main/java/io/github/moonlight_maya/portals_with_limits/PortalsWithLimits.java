package io.github.moonlight_maya.portals_with_limits;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PortalsWithLimits implements ModInitializer {

    public static final String MODID = "portals_with_limits";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final PortalCoreBlock PORTAL_CORE_BLOCK = new PortalCoreBlock(
            FabricBlockSettings.of(Material.METAL)
                    .strength(4.0f)
    );

    public static final PortalBlock PORTAL_BLOCK = new PortalBlock(
            FabricBlockSettings.of(Material.PORTAL)
                    .strength(-1.0f, 3600000f)
                    .luminance(15)
                    .dropsNothing()
    );

    public static final BlockEntityType<PortalBlockEntity> PORTAL_BLOCK_ENTITY = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new Identifier(MODID, "portal_block_entity"),
            FabricBlockEntityTypeBuilder.create(PortalBlockEntity::new, PORTAL_BLOCK).build()
    );

    private static Map<ServerWorld, Map<BlockPos, PortalFiller>> PORTAL_FILLERS;

    public static Map<ServerWorld, Map<BlockPos, CoreData>> PORTAL_CORES;
    private static Map<ServerWorld, PersistentState> PERSISTENT_STATES;

    private static final int DISTANCE_STEP = 16;
    private static final int DISTANCE_VARIANCE = 3;
    private static final int MAX_STEPS = 5;

    @Override
    public void onInitialize() {
        LOGGER.info("Hello from Portals with Limits!");

        Registry.register(Registry.BLOCK, new Identifier(MODID, "portal_core"), PORTAL_CORE_BLOCK);
        Registry.register(Registry.BLOCK, new Identifier(MODID, "portal"), PORTAL_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(MODID, "portal_core"), new BlockItem(PORTAL_CORE_BLOCK, new FabricItemSettings()));

        final String ID = "portals_with_limits_cores";
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PORTAL_CORES = new HashMap<>();
            PERSISTENT_STATES = new HashMap<>();
            PORTAL_FILLERS = new HashMap<>();
            for (ServerWorld world : server.getWorlds()) {
                Map<BlockPos, CoreData> cores = new HashMap<>();
                PORTAL_CORES.put(world, cores);

                //This is an object that saves the info, just write the stuff down
                PersistentState savingState = new PersistentState() {
                    @Override
                    public NbtCompound writeNbt(NbtCompound tag) {
                        return saveNbt(world, tag);
                    }
                };

                PERSISTENT_STATES.put(world, world.getPersistentStateManager().getOrCreate(
                        //Called when reading NBT from file
                        nbt -> {
                            loadNbt(world, nbt);
                            return savingState;
                        },
                        //Called when the file does not exist
                        () -> savingState,
                        ID
                ));
            }
        });

        ServerTickEvents.START_WORLD_TICK.register(PortalsWithLimits::tickPortalFillers);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (Map.Entry<ServerWorld, PersistentState> stateEntry : PERSISTENT_STATES.entrySet()) {
                stateEntry.getValue().markDirty();
                stateEntry.getKey().getPersistentStateManager().save();
            }
            PORTAL_FILLERS = null;
            PERSISTENT_STATES = null;
            PORTAL_CORES = null;
        });

    }

    public static void beginFillingPortal(ServerWorld world, BlockPos pos, Direction.Axis axis, ServerPlayerEntity user) {
        boolean xAxis = axis == Direction.Axis.X;
        Map<BlockPos, PortalFiller> map = PORTAL_FILLERS.computeIfAbsent(world, x -> new HashMap<>());
        if (map.containsKey(pos)) {
            user.sendMessage(new TranslatableText("portals_with_limits.already_in_progress"), true);
            return;
        }
        map.put(pos, new PortalFiller(world, pos, xAxis, user));
    }

    private static void tickPortalFillers(ServerWorld world) {
        Map<BlockPos, PortalFiller> map = PORTAL_FILLERS.get(world);
        if (map == null) return;
        //Remove completed portal fillers
        map.values().removeIf(PortalFiller::isCompleted);
        for (PortalFiller filler : map.values()) {
            filler.tick();
        }
    }

    private static NbtCompound saveNbt(ServerWorld world, NbtCompound tag) {
        NbtList xs = new NbtList(), ys = new NbtList(), zs = new NbtList(), xAxises = new NbtList();
        for (CoreData coreData : PORTAL_CORES.getOrDefault(world, Collections.emptyMap()).values()) {
            xs.add(NbtInt.of(coreData.pos.getX()));
            ys.add(NbtInt.of(coreData.pos.getY()));
            zs.add(NbtInt.of(coreData.pos.getZ()));
            xAxises.add(NbtInt.of(coreData.xAxis ? 1 : 0));
        }
        tag.put("xs", xs);
        tag.put("ys", ys);
        tag.put("zs", zs);
        tag.put("xAxis", xAxises);

        NbtList fillers = new NbtList();
        for (PortalFiller filler : PORTAL_FILLERS.getOrDefault(world, Collections.emptyMap()).values()) {
            fillers.add(filler.writeNbt(new NbtCompound()));
        }
        tag.put("fillers", fillers);
        return tag;
    }

    private static void loadNbt(ServerWorld world, NbtCompound tag) {
        Map<BlockPos, CoreData> positions = PORTAL_CORES.computeIfAbsent(world, x -> new HashMap<>());
        positions.clear();
        NbtList xs = tag.getList("xs", NbtElement.INT_TYPE);
        NbtList ys = tag.getList("ys", NbtElement.INT_TYPE);
        NbtList zs = tag.getList("zs", NbtElement.INT_TYPE);
        NbtList xAxises = tag.getList("xAxis", NbtElement.INT_TYPE);
        for (int i = 0; i < xs.size(); i++)
            addCoreData(world, new CoreData(new BlockPos(xs.getInt(i), ys.getInt(i), zs.getInt(i)), xAxises.getInt(i) > 0));

        Map<BlockPos, PortalFiller> portalFillers = PORTAL_FILLERS.computeIfAbsent(world, x -> new HashMap<>());
        portalFillers.clear();
        NbtList fillerList = tag.getList("fillers", NbtElement.COMPOUND_TYPE);
        for (NbtElement elem : fillerList) {
            PortalFiller loadedFiller = PortalFiller.fromNbt(world, (NbtCompound) elem);
            portalFillers.put(loadedFiller.rootPos, loadedFiller);
        }
    }

    private static void addCoreData(ServerWorld world, CoreData newCore) {
        Map<BlockPos, CoreData> set = PORTAL_CORES.computeIfAbsent(world, x -> new HashMap<>());
        for (CoreData existingCore : set.values()) {
            maybeUpdate(existingCore, newCore);
        }
        set.put(newCore.pos, newCore);
    }

    //Might change one of "from"'s target to become "to", and vice versa
    private static void maybeUpdate(CoreData a, CoreData b) {
        //If they're on different y levels, they can never connect
        if (a.pos.getY() != b.pos.getY())
            return;
        //If they're on different axes, they can never connect
        if (a.xAxis != b.xAxis)
            return;

        if (a.xAxis) {
            //We know we're working on the x axis here, ensure z values are the same.
            if (a.pos.getZ() != b.pos.getZ())
                return;
            //Ensure distance is valid
            int distance = Math.abs(a.pos.getX() - b.pos.getX());

            int nearestStep = (distance + (DISTANCE_STEP / 2)) / DISTANCE_STEP * DISTANCE_STEP; //rounds to nearest multiple of DISTANCE_STEP
            int distanceToNearestStep = Math.abs(distance - nearestStep);
            int numSteps = nearestStep / DISTANCE_STEP;
            //In any of these invalid scenarios, cancel. These two will not connect
            if (distanceToNearestStep > DISTANCE_VARIANCE || numSteps < 1 || numSteps > MAX_STEPS)
                return;

            //Compare the distance to the current target's distance, and see if we update.
            CoreData low, high;
            if (a.pos.getX() > b.pos.getX()) {
                low = b;
                high = a;
            } else {
                low = a;
                high = b;
            }
            if (low.positiveTarget == null) {
                low.positiveTarget = high.pos;
            } else {
                int previousDistance = low.positiveTarget.getX() - low.pos.getX();
                if (distance < previousDistance) {
                    low.positiveTarget = high.pos;
                }
            }
            if (high.negativeTarget == null) {
                high.negativeTarget = low.pos;
            } else {
                int previousDistance = high.pos.getX() - high.negativeTarget.getX();
                if (distance < previousDistance) {
                    high.negativeTarget = low.pos;
                }
            }
        } else {
            //Working on the z axis, ensure x values are the same
            if (a.pos.getX() != b.pos.getX())
                return;
            //Ensure distance is valid
            int distance = Math.abs(a.pos.getZ() - b.pos.getZ());

            int nearestStep = (distance + (DISTANCE_STEP / 2)) / DISTANCE_STEP * DISTANCE_STEP; //rounds to nearest multiple of DISTANCE_STEP
            int distanceToNearestStep = Math.abs(distance - nearestStep);
            int numSteps = nearestStep / DISTANCE_STEP;
            //In any of these invalid scenarios, cancel. These two will not connect
            if (distanceToNearestStep > DISTANCE_VARIANCE || numSteps < 1 || numSteps > MAX_STEPS)
                return;

            //Compare the distance to the current target's distance, and see if we update.
            CoreData low, high;
            if (a.pos.getZ() > b.pos.getZ()) {
                low = b;
                high = a;
            } else {
                low = a;
                high = b;
            }
            if (low.positiveTarget == null) {
                low.positiveTarget = high.pos;
            } else {
                int previousDistance = low.positiveTarget.getZ() - low.pos.getZ();
                if (distance < previousDistance) {
                    low.positiveTarget = high.pos;
                }
            }
            if (high.negativeTarget == null) {
                high.negativeTarget = low.pos;
            } else {
                int previousDistance = high.pos.getZ() - high.negativeTarget.getZ();
                if (distance < previousDistance) {
                    high.negativeTarget = low.pos;
                }
            }
        }
    }

    public static void placeCore(ServerWorld world, BlockPos position, BlockState state) {
        boolean xAxis = state.get(Properties.HORIZONTAL_AXIS) == Direction.Axis.X;
        addCoreData(world, new CoreData(position, xAxis));
    }

    public static void removeCore(ServerWorld world, BlockPos position) {
        if (PORTAL_CORES.containsKey(world)) {
            PORTAL_CORES.get(world).remove(position);
        }

        for (CoreData data : PORTAL_CORES.getOrDefault(world, Collections.emptyMap()).values()) {
            //Update all cores which had this one as a target destination
            if (position.equals(data.positiveTarget) || position.equals(data.negativeTarget)) {
                if (position.equals(data.positiveTarget))
                    data.positiveTarget = null;
                else
                    data.negativeTarget = null;
                for (CoreData data1: PORTAL_CORES.getOrDefault(world, Collections.emptyMap()).values()) {
                    if (data1 != data)
                        maybeUpdate(data, data1);
                }
            }
        }
    }

    public static class CoreData {
        public final BlockPos pos;
        public BlockPos positiveTarget;
        public BlockPos negativeTarget;
        public final boolean xAxis;
        public CoreData(BlockPos pos, boolean xAxis) {
            this.pos = pos;
            this.xAxis = xAxis;
        }

        public String toString() {
            return "(pos: "+ pos + ", posTarget: " + positiveTarget + ", negTarget: " + negativeTarget + ", axis: " + (xAxis ? "x)" : "z)");
        }
    }

}
