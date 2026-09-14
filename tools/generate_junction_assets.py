#!/usr/bin/env python3
"""Regenerate vent_junction multipart assets from canonical long/inner-bend models.

The junction keeps eight VentJunctionBlock states for topology ownership, but its
rendered T-shell reuses the ordinary segment models. Four cells render long
quarters and the four cells on the selected branch side render inner-bend
quarters. Two independently textured gate planes are then added at the side and
conditional-straight exits. Each gate state uses only a normal and mirrored
quarter model; ordinary blockstate rotations assemble the complete opening.
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/pneumaticdiversityvents"
BLOCKSTATES = ASSETS / "blockstates"
MODELS = ASSETS / "models"
TEXTURES = ASSETS / "textures"

CORNERS = ("bottom_left", "bottom_right", "top_left", "top_right")
AXES = ("x", "y", "z")
BRANCHES = ("top", "right", "bottom", "left")
VISUALS = ("straight_off", "turn_off", "straight_on", "turn_on")

VECTORS = {
    "west": (-1, 0, 0), "east": (1, 0, 0),
    "down": (0, -1, 0), "up": (0, 1, 0),
    "north": (0, 0, -1), "south": (0, 0, 1),
}
AXIS_NEG = {"x": "west", "y": "down", "z": "north"}
AXIS_POS = {"x": "east", "y": "up", "z": "south"}
BRANCH_DIRECTION = {
    "x": {"top": "up", "right": "south", "bottom": "down", "left": "north"},
    "y": {"top": "north", "right": "east", "bottom": "south", "left": "west"},
    "z": {"top": "up", "right": "east", "bottom": "down", "left": "west"},
}
ORIENTATION_NAMES = {
    frozenset(("up", "south")): "up_south", frozenset(("up", "north")): "up_north",
    frozenset(("up", "east")): "up_east", frozenset(("up", "west")): "up_west",
    frozenset(("down", "south")): "down_south", frozenset(("down", "north")): "down_north",
    frozenset(("down", "east")): "down_east", frozenset(("down", "west")): "down_west",
    frozenset(("east", "south")): "east_south", frozenset(("east", "north")): "east_north",
    frozenset(("west", "south")): "west_south", frozenset(("west", "north")): "west_north",
}
ORIENTATION_DIRECTIONS = {
    "up_south": ("up", "south"), "up_north": ("up", "north"),
    "up_east": ("up", "east"), "up_west": ("up", "west"),
    "down_south": ("down", "south"), "down_north": ("down", "north"),
    "down_east": ("down", "east"), "down_west": ("down", "west"),
    "east_south": ("east", "south"), "east_north": ("east", "north"),
    "west_south": ("west", "south"), "west_north": ("west", "north"),
}


def cross(a, b):
    ax, ay, az = VECTORS[a]
    bx, by, bz = VECTORS[b]
    value = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
    return next(name for name, vector in VECTORS.items() if vector == value)


def local_position(axis, corner, mirrored):
    layer = 1 if mirrored else 0
    if axis == "x":
        return layer, 0 if corner.startswith("bottom") else 1, 0 if corner.endswith("left") else 1
    if axis == "y":
        return 0 if corner.endswith("left") else 1, layer, 0 if corner.startswith("top") else 1
    return 0 if corner.endswith("left") else 1, 0 if corner.startswith("bottom") else 1, layer


def on_face(position, direction):
    x, y, z = position
    return {
        "west": x == 0, "east": x == 1,
        "down": y == 0, "up": y == 1,
        "north": z == 0, "south": z == 1,
    }[direction]


def rotate_x(vector, degrees):
    x, y, z = vector
    for _ in range((degrees // 90) % 4):
        x, y, z = x, z, -y
    return x, y, z


def rotate_y(vector, degrees):
    x, y, z = vector
    for _ in range((degrees // 90) % 4):
        x, y, z = -z, y, x
    return x, y, z


def rotate_model_vector(vector, x_rotation, y_rotation):
    # Blockstate model rotations apply X first, then Y. These helpers use Minecraft's rotation direction.
    return rotate_y(rotate_x(vector, x_rotation), y_rotation)


def gate_transform(position, direction):
    """Choose normal/mirrored gate model + ordinary blockstate rotations for one quarter.

    Canonical geometry faces SOUTH. model.json places the texture's shared-middle corner at
    physical top-left; model_mirrored.json flips it to top-right. X/Y blockstate rotations then
    cover the other two quarters and orient the slab onto any opening face. This deliberately
    mirrors the long-segment asset strategy: two authored models, never four roll models.
    """
    normal = VECTORS[direction]
    x, y, z = position
    desired = [0, 0, 0]
    for index, coordinate in enumerate((x, y, z)):
        if normal[index] == 0:
            desired[index] = 1 if coordinate == 0 else -1
    desired = tuple(desired)

    canonical_normal = VECTORS["south"]
    canonical_middle = {False: (-1, 1, 0), True: (1, 1, 0)}
    candidates = []
    for mirrored, middle in canonical_middle.items():
        for x_rotation in (0, 90, 180, 270):
            for y_rotation in (0, 90, 180, 270):
                if rotate_model_vector(canonical_normal, x_rotation, y_rotation) != normal:
                    continue
                if rotate_model_vector(middle, x_rotation, y_rotation) != desired:
                    continue
                # Prefer the simplest transform. The final terms keep generation deterministic.
                rotation_count = int(x_rotation != 0) + int(y_rotation != 0)
                rotation_amount = min(x_rotation, 360 - x_rotation) + min(y_rotation, 360 - y_rotation)
                candidates.append(((rotation_count, rotation_amount, int(mirrored), x_rotation, y_rotation),
                                   mirrored, x_rotation, y_rotation))
    if not candidates:
        raise ValueError(f"Cannot orient gate quarter at {position} on {direction}: wanted {desired}")

    _, mirrored, x_rotation, y_rotation = min(candidates)
    rotation = {}
    if x_rotation:
        rotation["x"] = x_rotation
    if y_rotation:
        rotation["y"] = y_rotation
    return mirrored, rotation


def gate_model(state, position, direction):
    mirrored, rotation = gate_transform(position, direction)
    suffix = "_mirrored" if mirrored else ""
    return f"pneumaticdiversityvents:block/vent/junction/gate/{state}/model{suffix}", rotation

def model_apply(model, rotation):
    result = {"model": model}
    result.update(rotation)
    return result



def gate_child_model_json(state, mirrored):
    """Texture-only child of the authored normal/mirrored master gate geometry."""
    suffix = "_mirrored" if mirrored else ""
    return {
        "parent": f"pneumaticdiversityvents:block/vent/junction/gate/model{suffix}",
        "textures": {
            "0": f"pneumaticdiversityvents:block/vent/junction/gate/{state}/gate",
            "particle": "pneumaticdiversityvents:block/vent/break/glass",
        },
    }


def write_gate_assets():
    model_root = MODELS / "block/vent/junction/gate"
    model_root.mkdir(parents=True, exist_ok=True)

    for state in ("closed", "unpowered", "powered"):
        target = model_root / state
        target.mkdir(parents=True, exist_ok=True)
        (target / "model.json").write_text(json.dumps(gate_child_model_json(state, False), indent=2) + "\n")
        (target / "model_mirrored.json").write_text(json.dumps(gate_child_model_json(state, True), indent=2) + "\n")

        # Purge the previous four-roll layout so generated assets stay intentionally minimal.
        for roll in range(4):
            obsolete = target / f"roll_{roll}"
            if obsolete.exists():
                for child in obsolete.iterdir():
                    child.unlink()
                obsolete.rmdir()

    obsolete = model_root / "base.json"
    if obsolete.exists():
        obsolete.unlink()

def generate_blockstate():
    long_variants = json.loads((BLOCKSTATES / "vent_segment_long.json").read_text())["variants"]
    turn_variants = json.loads((BLOCKSTATES / "vent_segment_turn.json").read_text())["variants"]
    multipart = []

    for axis in AXES:
        for corner in CORNERS:
            for mirrored in (False, True):
                position = local_position(axis, corner, mirrored)
                long_key = f"axis={axis},corner={corner},mirrored={str(mirrored).lower()}"
                for branch in BRANCHES:
                    branch_direction = BRANCH_DIRECTION[axis][branch]
                    when = {"axis": axis, "corner": corner, "mirrored": str(mirrored).lower(), "branch": branch}
                    if not on_face(position, branch_direction):
                        multipart.append({"when": when, "apply": long_variants[long_key]})
                        continue
                    axial_direction = AXIS_POS[axis] if mirrored else AXIS_NEG[axis]
                    orientation = ORIENTATION_NAMES[frozenset((axial_direction, branch_direction))]
                    first, second = ORIENTATION_DIRECTIONS[orientation]
                    bend_mirrored = not on_face(position, cross(first, second))
                    turn_key = f"piece=inner,orientation={orientation},mirrored={str(bend_mirrored).lower()}"
                    multipart.append({"when": when, "apply": turn_variants[turn_key]})

    for axis in AXES:
        for corner in CORNERS:
            for mirrored in (False, True):
                position = local_position(axis, corner, mirrored)
                for branch in BRANCHES:
                    branch_direction = BRANCH_DIRECTION[axis][branch]
                    for fixed_positive in (False, True):
                        conditional_straight = AXIS_NEG[axis] if fixed_positive else AXIS_POS[axis]
                        for visual in VISUALS:
                            turn_active = visual.startswith("turn_")
                            open_state = "powered" if visual.endswith("_on") else "unpowered"
                            when = {
                                "axis": axis, "corner": corner, "mirrored": str(mirrored).lower(), "branch": branch,
                                "fixed_positive": str(fixed_positive).lower(), "visual": visual,
                            }
                            if on_face(position, branch_direction):
                                state = open_state if turn_active else "closed"
                                model, rotation = gate_model(state, position, branch_direction)
                                multipart.append({"when": when, "apply": model_apply(model, rotation)})
                            if on_face(position, conditional_straight):
                                state = "closed" if turn_active else open_state
                                model, rotation = gate_model(state, position, conditional_straight)
                                multipart.append({"when": when, "apply": model_apply(model, rotation)})

    (BLOCKSTATES / "vent_junction.json").write_text(json.dumps({"multipart": multipart}, indent=2) + "\n")
    print(f"Wrote vent_junction.json with {len(multipart)} multipart entries")


if __name__ == "__main__":
    write_gate_assets()
    generate_blockstate()
