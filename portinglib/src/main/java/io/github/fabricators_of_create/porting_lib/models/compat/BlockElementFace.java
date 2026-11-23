package io.github.fabricators_of_create.porting_lib.models.compat;

import io.github.fabricators_of_create.porting_lib.models.ExtraFaceData;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

// PORT-26.3: Minecraft 26.3 removed this model-data class; the data generators only write it out as JSON, so Porting Lib
// keeps this small stand-in for them. Not used at runtime (generated resources are committed).
public final class BlockElementFace {
	private final @Nullable Direction cullForDirection;
	private final int tintIndex;
	private final String texture;
	private final BlockFaceUV uv;
	private ExtraFaceData faceData = ExtraFaceData.DEFAULT;

	public BlockElementFace(final @Nullable Direction cullForDirection, final int tintIndex, final String texture, final BlockFaceUV uv) {
		this.cullForDirection = cullForDirection;
		this.tintIndex = tintIndex;
		this.texture = texture;
		this.uv = uv;
	}

	public @Nullable Direction cullForDirection() { return this.cullForDirection; }
	public int tintIndex() { return this.tintIndex; }
	public String texture() { return this.texture; }
	public BlockFaceUV uv() { return this.uv; }
	public ExtraFaceData port_lib$faceData() { return this.faceData; }
	public void port_lib$setFaceData(final ExtraFaceData faceData, final Object ignored) { this.faceData = faceData; }
}
