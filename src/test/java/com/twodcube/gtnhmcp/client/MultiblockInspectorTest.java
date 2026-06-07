package com.twodcube.gtnhmcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
        // GregTech's controller holds the structure-error list in a (private) field of this name.
        public List<Object> structureErrors = new ArrayList<Object>();

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
    }

    /** Fake of a singleton {@code StructureError} (e.g. MISSING_MAINTENANCE / MISSING_MUFFLER): id only, no data. */
    public static final class FakeSingletonError {

        private final String id;

        FakeSingletonError(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    /** Fake of {@code MissingOutputHatchDT(layer)}. */
    public static final class FakeDtError {

        private final int layer;

        FakeDtError(int layer) {
            this.layer = layer;
        }

        public String getId() {
            return "MISSING_OUTPUT_HATCH_DT";
        }

        public int layer() {
            return layer;
        }
    }

    /** Fake of {@code TooFewCasings(current, required)}. */
    public static final class FakeCasingsError {

        private final int current;
        private final int required;

        FakeCasingsError(int current, int required) {
            this.current = current;
            this.required = required;
        }

        public String getId() {
            return "TOO_FEW_CASINGS";
        }

        public int current() {
            return current;
        }

        public int required() {
            return required;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void diagnosesUnformedMachineWithoutStructureErrorsAsksToOpenGui() throws McpToolException {
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
        // No synced structure errors (GUI not open) -> the summary should ask the user to open the GUI.

        Map<String, Object> d = MultiblockInspector.buildDiagnosis(fake, fake, "Large Chemical Reactor");

        assertEquals("Large Chemical Reactor", d.get("machineName"));
        assertEquals(Integer.valueOf(211), d.get("metaTileId"));
        assertEquals(Boolean.FALSE, d.get("structureFormed"));
        assertTrue(((List<Object>) d.get("structureErrors")).isEmpty());

        // Maintenance data is still computed (raw), but is NOT surfaced in the summary while unformed.
        Map<String, Object> maintenance = (Map<String, Object>) d.get("maintenance");
        assertEquals(Integer.valueOf(2), maintenance.get("problemCount"));
        assertTrue(((List<Object>) maintenance.get("toolsNeeded")).contains("wrench"));

        List<Object> summary = (List<Object>) d.get("summary");
        String text = summary.toString();
        assertTrue(text.contains("NOT formed"));
        assertTrue(text.contains("Open the controller's GUI"));
        assertFalse(text.contains("maintenance problem(s) detected"), "maintenance is not meaningful while unformed");
        assertTrue(
            d.get("freshnessNote")
                .toString()
                .contains("GUI is open"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsExactStructureErrorsWhenSynced() throws McpToolException {
        FakeMultiblock fake = new FakeMultiblock(false, 6, 6, 0L, 0L, 0L, 99, new String[0]);
        fake.mMachine = false;
        fake.structureErrors.add(new FakeDtError(3));
        fake.structureErrors.add(new FakeSingletonError("MISSING_MUFFLER"));
        fake.structureErrors.add(new FakeSingletonError("MISSING_MAINTENANCE"));
        fake.structureErrors.add(new FakeCasingsError(40, 60));

        Map<String, Object> d = MultiblockInspector.buildDiagnosis(fake, fake, "Dangote Distillus");
        assertEquals(Boolean.FALSE, d.get("structureFormed"));

        List<Object> errors = (List<Object>) d.get("structureErrors");
        assertEquals(4, errors.size());

        Map<String, Object> dt = (Map<String, Object>) errors.get(0);
        assertEquals("MISSING_OUTPUT_HATCH_DT", dt.get("id"));
        assertEquals(Integer.valueOf(3), dt.get("layer"));
        assertTrue(
            dt.get("reason")
                .toString()
                .contains("output hatch"));

        Map<String, Object> casings = (Map<String, Object>) errors.get(3);
        assertEquals("TOO_FEW_CASINGS", casings.get("id"));
        assertEquals(Integer.valueOf(40), casings.get("current"));
        assertEquals(Integer.valueOf(60), casings.get("required"));

        List<Object> summary = (List<Object>) d.get("summary");
        String text = summary.toString();
        assertTrue(text.contains("structure problem(s)"));
        assertTrue(text.contains("output hatch"));
        assertTrue(text.contains("muffler hatch"));
        assertTrue(text.contains("maintenance hatch"));
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
        assertTrue(((List<Object>) d.get("structureErrors")).isEmpty());
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
