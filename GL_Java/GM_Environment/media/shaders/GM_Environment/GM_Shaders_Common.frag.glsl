#version 330

uniform sampler2D GM_Depth;
uniform sampler2D GM_Field;
uniform sampler2D GM_Bank;
uniform float GM_Indoors;
uniform sampler2D GM_Fog_Map;
uniform vec4 GM_View;
uniform vec4 GM_Tex;
uniform vec4 GM_Ref;
uniform vec4 GM_Field_Rect;
uniform float GM_Field_On;
uniform vec4 GM_Fog_Rect;
uniform vec4 GM_Fog;
uniform vec4 GM_Wind;
uniform float GM_Time;
uniform vec4 GM_Player;
uniform float GM_Wake;
uniform float GM_Fog_Time;

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
	return mix(mix(GM_Hash(i), GM_Hash(i + vec2(1.0, 0.0)), u.x), mix(GM_Hash(i + vec2(0.0, 1.0)), GM_Hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float GM_Fbm(vec2 p)
{
	return GM_Noise(p) * 0.55 + GM_Noise(p * 2.07 + 5.3) * 0.3 + GM_Noise(p * 4.3 + 11.1) * 0.15;
}

float GM_Luma(vec3 c)
{
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec2 GM_Screen_Pixel(vec2 uv)
{
	return vec2(uv.x / GM_Tex.z - GM_Tex.x, GM_Tex.y - uv.y / GM_Tex.w);
}

vec2 GM_World_Pixel(vec2 sp)
{
	return sp * GM_View.z + GM_View.xy;
}

float GM_Height(float depth, float sy)
{
	return ((GM_Ref.x - depth) / GM_Ref.z - (sy - GM_Ref.y)) * 0.125;
}

vec2 GM_World_Tile(float s, float lat)
{
	return vec2((s + lat) * 0.5, (s - lat) * 0.5);
}

float GM_Water_Near(vec2 w)
{
	if (GM_Field_On < 0.5) return 0.0;
	vec2 fuv = (w - GM_Field_Rect.xy + 0.5) * GM_Field_Rect.zw;
	if (fuv.x <= 0.0 || fuv.y <= 0.0 || fuv.x >= 1.0 || fuv.y >= 1.0) return 0.0;
	return texture2D(GM_Field, fuv).b;
}

float GM_Outdoor(vec2 w, float h)
{
	if (GM_Field_On < 0.5 || GM_Indoors <= 0.0) return 1.0;
	vec2 fuv = (w - GM_Field_Rect.xy + 0.5) * GM_Field_Rect.zw;
	if (fuv.x <= 0.0 || fuv.y <= 0.0 || fuv.x >= 1.0 || fuv.y >= 1.0) return 1.0;
	float below = 1.0 - smoothstep(GM_Indoors - 0.25, GM_Indoors, h);
	return mix(1.0, texture2D(GM_Bank, fuv).a, below);
}

vec4 GM_Fog_Sample(vec2 w)
{
	vec2 fuv = (w - GM_Wind.zw - GM_Fog_Rect.xy) * GM_Fog_Rect.z;
	if (fuv.x <= 0.0 || fuv.y <= 0.0 || fuv.x >= 1.0 || fuv.y >= 1.0) return vec4(0.6, 0.5, 0.0, 0.0);
	return texture2D(GM_Fog_Map, fuv);
}

float GM_Fog_Density(vec2 w, float h)
{
	vec4 s = GM_Fog_Sample(w);
	float fine = GM_Noise((w - GM_Wind.zw) * 1.1 + vec2(GM_Time * 0.05, 0.0));
	float n = s.x * (0.7 + 0.6 * s.y) * (0.85 + 0.3 * fine);
	float low = GM_Water_Near(w);
	float a = GM_Fog.x;
	float d = 16.0 * a * a * pow(n, 1.6) * (1.0 + 0.6 * low);
	d *= exp(-max(h, 0.0) / (GM_Fog.y * 2.5)) * GM_Outdoor(w, h) * (1.0 - 0.75 * clamp(s.z, 0.0, 1.0));
	vec2 dp = w - GM_Player.xy;
	d *= 1.0 - 0.85 * clamp(GM_Wake, 0.0, 1.0) * exp(-dot(dp, dp) / 0.8);
	return d;
}
