package com.vinz.horror

import android.opengl.GLES20

object ShaderUtil {
    fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile error: $log")
        }
        return shader
    }

    fun link(vertSrc: String, fragSrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            throw RuntimeException("Program link error: $log")
        }
        return program
    }
}

object Shaders {
    const val VERTEX = """
attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aUV;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProj;
uniform mat3 uNormalMatrix;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUV;

void main() {
    vec4 worldPos = uModel * vec4(aPosition, 1.0);
    vWorldPos = worldPos.xyz;
    vNormal = uNormalMatrix * aNormal;
    vUV = aUV;
    gl_Position = uProj * uView * worldPos;
}
"""

    const val FRAGMENT = """
precision mediump float;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUV;

uniform sampler2D uDiffuseMap;
uniform sampler2D uNormalMap;
uniform vec3 uCameraPos;
uniform vec3 uFlashDir;
uniform float uFlashOn;
uniform float uFlashIntensity;
uniform float uUseNormalMap;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform vec3 uTintColor;
uniform float uAlpha;

void main() {
    vec3 N = normalize(vNormal);
    if (uUseNormalMap > 0.5) {
        vec3 tangent = (abs(N.y) > 0.9) ? vec3(1.0, 0.0, 0.0) : normalize(cross(vec3(0.0, 1.0, 0.0), N));
        vec3 bitangent = cross(N, tangent);
        mat3 TBN = mat3(tangent, bitangent, N);
        vec3 mapN = texture2D(uNormalMap, vUV).rgb * 2.0 - 1.0;
        N = normalize(TBN * mapN);
    }

    vec3 toCam = uCameraPos - vWorldPos;
    float dist = length(toCam);
    vec3 L = toCam / max(dist, 0.0001);

    float ambient = 0.06;
    float diffuse = max(dot(N, L), 0.0);

    float spotCos = dot(normalize(-toCam), normalize(uFlashDir));
    float spotCone = smoothstep(0.80, 0.95, spotCos);
    float atten = uFlashIntensity * spotCone / (1.0 + 0.35 * dist + 0.12 * dist * dist);
    float flashLight = diffuse * atten * uFlashOn;

    float lightSum = ambient + flashLight;
    vec3 base = texture2D(uDiffuseMap, vUV).rgb * uTintColor;
    vec3 color = base * lightSum;

    float fog = 1.0 - exp(-uFogDensity * dist * dist * 0.01);
    fog = clamp(fog, 0.0, 1.0);
    color = mix(color, uFogColor, fog);

    gl_FragColor = vec4(color, uAlpha);
}
"""
}
