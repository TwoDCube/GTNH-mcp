package com.twodcube.gtnhmcp.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;

/** Unit tests for {@link ReadRegionTool#validateBounds}: volume math and bound checks. */
class ReadRegionToolTest {

    @Test
    void computesVolumeForValidBox() throws McpToolException {
        assertEquals(1L, ReadRegionTool.validateBounds(new int[] { 5, 5, 5 }, new int[] { 5, 5, 5 }));
        // 2 wide x 5 tall x 1 deep = 10 (the Space Elevator module panel).
        assertEquals(10L, ReadRegionTool.validateBounds(new int[] { 0, 0, 0 }, new int[] { 1, 4, 0 }));
        // Negative coordinates handled correctly.
        assertEquals(27L, ReadRegionTool.validateBounds(new int[] { -1, -1, -1 }, new int[] { 1, 1, 1 }));
    }

    @Test
    void rejectsInvertedCorners() {
        McpToolException ex = assertThrows(
            McpToolException.class,
            () -> ReadRegionTool.validateBounds(new int[] { 0, 0, 0 }, new int[] { -1, 0, 0 }));
        assertTrue(
            ex.getMessage()
                .contains("max.x"));
    }

    @Test
    void rejectsOversizeVolume() {
        // 17^3 = 4913 > MAX_CELLS (4096).
        McpToolException ex = assertThrows(
            McpToolException.class,
            () -> ReadRegionTool.validateBounds(new int[] { 0, 0, 0 }, new int[] { 16, 16, 16 }));
        assertTrue(
            ex.getMessage()
                .contains("too large"));
    }

    @Test
    void acceptsVolumeAtTheCap() throws McpToolException {
        // 16 x 16 x 16 = 4096 == MAX_CELLS, allowed.
        assertEquals(
            ReadRegionTool.MAX_CELLS,
            ReadRegionTool.validateBounds(new int[] { 0, 0, 0 }, new int[] { 15, 15, 15 }));
    }
}
