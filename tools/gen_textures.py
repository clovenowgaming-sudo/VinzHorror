#!/usr/bin/env python3
"""
Procedural PBR-style texture generator for VinzHorror.
Pure stdlib (zlib + struct) PNG writer + numpy for speed.
Generates tileable diffuse + normal maps for a decayed-asylum
horror look: concrete, rust, flesh, tile floor, blood decal.
"""
import struct, zlib, math
import numpy as np

SIZE = 512


def png_write(path, rgb_u8):
    h, w, _ = rgb_u8.shape

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    raw = bytearray()
    row_bytes = rgb_u8.tobytes()
    stride = w * 3
    for y in range(h):
        raw.append(0)
        raw += row_bytes[y * stride:(y + 1) * stride]
    idat = zlib.compress(bytes(raw), 6)
    with open(path, "wb") as f:
        f.write(sig)
        f.write(chunk(b"IHDR", ihdr))
        f.write(chunk(b"IDAT", idat))
        f.write(chunk(b"IEND", b""))


def value_noise(w, h, cell, seed):
    rnd = np.random.RandomState(seed)
    gw, gh = w // cell + 2, h // cell + 2
    grid = rnd.random_sample((gh, gw)).astype(np.float32)
    xs = np.arange(w) / cell
    ys = np.arange(h) / cell
    ix = xs.astype(np.int32)
    iy = ys.astype(np.int32)
    fx = (xs - ix).astype(np.float32)
    fy = (ys - iy).astype(np.float32)
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    v00 = grid[np.ix_(iy, ix)]
    v10 = grid[np.ix_(iy, ix + 1)]
    v01 = grid[np.ix_(iy + 1, ix)]
    v11 = grid[np.ix_(iy + 1, ix + 1)]
    fx2 = fx.reshape(1, w)
    fy2 = fy.reshape(h, 1)
    top = v00 + (v10 - v00) * fx2
    bot = v01 + (v11 - v01) * fx2
    return top + (bot - top) * fy2


def fbm(w, h, octaves, base_cell, seed):
    total = np.zeros((h, w), dtype=np.float32)
    amp = 1.0
    amp_sum = 0.0
    for o in range(octaves):
        cell = max(2, base_cell // (2 ** o))
        total += value_noise(w, h, cell, seed + o * 97) * amp
        amp_sum += amp
        amp *= 0.5
    return total / amp_sum


def make_normal_from_height(hf, strength=5.0):
    h, w = hf.shape
    hx1 = np.roll(hf, -1, axis=1)
    hx0 = np.roll(hf, 1, axis=1)
    hy1 = np.roll(hf, -1, axis=0)
    hy0 = np.roll(hf, 1, axis=0)
    dx = (hx1 - hx0) * strength
    dy = (hy1 - hy0) * strength
    dz = np.ones_like(dx)
    length = np.sqrt(dx * dx + dy * dy + dz * dz)
    nx, ny, nz = dx / length, dy / length, dz / length
    out = np.stack([(nx * 0.5 + 0.5) * 255,
                     (ny * 0.5 + 0.5) * 255,
                     (nz * 0.5 + 0.5) * 255], axis=-1)
    return np.clip(out, 0, 255).astype(np.uint8)


def to_u8(*channels):
    return np.clip(np.stack(channels, axis=-1), 0, 255).astype(np.uint8)


def gen_concrete(seed, tint=(120, 116, 108)):
    base = fbm(SIZE, SIZE, 5, 64, seed)
    cracks = fbm(SIZE, SIZE, 2, 160, seed + 500)
    shade = 0.55 + base * 0.55
    shade = np.where(cracks < 0.08, shade * 0.35, shade)
    diffuse = to_u8(tint[0] * shade, tint[1] * shade, tint[2] * shade)
    normal = make_normal_from_height(base, strength=4.0)
    return diffuse, normal


def gen_rust_metal(seed):
    base = fbm(SIZE, SIZE, 5, 48, seed)
    stain = fbm(SIZE, SIZE, 3, 96, seed + 300)
    metal_r, metal_g, metal_b = 70 + base * 60, 68 + base * 58, 66 + base * 55
    rust_amt = np.clip((stain - 0.42) * 2.2, 0, 1)
    r = metal_r * (1 - rust_amt) + 120 * rust_amt
    g = metal_g * (1 - rust_amt) + 55 * rust_amt
    b = metal_b * (1 - rust_amt) + 25 * rust_amt
    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(base, strength=5.0)
    return diffuse, normal


def gen_flesh_organic(seed):
    base = fbm(SIZE, SIZE, 6, 40, seed)
    veins = fbm(SIZE, SIZE, 2, 128, seed + 777)
    r, g, b = 90 + base * 70, 40 + base * 30, 38 + base * 28
    vmask = (veins > 0.46) & (veins < 0.52)
    r = np.where(vmask, r * 0.5, r)
    g = np.where(vmask, g * 0.3, g)
    b = np.where(vmask, b * 0.3, b)
    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(base, strength=7.0)
    return diffuse, normal


def gen_blood_decal(seed):
    base = fbm(SIZE, SIZE, 4, 80, seed)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
    cx, cy = SIZE * 0.5, SIZE * 0.4
    d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2) / (SIZE * 0.5)
    splatter = np.clip(1.0 - d * (1.4 + base * 0.8), 0, 1)
    r = 20 + splatter * 110
    g = 4 + splatter * 10
    b = 4 + splatter * 10
    return to_u8(r, g, b)


