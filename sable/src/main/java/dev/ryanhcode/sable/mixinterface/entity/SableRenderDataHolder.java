package dev.ryanhcode.sable.mixinterface.entity;

/** PORT-26.3: lets a frame start empty the entity dispatcher's per-frame render data. */
public interface SableRenderDataHolder {
    void sable$clearRenderData();
}
