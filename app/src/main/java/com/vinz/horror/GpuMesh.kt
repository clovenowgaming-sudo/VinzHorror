package com.vinz.horror

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Uploads one ObjModel to the GPU as a VBO+IBO and knows how to draw itself. */
class GpuMesh(model: ObjModel) {
    private val vbo = IntArray(1)
    private val ibo = IntArray(1)
    private val indexCount = model.indexData.size

    init {
        val vBuf = ByteBuffer.allocateDirect(model.vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vBuf.put(model.vertexData).position(0)

        val iBuf = ByteBuffer.allocateDirect(model.indexData.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        iBuf.put(model.indexData).position(0)

        GLES20.glGenBuffers(1, vbo, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, model.vertexData.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glGenBuffers(1, ibo, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, model.indexData.size * 2, iBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw(posLoc: Int, normalLoc: Int, uvLoc: Int) {
        val stride = ObjModel.STRIDE_FLOATS * 4
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0])

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(normalLoc)
        GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, stride, 6 * 4)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(normalLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}
