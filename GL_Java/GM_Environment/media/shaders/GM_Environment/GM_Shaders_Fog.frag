#version 330

#include "GM_Shaders_Common.frag"

uniform vec4 GM_Fog_Prev;
uniform vec4 GM_Fog_Step;
uniform vec4 GM_Movers[32];
uniform int GM_Mover_Count;

in vec2 GM_Coord;

vec2 GM_Curl(vec2 p)
{
	float e = 0.35;
	float n1 = GM_Noise(p + vec2(0.0, e));
	float n2 = GM_Noise(p - vec2(0.0, e));
	float n3 = GM_Noise(p + vec2(e, 0.0));
	float n4 = GM_Noise(p - vec2(e, 0.0));
	return vec2(n1 - n2, n4 - n3) / (2.0 * e);
}

vec2 GM_Source(vec2 u, float t)
{
	float coarse = GM_Fbm(u * 0.06 + vec2(t * 0.013, -t * 0.009) + GM_Fog_Step.y);
	float fine = GM_Fbm(u * 0.21 - vec2(t * 0.02, t * 0.015) + GM_Fog_Step.y * 3.0);
	coarse = smoothstep(0.18, 0.82, coarse);
	fine = smoothstep(0.25, 0.75, fine);
	return vec2(0.2 + 0.8 * coarse, fine);
}

vec4 GM_Prev(vec2 uv)
{
	vec2 size = vec2(GM_Fog_Step.w);
	vec2 samplePos = uv * size;
	vec2 texPos1 = floor(samplePos - 0.5) + 0.5;
	vec2 f = samplePos - texPos1;
	vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
	vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
	vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
	vec2 w3 = f * f * (-0.5 + 0.5 * f);
	vec2 w12 = w1 + w2;
	vec2 offset12 = w2 / w12;
	vec2 texPos0 = (texPos1 - 1.0) / size;
	vec2 texPos3 = (texPos1 + 2.0) / size;
	vec2 texPos12 = (texPos1 + offset12) / size;
	vec4 r = vec4(0.0);
	r += texture2D(GM_Fog_Map, vec2(texPos0.x, texPos0.y)) * w0.x * w0.y;
	r += texture2D(GM_Fog_Map, vec2(texPos12.x, texPos0.y)) * w12.x * w0.y;
	r += texture2D(GM_Fog_Map, vec2(texPos3.x, texPos0.y)) * w3.x * w0.y;
	r += texture2D(GM_Fog_Map, vec2(texPos0.x, texPos12.y)) * w0.x * w12.y;
	r += texture2D(GM_Fog_Map, vec2(texPos12.x, texPos12.y)) * w12.x * w12.y;
	r += texture2D(GM_Fog_Map, vec2(texPos3.x, texPos12.y)) * w3.x * w12.y;
	r += texture2D(GM_Fog_Map, vec2(texPos0.x, texPos3.y)) * w0.x * w3.y;
	r += texture2D(GM_Fog_Map, vec2(texPos12.x, texPos3.y)) * w12.x * w3.y;
	r += texture2D(GM_Fog_Map, vec2(texPos3.x, texPos3.y)) * w3.x * w3.y;
	return clamp(r, 0.0, 1.2);
}

void main()
{
	vec2 u = GM_Fog_Rect.xy + GM_Coord * GM_Fog_Rect.w;
	float dt = GM_Fog_Step.x;
	float t = GM_Fog_Time;
	vec2 uw = u + GM_Wind.zw;
	vec2 src = GM_Source(uw, t);
	if (GM_Fog_Step.z > 0.5)
	{
		gl_FragColor = vec4(src, 0.0, 0.0);
		return;
	}
	vec2 v = GM_Curl(uw * 0.07 + vec2(t * 0.02, t * 0.017)) * 0.9;
	v += GM_Curl(uw * 0.3 + vec2(-t * 0.05, t * 0.04)) * 0.25;
	float wake = clamp(GM_Wake, 0.0, 1.0);
	vec2 dp = uw - GM_Player.xy;
	float r2 = dot(dp, dp);
	float g = exp(-r2 / 1.6) * wake;
	vec2 pv = GM_Player.zw - GM_Wind.xy;
	float speed = length(pv);
	v += pv * g * 0.9;
	v += dp * inversesqrt(r2 + 0.0001) * g * (0.1 + speed * 0.9);
	vec2 back = u - v * dt;
	vec2 puv = (back - GM_Fog_Prev.xy) * GM_Fog_Prev.z;
	vec4 prev = vec4(src, 0.0, 0.0);
	if (puv.x > 0.0 && puv.y > 0.0 && puv.x < 1.0 && puv.y < 1.0)
	{
		prev = GM_Prev(puv);
	}
	float k = 1.0 - exp(-dt / 10.0);
	vec2 d = mix(prev.xy, src, k);
	float thin = exp(-r2 / 0.9) * wake;
	d.x *= 1.0 - thin * min(1.0, dt);
	float trail = prev.z * exp(-dt / 10.0);
	for (int i = 0; i < 32; i++)
	{
		if (i >= GM_Mover_Count) break;
		vec4 m = GM_Movers[i];
		vec2 q = uw - m.xy;
		float reach = m.w * 3.0;
		if (abs(q.x) > reach || abs(q.y) > reach) continue;
		trail = max(trail, min(1.0, m.z * 1.6) * exp(-dot(q, q) / (m.w * m.w * 1.6)));
	}
	gl_FragColor = vec4(d, trail, 0.0);
}
