#!/usr/bin/env python3
"""
Procedural 4K texture generator for VinzHorror -- haunted-house set.
Pure stdlib (zlib+struct) PNG writer + numpy. No PIL needed.
"""
import struct, zlib, math, os, sys, time
import numpy as np

SIZE = int(os.environ.get("TEX_SIZE", "4096"))


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


def value_noise(w, h, cell, seed, anis_x=1.0, anis_y=1.0):
    rnd = np.random.RandomState(seed)
    cellx = max(2, int(cell * anis_x))
    celly = max(2, int(cell * anis_y))
    gw, gh = w // cellx + 2, h // celly + 2
    grid = rnd.random_sample((gh, gw)).astype(np.float32)
    xs = np.arange(w) / cellx
    ys = np.arange(h) / celly
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


def fbm(w, h, octaves, base_cell, seed, anis_x=1.0, anis_y=1.0):
    total = np.zeros((h, w), dtype=np.float32)
    amp = 1.0
    amp_sum = 0.0
    for o in range(octaves):
        cell = max(2, base_cell // (2 ** o))
        total += value_noise(w, h, cell, seed + o * 97, anis_x, anis_y) * amp
        amp_sum += amp
        amp *= 0.5
    return total / amp_sum


def make_normal_from_height(hf, strength=5.0):
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


def gen_wallpaper(seed):
    """Old, peeling floral-ish wallpaper over damaged plaster."""
    base = fbm(SIZE, SIZE, 5, 72, seed)
    damask = fbm(SIZE, SIZE, 3, 96, seed + 11, anis_x=1.4, anis_y=1.0)
    stains = fbm(SIZE, SIZE, 2, 220, seed + 900)
    tear = fbm(SIZE, SIZE, 2, 140, seed + 410)

    wp_r = 90 + damask * 40
    wp_g = 60 + damask * 30
    wp_b = 55 + damask * 26
    shade = 0.55 + base * 0.55

    r = wp_r * shade
    g = wp_g * shade
    b = wp_b * shade

    plaster_mask = tear < 0.12
    plaster_shade = 0.6 + base * 0.4
    r = np.where(plaster_mask, 150 * plaster_shade, r)
    g = np.where(plaster_mask, 142 * plaster_shade, g)
    b = np.where(plaster_mask, 128 * plaster_shade, b)

    damp = np.clip((stains - 0.55) * 2.0, 0, 1)
    r = r * (1 - damp * 0.5) + 30 * damp * 0.5
    g = g * (1 - damp * 0.5) + 35 * damp * 0.5
    b = b * (1 - damp * 0.5) + 28 * damp * 0.5

    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(base + tear * 0.6, strength=4.5)
    return diffuse, normal


def gen_wood_floor(seed, plank_h=None):
    """Worn wooden floorboards with long grain + plank seams."""
    if plank_h is None:
        plank_h = SIZE // 8
    grain = fbm(SIZE, SIZE, 5, 48, seed, anis_x=0.15, anis_y=1.0)
    knots = fbm(SIZE, SIZE, 2, 300, seed + 55, anis_x=0.2, anis_y=1.0)
    ys = np.arange(SIZE).reshape(SIZE, 1)
    seam = (ys % plank_h) < 4

    base_tone = 95 + grain * 70
    knot_mask = np.clip((knots - 0.6) * 3.0, 0, 1)
    tone = base_tone * (1 - knot_mask * 0.4)

    r = tone * 1.05
    g = tone * 0.78
    b = tone * 0.55

    r = np.where(seam, r * 0.35, r)
    g = np.where(seam, g * 0.35, g)
    b = np.where(seam, b * 0.35, b)

    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(grain * 0.7 - seam.astype(np.float32) * 0.8, strength=5.0)
    return diffuse, normal


def gen_plaster_ceiling(seed):
    base = fbm(SIZE, SIZE, 5, 60, seed)
    cracks = fbm(SIZE, SIZE, 2, 180, seed + 500)
    stains = fbm(SIZE, SIZE, 3, 260, seed + 71)

    shade = 0.55 + base * 0.5
    shade = np.where(cracks < 0.07, shade * 0.3, shade)

    tint = (128, 122, 110)
    r = tint[0] * shade
    g = tint[1] * shade
    b = tint[2] * shade

    water = np.clip((stains - 0.58) * 2.3, 0, 1)
    r = r * (1 - water * 0.55) + 70 * water * 0.55
    g = g * (1 - water * 0.55) + 68 * water * 0.55
    b = b * (1 - water * 0.55) + 55 * water * 0.55

    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(base, strength=4.0)
    return diffuse, normal


def gen_wood_door(seed):
    grain = fbm(SIZE, SIZE, 5, 40, seed, anis_x=0.12, anis_y=1.0)
    xs = np.arange(SIZE).reshape(1, SIZE)
    panel_w = SIZE // 2
    groove = (xs % panel_w) < 5

    tone = 80 + grain * 65
    r = tone * 1.1
    g = tone * 0.72
    b = tone * 0.45

    r = np.where(groove, r * 0.3, r)
    g = np.where(groove, g * 0.3, g)
    b = np.where(groove, b * 0.3, b)

    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(grain * 0.6 - groove.astype(np.float32) * 0.7, strength=5.0)
    return diffuse, normal


def gen_ghost_flesh(seed):
    base = fbm(SIZE, SIZE, 6, 36, seed)
    veins = fbm(SIZE, SIZE, 2, 128, seed + 777)
    blotch = fbm(SIZE, SIZE, 3, 150, seed + 321)

    r = 150 + base * 40
    g = 150 + base * 42
    b = 158 + base * 46

    decay = np.clip((blotch - 0.5) * 2.0, 0, 1)
    r = r * (1 - decay * 0.6) + 60 * decay * 0.6
    g = g * (1 - decay * 0.6) + 75 * decay * 0.6
    b = g * 1.0

    vmask = (veins > 0.47) & (veins < 0.51)
    r = np.where(vmask, r * 0.4, r)
    g = np.where(vmask, g * 0.5, g)
    b = np.where(vmask, b * 0.55, b)

    diffuse = to_u8(r, g, b)
    normal = make_normal_from_height(base, strength=6.5)
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


def gen_icon(size=192):
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    cx, cy = size / 2, size / 2
    d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2) / (size / 2)
    bg = np.clip(14 + d * 6, 0, 255)
    eye_r = 0.22
    glow = np.clip(1.0 - (np.abs(d - eye_r) * 6.0), 0, 1)
    r = bg * (1 - glow) + 150 * glow
    g = bg * (1 - glow) + 190 * glow
    b = bg * (1 - glow) + 200 * glow
    pupil = (d < eye_r * 0.45).astype(np.float32)
    r = r * (1 - pupil)
    g = g * (1 - pupil)
    b = b * (1 - pupil)
    return to_u8(r, g, b)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    t0 = time.time()

    def step(name, fn):
        ts = time.time()
        d, n = fn()
        png_write(os.path.join(out, name + "_diffuse.png"), d)
        png_write(os.path.join(out, name + "_normal.png"), n)
        print(name, "done in %.1fs" % (time.time() - ts), flush=True)

    step("wall", lambda: gen_wallpaper(1))
    step("floor", lambda: gen_wood_floor(2))
    step("ceiling", lambda: gen_plaster_ceiling(3))
    step("door", lambda: gen_wood_door(4))
    step("monster", lambda: gen_ghost_flesh(5))
    step("pipe", lambda: gen_rust_metal(6))

    d = gen_blood_decal(7)
    png_write(os.path.join(out, "blood_decal.png"), d)
    print("blood_decal done", flush=True)

    print("ALL TEXTURES DONE in %.1fs" % (time.time() - t0), flush=True)
