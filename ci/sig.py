#!/usr/bin/env python3
"""Dump ground-truth signatures from the real 26.2 jar and the compile classpath.

The sandbox this port is driven from cannot download Minecraft or decompile it,
so every signature the port depends on is read back out of the jars CI actually
compiled against. Guessed signatures are what produced the original
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

# Everything that has to be on javap's classpath for the classes below to
# resolve their own field and parameter types. The fabric modules are here
# because a supported hook is worth far more than a mixin into vanilla.
LIB_KEYWORDS = (
	"viabedrock",
	"viaversion",
	"nbt",
	"cubeconverter",
	"mocha",
	"fastutil",
	"fabric-model-loading",
	"fabric-renderer-api",
)

# The bundled server jar is ~50 MB but holds four loader classes, so it wins on
# file size and loses on everything that matters.
JAR_NAME_SKIP = ("server", "sources", "javadoc")

# Whether fabric still offers a way to contribute block models for blocks that
# only exist once a bedrock server has told us about them.
INDEXES = (
	(
		"fabric-model-loading",
		re.compile(
			r"^net\.fabricmc\.fabric\.api\.client\.model\.loading\.v1\."
			r"[A-Za-z0-9_.$]+$"
		),
	),
)

# The models a block state resolver has to hand back live in this package.
MC_INDEX = re.compile(
	r"^net\.minecraft\.client\.renderer\.block\.dispatch\.[A-Za-z0-9_$]+$"
)

# (class, member filter). The filter keeps the output readable for the huge
# classes; None dumps every member.
TARGETS = (
	# -- the supported alternative to mixing into the baker --
	("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin", None),
	(
		"net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin$Context",
		None,
	),
	("net.fabricmc.fabric.api.client.model.loading.v1.BlockStateResolver", None),
	(
		"net.fabricmc.fabric.api.client.model.loading.v1.BlockStateResolver$Context",
		None,
	),
	# -- what such a resolver has to produce --
	("net.minecraft.client.renderer.block.dispatch.BlockStateModel$UnbakedRoot", None),
	("net.minecraft.client.renderer.block.dispatch.BlockStateModel$Unbaked", None),
	("net.minecraft.client.renderer.block.dispatch.SingleVariant", None),
	("net.minecraft.client.renderer.block.dispatch.SingleVariant$Unbaked", None),
	# -- and the baker it would be hbaked with --
	("net.minecraft.client.resources.model.ModelBaker", None),
	# -- regression guard for the crash that started all of this --
	(
		"net.minecraft.client.renderer.entity.EntityRenderDispatcher",
		r"getRenderer",
	),
)

MAX_LINES = 140


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


def print_index(title, names):
	print()
	print(f"== {title} ({len(names)} matched) ==")
	for clazz in names[:MAX_LINES]:
		print(f"   {clazz}")
	if len(names) > MAX_LINES:
		print(f"   ... truncated ({len(names) - MAX_LINES} more)")


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

	print_index("MC BLOCK DISPATCH PACKAGE", class_names(minecraft, MC_INDEX))

	for keyword, pattern in INDEXES:
		matches = [path for path in libs if keyword in path.name.lower()]
		if not matches:
			print()
			print(f"== CLASS INDEX: {keyword} == (jar not on the classpath)")
			continue
		for path in matches:
			print_index(f"CLASS INDEX: {path.name}", class_names(path, pattern))

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
