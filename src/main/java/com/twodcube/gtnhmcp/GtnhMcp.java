package com.twodcube.gtnhmcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.twodcube.gtnhmcp.proxy.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Mod entry point for GTNH MCP — a client-side mod that embeds a local Model Context Protocol (MCP) server so an AI
 * assistant (e.g. Claude) can introspect the running GregTech New Horizons client and help diagnose things like why a
 * multiblock will not form.
 *
 * <p>
 * This class only wires the FML lifecycle to the side-specific proxy and loads config. The HTTP/MCP server is started
 * by
 * {@link com.twodcube.gtnhmcp.proxy.ClientProxy} on the physical client; on a dedicated server the common proxy makes
 * the
 * mod a no-op. GregTech is an optional, soft ("after") dependency: the generic inspection tools work in any 1.7.10
 * instance, and multiblock diagnosis activates when GregTech is present.
 */
@Mod(
    modid = GtnhMcp.MODID,
    version = Tags.VERSION,
    name = "GTNH MCP",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "after:gregtech")
public class GtnhMcp {

    /** The FML mod id; also used as the logger name and the maintenance config category root. */
    public static final String MODID = "gtnhmcp";

    /** Shared logger for the mod. */
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.twodcube.gtnhmcp.proxy.ClientProxy",
        serverSide = "com.twodcube.gtnhmcp.proxy.CommonProxy")
    public static CommonProxy proxy;

    /**
     * Load configuration before anything else, then delegate to the side proxy.
     *
     * @param event FML pre-initialization event.
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GtnhMcpConfig.synchronize(event.getSuggestedConfigurationFile());
        proxy.preInit(event);
    }

    /**
     * Start the MCP server (client side) via the proxy.
     *
     * @param event FML initialization event.
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    /**
     * @param event FML post-initialization event.
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
