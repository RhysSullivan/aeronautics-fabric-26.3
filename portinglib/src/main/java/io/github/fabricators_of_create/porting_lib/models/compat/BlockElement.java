package io.github.fabricators_of_create.porting_lib.models.compat;

import io.github.fabricators_of_create.porting_lib.models.ExtraFaceData;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;

// PORT-26.3: Minecraft 26.3 removed this model-data class; the data generators only write it out as JSON, so Porting Lib
// keeps this small stand-in for them. Not used at runtime (generated resources are committed).
public final class BlockElement {
	public final Vector3f from;
	public final Vector3f to;
	public final Map<Direction, BlockElementFace> faces;
	public final @Nullable BlockElementRotation rotation;
	public final boolean shade;
	private ExtraFaceData faceData = ExtraFaceData.DEFAULT;

	public BlockElement(final Vector3f from, final Vector3f to, final Map<Direction, BlockElementFace> faces, final @Nullable BlockElementRotation rotation, final boolean shade) {
		this.from = from;
		this.to = to;
		this.faces = faces;
		this.rotation = rotation;
		this.shade = shade;
	}

	public ExtraFaceData port_lib$getFaceData() { return this.faceData; }
	public void port_lib$setFaceData(final ExtraFaceData faceData) { this.faceData = faceData; }

	/** The UVs a face gets by default from the element's bounds (as vanilla's BlockElement computed them). */
	public float[] uvsByFace(final Direction face) {
		return switch (face) {
			case DOWN -> new float[]{this.from.x(), 16.0F - this.to.z(), this.to.x(), 16.0F - this.from.z()};
			case UP -> new float[]{this.from.x(), this.from.z(), this.to.x(), this.to.z()};
			case NORTH -> new float[]{16.0F - this.to.x(), 16.0F - this.to.y(), 16.0F - this.from.x(), 16.0F - this.from.y()};
			case SOUTH -> new float[]{this.from.x(), 16.0F - this.to.y(), this.to.x(), 16.0F - this.from.y()};
			case WEST -> new float[]{this.from.z(), 16.0F - this.to.y(), this.to.z(), 16.0F - this.from.y()};
			case EAST -> new float[]{16.0F - this.to.z(), 16.0F - this.to.y(), 16.0F - this.from.z(), 16.0F - this.from.y()};
		};
	}
}
