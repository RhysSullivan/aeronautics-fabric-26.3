package io.github.fabricators_of_create.porting_lib.models.compat;

// PORT-26.3: Minecraft 26.3 removed this model-data class; the data generators only write it out as JSON, so Porting Lib
// keeps this small stand-in for them. Not used at runtime (generated resources are committed).
public enum GuiLight {
	FRONT, SIDE
}
