#!/usr/bin/env python3
"""Dump ground-truth signatures from the real 26.2 jar and the compile classpath.

The sandbox this port is driven from cannot download Minecraft or decompile it,
so every signature the mixins depend on is read back out of the jar that CI
actually compiled against. Guessed signatures are what produced the original
InvalidInjectionException crash.
"""

import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HOME = Path.home()

SEARCH_DIRS = [
	ROOT / ".gradle" / "loom-cache",
	HOME / ".gradle" / "caches" / "fabric-loom",
	HOME / ".gradle" / "caches" / "modules-2",
]

# Everything that has to be on javap's classpath for the Minecraft classes and
# the library classes below to resolve their own field/parameter types.
LIB_KEYWORDS = ("viabedrock", "viaversion", "cubeconverter", "mocha", "fastutil")

# The bundled server jar is ~50 MB but contains four loader classes, so it wins
# on file size and loses on everything that matters.
JAR_NAME_SKIP = ("server", "sources", "javadoc")

# Class-name index. Package paths inside ViaBedrock move between snapshots, so
# list the candidates instead of hard-coding a guess.
INDEXES = (
	(
		"viabedrock",
		re.compile(r"(?i)(api\.model\.|resourcepack\.definition\.|resourcepack\.content\.)"),
	),
)

# (class, member filter). A filter keeps the output readable for the huge
# classes; None dumps every member.
TARGETS = (
	# -- phase 2: turning Bedrock block definitions into real blocks --
	(
		"net.minecraft.world.level.block.state.BlockBehaviour",
		r"class net|getShape|getCollisionShape|getOcclusionShape|getVisualShape"
		r"|getInteractionShape|getLightBlock|isPathfindable|getDestroyProgress"
		r"|propagatesSkylightDown|getBlockSupportShape",
	),
	(
		"net.minecraft.world.level.block.Block",
		r"class net|BLOCK_STATE_REGISTRY|static int getId|Block\(net.minecraft.world.level.block.state.BlockBehaviour"
		r"|defaultBlockState|createBlockStateDefinition|getStateDefinition|registerDefaultState",
	),
	(
		"net.minecraft.world.level.block.state.BlockState",
		r"class net|initCache|getBlock|BlockState\(",
	),
	(
		"net.minecraft.world.level.block.state.StateDefinition",
		r"class net|any\(|getPossibleStates|owner|StateDefinition\(",
	),
	("net.minecraft.core.RegistrationInfo", None),
	("net.minecraft.core.WritableRegistry", None),
	(
		"net.minecraft.core.registries.BuiltInRegistries",
		r"class net|Registry<net.minecraft.world.level.block.Block>|\bBLOCK\b",
	),
	(
		"net.minecraft.resources.Identifier",
		r"class net|public static|Identifier\(",
	),
	(
		"net.minecraft.world.phys.shapes.CollisionContext",
		r"interface net|class net|empty\(|of\(",
	),
	# -- phase 4: the ViaBedrock side the hooks attach to --
	("net.raphimc.viabedrock.api.resourcepack.definition.BlockDefinitions", None),
	(
		"net.raphimc.viabedrock.api.resourcepack.definition.BlockDefinitions$BlockDefinition",
		None,
	),
	(
		"net.raphimc.viabedrock.api.resourcepack.definition.TextureDefinitions",
		r"class net|public",
	),
	("net.raphimc.viabedrock.api.model.BlockState", None),
	# -- mocha, for evaluating the MoLang conditions on block permutations --
	("team.unnamed.mocha.runtime.Scope", None),
	("team.unnamed.mocha.runtime.value.MutableObjectBinding", None),
	("team.unnamed.mocha.runtime.binding.JavaObjectBinding", r"class team|public|static"),
	("team.unnamed.mocha.runtime.value.Value", None),
	# -- regression guard for the crash that started all of this --
	(
		"net.minecraft.client.renderer.entity.EntityRenderDispatcher",
		r"getRenderer",
	),
)

MAX_LINES = 90


def all_jars():
	found = {}
	for directory in SEARCH_DIRS:
		if not directory.is_dir():
			continue
		for path in directory.rglob("*.jar"):
			found.setdefault(path.name, path)
	return found


def class_count(path):
	try:
		with zipfile.ZipFile(path) as archive:
			return sum(1 for name in archive.namelist() if name.endswith(".class"))
	except Exception:
		return 0


def class_names(path, pattern):
	names = []
	try:
		with zipfile.ZipFile(path) as archive:
			for name in archive.namelist():
				if not name.endswith(".class"):
					continue
				clazz = name[:-6].replace("/", ".")
				if pattern.search(clazz):
					names.append(clazz)
	except Exception:
		return []
	return sorted(names)


def pick_minecraft(jars):
	best = None
	best_count = 0
	for name, path in jars.items():
		lowered = name.lower()
		if not lowered.startswith("minecraft"):
			continue
		if any(skip in lowered for skip in JAR_NAME_SKIP):
			continue
		count = class_count(path)
		if count > best_count:
			best, best_count = path, count
	return best, best_count


def main():
	jars = all_jars()
	minecraft, minecraft_classes = pick_minecraft(jars)

	if minecraft is None:
		print("no minecraft jar found; searched:")
		for directory in SEARCH_DIRS:
			print(f"   {directory}")
		return

	libs = sorted(
		(
			path
			for name, path in jars.items()
			if path != minecraft
			and any(keyword in name.lower() for keyword in LIB_KEYWORDS)
		),
		key=lambda path: path.name,
	)

	print(f"jar: {minecraft.name} ({minecraft_classes} classes)")
	print()
	print(f"== CLASSPATH LIBS ({len(libs)}) ==")
	for path in libs:
		print(f"   {path.name:<60} {class_count(path)} classes")

	for keyword, pattern in INDEXES:
		matches = [path for path in libs if keyword in path.name.lower()]
		if not matches:
			print()
			print(f"== CLASS INDEX: {keyword} == (jar not on the classpath)")
			continue
		for path in matches:
			names = class_names(path, pattern)
			print()
			print(f"== CLASS INDEX: {path.name} ({len(names)} matched) ==")
			for clazz in names:
				print(f"   {clazz}")

	classpath = ":".join(str(path) for path in [minecraft, *libs])

	print()
	print("== SIGNATURES ==")
	for target, member_filter in TARGETS:
		print(f"-- {target} --")
		result = subprocess.run(
			["javap", "-p", "-classpath", classpath, target],
			capture_output=True,
			text=True,
		)
		if result.returncode != 0:
			detail = (result.stderr or result.stdout).strip().splitlines()
			print(f"   javap error: {detail[0] if detail else 'unknown'}")
			continue

		lines = [line.rstrip() for line in result.stdout.splitlines() if line.strip()]
		if member_filter:
			pattern = re.compile(member_filter)
			lines = [line for line in lines if pattern.search(line)]
			if not lines:
				print(f"   (no member matched: {member_filter})")
				continue

		for line in lines[:MAX_LINES]:
			print(f"   {line.strip()}")
		if len(lines) > MAX_LINES:
			print(f"   ... truncated ({len(lines) - MAX_LINES} more)")


if __name__ == "__main__":
	main()
