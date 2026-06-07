package com.twodcube.gtnhmcp.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Read-only facade over the Minecraft client's world and player state.
 *
 * <p>
 * Every method here assumes it is executing <b>on the client thread</b> — callers must route invocations through
 * {@link ClientThreadExecutor}. The methods read game state and return it as the plain-map JSON model so the protocol
 * layer can serialize it without any Minecraft awareness. Nothing here mutates game state; this mod only ever observes.
 *
 * <p>
 * GregTech-specific inspection is delegated to {@link MultiblockInspector} and only invoked when GregTech is present
 * (checked once at construction). That keeps the GregTech classes off the verification path when the mod is installed
 * in
 * a non-GregTech instance, so the generic tools (target/player/inventory/scan) still work anywhere.
 */
public final class GameIntrospector {

    private final boolean gregTechLoaded;

    public GameIntrospector() {
        this.gregTechLoaded = Loader.isModLoaded("gregtech");
    }

    /** @return whether GregTech is installed, so callers can advertise whether multiblock diagnosis is available. */
    public boolean isGregTechLoaded() {
        return gregTechLoaded;
    }

    /**
     * Describe the block the player is currently looking at.
     *
     * @return a structured description of the targeted block (and any tile entity / GregTech machine on it).
     * @throws McpToolException if no world is loaded or the player is not pointing at a block.
     */
    public Map<String, Object> describeTarget() throws McpToolException {
        World world = requireWorld();
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            throw new McpToolException(
                "You are not looking at a block. Point your crosshair directly at the block (e.g. the multiblock "
                    + "controller) and try again.");
        }
        Map<String, Object> result = describeBlock(world, hit.blockX, hit.blockY, hit.blockZ);
        result.put("sideHit", Integer.valueOf(hit.sideHit));
        return result;
    }

    /**
     * Describe the player's current state plus a brief note of what they are looking at.
     *
     * @return a structured player snapshot.
     * @throws McpToolException if no player/world is present.
     */
    public Map<String, Object> describePlayer() throws McpToolException {
        World world = requireWorld();
        EntityPlayer player = requirePlayer();

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", player.getCommandSenderName());
        result.put("dimensionId", Integer.valueOf(world.provider.dimensionId));
        result.put("dimensionName", world.provider.getDimensionName());

        Map<String, Object> pos = new LinkedHashMap<String, Object>();
        pos.put("x", Double.valueOf(round(player.posX)));
        pos.put("y", Double.valueOf(round(player.posY)));
        pos.put("z", Double.valueOf(round(player.posZ)));
        result.put("position", pos);

        List<Object> block = new ArrayList<Object>();
        block.add(Integer.valueOf((int) Math.floor(player.posX)));
        block.add(Integer.valueOf((int) Math.floor(player.posY)));
        block.add(Integer.valueOf((int) Math.floor(player.posZ)));
        result.put("blockPosition", block);

        result.put("health", Double.valueOf(round(player.getHealth())));
        result.put("maxHealth", Double.valueOf(round(player.getMaxHealth())));
        result.put(
            "foodLevel",
            Integer.valueOf(
                player.getFoodStats()
                    .getFoodLevel()));
        result.put("heldItem", describeItem(player.getCurrentEquippedItem()));

        // A lightweight note of the target so the model gets context without a second call.
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            Map<String, Object> looking = new LinkedHashMap<String, Object>();
            looking.put("position", intList(hit.blockX, hit.blockY, hit.blockZ));
            Block b = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
            looking.put("displayName", safeBlockName(b));
            result.put("lookingAt", looking);
        } else {
            result.put("lookingAt", null);
        }
        return result;
    }

    /**
     * Describe the player's inventory (hotbar + main inventory + armor + held item).
     *
     * @return a structured inventory snapshot; empty slots are omitted.
     * @throws McpToolException if no player is present.
     */
    public Map<String, Object> describeInventory() throws McpToolException {
        EntityPlayer player = requirePlayer();
        ItemStack[] main = player.inventory.mainInventory;

        List<Object> mainItems = new ArrayList<Object>();
        for (int slot = 0; slot < main.length; slot++) {
            Map<String, Object> item = describeItem(main[slot]);
            if (item != null) {
                item.put("slot", Integer.valueOf(slot));
                mainItems.add(item);
            }
        }

        List<Object> armorItems = new ArrayList<Object>();
        ItemStack[] armor = player.inventory.armorInventory;
        for (int slot = 0; slot < armor.length; slot++) {
            Map<String, Object> item = describeItem(armor[slot]);
            if (item != null) {
                item.put("armorSlot", Integer.valueOf(slot));
                armorItems.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("heldItem", describeItem(player.getCurrentEquippedItem()));
        result.put("selectedHotbarSlot", Integer.valueOf(player.inventory.currentItem));
        result.put("mainInventory", mainItems);
        result.put("armor", armorItems);
        return result;
    }

    /**
     * Scan a cubic region for block types and tile entities — the workhorse for "what did I actually build" questions.
     *
     * @param radius     half-extent of the cube in blocks (1..16).
     * @param center     the cube center as [x, y, z], or {@code null} to center on the player's feet.
     * @param includeAir whether to count air blocks.
     * @param maxTiles   maximum number of tile entities to list before truncating.
     * @return aggregated block counts plus a (possibly truncated) list of tile entities.
     * @throws McpToolException if no world is loaded.
     */
    public Map<String, Object> scan(int radius, int[] center, boolean includeAir, int maxTiles)
        throws McpToolException {
        World world = requireWorld();
        int cx;
        int cy;
        int cz;
        if (center != null) {
            cx = center[0];
            cy = center[1];
            cz = center[2];
        } else {
            EntityPlayer player = requirePlayer();
            cx = (int) Math.floor(player.posX);
            cy = (int) Math.floor(player.posY);
            cz = (int) Math.floor(player.posZ);
        }

        // TreeMap keeps the counts output sorted for stable, readable results.
        TreeMap<String, Integer> counts = new TreeMap<String, Integer>();
        List<Object> tiles = new ArrayList<Object>();
        int totalNonAir = 0;
        boolean truncated = false;

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                if (y < 0 || y > 255) {
                    continue;
                }
                for (int z = cz - radius; z <= cz + radius; z++) {
                    Block block = world.getBlock(x, y, z);
                    boolean isAir = world.isAirBlock(x, y, z);
                    if (!isAir) {
                        totalNonAir++;
                    }
                    if (isAir && !includeAir) {
                        continue;
                    }
                    int meta = world.getBlockMetadata(x, y, z);
                    String key = blockCountKey(block, meta);
                    Integer prior = counts.get(key);
                    counts.put(key, Integer.valueOf(prior == null ? 1 : prior.intValue() + 1));

                    TileEntity te = world.getTileEntity(x, y, z);
                    if (te != null && tiles.size() < maxTiles) {
                        tiles.add(describeTileBrief(world, x, y, z, te));
                    } else if (te != null) {
                        truncated = true;
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("center", intList(cx, cy, cz));
        result.put("radius", Integer.valueOf(radius));
        result.put("dimensionId", Integer.valueOf(world.provider.dimensionId));
        result.put("totalNonAirBlocks", Integer.valueOf(totalNonAir));
        result.put("distinctBlockTypes", Integer.valueOf(counts.size()));
        result.put("blockCounts", counts);
        result.put("tileEntities", tiles);
        result.put("tileEntitiesTruncated", Boolean.valueOf(truncated));
        return result;
    }

    /**
     * Diagnose the GregTech multiblock controller at the given position (or at the looked-at block when none is given).
     *
     * @param coords explicit [x, y, z] of the controller, or {@code null} to use the looked-at block.
     * @return a structured diagnosis (delegated to {@link MultiblockInspector}).
     * @throws McpToolException if GregTech is absent, no controller is found, or no world/target is available.
     */
    public Map<String, Object> diagnoseMultiblock(int[] coords) throws McpToolException {
        if (!gregTechLoaded) {
            throw new McpToolException("GregTech is not installed, so multiblock diagnosis is unavailable.");
        }
        World world = requireWorld();
        int x;
        int y;
        int z;
        if (coords != null) {
            x = coords[0];
            y = coords[1];
            z = coords[2];
        } else {
            Minecraft mc = Minecraft.getMinecraft();
            MovingObjectPosition hit = mc.objectMouseOver;
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                throw new McpToolException(
                    "Provide the controller coordinates, or point your crosshair at the multiblock controller.");
            }
            x = hit.blockX;
            y = hit.blockY;
            z = hit.blockZ;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) {
            throw new McpToolException(
                "There is no tile entity at " + x + ", " + y + ", " + z + " — that is not a GregTech machine.");
        }
        Map<String, Object> diagnosis = MultiblockInspector.diagnose(te);
        diagnosis.put("position", intList(x, y, z));
        return diagnosis;
    }

    // ---- shared helpers -------------------------------------------------------------------------------------------

    private Map<String, Object> describeBlock(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("position", intList(x, y, z));
        result.put("registryName", blockRegistryName(block));
        result.put("displayName", safeBlockName(block));
        result.put("unlocalizedName", block.getUnlocalizedName());
        result.put("metadata", Integer.valueOf(world.getBlockMetadata(x, y, z)));
        result.put("isAir", Boolean.valueOf(world.isAirBlock(x, y, z)));

        TileEntity te = world.getTileEntity(x, y, z);
        if (te != null) {
            result.put(
                "tileEntityClass",
                te.getClass()
                    .getName());
            if (gregTechLoaded) {
                Map<String, Object> gt = MultiblockInspector.describeGregTech(te);
                if (gt != null) {
                    result.put("gregtech", gt);
                }
            }
        }
        return result;
    }

    private Map<String, Object> describeTileBrief(World world, int x, int y, int z, TileEntity te) {
        Map<String, Object> brief = new LinkedHashMap<String, Object>();
        brief.put("position", intList(x, y, z));
        brief.put("displayName", safeBlockName(world.getBlock(x, y, z)));
        brief.put(
            "class",
            te.getClass()
                .getName());
        if (gregTechLoaded) {
            Map<String, Object> gt = MultiblockInspector.describeGregTech(te);
            if (gt != null) {
                brief.put("gregtech", gt);
            }
        }
        return brief;
    }

    private static Map<String, Object> describeItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("displayName", safeDisplayName(stack));
        item.put("registryName", itemRegistryName(stack.getItem()));
        item.put("count", Integer.valueOf(stack.stackSize));
        item.put("metadata", Integer.valueOf(stack.getItemDamage()));
        return item;
    }

    private static String blockCountKey(Block block, int meta) {
        String registry = blockRegistryName(block);
        String name = safeBlockName(block);
        return name + " [" + (registry == null ? "?" : registry) + ":" + meta + "]";
    }

    private static String blockRegistryName(Block block) {
        if (block == null) {
            return null;
        }
        GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
        return uid == null ? null : uid.modId + ":" + uid.name;
    }

    private static String itemRegistryName(Item item) {
        if (item == null) {
            return null;
        }
        GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(item);
        return uid == null ? null : uid.modId + ":" + uid.name;
    }

    private static String safeBlockName(Block block) {
        if (block == null) {
            return "air";
        }
        try {
            return block.getLocalizedName();
        } catch (RuntimeException e) {
            return block.getUnlocalizedName();
        }
    }

    private static String safeDisplayName(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (RuntimeException e) {
            return stack.getUnlocalizedName();
        }
    }

    private World requireWorld() throws McpToolException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            throw new McpToolException("No world is loaded — you are at the main menu. Load a world and try again.");
        }
        return mc.theWorld;
    }

    private EntityPlayer requirePlayer() throws McpToolException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            throw new McpToolException("There is no player in the world yet.");
        }
        return mc.thePlayer;
    }

    private static List<Object> intList(int x, int y, int z) {
        List<Object> list = new ArrayList<Object>(3);
        list.add(Integer.valueOf(x));
        list.add(Integer.valueOf(y));
        list.add(Integer.valueOf(z));
        return list;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