def gen_tile_floor(seed):
    base = fbm(SIZE, SIZE, 4, 56, seed)
    tile = SIZE // 4
    grout = 6
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    gx, gy = xs % tile, ys % tile
    is_grout = (gx < grout) | (gy < grout)
    shade_grout = 0.25 + base * 0.1
    shade_tile = 0.5 + base * 0.5
    shade = np.where(is_grout, shade_grout, shade_tile)
    col_grout = (60, 58, 55)
    col_tile = (150, 142, 128)
    r = np.where(is_grout, col_grout[0], col_tile[0]) * shade
    g = np.where(is_grout, col_grout[1], col_tile[1]) * shade
    b = np.where(is_grout, col_grout[2], col_tile[2]) * shade
    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(base, strength=3.0)
    return diffuse, normal


if __name__ == "__main__":
    import os, sys, time
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    t0 = time.time()

    d, n = gen_concrete(1)
    png_write(os.path.join(out, "wall_diffuse.png"), d)
    png_write(os.path.join(out, "wall_normal.png"), n)

    d, n = gen_tile_floor(2)
    png_write(os.path.join(out, "floor_diffuse.png"), d)
    png_write(os.path.join(out, "floor_normal.png"), n)

    d, n = gen_concrete(3, tint=(90, 88, 84))
    png_write(os.path.join(out, "ceiling_diffuse.png"), d)
    png_write(os.path.join(out, "ceiling_normal.png"), n)

    d, n = gen_rust_metal(4)
    png_write(os.path.join(out, "door_diffuse.png"), d)
    png_write(os.path.join(out, "door_normal.png"), n)

    d, n = gen_flesh_organic(5)
    png_write(os.path.join(out, "monster_diffuse.png"), d)
    png_write(os.path.join(out, "monster_normal.png"), n)

    d = gen_blood_decal(6)
    png_write(os.path.join(out, "blood_decal.png"), np.stack([d[:, :, 0], d[:, :, 1], d[:, :, 2]], axis=-1))

    print("textures generated ->", out, " in %.1fs" % (time.time() - t0))


def gen_icon(size=192):
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    cx, cy = size / 2, size / 2
    d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2) / (size / 2)
    bg = np.clip(18 + d * 8, 0, 255)
    eye_r = 0.22
    glow = np.clip(1.0 - (np.abs(d - eye_r) * 6.0), 0, 1)
    r = bg * (1 - glow) + 210 * glow
    g = bg * (1 - glow) + 40 * glow
    b = bg * (1 - glow) + 30 * glow
    pupil = (d < eye_r * 0.45).astype(np.float32)
    r = r * (1 - pupil)
    g = g * (1 - pupil)
    b = b * (1 - pupil)
    return to_u8(r, g, b)
