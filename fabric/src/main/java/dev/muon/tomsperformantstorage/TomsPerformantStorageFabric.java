package dev.muon.tomsperformantstorage;

import net.fabricmc.api.ModInitializer;

public class TomsPerformantStorageFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        TomsPerformantStorage.init();
    }
}
