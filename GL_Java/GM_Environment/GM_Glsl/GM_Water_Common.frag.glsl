#version 120

varying vec4 GM_Shade;
varying float GM_Water_Depth;
varying vec2 GM_Water_Flow;
varying float GM_Water_Speed;
varying float GM_Water_Open;
varying float GM_Water_Plane;
varying vec2 GM_World;

uniform sampler2D WaterGroundTex;
uniform sampler2D WaterTextureReflectionA;
uniform sampler2D WaterTextureReflectionB;
uniform float WTime;
uniform vec4 WViewport;
uniform float WReflectionParam;
uniform vec2 WParamWind;
uniform float WParamWindSpeed;
uniform float WParamRainIntensity;

uniform sampler2D GM_Field;
uniform sampler2D GM_Bank;
uniform vec4 GM_Field_Rect;
uniform float GM_Field_On;
uniform float GM_Toon;
uniform float GM_Shore_Quad;

const float GM_Shore_Lift = 0.0013;
const float GM_Near_Range = 7.0;
const float GM_Shore_Reach = 1.25;

const vec3 GM_Deep = vec3(0.094, 0.216, 0.286);
const vec3 GM_Mid = vec3(0.165, 0.345, 0.380);
const vec3 GM_Shallow = vec3(0.290, 0.500, 0.480);
const vec3 GM_Murk = vec3(0.190, 0.335, 0.320);
const vec3 GM_Light = vec3(0.440, 0.640, 0.620);
const vec3 GM_Bright = vec3(0.680, 0.840, 0.800);
const vec3 GM_Foam = vec3(0.910, 0.935, 0.910);
const vec3 GM_Foam_Rim = vec3(0.660, 0.800, 0.780);
const vec3 GM_Mud = vec3(0.400, 0.290, 0.200);
const vec3 GM_Wet = vec3(0.17, 0.13, 0.08);
const vec3 GM_Clear = vec3(0.35, 0.56, 0.54);
const vec3 GM_Sky_Tint = vec3(0.70, 0.88, 0.90);
const float GM_Sky_Body = 0.45;
const float GM_Bank_Show = 0.30;
const vec3 GM_Sun_Light = vec3(1.000, 0.960, 0.860);
const vec3 GM_Eye = vec3(-0.6325, 0.4472, -0.6325);
const mat2 GM_Rot = mat2(0.8, -0.6, 0.6, 0.8);
const vec2 GM_Screen_X = vec2(0.70710678, -0.70710678);
const vec2 GM_Screen_Y = vec2(0.70710678, 0.70710678);

