package com.twodcube.gtnhmcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;

/**
 * Unit tests for {@link MultiblockInspector}. Because GregTech access is by-name reflection, the diagnosis logic can be
 * verified against a fake "GregTech-shaped" object that exposes the same public field/method names — no GregTech on the
 * classpath. This is the stub-based test the project's standards require for code that talks to an external system.
 */
class MultiblockInspectorTest {

    /**
     * Stand-in for a GregTech {@code MTEMultiBlockBase} + {@code IGregTechTileEntity}, exposing exactly the members
     * {@link MultiblockInspector#buildDiagnosis} reads by name. One instance plays both roles (base tile and meta
     * tile).
     */
    public static final class FakeMultiblock {

        public boolean mMachine;
        public boolean mWrench;
        public boolean mScrewdriver;
        public boolean mSoftMallet;
        public boolean mHardHammer;
        public boolean mSolderingTool;
        public boolean mCrowbar;
        public int mProgresstime;
        public int mMaxProgresstime;
        public int mEfficiency;

        private final boolean active;
        private final int repair;
        private final int ideal;
        private final long storedEu;
        private final long capacityEu;
        private final long outputVoltage;
        private final int metaTileId;
        private final String[] infoData;

        FakeMultiblock(boolean active, int repair, int ideal, long storedEu, long capacityEu, long outputVoltage,
            int metaTileId, String[] infoData) {
            this.active = active;
            this.repair = repair;
            this.ideal = ideal;
            this.storedEu = storedEu;
            this.capacityEu = capacityEu;
            this.outputVoltage = outputVoltage;
            this.metaTileId = metaTileId;
            this.infoData = infoData;
        }

        public boolean isActive() {
            return active;
        }

        public int getRepairStatus() {
            return repair;
        }

        public int getIdealStatus() {
            return ideal;
        }

        public long getStoredEU() {
            return storedEu;
        }

        public long getEUCapacity() {
            return capacityEu;
        }

        public long getOutputVoltage() {
            return outputVoltage;
        }

        public int getMetaTileID() {
            return metaTileId;
        }

        public String[] getInfoData() {
            return infoData;
        }

        public String getFrontFacing() {
            return "NORTH";
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void diagnosesUnformedMachineWithMaintenanceProblems() throws McpToolException {
        FakeMultiblock fake = new FakeMultiblock(
            false,
            4,
            6,
            1000L,
            100000L,
            512L,
            211,
            new String[] { "Progress: 5 / 10s", "Problems: 2" });
        fake.mMachine = false;
        fake.mWrench = false; // needed
        fake.mScrewdriver = true;
        fake.mSoftMallet = true;
        fake.mHardHammer = true;
        fake.mSolderingTool = true;
        fake.mCrowbar = false; // needed
        fake.mProgresstime = 100;
        fake.mMaxProgresstime = 200;
        fake.mEfficiency = 9500;

        Map<String, Object> d = MultiblockInspector.buildDiagnosis(fake, fake, "Large Chemical Reactor");

        assertEquals("Large Chemical Reactor", d.get("machineName"));
        assertEquals(Integer.valueOf(211), d.get("metaTileId"));
        assertEquals(Boolean.FALSE, d.get("structureFormed"));
        assertEquals(Boolean.FALSE, d.get("isActive"));

        Map<String, Object> maintenance = (Map<String, Object>) d.get("maintenance");
        assertEquals(Integer.valueOf(2), maintenance.get("problemCount"));
        List<Object> toolsNeeded = (List<Object>) maintenance.get("toolsNeeded");
        assertEquals(2, toolsNeeded.size());
        assertTrue(toolsNeeded.contains("wrench"));
        assertTrue(toolsNeeded.contains("crowbar"));

        Map<String, Object> progress = (Map<String, Object>) d.get("progress");
        assertEquals(Integer.valueOf(100), progress.get("currentTicks"));
        assertEquals(Integer.valueOf(200), progress.get("maxTicks"));
        assertEquals(Double.valueOf(5.0), progress.get("currentSeconds"));
        assertEquals(Double.valueOf(10.0), progress.get("maxSeconds"));
        assertEquals(Double.valueOf(95.0), d.get("efficiencyPercent"));

        Map<String, Object> energy = (Map<String, Object>) d.get("energy");
        assertEquals(Long.valueOf(1000L), energy.get("storedEU"));
        assertEquals(Long.valueOf(100000L), energy.get("capacityEU"));
        assertEquals(Long.valueOf(512L), energy.get("outputVoltage"));

        List<Object> scanner = (List<Object>) d.get("scannerInfo");
        assertEquals(2, scanner.size());

        List<Object> summary = (List<Object>) d.get("summary");
        assertTrue(
            summary.get(0)
                .toString()
                .contains("NOT formed"));
        boolean mentionsMaintenance = false;
        for (Object line : summary) {
            if (line.toString()
                .contains("maintenance problem")) {
                mentionsMaintenance = true;
            }
        }
        assertTrue(mentionsMaintenance, "summary should mention maintenance problems");
        assertTrue(
            d.get("freshnessNote")
                .toString()
                .contains("GUI is open"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void diagnosesFormedRunningMachine() throws McpToolException {
        FakeMultiblock fake = new FakeMultiblock(true, 6, 6, 5000L, 5000L, 2048L, 1000, new String[0]);
        fake.mMachine = true;
        fake.mWrench = true;
        fake.mScrewdriver = true;
        fake.mSoftMallet = true;
        fake.mHardHammer = true;
        fake.mSolderingTool = true;
        fake.mCrowbar = true;
        fake.mProgresstime = 40;
        fake.mMaxProgresstime = 400;
        fake.mEfficiency = 10000;

        Map<String, Object> d = MultiblockInspector.buildDiagnosis(fake, fake, "Electric Blast Furnace");
        assertEquals(Boolean.TRUE, d.get("structureFormed"));
        assertEquals(Boolean.TRUE, d.get("isActive"));
        Map<String, Object> maintenance = (Map<String, Object>) d.get("maintenance");
        assertEquals(Integer.valueOf(0), maintenance.get("problemCount"));
        assertTrue(((List<Object>) maintenance.get("toolsNeeded")).isEmpty());

        List<Object> summary = (List<Object>) d.get("summary");
        boolean running = false;
        for (Object line : summary) {
            if (line.toString()
                .contains("running")) {
                running = true;
            }
        }
        assertTrue(running, "summary should report the machine is running");
    }

    @Test
    void describeGregTechReturnsNullWhenGregTechAbsent() {
        // With no GregTech on the test classpath the instanceof check fails fast and returns null.
        assertNull(MultiblockInspector.describeGregTech(null));
    }

    @Test
    void diagnoseReportsGregTechMissing() {
        McpToolException ex = assertThrows(McpToolException.class, () -> MultiblockInspector.diagnose(null));
        assertTrue(
            ex.getMessage()
                .contains("GregTech is not installed"));
    }
}
