package riskod.world.bullets;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mat;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Layer;

import static mindustry.Vars.headless;
import static mindustry.Vars.state;

public class LensWarp {
    static final Seq<Warp> warps = new Seq<>();
    static FrameBuffer buffer;
    static TextureRegion region;
    static Shader shader;
    static boolean registered;
    static final Vec2 tmp = new Vec2();
    static final Mat ortho = new Mat();
    static final Mat oldProj = new Mat();

    public static class Warp {
        public float x, y, radius, strength, life, maxLife, rotation, coneHalf;
    }

    public static void register() {
        if (registered || headless) return;
        registered = true;

        try {
            //im braindead after this
            shader = new Shader(
                    """
                    attribute vec4 a_position;
                    attribute vec4 a_color;
                    attribute vec2 a_texCoord0;
                    uniform mat4 u_projTrans;
                    void main(){
                      gl_Position = u_projTrans * a_position;
                    }
                    """,
                    """
                    uniform sampler2D u_texture;
                    uniform vec2 u_resolution;
                    uniform vec2 u_center;
                    uniform vec2 u_dir;
                    uniform float u_radius;
                    uniform float u_progress;
                    uniform float u_strength;
                    uniform float u_cone;
                    void main(){
                      vec2 delta = gl_FragCoord.xy - u_center;
                      float dist = length(delta);
                      float t = dist / max(u_radius, 1.0);
                      if(t > 1.0) discard;

                      vec2 dir = dist > 0.001 ? delta / dist : vec2(0.0);

                      float coneMask = 1.0;
                      if(u_cone > -0.999){
                        coneMask = smoothstep(u_cone - 0.12, u_cone + 0.06, dot(dir, u_dir));
                      }

                      float d = (t - u_progress) / 0.14;
                      float k = d * exp(-d * d);
                      float edge = 1.0 - smoothstep(0.8, 1.0, t);
                      float amp = u_strength * clamp(u_radius * 0.05, 4.0, 10.0) * (1.0 - u_progress) * coneMask * edge;
                      float shift = k * amp;
                      if(abs(shift) < 0.05) discard;

                      vec2 uv = gl_FragCoord.xy / u_resolution;
                      vec2 sampleUV = clamp(uv - dir * shift / u_resolution, vec2(0.001), vec2(0.999));
                      gl_FragColor = vec4(texture2D(u_texture, sampleUV).rgb, 1.0);
                    }
                    """
            );
        } catch (Throwable t) {
            Log.err("LensWarp shader failed", t);
            shader = null;
        }

        buffer = new FrameBuffer();
        region = new TextureRegion();

        Events.run(Trigger.draw, LensWarp::queue);
        Log.info("LensWarp registered shader=@", shader != null);
    }

    public static void add(float x, float y, float radius, float strength, float life, float rotation, float coneHalf) {
        Warp w = new Warp();
        w.x = x;
        w.y = y;
        w.radius = Math.max(1f, radius);
        w.strength = Mathf.clamp(strength, 0f, 4f);
        w.life = Math.max(life, 1f);
        w.maxLife = w.life;
        w.rotation = rotation;
        w.coneHalf = coneHalf;
        warps.add(w);
    }

    public static void add(float x, float y, float radius, float strength) {
        add(x, y, radius, strength, 18f, 0f, 180f);
    }

    static void queue() {
        if (warps.isEmpty()) return;

        if (!state.isPaused()) {
            for (int i = warps.size - 1; i >= 0; i--) {
                Warp w = warps.get(i);
                w.life -= Time.delta;
                if (w.life <= 0f) warps.remove(i);
            }
            if (warps.isEmpty()) return;
        }

        Draw.draw(Layer.effect + 50f, LensWarp::apply);
    }

    static void apply() {
        if (warps.isEmpty()) return;

        int sw = Core.graphics.getWidth();
        int sh = Core.graphics.getHeight();
        if (sw <= 0 || sh <= 0) return;

        Draw.flush();

        if (shader != null) {
            buffer.resize(sw, sh);
            Texture tex = buffer.getTexture();
            Gl.bindFramebuffer(Gl.framebuffer, 0);
            tex.bind(0);
            Gl.pixelStorei(Gl.packAlignment, 1);
            Gl.copyTexImage2D(Gl.texture2d, 0, Gl.rgba, 0, 0, sw, sh, 0);
            region.set(tex);
        }

        oldProj.set(Draw.proj());
        ortho.setOrtho(0f, 0f, sw, sh);
        Draw.proj(ortho);

        float pxPerWorld = sw / Math.max(Core.camera.width, 1f);
        float maxPx = Math.max(sw, sh) * 0.5f;

        for (Warp warp : warps) {
            float fin = 1f - warp.life / warp.maxLife;
            Core.camera.project(tmp.set(warp.x, warp.y));
            float cx = tmp.x;
            float cy = tmp.y;
            float radPx = Mathf.clamp(warp.radius * pxPerWorld, 24f, maxPx);

            if (shader != null) {
                Draw.shader(shader);
                shader.bind();
                shader.setUniformf("u_resolution", (float) sw, (float) sh);
                shader.setUniformf("u_center", cx, cy);
                shader.setUniformf("u_dir", Mathf.cosDeg(warp.rotation), Mathf.sinDeg(warp.rotation));
                shader.setUniformf("u_radius", radPx);
                shader.setUniformf("u_progress", fin);
                shader.setUniformf("u_strength", warp.strength);
                shader.setUniformf("u_cone", warp.coneHalf >= 179f ? -1f : Mathf.cosDeg(warp.coneHalf));
                shader.setUniformi("u_texture", 0);

                Draw.color(Color.white);
                Draw.rect(region, cx, cy, radPx * 2.2f, radPx * 2.2f);
                Draw.flush();
                Draw.shader();
            }
        }

        Draw.proj(oldProj);
        Draw.reset();
    }
}