package com.twodcube.gtnhmcp.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;

import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;

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
            m.put("frontFacing", Reflect.invokeString(te, "getFrontFacing"));
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
        return buildDiagnosis(te, mte, machineName);
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
        // The controller's front facing; the structure is checked relative to it, so a wrong facing makes an otherwise
        // correctly-built shape fail.
        result.put("frontFacing", Reflect.invokeString(baseTile, "getFrontFacing"));

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
        result.put("summary", buildSummary(formed, active, ideal - repair, toolsNeeded));
        result.put(
            "freshnessNote",
            "Structure, maintenance, progress and efficiency are synced to the client most reliably while the "
                + "controller's GUI is open. If a value looks stale, open the controller GUI once and re-run. The energy "
                + "figures are the controller tile's own buffer; a multiblock's real energy lives in its energy hatches "
                + "and may read as 0 here.");
        return result;
    }

    private static void addIfMissing(List<Object> toolsNeeded, Object mte, String field, String toolLabel)
        throws McpToolException {
        // A maintenance flag of `true` means that aspect is satisfied; `false` means that tool's repair is needed.
        if (!Reflect.getBooleanField(mte, field)) {
            toolsNeeded.add(toolLabel);
        }
    }

    private static List<Object> buildSummary(boolean formed, boolean active, int problems, List<Object> toolsNeeded) {
        List<Object> summary = new ArrayList<Object>();
        if (!formed) {
            summary.add(
                "The structure is NOT formed — the multiblock has not assembled. Common causes: a wrong or misplaced "
                    + "block, a missing/incorrect hatch or bus, an air gap, the controller facing the wrong way, or the "
                    + "wrong tier of casing. Use scan_blocks around the controller and compare against the required "
                    + "structure for this machine.");
        } else {
            summary.add("The structure IS formed.");
        }
        if (problems > 0) {
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
