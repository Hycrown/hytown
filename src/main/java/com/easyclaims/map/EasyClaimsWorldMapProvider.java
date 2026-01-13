package com.easyclaims.map;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapLoadException;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.IWorldMapProvider;

/**
 * World map provider that renders claim overlays on the map.
 * Based on SimpleClaims' approach of implementing IWorldMapProvider.
 */
public class EasyClaimsWorldMapProvider implements IWorldMapProvider {
    public static final String ID = "EasyClaims";
    public static final BuilderCodec<EasyClaimsWorldMapProvider> CODEC = BuilderCodec.builder(
            EasyClaimsWorldMapProvider.class, EasyClaimsWorldMapProvider::new).build();

    @Override
    public IWorldMap getGenerator(World world) throws WorldMapLoadException {
        return EasyClaimsChunkWorldMap.INSTANCE;
    }
}
