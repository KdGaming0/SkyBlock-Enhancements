package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.api.ReliableRecipeViewerPlugin;

/**
 * RRV plugin entry point on the server side. RRV discovers this class via its fabric
 * entrypoint and instantiates it to register server-side integration.
 *
 * <p>No work is performed at load time — item injection runs through
 * {@link com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.SkyblockInjectionCache}
 * after the NEU repo is ready, and recipe generation lives in
 * {@link com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.generator.SkyblockRecipeGenerator}.
 */
public final class SkyblockRrvPlugin implements ReliableRecipeViewerPlugin {

    @Override
    public void onIntegrationInitialize() {
        // Intentionally empty — see class Javadoc.
    }
}