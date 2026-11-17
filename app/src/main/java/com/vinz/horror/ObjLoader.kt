package com.vinz.horror

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * One real mesh loaded from a real .obj file (positions + normals + UVs).
 * Vertex layout: 8 floats per vertex = pos.xyz, normal.xyz, uv.xy
 */
class ObjModel(val vertexData: FloatArray, val indexData: ShortArray) {
    companion object {
        const val STRIDE_FLOATS = 8
    }
}

object ObjLoader {
    fun load(assets: AssetManager, path: String): ObjModel {
        val positions = ArrayList<FloatArray>()
        val normals = ArrayList<FloatArray>()
        val uvs = ArrayList<FloatArray>()
        val verts = ArrayList<Float>()
        val indices = ArrayList<Short>()

        assets.open(path).use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val parts = line.split(" ").filter { it.isNotEmpty() }
                    when (parts[0]) {
                        "v" -> positions.add(floatArrayOf(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
                        "vn" -> normals.add(floatArrayOf(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
                        "vt" -> uvs.add(floatArrayOf(parts[1].toFloat(), parts[2].toFloat()))
                        "f" -> {
                            val corners = parts.drop(1).map { tok ->
                                val idx = tok.split("/")
                                Triple(idx[0].toInt(), idx[1].toInt(), idx[2].toInt())
                            }

                            fun emit(c: Triple<Int, Int, Int>): Short {
                                val p = positions[c.first - 1]
                                val t = uvs[c.second - 1]
                                val n = normals[c.third - 1]
                                verts.add(p[0]); verts.add(p[1]); verts.add(p[2])
                                verts.add(n[0]); verts.add(n[1]); verts.add(n[2])
                                verts.add(t[0]); verts.add(t[1])
                                return ((verts.size / ObjModel.STRIDE_FLOATS) - 1).toShort()
                            }

                            val i0 = emit(corners[0])
                            val i1 = emit(corners[1])
                            val i2 = emit(corners[2])
                            indices.add(i0); indices.add(i1); indices.add(i2)
                            if (corners.size == 4) {
                                val i3 = emit(corners[3])
                                indices.add(i0); indices.add(i2); indices.add(i3)
                            }
                        }
                    }
                }
            }
        }
        return ObjModel(verts.toFloatArray(), indices.toShortArray())
    }
}
