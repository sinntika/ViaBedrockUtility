package org.oryxel.viabedrockutility.mixin.interfaces;

/**
 * A holder created after the registry was frozen never gets its tag set bound,
 * and any later tag lookup on it throws. Custom blocks are in no tags at all, so
 * an empty set is the correct answer.
 */
public interface IHolderReference {

	void viaBedrockUtility$resolveTags();
}