float GM_Hash(vec2 p)
{
	p = mod(p, 289.0);
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float GM_Noise(vec2 p)
{
	vec2 i = floor(p);
	vec2 f = p - i;
	vec2 u = f * f * (3.0 - 2.0 * f);
	float a = GM_Hash(i);
	float b = GM_Hash(i + vec2(1.0, 0.0));
	float c = GM_Hash(i + vec2(0.0, 1.0));
	float d = GM_Hash(i + vec2(1.0, 1.0));
	return a + (b - a) * u.x + (c - a) * u.y + (a - b - c + d) * u.x * u.y;
}

vec3 GM_Noise_Grad(vec2 p)
{
	vec2 i = floor(p);
	vec2 f = p - i;
	vec2 u = f * f * (3.0 - 2.0 * f);
	vec2 du = 6.0 * f * (1.0 - f);
	float a = GM_Hash(i);
	float b = GM_Hash(i + vec2(1.0, 0.0));
	float c = GM_Hash(i + vec2(0.0, 1.0));
	float d = GM_Hash(i + vec2(1.0, 1.0));
	float k = a - b - c + d;
	return vec3(a + (b - a) * u.x + (c - a) * u.y + k * u.x * u.y, du.x * ((b - a) + k * u.y), du.y * ((c - a) + k * u.x));
}

vec3 GM_Ripple(vec2 p, vec2 drift)
{
	vec3 a = GM_Noise_Grad(p * 1.6 + drift);
	vec3 b = GM_Noise_Grad(GM_Rot * p * 3.7 - drift * 1.4 + vec2(17.3, 5.1));
	return vec3(a.x * 0.62 + b.x * 0.38, a.yz * 0.992 + (b.yz * GM_Rot) * 1.406);
}

vec2 GM_World_Dir(vec2 s)
{
	return normalize(vec2(s.x / 64.0 + s.y / 32.0, s.y / 32.0 - s.x / 64.0) + vec2(0.00001));
}

float GM_Edge(float x, float c, float soft)
{
	return smoothstep(c - soft, c + soft, x);
}

float GM_Streaks(vec2 pp, vec2 sdrift, vec2 drift, float tone)
{
	vec2 sp = vec2(dot(pp, GM_Screen_X), dot(pp, GM_Screen_Y));
	float warp = GM_Noise(vec2(sp.x * 0.9, sp.y * 1.6) + sdrift * 0.6) - 0.5;
	vec2 q = vec2(sp.x * 0.9 + sdrift.x, sp.y * 6.5 + warp * 2.2 + sdrift.y);
	float r1 = 1.0 - abs(2.0 * GM_Noise(q) - 1.0);
	float cluster = GM_Noise(vec2(sp.x * 0.35, sp.y * 0.9) + drift * 0.5 + 23.0);
	return r1 * (0.62 + 0.38 * cluster) * (0.85 + 0.15 * tone);
}

void GM_Over(inout vec3 c, inout float a, vec3 src, float srcA)
{
	float outA = srcA + a * (1.0 - srcA);
	c = outA > 0.0001 ? (src * srcA + c * a * (1.0 - srcA)) / outA : c;
	a = outA;
}

float GM_Rain(vec2 p, float k)
{
	vec2 q = p * 2.4 + k * 13.7;
	vec2 c = floor(q);
	vec2 f = fract(q) - 0.5;
	float hh = GM_Hash(c + k * 31.0);
	float t = fract(WTime * 0.85 + hh * 7.3);
	vec2 o = vec2(GM_Hash(c + 3.7), GM_Hash(c + 9.1)) - 0.5;
	float r = length(f - o * 0.35);
	return step(hh, WParamRainIntensity) * (1.0 - t) * (1.0 - smoothstep(0.02, 0.06, abs(r - t * 0.34)));
}

vec3 GM_Sky_Vec(vec2 uv)
{
	vec3 dir;
	uv = uv - 0.5;
	dir.x = uv.x;
	dir.z = uv.y;
	dir.y = 0.18 * sqrt(1.0 + dir.x * dir.x - dir.z * dir.z);
	return normalize(dir);
}

vec2 GM_Sphere_Map(vec3 n, vec3 e)
{
	vec3 r = reflect(normalize(e), -n);
	float m = 2.0 * sqrt(r.x * r.x + r.y * r.y + (r.z + 1.0) * (r.z + 1.0));
	return vec2(r.x / m + 0.5, r.y / m + 0.5);
}

float GM_Shore_Gap(vec2 uv)
{
	return (1.0 - texture2D(GM_Field, uv).b) * GM_Near_Range - 0.5;
}

float GM_Shore_Line(vec2 fuv)
{
	vec2 t = GM_Field_Rect.zw * GM_Shore_Reach;
	vec2 d = t * 0.7071;
	float e = GM_Shore_Gap(fuv);
	float ring = GM_Shore_Gap(fuv + vec2(t.x, 0.0)) + GM_Shore_Gap(fuv - vec2(t.x, 0.0)) + GM_Shore_Gap(fuv + vec2(0.0, t.y)) + GM_Shore_Gap(fuv - vec2(0.0, t.y))
		+ GM_Shore_Gap(fuv + d) + GM_Shore_Gap(fuv - d) + GM_Shore_Gap(fuv + vec2(d.x, -d.y)) + GM_Shore_Gap(fuv + vec2(-d.x, d.y));
	return clamp(1.0 - min(e, (e * 2.0 + ring) / 10.0), 0.0, 1.0);
}

void GM_Water_Main(float quality)
{
	float hq = quality;
	float h = GM_Water_Depth;
	bool shore = GM_Shore_Quad > 0.5 || (GM_Shore_Quad > -0.5 && h < 0.9995);
	float u = clamp(h, 0.0, 1.0);
	float dist = shore ? 0.0 : 3.0;
	float fetch = 4.0;
	vec2 fuv = (GM_World - GM_Field_Rect.xy + 0.5) * GM_Field_Rect.zw;
	vec2 fe = min(fuv, 1.0 - fuv) / (8.0 * GM_Field_Rect.zw);
	float fk = GM_Field_On > 0.5 ? clamp(min(fe.x, fe.y), 0.0, 1.0) : 0.0;
	bool known = fk > 0.0;
	vec3 bank = GM_Mud;
	if (known)
	{
		vec2 fv = texture2D(GM_Field, fuv).rg;
		fetch = mix(fetch, fv.g * 8.0, fk);
		dist = mix(dist, fv.r * 8.0, fk);
		bank = mix(bank, texture2D(GM_Bank, fuv).rgb, fk);
	}
	vec2 p = GM_World;
	float ws = clamp(WParamWindSpeed, 0.0, 10.0);
	float spd = clamp(GM_Water_Speed, 0.0, 1.5);
	vec2 fw = GM_World_Dir(-GM_Water_Flow);
	float soft = mix(0.20, 0.035, GM_Toon);

	vec2 drift = WParamWind * 0.12 + vec2(WTime * 0.045, WTime * 0.03);
	float swell = GM_Noise(p * 0.45 + drift * 0.7 + 51.0);
	vec2 sdrift = vec2(WTime * 0.09, WTime * 0.02) + WParamWind * 0.05;
	float cyc = fract(WTime * 0.25);
	float mv = 1.0 - abs(cyc * 2.0 - 1.0);
	float flowing = smoothstep(0.08, 0.3, spd);
	vec3 rip;
	float ridge;
	if (spd > 0.03)
	{
		vec2 advA = fw * spd * 4.5 * fract(cyc + 0.5);
		vec2 advB = fw * spd * 4.5 * cyc;
		rip = mix(GM_Ripple(p - advA + 0.37, drift), GM_Ripple(p - advB, drift), mv);
		ridge = mix(GM_Streaks(p - advA * 0.4 + 0.37, sdrift, drift, rip.x), GM_Streaks(p - advB * 0.4, sdrift, drift, rip.x), mv);
	}
	else
	{
		rip = GM_Ripple(p, drift);
		ridge = GM_Streaks(p, sdrift, drift, rip.x);
	}
	float gust = smoothstep(0.25, 0.85, GM_Noise(p * 0.07 + drift * 0.08 + vec2(41.0, 13.0)));
	float thr = 0.78 - ws * 0.01 - gust * 0.04;
	float hgt = rip.x + (ws * 0.02 - 0.1) * gust;
	float band1 = GM_Edge(hgt, 0.56, soft);
	float band2 = GM_Edge(ridge, thr, soft * 0.55);
	float band3 = GM_Edge(ridge, thr + 0.11, soft * 0.4);

	vec2 g = rip.yz * (0.10 + ws * 0.035) * (0.55 + 0.9 * gust);
	float rain = 0.0;
	if (WParamRainIntensity > 0.01 && hq > 0.5)
	{
		rain = (GM_Rain(p, 0.0) + GM_Rain(p, 0.37)) * GM_Water_Open;
		g += vec2(rain * 0.6, rain * 0.3);
	}
	vec3 n = normalize(vec3(-g.x, 1.0, -g.y));
	vec3 L = normalize(vec3(-0.45, 0.80, -0.40));
	float dif = clamp(dot(n, L), 0.0, 1.0);
	float fres = 0.04 + 0.96 * pow(1.0 - clamp(dot(n, GM_Eye), 0.0, 1.0), 5.0);
	vec3 posEye = GM_Sky_Vec(vec2((gl_FragCoord.x - WViewport.x) / WViewport.z, 1.0 - (gl_FragCoord.y - WViewport.y) / WViewport.w));
	vec2 rc = GM_Sphere_Map(n, posEye);
	vec3 sky = mix(texture2D(WaterTextureReflectionB, rc).rgb, texture2D(WaterTextureReflectionA, rc).rgb, min(WReflectionParam, 1.0));

	float dd = max(dist, 0.0);
	float z1 = smoothstep(0.3, 2.8, dd + swell * 0.6 - 0.3);
	float z2 = smoothstep(2.8, 7.5, dd + swell * 1.2 - 0.6);
	z1 = mix(z1, GM_Edge(z1, 0.5, 0.08), GM_Toon * 0.7);
	z2 = mix(z2, GM_Edge(z2, 0.5, 0.08), GM_Toon * 0.7);
	vec3 base = mix(GM_Shallow, GM_Mid, z1);
	base = mix(base, GM_Deep, z2);
	float murk = max(flowing, (1.0 - smoothstep(1.5, 4.5, fetch)) * 0.6);
	float clear = (1.0 - smoothstep(0.0, 2.3, dd)) * (1.0 - murk * 0.7);
	base = mix(base, GM_Murk, murk * 0.7);
	if (clear > 0.001)
	{
		float det = texture2D(WaterGroundTex, p * 0.22 + n.xz * 0.05).g * 3.8;
		vec3 ground = mix(vec3(dot(bank, vec3(0.299, 0.587, 0.114))), bank, 0.5) * (0.55 + 0.5 * det) * vec3(0.80, 0.95, 0.92);
		float ca = 0.0;
		if (hq > 0.5)
		{
			vec2 cp = p * 2.4 + n.xz * 0.3;
			float c1 = GM_Noise(cp + vec2(WTime * 0.13, WTime * 0.08));
			float c2 = GM_Noise(cp * 1.27 + vec2(-WTime * 0.10, WTime * 0.14) + 5.3);
			float c3 = GM_Noise(cp * 0.71 + vec2(WTime * 0.06, -WTime * 0.11) + 11.0);
			ca = (1.0 - smoothstep(0.0, 0.05, abs(c1 - c2))) * 0.75 + (1.0 - smoothstep(0.0, 0.035, abs(c2 - c3))) * 0.45;
			ca *= (0.6 + 0.4 * GM_Edge(hgt, 0.45, 0.25)) * (0.45 + 0.9 * GM_Noise(p * 0.7 + vec2(WTime * 0.05, -WTime * 0.03) + 77.0));
		}
		vec3 tinted = mix(GM_Clear, ground, GM_Bank_Show) * (0.88 + ca * (0.40 + 0.35 * GM_Toon));
		base = mix(base, tinted, clear * 0.62);
	}
	vec2 rf = GM_Sphere_Map(vec3(0.0, 1.0, 0.0), posEye);
	vec3 skyBody = mix(texture2D(WaterTextureReflectionB, rf).rgb, texture2D(WaterTextureReflectionA, rf).rgb, min(WReflectionParam, 1.0));
	base = mix(base, skyBody * GM_Sky_Tint, GM_Sky_Body);
	vec3 col = base * (0.86 + 0.18 * dif) * (0.94 + 0.12 * swell);
	col = mix(col, mix(col, GM_Light, 0.6), band1 * (0.14 + 0.10 * GM_Toon));
	col = mix(col, mix(col, GM_Light, 0.85), band2 * (0.70 + 0.30 * GM_Toon));
	col = mix(col, GM_Bright, band3 * (0.55 + 0.35 * GM_Toon) * (0.70 + 0.30 * clear));
	col = mix(col, sky * 0.9, fres * 0.45 * (1.0 - clear * 0.5));
	float spec = pow(clamp(dot(reflect(-GM_Eye, n), L), 0.0, 1.0), 120.0);
	col += GM_Sun_Light * smoothstep(0.55, 0.9, spec) * (0.08 + ws * 0.02);
	if (hq > 0.5)
	{
		vec2 sc = p * 7.0;
		vec2 ci = floor(sc);
		float hs = GM_Hash(ci + 17.0);
		vec2 off = vec2(GM_Hash(ci + 3.0), GM_Hash(ci + 9.0)) - 0.5;
		vec2 d = fract(sc) - 0.5 - off * 0.6;
		float tw = 0.5 + 0.5 * sin(WTime * 5.0 + hs * 60.0);
		float star = max(0.0, 1.0 - (abs(d.x) + abs(d.y)) * 5.5);
		float sparkle = step(0.93, hs) * tw * tw * star * max(band1, band2) * (1.0 - flowing * 0.6);
		col += GM_Sun_Light * sparkle * 1.1;
	}
	float openw = smoothstep(2.0, 4.0, dd) * smoothstep(1.5, 5.0, fetch);
	if (hq > 0.5 && ws > 0.5)
	{
		vec2 wd = vec2(0.5525, -0.8335);
		float cat = GM_Noise(vec2(dot(p, wd) * 0.38, dot(p, vec2(-wd.y, wd.x)) * 2.6) + vec2(WTime * 0.25, 0.0));
		col *= 1.0 - gust * ws * 0.022 * cat;
	}
	if (hq > 0.5 && ws > 4.0)
	{
		float capn = GM_Noise(p * 2.3 + drift * 2.0 + 3.0);
		float cap = GM_Edge(ridge + capn * 0.22, 1.02 - (ws - 4.0) * 0.016, soft * 0.4) * smoothstep(4.0, 8.0, ws) * openw * GM_Water_Open;
		col = mix(col, GM_Foam, cap * 0.85);
	}
	float sea = clamp(0.3 + ws * 0.08, 0.0, 1.0) * (1.0 - smoothstep(0.08, 0.3, spd)) * GM_Water_Open * smoothstep(0.5, 4.0, fetch);
	float jit = GM_Noise(p * 0.085 + vec2(3.1, 7.7));
	float sets = 0.6 + 0.4 * sin(WTime * 0.21 + jit * 5.0);
	float phase0 = WTime * 1.5 + jit * 3.0;
	if (known)
	{
		float sw = sin(dd * 0.9 + WTime * 0.9 + jit * 2.0);
		col *= 1.0 + sw * 0.05 * sea * smoothstep(2.0, 6.0, dd) * fk;
	}
	if (known)
	{
		float wob = GM_Noise(p * 1.1 + vec2(0.0, WTime * 0.03) + 5.0) - 0.5;
		float w = fract(((dd + wob * 0.28) * 1.85 + phase0) * 0.159155);
		float prof = w < 0.86 ? pow(1.0 - w / 0.86, 3.0) : smoothstep(0.86, 1.0, w);
		float amp = sea * sets * (1.0 - smoothstep(1.2, 9.0, dd)) * fk;
		col *= 1.0 - smoothstep(0.55, 0.86, w) * (1.0 - smoothstep(0.86, 0.95, w)) * amp * 0.22;
		col = mix(col, GM_Light, prof * amp * 0.55);
		float brk = (1.0 - smoothstep(0.9, 2.6, dd));
		float wash = (1.0 - smoothstep(0.1, 1.7, dd));
		float seg = 0.55 + 0.9 * GM_Noise(p * 0.45 + vec2(WTime * 0.04, 7.0));
		float cl = 1.0 - smoothstep(0.0, 0.05 + 0.03 * (1.0 - GM_Toon), abs(w - 0.875 + (seg - 1.0) * 0.03));
		col = mix(col, GM_Bright, cl * amp * 0.55 * (1.0 - smoothstep(1.6, 3.6, dd)) * clamp(seg, 0.3, 1.0));
		float since = fract(w - 0.875 + 1.0);
		float lace = hq > 0.5 ? (1.0 - smoothstep(0.0, 0.45, since)) * wash * 0.42 : 0.0;
		float fz = amp * (brk * smoothstep(0.62, 0.97, prof) * 1.25 * seg + lace + wash * 0.08);
		if (fz > 0.03)
		{
			float fn = GM_Noise(GM_Rot * p * 2.9 + vec2(WTime * 0.12, 0.0)) * 0.55 + GM_Noise(GM_Rot * GM_Rot * p * 6.7 - vec2(0.0, WTime * 0.10)) * 0.45;
			float fv = fz + (fn - 0.5) * 1.1;
			float rim = GM_Edge(fv, 0.47, soft * 0.6);
			float core = GM_Edge(fv, 0.65, soft * 0.6);
			col = mix(col, GM_Foam_Rim, rim * 0.8);
			col = mix(col, GM_Foam, core * 0.95);
		}
	}
	col += rain * 0.22;
	col = mix(col, col * vec3(0.90, 0.95, 1.0) * 0.92, WParamRainIntensity * 0.4);

	float alpha = 1.0;
	vec3 outc = col;
	if (shore)
	{
		float su = known ? mix(u, GM_Shore_Line(fuv), fk) : u;
		float w0 = fract(phase0 * 0.159155);
		float run = w0 < 0.2 ? smoothstep(0.0, 0.2, w0) : 1.0 - smoothstep(0.2, 0.92, w0);
		float ret = step(0.2, w0);
		float reach = 0.06 + 0.30 * sea * sets;
		float en = GM_Noise(p * 2.1 + vec2(0.0, WTime * 0.07));
		float wob = (GM_Noise(p * 1.7 + 31.0) - 0.5) * 0.16;
		float front = 0.52 - reach * run + wob + (en - 0.5) * 0.06;
		float width = mix(0.30, 0.17, GM_Toon);
		float blend = smoothstep(front - width, front + width, su);
		float wetLine = 0.52 - reach + wob;
		float wet = smoothstep(wetLine - width, wetLine + 0.04, su) * (1.0 - blend);
		float thin = smoothstep(front, min(front + 0.4, 1.0), su);
		vec3 sheet = mix(mix(col, GM_Bright, 0.30), col, thin);
		float fn = GM_Noise(GM_Rot * p * 5.3 + vec2(WTime * 0.35, 0.0));
		float fband = 1.0 - smoothstep(0.0, 0.05 + 0.06 * run, abs(su - front));
		float ff = fband * (0.25 + 0.75 * run * (0.3 + sea)) * GM_Edge(en * 0.4 + fn * 0.6, 0.46, soft * 0.5);
		float laceS = 0.0;
		if (hq > 0.5)
		{
			float ln = GM_Noise(GM_Rot * p * 4.1 + vec2(0.0, WTime * 0.05)) * 0.6 + GM_Noise(p * 9.3 + 2.0) * 0.4;
			laceS = GM_Edge(ln, 0.60, soft * 0.5) * (1.0 - blend) * smoothstep(wetLine - 0.04, wetLine + 0.06, su) * (1.0 - smoothstep(0.2, 0.9, w0)) * ret * sea;
		}
		float sheen = wet * band2 * 0.30;
		vec3 oc = vec3(0.0);
		float oa = 0.0;
		GM_Over(oc, oa, GM_Wet, wet * 0.30);
		GM_Over(oc, oa, sheet, blend);
		GM_Over(oc, oa, GM_Sun_Light, sheen);
		GM_Over(oc, oa, GM_Foam, clamp(max(ff, laceS), 0.0, 1.0) * 0.9);
		outc = oc;
		alpha = oa;
	}
	gl_FragColor = vec4(outc * GM_Shade.rgb, alpha);
	gl_FragDepth = shore ? max(GM_Water_Plane - GM_Shore_Lift, 0.0) : GM_Water_Plane;
}
