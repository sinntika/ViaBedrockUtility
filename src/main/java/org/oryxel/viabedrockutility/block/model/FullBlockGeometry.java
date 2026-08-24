package org.oryxel.viabedrockutility.block.model;

import java.util.List;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.parser.bedrock.BedrockGeometryParser;

/**
 * The geometry every plain Bedrock block uses.
 *
 * <p>Bedrock ships {@code minecraft:geometry.full_block} with the client rather
 * than inside a resource pack, so it never arrives over the network and has to
 * exist locally. It is embedded as a string instead of a resource file because a
 * missing or unreadable resource would leave every simple custom block
 * invisible, and because this way it cannot be lost when the jar is repackaged.
 */
public final class FullBlockGeometry {

	/** The identifier Bedrock block definitions refer to. */
	public static final String IDENTIFIER = "geometry.full_block";

	private static final String JSON =
			"""
			{
			  "format_version": "1.12.0",
			  "minecraft:geometry": [
			    {
			      "description": {
			        "identifier": "geometry.full_block",
			        "texture_width": 16,
			        "texture_height": 16,
			        "visible_bounds_width": 2,
			        "visible_bounds_height": 2.5,
			        "visible_bounds_offset": [0, 0.75, 0]
			      },
			      "bones": [
			        {
			          "name": "block",
			          "pivot": [0, 0, 0],
			          "cubes": [
			            {
			              "origin": [-8, 0, -8],
			              "size": [16, 16, 16],
			              "uv": [0, 0]
			            }
			          ]
			        }
			      ]
			    }
			  ]
			}
			""";

	private static volatile BedrockGeometryModel cached;

	private FullBlockGeometry() {
	}

	/** Parsed on first use, then reused; parsing this on every block would be wasteful. */
	public static BedrockGeometryModel get() {
		BedrockGeometryModel model = cached;
		if (model != null) {
			return model;
		}

		synchronized (FullBlockGeometry.class) {
			model = cached;
			if (model == null) {
				final List<BedrockGeometryModel> parsed = BedrockGeometryParser.parse(JSON);
				if (parsed == null || parsed.isEmpty()) {
					throw new IllegalStateException(
							"The built-in full block geometry did not parse");
				}
				model = parsed.get(0);
				cached = model;
			}
		}
		return model;
	}
}
