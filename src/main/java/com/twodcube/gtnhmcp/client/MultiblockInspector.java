package com.twodcube.gtnhmcp.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * GregTech-aware multiblock inspection, implemented entirely via {@link Reflect} so this class carries no compile-time
 * GregTech dependency (see {@link Reflect} for the rationale).
 *
 * <p>
 * The diagnosis is assembled from state GregTech synchronizes to the client: the structure-formed flag
 * ({@code mMachine}), the six maintenance flags, progress/efficiency counters, the controller's own energy buffer, and
 * the {@code getInfoData()} "scanner" lines. We surface raw values <em>and</em> a plain-language {@code summary}, plus
 * a
 * {@code freshnessNote}, because several of these fields refresh on the client most reliably while the controller GUI
 * is
 * open — being explicit prevents over-trusting a stale value.
 *
 * <p>
 * All methods assume they run on the client thread (see {@link ClientThreadExecutor}). The member names below are the
 * long-stable public API of GregTech's {@code MTEMultiBlockBase}/{@code IGregTechTileEntity}.
 */
final class MultiblockInspector {

    private static final String IGREGTECH_TILE = "gregtech.api.interfaces.tileentity.IGregTechTileEntity";
    private static final String META_TILE_ENTITY = "gregtech.api.metatileentity.MetaTileEntity";
    private static final String MULTIBLOCK_BASE = "gregtech.api.metatileentity.implementations.MTEMultiBlockBase";

    private MultiblockInspector() {}

    /**
     * Brief GregTech identity for any tile, used to annotate generic block/scan results. Best-effort: returns
     * {@code null} for non-GregTech tiles and also swallows reflective read failures (the explicit {@link #diagnose}
     * path surfaces those instead).
     *
     * @param te the tile entity to inspect.
     * @return a small identity map, or {@code null} if the tile is not a GregTech machine / could not be read.
     */
    static Map<String, Object> describeGregTech(TileEntity te) {
        if (!Reflect.isInstance(IGREGTECH_TILE, te)) {
            return null;
        }
        try {
            Object mte = Reflect.invoke(te, "getMetaTileEntity");
            boolean isMultiblock = mte != null && Reflect.isInstance(MULTIBLOCK_BASE, mte);
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("metaTileId", Integer.valueOf(Reflect.invokeInt(te, "getMetaTileID")));
            m.put("machineName", machineName(mte));
            m.put("isMultiblockController", Boolean.valueOf(isMultiblock));
            m.put("isActive", Boolean.valueOf(Reflect.invokeBoolean(te, "isActive")));
            if (isMultiblock) {
                m.put("structureFormed", Boolean.valueOf(Reflect.getBooleanField(mte, "mMachine")));
            }
            return m;
        } catch (McpToolException e) {
            return null;
        }
    }

    /**
     * Full diagnosis of a multiblock controller.
     *
     * @param te the tile entity expected to be a GregTech multiblock controller.
     * @return a structured diagnosis with raw state, a human-readable summary, and a freshness caveat.
     * @throws McpToolException if GregTech is absent, the tile is not a GregTech machine, has no machine attached, is a
     *                          non-multiblock GregTech machine, or its state cannot be read.
     */
    static Map<String, Object> diagnose(TileEntity te) throws McpToolException {
        if (!Reflect.isInstance(IGREGTECH_TILE, te)) {
            if (Reflect.optClass(IGREGTECH_TILE) == null) {
                throw new McpToolException("GregTech is not installed, so multiblock diagnosis is unavailable.");
            }
            throw new McpToolException(
                "That block is not a GregTech machine. Look at the multiblock controller block (the one with the GUI).");
        }
        Object mte = Reflect.invoke(te, "getMetaTileEntity");
        if (mte == null) {
            throw new McpToolException("This GregTech tile has no machine attached (it may be mid-removal).");
        }
        String machineName = machineName(mte);
        if (!Reflect.isInstance(MULTIBLOCK_BASE, mte)) {
            throw new McpToolException(
                "'" + machineName
                    + "' is a GregTech machine but not a multiblock controller. Diagnosis applies to multiblock "
                    + "controllers; use get_target to inspect this block instead.");
        }
        Map<String, Object> result = buildDiagnosis(te, mte, machineName);
        // buildDiagnosis is Minecraft-free (testable); enrich the structure errors with live world/item/lang lookups
        // here, where the client world and item registry are available.
        enrichStructureErrors(result, te);
        return result;
    }

