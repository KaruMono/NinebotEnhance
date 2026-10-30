package dev.ichinomiya.ninebotcast;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.view.Surface;
import dev.ichinomiya.ninebotcast.core.DisplaySettings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Composes the captured app area into the dashboard frame on the encoder's GL surface. */
final class FrameRenderer implements AutoCloseable {
    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture texture;
    private Surface input;
    private int program, textureId, position, coordinates, matrixUniform;
    private final float[] matrix = new float[16];
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(new float[]{-1, -1, 0, 0, 1, -1, 1, 0, -1, 1, 0, 1, 1, 1, 1, 1});
    private final DisplaySettings settings;
    private boolean closed;

    FrameRenderer(Surface output, DisplaySettings settings, Handler handler, Runnable beforeFrame, Runnable failed) {
        this.settings = settings;
        try {
            initialize(output);
            texture = new SurfaceTexture(textureId);
            texture.setDefaultBufferSize(settings.virtualWidth, settings.virtualHeight);
            input = new Surface(texture);
            texture.setOnFrameAvailableListener(available -> {
                if (closed) return;
                try {
                    // Free encoder output before submitting another GL buffer, so eglSwapBuffers cannot
                    // block this worker behind output buffers that only the same worker can release.
                    beforeFrame.run();
                    if (!closed) render();
                }
                catch (RuntimeException e) { LogStore.add("CAST 合成失败：" + e); failed.run(); }
            }, handler);
        } catch (RuntimeException e) { close(); throw e; }
    }

    Surface inputSurface() { return input; }

    private void initialize(Surface output) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(eglDisplay, new int[2], 0, new int[2], 0))
            throw new IllegalStateException("无法初始化 EGL");
        int[] attributes = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, 0x3142, 1, EGL14.EGL_NONE}; // EGL_RECORDABLE_ANDROID
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0)
            throw new IllegalStateException("未找到编码器 EGL 配置");
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], output, new int[]{EGL14.EGL_NONE}, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT || eglSurface == EGL14.EGL_NO_SURFACE
                || !EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
            throw new IllegalStateException("无法创建编码器 EGL 上下文");
        int vertex = shader(GLES20.GL_VERTEX_SHADER,
                "attribute vec2 aPosition; attribute vec2 aCoordinates; uniform mat4 uMatrix; varying vec2 vCoordinates;"
                        + "void main(){gl_Position=vec4(aPosition,0.,1.);vCoordinates=(uMatrix*vec4(aCoordinates,0.,1.)).xy;}");
        int fragment = shader(GLES20.GL_FRAGMENT_SHADER,
                "#extension GL_OES_EGL_image_external : require\nprecision mediump float;"
                        + "uniform samplerExternalOES uTexture; varying vec2 vCoordinates;"
                        + "void main(){gl_FragColor=texture2D(uTexture,vCoordinates);}");
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment); GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment);
        int[] linked = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        position = GLES20.glGetAttribLocation(program, "aPosition");
        coordinates = GLES20.glGetAttribLocation(program, "aCoordinates");
        matrixUniform = GLES20.glGetUniformLocation(program, "uMatrix");
        int[] ids = new int[1]; GLES20.glGenTextures(1, ids, 0); textureId = ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
        int[] compiled = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }

    private void render() {
        texture.updateTexImage(); texture.getTransformMatrix(matrix);
        int color = settings.backgroundColor;
        GLES20.glClearColor(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // GL's origin is bottom-left; this leaves contentTop() rows above and bottomInset rows below the app.
        GLES20.glViewport(0, settings.bottomInset, settings.virtualWidth, settings.virtualHeight);
        GLES20.glUseProgram(program); GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, matrix, 0);
        vertices.position(0); GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices);
        vertices.position(2); GLES20.glVertexAttribPointer(coordinates, 2, GLES20.GL_FLOAT, false, 16, vertices);
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(coordinates);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, texture.getTimestamp());
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) throw new IllegalStateException("无法提交编码画面");
    }

    /** Called on the same worker that owns the EGL context, after releasing the VirtualDisplay. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (texture != null) texture.setOnFrameAvailableListener(null);
        if (input != null) { input.release(); input = null; }
        if (texture != null) { texture.release(); texture = null; }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (program != 0) GLES20.glDeleteProgram(program);
            if (textureId != 0) GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread(); EGL14.eglTerminate(eglDisplay);
            eglDisplay = EGL14.EGL_NO_DISPLAY;
        }
    }
}
