package io.github.fabricators_of_create.porting_lib.models.compat;

import org.joml.Vector3f;
import org.joml.Vector3fc;

// PORT-26.3: Minecraft 26.3 removed this model-data class; the data generators only write it out as JSON, so Porting Lib
// keeps this small stand-in for them. Not used at runtime (generated resources are committed).
public final class ItemTransform {
	public static final ItemTransform NO_TRANSFORM = new ItemTransform(new Vector3f(), new Vector3f(), new Vector3f(1.0F, 1.0F, 1.0F));
	public final Vector3f rotation;
	public final Vector3f translation;
	public final Vector3f scale;
	private Vector3f rightRotation = new Vector3f();

	public ItemTransform(final Vector3f rotation, final Vector3f translation, final Vector3f scale) {
		this.rotation = rotation;
		this.translation = translation;
		this.scale = scale;
	}

	public Vector3f port_lib$getRightRotation() { return this.rightRotation; }
	public void port_lib$setRightRotation(final Vector3f rightRotation) { this.rightRotation = rightRotation; }

	@Override
	public boolean equals(final Object o) {
		return o instanceof ItemTransform t && t.rotation.equals(this.rotation) && t.translation.equals(this.translation)
				&& t.scale.equals(this.scale) && t.rightRotation.equals(this.rightRotation);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(this.rotation, this.translation, this.scale, this.rightRotation);
	}

	public static final class Deserializer {
		public static final Vector3fc DEFAULT_ROTATION = new Vector3f();
		public static final Vector3fc DEFAULT_TRANSLATION = new Vector3f();
		public static final Vector3fc DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);

		private Deserializer() {
		}
	}
}