    /**
     * Build the diagnosis map from a base tile (the {@code IGregTechTileEntity}) and its meta tile entity (the
     * {@code MTEMultiBlockBase}). Split out and package-private so it can be unit-tested against a fake
     * "GregTech-shaped"
     * object that exposes the same field/method names, with no GregTech on the classpath.
     *
     * @param baseTile    object exposing {@code isActive()}, {@code getMetaTileID()}, {@code getStoredEU()},
     *                    {@code getEUCapacity()}, {@code getOutputVoltage()}, {@code getInfoData()}.
     * @param mte         object exposing the {@code mMachine}/maintenance/progress/{@code mEfficiency} fields and
     *                    {@code getRepairStatus()}/{@code getIdealStatus()}.
     * @param machineName resolved human-readable controller name.
     * @return the diagnosis map.
     * @throws McpToolException if any expected member is missing or unreadable.
     */
    static Map<String, Object> buildDiagnosis(Object baseTile, Object mte, String machineName) throws McpToolException {
        boolean formed = Reflect.getBooleanField(mte, "mMachine");
        boolean active = Reflect.invokeBoolean(baseTile, "isActive");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("machineName", machineName);
        result.put("metaTileId", Integer.valueOf(Reflect.invokeInt(baseTile, "getMetaTileID")));
        result.put("isMultiblockController", Boolean.TRUE);
        result.put("structureFormed", Boolean.valueOf(formed));
        result.put("isActive", Boolean.valueOf(active));

        int repair = Reflect.invokeInt(mte, "getRepairStatus");
        int ideal = Reflect.invokeInt(mte, "getIdealStatus");
        List<Object> toolsNeeded = new ArrayList<Object>();
        addIfMissing(toolsNeeded, mte, "mWrench", "wrench");
        addIfMissing(toolsNeeded, mte, "mScrewdriver", "screwdriver");
        addIfMissing(toolsNeeded, mte, "mSoftMallet", "soft mallet");
        addIfMissing(toolsNeeded, mte, "mHardHammer", "hard hammer");
        addIfMissing(toolsNeeded, mte, "mSolderingTool", "soldering iron");
        addIfMissing(toolsNeeded, mte, "mCrowbar", "crowbar");
        Map<String, Object> maintenance = new LinkedHashMap<String, Object>();
        maintenance.put("problemCount", Integer.valueOf(ideal - repair));
        maintenance.put("toolsNeeded", toolsNeeded);
        result.put("maintenance", maintenance);

        int progressTicks = Reflect.getIntField(mte, "mProgresstime");
        int maxTicks = Reflect.getIntField(mte, "mMaxProgresstime");
        Map<String, Object> progress = new LinkedHashMap<String, Object>();
        progress.put("currentTicks", Integer.valueOf(progressTicks));
        progress.put("maxTicks", Integer.valueOf(maxTicks));
        progress.put("currentSeconds", Double.valueOf(round(progressTicks / 20.0)));
        progress.put("maxSeconds", Double.valueOf(round(maxTicks / 20.0)));
        result.put("progress", progress);
        result.put("efficiencyPercent", Double.valueOf(round(Reflect.getIntField(mte, "mEfficiency") / 100.0)));

        Map<String, Object> energy = new LinkedHashMap<String, Object>();
        energy.put("storedEU", Long.valueOf(Reflect.invokeLong(baseTile, "getStoredEU")));
        energy.put("capacityEU", Long.valueOf(Reflect.invokeLong(baseTile, "getEUCapacity")));
        energy.put("outputVoltage", Long.valueOf(Reflect.invokeLong(baseTile, "getOutputVoltage")));
        result.put("energy", energy);

        result.put("scannerInfo", toList(Reflect.invokeStringArray(baseTile, "getInfoData")));

        // The real reasons a structure won't form: GregTech populates this list during its structure check and syncs it
        // to the client (while the GUI is open). Reading it lets us report exact causes instead of guessing.
        List<Object> structureErrors = extractStructureErrors(mte);
        result.put("structureErrors", structureErrors);
        result.put("summary", buildSummary(formed, active, ideal - repair, toolsNeeded, structureErrors));
        result.put(
            "freshnessNote",
            "Structure status, the structure-error list, maintenance, progress and efficiency are synced to the client "
                + "most reliably while the controller's GUI is open. If the structure is not formed but no structure "
                + "errors are listed, open the controller GUI once and re-run to sync them. The energy figures are the "
                + "controller tile's own buffer; a multiblock's real energy lives in its energy hatches and may read as "
                + "0 here.");
        return result;
    }

