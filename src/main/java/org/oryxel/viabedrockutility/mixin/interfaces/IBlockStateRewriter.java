package org.oryxel.viabedrockutility.mixin.interfaces;

import com.viaversion.viaversion.libs.fastutil.ints.Int2IntMap;

/**
 * Access to the bedrock to java block id map of ViaBedrock's block state
 * rewriter.
 *
 * <p>Custom blocks are not in ViaBedrock's own mapping data, so the entries for
 * them have to be added once the blocks exist on the client. The map is the only
 * piece of internal state this needs; everything else about the palette is
 * public.
 */
public interface IBlockStateRewriter {

	Int2IntMap viaBedrockUtility$blockStateIdMappings();
}
