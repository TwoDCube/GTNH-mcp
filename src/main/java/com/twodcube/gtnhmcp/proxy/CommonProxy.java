package com.twodcube.gtnhmcp.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * The common (physical-server) proxy. This mod is a client-side diagnostic tool, so on a dedicated server it does
 * nothing: there is no client world/player to introspect and no MCP server is started. The lifecycle hooks exist only
 * so
 * the {@code @SidedProxy} contract is satisfied; the real behaviour lives in {@link ClientProxy}.
 */
public class CommonProxy {

    /** @param event FML pre-init event (unused on the server side). */
    public void preInit(FMLPreInitializationEvent event) {}

    /** @param event FML init event (unused on the server side). */
    public void init(FMLInitializationEvent event) {}

    /** @param event FML post-init event (unused on the server side). */
    public void postInit(FMLPostInitializationEvent event) {}
}