    private static void addIfMissing(List<Object> toolsNeeded, Object mte, String field, String toolLabel)
        throws McpToolException {
        // A maintenance flag of `true` means that aspect is satisfied; `false` means that tool's repair is needed.
        if (!Reflect.getBooleanField(mte, field)) {
            toolsNeeded.add(toolLabel);
        }
    }

    private static List<Object> buildSummary(boolean formed, boolean active, int problems, List<Object> toolsNeeded,
        List<Object> structureErrors) {
        List<Object> summary = new ArrayList<Object>();
        if (!formed) {
            if (!structureErrors.isEmpty()) {
                summary.add(
                    "The structure is NOT formed. GregTech reports " + structureErrors.size()
                        + " structure problem(s):");
                for (Object error : structureErrors) {
                    if (error instanceof Map) {
                        Object reason = ((Map<?, ?>) error).get("reason");
                        if (reason != null) {
                            summary.add("• " + reason);
                        }
                    }
                }
            } else {
                summary.add(
                    "The structure is NOT formed, and no detailed structure errors are currently synced to the client. "
                        + "Open the controller's GUI once so the errors sync, then re-run. (Common causes: a wrong or "
                        + "misplaced block, a missing hatch or bus, an air gap, the wrong casing tier, or the controller "
                        + "facing the wrong way.)");
            }
        } else {
            summary.add("The structure IS formed.");
        }
        // Maintenance flags are only meaningful once the structure is formed (before that they read as defaults), so
        // only surface maintenance in the summary for a formed machine.
        if (formed && problems > 0) {
            summary.add(
                problems + " maintenance problem(s) detected. Apply these tools to the maintenance hatch: "
                    + join(toolsNeeded)
                    + ".");
        }
        if (formed && !active) {
            summary.add(
                "The structure is formed but the machine is idle. Check: a valid recipe for the inserted items/fluids, "
                    + "enough input items and fluids, enough power and the correct voltage tier in the energy hatches, "
                    + "the machine is enabled (not switched off by redstone or the GUI power button), and that output "
                    + "buses/tanks are not full.");
        }
        if (formed && active) {
            summary.add("The machine is currently running.");
        }
        return summary;
    }

    /**
     * Read GregTech's {@code structureErrors} list off the controller and turn each {@code StructureError} into a small
     * descriptive map. Reflection-only and Minecraft-free so it stays unit-testable; world/item enrichment happens
     * later
     * in {@link #enrichStructureErrors}.
     *
     * @param mte the {@code MTEMultiBlockBase} (or a fake exposing a {@code structureErrors} field) to read.
     * @return one map per structure error (each with at least {@code id} and {@code reason}); empty when none.
     * @throws McpToolException if the {@code structureErrors} field cannot be read.
     */
    private static List<Object> extractStructureErrors(Object mte) throws McpToolException {
        Object raw = Reflect.getObjectField(mte, "structureErrors");
        List<Object> out = new ArrayList<Object>();
        if (raw instanceof List) {
            for (Object error : (List<?>) raw) {
                if (error != null) {
                    out.add(describeStructureError(error));
                }
            }
        }
        return out;
    }

