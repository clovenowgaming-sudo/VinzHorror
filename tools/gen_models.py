#!/usr/bin/env python3
"""
Procedural OBJ model generator for VinzHorror.
Builds real, usable .obj meshes (positions + normals + UVs) for the
asylum corridor kit and a low-poly humanoid monster rig base.
Grid unit = 4.0 world units per corridor tile (matches Maze.kt TILE).
"""
import math

TILE = 4.0
WALL_H = 3.2


class Mesh:
    def __init__(self, name):
        self.name = name
        self.v = []   # positions
        self.vt = []  # uvs
        self.vn = []  # normals
        self.faces = []  # list of [(vi,ti,ni), ...] 1-based already resolved at write time

    def add_quad(self, p0, p1, p2, p3, normal, uv_scale=(1.0, 1.0)):
        base = len(self.v)
        for p in (p0, p1, p2, p3):
            self.v.append(p)
        uvs = [(0, 0), (uv_scale[0], 0), (uv_scale[0], uv_scale[1]), (0, uv_scale[1])]
        for uv in uvs:
            self.vt.append(uv)
        self.vn.append(normal)
        ni = len(self.vn)
        f = [(base + 1, len(self.vt) - 3, ni), (base + 2, len(self.vt) - 2, ni),
             (base + 3, len(self.vt) - 1, ni), (base + 4, len(self.vt) - 0, ni)]
        self.faces.append(f)

    def add_tri(self, p0, p1, p2, normal, uv0=(0, 0), uv1=(1, 0), uv2=(0.5, 1)):
        base = len(self.v)
        for p in (p0, p1, p2):
            self.v.append(p)
        for uv in (uv0, uv1, uv2):
            self.vt.append(uv)
        self.vn.append(normal)
        ni = len(self.vn)
        f = [(base + 1, len(self.vt) - 2, ni), (base + 2, len(self.vt) - 1, ni),
             (base + 3, len(self.vt) - 0, ni)]
        self.faces.append(f)

    def add_box(self, cx, cy, cz, sx, sy, sz, uv_scale=(1.0, 1.0)):
        x0, x1 = cx - sx / 2, cx + sx / 2
        y0, y1 = cy - sy / 2, cy + sy / 2
        z0, z1 = cz - sz / 2, cz + sz / 2
        # +Y top
        self.add_quad((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1), (0, 1, 0), uv_scale)
        # -Y bottom
        self.add_quad((x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0), (0, -1, 0), uv_scale)
        # +Z front
        self.add_quad((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1), uv_scale)
        # -Z back
        self.add_quad((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (0, 0, -1), uv_scale)
        # +X right
        self.add_quad((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (1, 0, 0), uv_scale)
        # -X left
        self.add_quad((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0), uv_scale)

    def write(self, path):
        with open(path, "w") as f:
            f.write("# VinzHorror procedural mesh: %s\n" % self.name)
            f.write("o %s\n" % self.name)
            for p in self.v:
                f.write("v %.5f %.5f %.5f\n" % p)
            for uv in self.vt:
                f.write("vt %.5f %.5f\n" % uv)
            for n in self.vn:
                f.write("vn %.5f %.5f %.5f\n" % n)
            for face in self.faces:
                parts = ["%d/%d/%d" % (vi, ti, ni) for (vi, ti, ni) in face]
                f.write("f " + " ".join(parts) + "\n")


def gen_floor_tile():
    m = Mesh("floor_tile")
    h = TILE / 2
    m.add_quad((-h, 0, h), (h, 0, h), (h, 0, -h), (-h, 0, -h), (0, 1, 0), (1, 1))
    return m


def gen_ceiling_tile():
    m = Mesh("ceiling_tile")
    h = TILE / 2
    m.add_quad((-h, WALL_H, -h), (h, WALL_H, -h), (h, WALL_H, h), (-h, WALL_H, h), (0, -1, 0), (1, 1))
    return m


def gen_wall_segment():
    # a wall panel filling one tile edge, facing +Z (place/rotate per-edge at runtime)
    m = Mesh("wall_segment")
    h = TILE / 2
    m.add_quad((-h, 0, 0), (h, 0, 0), (h, WALL_H, 0), (-h, WALL_H, 0), (0, 0, 1), (1, WALL_H / TILE))
    # thin backing so it reads as solid from both sides
    m.add_quad((h, 0, -0.15), (-h, 0, -0.15), (-h, WALL_H, -0.15), (h, WALL_H, -0.15), (0, 0, -1), (1, WALL_H / TILE))
    return m


def gen_door_frame():
    m = Mesh("door")
    h = TILE / 2
    dw, dh = 1.6, 2.6
    # door slab, slightly recessed, hinge along left edge at x=-dw/2
    m.add_box(0, dh / 2, -0.05, dw, dh, 0.1, (1.2, 1.6))
    # frame top lintel
    m.add_box(0, dh + 0.1, 0, dw + 0.3, 0.2, 0.3, (1, 1))
    return m


def gen_pipe_prop():
    m = Mesh("pipe_prop")
    segs = 10
    r = 0.12
    length = TILE * 0.9
    for i in range(segs):
        a0 = 2 * math.pi * i / segs
        a1 = 2 * math.pi * (i + 1) / segs
        x0, z0 = r * math.cos(a0), r * math.sin(a0)
        x1, z1 = r * math.cos(a1), r * math.sin(a1)
        nx, nz = math.cos((a0 + a1) / 2), math.sin((a0 + a1) / 2)
        m.add_quad((x0, -length / 2, z0), (x1, -length / 2, z1), (x1, length / 2, z1), (x0, length / 2, z0),
                    (nx, 0, nz), (0.25, length / TILE))
    return m


def gen_crate_prop():
    m = Mesh("crate_prop")
    m.add_box(0, 0.5, 0, 1.0, 1.0, 1.0, (1, 1))
    return m


def gen_monster():
    """Low-poly humanoid: box torso/limbs, enough silhouette to read in a
    flashlight beam. Rigged visually (not skeletally) via separate part
    groups baked into one mesh -- animation is done with vertex-shader
    bone-less bob/sway driven from Kotlin, good enough for a chase model."""
    m = Mesh("monster")
    # torso
    m.add_box(0, 1.05, 0, 0.46, 0.62, 0.26)
    # pelvis
    m.add_box(0, 0.68, 0, 0.42, 0.16, 0.24)
    # head (elongated, unsettling proportions)
    m.add_box(0, 1.55, 0.02, 0.26, 0.32, 0.26)
    # neck stretch
    m.add_box(0, 1.38, 0, 0.14, 0.12, 0.14)
    # arms (long, past-the-knee)
    m.add_box(-0.32, 0.78, 0, 0.14, 0.95, 0.14)
    m.add_box(0.32, 0.78, 0, 0.14, 0.95, 0.14)
    # forearm bend hint
    m.add_box(-0.34, 0.32, 0.05, 0.12, 0.3, 0.12)
    m.add_box(0.34, 0.32, 0.05, 0.12, 0.3, 0.12)
    # legs
    m.add_box(-0.15, 0.3, 0, 0.18, 0.6, 0.18)
    m.add_box(0.15, 0.3, 0, 0.18, 0.6, 0.18)
    return m


def gen_flashlight_viewmodel():
    m = Mesh("flashlight")
    m.add_box(0, 0, 0.25, 0.06, 0.06, 0.4)
    m.add_box(0, 0, 0.47, 0.08, 0.08, 0.05)
    m.add_box(0, -0.08, 0.05, 0.045, 0.14, 0.09)
    return m


if __name__ == "__main__":
    import os, sys
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    gen_floor_tile().write(os.path.join(out, "floor_tile.obj"))
    gen_ceiling_tile().write(os.path.join(out, "ceiling_tile.obj"))
    gen_wall_segment().write(os.path.join(out, "wall_segment.obj"))
    gen_door_frame().write(os.path.join(out, "door.obj"))
    gen_pipe_prop().write(os.path.join(out, "pipe_prop.obj"))
    gen_crate_prop().write(os.path.join(out, "crate_prop.obj"))
    gen_monster().write(os.path.join(out, "monster.obj"))
    gen_flashlight_viewmodel().write(os.path.join(out, "flashlight.obj"))
    print("models generated ->", out)