    /**
     * Describe one GregTech {@code StructureError} by its {@code StructureErrorId} plus the data its record carries
     * (read via reflective accessors). Produces a stable {@code id} and a human {@code reason}, degrading gracefully if
     * a
     * detail cannot be read.
     */
    private static Map<String, Object> describeStructureError(Object error) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        String id;
        try {
            id = String.valueOf(Reflect.invoke(error, "getId"));
        } catch (McpToolException e) {
            id = "UNKNOWN_STRUCTURE_ERROR";
        }
        m.put("id", id);
        try {
            if ("MISSING_MAINTENANCE".equals(id)) {
                m.put("reason", "Missing maintenance hatch.");
            } else if ("MISSING_MUFFLER".equals(id)) {
                m.put("reason", "Missing muffler hatch.");
            } else if ("UNNEEDED_MUFFLER".equals(id)) {
                m.put("reason", "Has a muffler hatch, but this machine doesn't need one.");
            } else if ("BLOCK_NOT_LOADED".equals(id)) {
                m.put("reason", "Part of the structure is in an unloaded chunk — move closer and recheck.");
            } else if ("MISSING_STRUCTURE_WRAPPER_CASINGS".equals(id)) {
                m.put("reason", "Missing required structure casings.");
            } else if ("MISSING_OUTPUT_HATCH_DT".equals(id)) {
                int layer = Reflect.invokeInt(error, "layer");
                m.put("layer", Integer.valueOf(layer));
                m.put("reason", "Missing an output hatch on distillation-tower layer " + layer + ".");
            } else if ("TOO_FEW_CASINGS".equals(id)) {
                int current = Reflect.invokeInt(error, "current");
                int required = Reflect.invokeInt(error, "required");
                m.put("current", Integer.valueOf(current));
                m.put("required", Integer.valueOf(required));
                m.put("reason", "Too few casings: have " + current + ", need " + required + ".");
            } else if ("TOO_MANY_HATCHES".equals(id)) {
                m.put("itemId", Integer.valueOf(Reflect.invokeInt(error, "itemId")));
                m.put("itemMeta", Integer.valueOf(Reflect.invokeInt(error, "itemMeta")));
                int max = Reflect.invokeInt(error, "max");
                m.put("max", Integer.valueOf(max));
                m.put("reason", "Too many of a hatch type (maximum " + max + ").");
            } else if ("MISSING_HATCH".equals(id)) {
                m.put("itemId", Integer.valueOf(Reflect.invokeInt(error, "itemId")));
                m.put("itemMeta", Integer.valueOf(Reflect.invokeInt(error, "itemMeta")));
                m.put("reason", "Missing a required hatch or bus.");
            } else if ("WRONG_BLOCK".equals(id)) {
                int x = Reflect.invokeInt(error, "x");
                int y = Reflect.invokeInt(error, "y");
                int z = Reflect.invokeInt(error, "z");
                m.put("position", intList(x, y, z));
                m.put("reason", "Wrong block at " + x + ", " + y + ", " + z + ".");
            } else if ("SIMPLE_STRUCTURE_ERROR".equals(id)) {
                String langKey = Reflect.invokeString(error, "langKey");
                m.put("langKey", langKey);
                m.put("reason", langKey);
            } else {
                m.put("reason", "Structure error: " + id);
            }
        } catch (McpToolException e) {
            m.put("reason", "Structure error: " + id + " (could not read details: " + e.getMessage() + ")");
        }
        return m;
    }

    /**
     * Best-effort enrichment of the structure-error maps using the live client world and item registry: resolves the
     * actual block at a {@code WRONG_BLOCK} coordinate, the item name of a missing/excess hatch, and the localized text
     * of a {@code SIMPLE_STRUCTURE_ERROR}. Any failure leaves the base {@code reason} untouched.
     *
     * @param result the diagnosis map (its {@code structureErrors} entry is mutated in place).
     * @param te     the controller tile, used to reach the world.
     */
    private static void enrichStructureErrors(Map<String, Object> result, TileEntity te) {
        Object raw = result.get("structureErrors");
        if (!(raw instanceof List)) {
            return;
        }
        World world = te.getWorldObj();
        for (Object entry : (List<?>) raw) {
            if (entry instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> error = (Map<String, Object>) entry;
                    enrichOneError(error, world);
                } catch (RuntimeException ignored) {
                    // Enrichment is optional; the base reason already describes the problem.
                }
            }
        }
    }

    private static void enrichOneError(Map<String, Object> error, World world) {
        String id = String.valueOf(error.get("id"));
        if ("WRONG_BLOCK".equals(id) && world != null && error.get("position") instanceof List) {
            List<?> pos = (List<?>) error.get("position");
            int x = ((Number) pos.get(0)).intValue();
            int y = ((Number) pos.get(1)).intValue();
            int z = ((Number) pos.get(2)).intValue();
            Block block = world.getBlock(x, y, z);
            String name = block == null ? "air" : block.getLocalizedName();
            error.put("currentBlock", name);
            error.put("currentBlockMetadata", Integer.valueOf(world.getBlockMetadata(x, y, z)));
            GameRegistry.UniqueIdentifier uid = block == null ? null : GameRegistry.findUniqueIdentifierFor(block);
            if (uid != null) {
                error.put("currentBlockId", uid.modId + ":" + uid.name);
            }
            error.put("reason", "Wrong block at " + x + ", " + y + ", " + z + " (currently: " + name + ").");
        } else if (("MISSING_HATCH".equals(id) || "TOO_MANY_HATCHES".equals(id))
            && error.get("itemId") instanceof Number) {
                Item item = Item.getItemById(((Number) error.get("itemId")).intValue());
                if (item != null) {
                    int meta = error.get("itemMeta") instanceof Number ? ((Number) error.get("itemMeta")).intValue()
                        : 0;
                    String name = new ItemStack(item, 1, meta).getDisplayName();
                    error.put("hatchName", name);
                    if ("MISSING_HATCH".equals(id)) {
                        error.put("reason", "Missing a required hatch or bus: " + name + ".");
                    } else {
                        error.put("reason", "Too many '" + name + "' (maximum " + error.get("max") + ").");
                    }
                }
            } else if ("SIMPLE_STRUCTURE_ERROR".equals(id) && error.get("langKey") instanceof String) {
                String key = (String) error.get("langKey");
                String translated = StatCollector.translateToLocal(key);
                if (translated != null && !translated.isEmpty() && !translated.equals(key)) {
                    error.put("reason", translated);
                }
            }
    }

    private static List<Object> intList(int x, int y, int z) {
        List<Object> list = new ArrayList<Object>(3);
        list.add(Integer.valueOf(x));
        list.add(Integer.valueOf(y));
        list.add(Integer.valueOf(z));
        return list;
    }

    private static String machineName(Object mte) throws McpToolException {
        if (mte == null) {
            return null;
        }
        if (Reflect.isInstance(META_TILE_ENTITY, mte)) {
            return Reflect.invokeString(mte, "getLocalName");
        }
        return mte.getClass()
            .getSimpleName();
    }

    private static List<Object> toList(String[] lines) {
        List<Object> list = new ArrayList<Object>();
        if (lines != null) {
            for (String line : lines) {
                list.add(line);
            }
        }
        return list;
    }

    private static String join(List<Object> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
