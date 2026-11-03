#version 330
uniform sampler2D DIFFUSE;

uniform float bgl_RenderedTextureWidth;
uniform float bgl_RenderedTextureHeight;
uniform float timer;
uniform float timerWrap;
uniform vec2 TextureSize;

uniform float NightValue;
uniform float NightVisionGoggles;
uniform float DesaturationVal;
uniform vec4 SearchMode;
uniform vec4 ScreenInfo;
uniform vec4 ParamInfo;
uniform vec4 VarInfo;

uniform float DrunkFactor;
uniform float BlurFactor;

#include "GM_Shaders_Common.frag"

uniform vec4 GM_Sun;
uniform vec3 GM_Sun_Colour;
uniform vec3 GM_Fog_Colour;
uniform float GM_Effects[2];
uniform float GM_Bypass;
uniform float GM_Debug;

in vec2 GM_Coord;

float GM_Screen_Width = bgl_RenderedTextureWidth;
float GM_Screen_Height = bgl_RenderedTextureHeight;

const vec3 GM_Pivot = vec3(0.4, 0.4, 0.4);
const float GM_Perm_Unit = 1.0/256.0;
const float GM_Perm_Half = 0.5/256.0;

const float GM_Grain_Amount = 0.0003;
const float GM_Grain_Size = 1.0;

const float GM_Night_Contrast = 0.5;
const vec3 GM_Night_Luma = vec3(0.30, 0.59, 0.11);
const float GM_Night_Gain = 1.3;

const float GM_Blur_Turn = 6.28318530718;
const float GM_Blur_Directions = 16.0;
const float GM_Blur_Steps = 3.0;
const float GM_Blur_Size = 12.0;

vec3 GM_Desaturate(vec3 color, float amount)
{
	vec3 gray = vec3(dot(vec3(0.2126,0.7152,0.0722), color));
	return vec3(mix(color, gray, amount));
}

vec4 GM_Random(in vec2 tc)
{
	float noise =  sin(dot(tc + vec2(timer,timer),vec2(12.9898,78.233))) * 43758.5453;
	float noiseR =  fract(noise)*2.0-1.0;
	float noiseG =  fract(noise*1.2154)*2.0-1.0;
	float noiseB =  fract(noise*1.3453)*2.0-1.0;
	float noiseA =  fract(noise*1.3647)*2.0-1.0;
	return vec4(noiseR,noiseG,noiseB,noiseA);
}

float GM_Fade(in float t)
{
	return t*t*t*(t*(t*6.0-15.0)+10.0);
}

float GM_Perlin(in vec3 p)
{
	vec3 pi = GM_Perm_Unit*floor(p)+GM_Perm_Half;
	vec3 pf = fract(p);
	float perm00 = GM_Random(pi.xy).a ;
	vec3  grad000 = GM_Random(vec2(perm00, pi.z)).rgb * 4.0 - 1.0;
	float n000 = dot(grad000, pf);
	vec3  grad001 = GM_Random(vec2(perm00, pi.z + GM_Perm_Unit)).rgb * 4.0 - 1.0;
	float n001 = dot(grad001, pf - vec3(0.0, 0.0, 1.0));
	float perm01 = GM_Random(pi.xy + vec2(0.0, GM_Perm_Unit)).a ;
	vec3  grad010 = GM_Random(vec2(perm01, pi.z)).rgb * 4.0 - 1.0;
	float n010 = dot(grad010, pf - vec3(0.0, 1.0, 0.0));
	vec3  grad011 = GM_Random(vec2(perm01, pi.z + GM_Perm_Unit)).rgb * 4.0 - 1.0;
	float n011 = dot(grad011, pf - vec3(0.0, 1.0, 1.0));
	float perm10 = GM_Random(pi.xy + vec2(GM_Perm_Unit, 0.0)).a ;
	vec3  grad100 = GM_Random(vec2(perm10, pi.z)).rgb * 4.0 - 1.0;
	float n100 = dot(grad100, pf - vec3(1.0, 0.0, 0.0));
	vec3  grad101 = GM_Random(vec2(perm10, pi.z + GM_Perm_Unit)).rgb * 4.0 - 1.0;
	float n101 = dot(grad101, pf - vec3(1.0, 0.0, 1.0));
	float perm11 = GM_Random(pi.xy + vec2(GM_Perm_Unit, GM_Perm_Unit)).a ;
	vec3  grad110 = GM_Random(vec2(perm11, pi.z)).rgb * 4.0 - 1.0;
	float n110 = dot(grad110, pf - vec3(1.0, 1.0, 0.0));
	vec3  grad111 = GM_Random(vec2(perm11, pi.z + GM_Perm_Unit)).rgb * 4.0 - 1.0;
	float n111 = dot(grad111, pf - vec3(1.0, 1.0, 1.0));
	vec4 n_x = mix(vec4(n000, n001, n010, n011), vec4(n100, n101, n110, n111), GM_Fade(pf.x));
	vec2 n_xy = mix(n_x.xy, n_x.zw, GM_Fade(pf.y));
	float n_xyz = mix(n_xy.x, n_xy.y, GM_Fade(pf.z));
	return n_xyz;
}

vec2 GM_Rotate(in vec2 tc, in float angle)
{
	float aspect = GM_Screen_Width/GM_Screen_Height;
	float rotX = ((tc.x*2.0-1.0)*aspect*cos(angle)) - ((tc.y*2.0-1.0)*sin(angle));
	float rotY = ((tc.y*2.0-1.0)*cos(angle)) + ((tc.x*2.0-1.0)*aspect*sin(angle));
	rotX = ((rotX/aspect)*0.5+0.5);
	rotY = rotY*0.5+0.5;
	return vec2(rotX,rotY);
}

vec3 GM_Contrast(vec3 color, float amount)
{
	color.r = color.r - GM_Pivot.r;
	color.g = color.g - GM_Pivot.g;
	color.b = color.b - GM_Pivot.b;
	color = color * amount;
	color.r = color.r + GM_Pivot.r;
	color.g = color.g + GM_Pivot.g;
	color.b = color.b + GM_Pivot.b;
	return color;
}

vec4 GM_Cubic(float v)
{
	vec4 n = vec4(1.0, 2.0, 3.0, 4.0) - v;
	vec4 s = n * n * n;
	float x = s.x;
	float y = s.y - 4.0 * s.x;
	float z = s.z - 4.0 * s.y + 6.0 * s.x;
	float w = 6.0 - x - y - z;
	return vec4(x, y, z, w) * (1.0/6.0);
}

vec4 GM_Bicubic(sampler2D sampler, vec2 texCoords)
{
	vec2 texSize = TextureSize;
	vec2 invTexSize = 1.0 / texSize;
	texCoords = texCoords * texSize - 0.5;
	vec2 fxy = fract(texCoords);
	texCoords -= fxy;
	vec4 xcubic = GM_Cubic(fxy.x);
	vec4 ycubic = GM_Cubic(fxy.y);
	vec4 c = texCoords.xxyy + vec2(-0.5, +1.5).xyxy;
	vec4 s = vec4(xcubic.xz + xcubic.yw, ycubic.xz + ycubic.yw);
	vec4 offset = c + vec4(xcubic.yw, ycubic.yw) / s;
	offset *= invTexSize.xxyy;
	vec4 sample0 = texture2D(sampler, offset.xz);
	vec4 sample1 = texture2D(sampler, offset.yz);
	vec4 sample2 = texture2D(sampler, offset.xw);
	vec4 sample3 = texture2D(sampler, offset.yw);
	float sx = s.x / (s.x + s.y);
	float sy = s.z / (s.z + s.w);
	return mix(mix(sample3, sample2, sx), mix(sample1, sample0, sx), sy);
}

vec3 GM_Blur(in vec3 col, in float alpha)
{
	vec2 rad = GM_Blur_Size/ScreenInfo.xy;
	vec2 uv = GM_Coord.st;
	vec3 c = texture2D(DIFFUSE, uv).rgb;
	for( float d=0.0; d<GM_Blur_Turn; d+=GM_Blur_Turn/GM_Blur_Directions)
	{
		for(float i=1.0/GM_Blur_Steps; i<=1.01; i+=1.0/GM_Blur_Steps)
		{
			c += texture2D( DIFFUSE, uv+vec2(cos(d),sin(d))*rad*i).rgb;
		}
	}
	c /= (GM_Blur_Steps * (GM_Blur_Directions + 1));
	c = clamp(c,0.0,1.0);
	return (col*(1.0-alpha))+(c*alpha);
}

float GM_Search_Circle(in float rad, in vec2 coord)
{
	vec2 center = vec2(0.5, 0.5);
	center.x -= (ScreenInfo.z)/ScreenInfo.x;
	center.y += (ScreenInfo.w)/ScreenInfo.y;
	float dist = distance(coord.xy, center);
	dist *= ScreenInfo.x;
	return smoothstep( max(0.0,(rad*ParamInfo.y)-ParamInfo.z), (rad*ParamInfo.y)+ParamInfo.z, dist);
}

struct GM_Px
{
	vec2 sp;
	vec2 w;
	float z;
	float h;
};

GM_Px GM_Decode(vec2 uv)
{
	GM_Px p;
	p.sp = GM_Screen_Pixel(uv);
	vec2 wp = GM_World_Pixel(p.sp);
	float ts = GM_View.w;
	float sy = wp.y / (16.0 * ts);
	float lat = wp.x / (32.0 * ts);
	float d = texture2D(GM_Depth, uv).r;
	p.z = GM_Height(d, sy);
	p.h = clamp(p.z, 0.0, 2.0);
	float s = sy + 6.0 * p.h * 0.9;
	p.w = GM_World_Tile(s, lat);
	return p;
}

vec3 GM_Debug_View(vec3 scene, vec2 uv)
{
	GM_Px p = GM_Decode(uv);
	vec3 back = vec3(GM_Luma(scene) * 0.3);
	int mode = int(GM_Debug + 0.5);
	if (mode == 1)
	{
		float zz = clamp(p.z, -0.5, 2.0);
		vec3 dc = vec3(fract(zz * 2.0), clamp(zz * 0.5, 0.0, 1.0), 0.0);
		if (p.z < -0.04) dc = vec3(0.0, 0.0, 0.7);
		return mix(back, dc, 0.85);
	}
	if (mode == 4)
	{
		float f = 1.0 - exp(-GM_Fog_Density(p.w, p.h));
		return back + vec3(f) + vec3(0.0, 0.0, 0.5) * GM_Fog_Sample(p.w).x;
	}
	if (mode == 6)
	{
		if (GM_Field_On < 0.5) return vec3(0.6, 0.0, 0.6);
		vec2 fuv = (p.w - GM_Field_Rect.xy + 0.5) * GM_Field_Rect.zw;
		if (fuv.x <= 0.0 || fuv.y <= 0.0 || fuv.x >= 1.0 || fuv.y >= 1.0) return back;
		return texture2D(GM_Field, fuv).rgb;
	}
	return scene;
}

vec4 GM_Fog_At(vec2 uv)
{
	if (GM_Bypass >= 0.5 || GM_Effects[0] < 0.5 || GM_Fog.x <= 0.001) return vec4(0.0);
	GM_Px p = GM_Decode(uv);
	float up = 1.0 - clamp(p.sp.y / ScreenInfo.y, 0.0, 1.0);
	vec3 fc = GM_Fog_Colour * (0.9 + 0.14 * up);
	vec3 sunSoft = mix(GM_Sun_Colour, vec3(GM_Luma(GM_Sun_Colour)), 0.5);
	fc += sunSoft * GM_Sun.z * 0.086 * (0.5 + 0.5 * up);
	fc = min(fc, max(GM_Fog_Colour * 1.15, vec3(0.04)));
	return vec4(fc, (1.0 - exp(-GM_Fog_Density(p.w, p.h))) * 0.88);
}

vec3 GM_Apply(vec3 col, vec4 fog)
{
	return mix(col, fog.rgb, fog.a);
}

vec3 GM_Screen_World(in vec3 pixel, in vec3 noise, in vec4 fog)
{
	float circleAlpha = 0.0;
	vec3 Diffuse = vec3(1,1,1);
	if(VarInfo.x==0.0) {
		Diffuse = GM_Desaturate(GM_Apply(pixel, fog), DesaturationVal);
	} else {
		vec2 coord = (vec2(gl_FragCoord.x-SearchMode.z, gl_FragCoord.y-SearchMode.w+(56.0/ParamInfo.x)) * ParamInfo.x) / ScreenInfo.xy;
		circleAlpha = GM_Search_Circle(SearchMode.y, coord);
		Diffuse = GM_Blur(pixel, circleAlpha*SearchMode.x);
		Diffuse = GM_Desaturate(GM_Apply(Diffuse, fog), max(DesaturationVal,circleAlpha*ParamInfo.w));
		Diffuse *= (1.0-(VarInfo.y*circleAlpha));
	}
	Diffuse = clamp(Diffuse, 0.0, 1.0);
	float intensity = (0.299 * Diffuse.r) + (0.587 * Diffuse.g) + (0.114 * Diffuse.b);
	vec3 col = GM_Contrast(GM_Desaturate(Diffuse, 0.1), 1.2);
	float invintensity = 1.0 - intensity;
	invintensity = invintensity * invintensity;
	col = col+((noise*GM_Grain_Amount) * (invintensity * 5.0));
	return col;
}

vec3 GM_Screen_Night(in vec3 pixel, in vec3 noise)
{
	vec3 Diffuse = pixel;
	float intensity = (0.299 * Diffuse.r) + (0.587 * Diffuse.g) + (0.114 * Diffuse.b);
	Diffuse = mix(Diffuse, vec3(intensity), (NightValue*0.8) * (1.0 - intensity));
	Diffuse = ((1.0 - NightValue) * Diffuse) + (NightValue * Diffuse*Diffuse) * 1.5;
	Diffuse.rg -= NightValue * 0.25 * (1.0 - intensity);
	Diffuse.b +=  (NightValue * 0.25 * (1.0 - intensity));
	vec3 col = GM_Contrast(GM_Desaturate(Diffuse, 0.1), 1.2);
	float invintensity = 1.0 - intensity;
	invintensity = invintensity * invintensity;
	col.rg+=0.45;
	col = col*1.9+((noise*0.015) * (invintensity * 5.0));
	float intensity2 = dot(GM_Night_Luma,col) ;
	intensity2 = clamp(GM_Night_Contrast * (intensity2 - 0.5) + 0.5, 0.0, 1.0);
	float green = clamp(intensity2 / 0.59, 0.0, 1.0) * GM_Night_Gain;
	vec3 visionColor = vec3(0.0,green,0.0);
	col = col * visionColor;
	return col;
}

float GM_Drunk_Edges(in vec2 coord)
{
	vec2 center = vec2(0.5, 0.5);
	center.x -= (ScreenInfo.z)/ScreenInfo.x;
	center.y += (ScreenInfo.w)/ScreenInfo.y;
	float distX = distance(coord.x - (0.5 - center.x), center.x);
	float distY = distance(coord.y - (0.5 - center.y), center.y);
	float aXret = (distX) / 0.5;
	float aYret = (distY) / 0.5;
	return 1 - max(aXret, aYret);
}

float GM_Drunk_Circle(in vec2 coord)
{
	vec2 center = vec2(0.5, 0.5);
	center.x -= (ScreenInfo.z)/ScreenInfo.x;
	center.y += (ScreenInfo.w)/ScreenInfo.y;
	float dist = distance(coord.xy, center);
	dist *= ScreenInfo.x;
	float gradientWidth = 20;
	float gradientWidthAlpha = (gradientWidth * 64.0) / 2.0;
	return smoothstep( max(0.0,(gradientWidth)-gradientWidthAlpha), (gradientWidth)+gradientWidthAlpha, dist);
}

vec3 GM_Drunk_Deform(in vec3 pixel)
{
	vec3 col = clamp(pixel, 0.0, 1.0);
	vec2 UV =  GM_Coord.st;
	vec2 coord = (vec2(gl_FragCoord.x-SearchMode.z, gl_FragCoord.y-SearchMode.w) * ParamInfo.x) / ScreenInfo.xy;
	float circleAlpha = GM_Drunk_Circle(coord);
	float drunkAlpha = GM_Drunk_Edges(coord);
	float drunkAlphaB = drunkAlpha * circleAlpha;
	float jiggle1 = 7.3;
	float jiggle2 = 13.3;
	float jiggleMag = 0.005 * DrunkFactor;
	vec2 coord2 = UV + ((jiggleMag) * vec2(cos(timer/(timerWrap * jiggle1)), sin(timer/(timerWrap * jiggle2))) / ParamInfo.x);
	vec2 coord3 = UV + ((jiggleMag) * vec2(cos(timer/(timerWrap * jiggle2)), sin(timer/(timerWrap * jiggle1))) / ParamInfo.x);
	vec3 pixel2 = GM_Bicubic(DIFFUSE, coord2).xyz;
	vec3 pixel3 = GM_Bicubic(DIFFUSE, coord3).xyz;
	vec3 pixelBlur = GM_Blur(col, DrunkFactor);
	col = mix(col, pixel2, drunkAlphaB);
	col = mix(col, pixel3, drunkAlphaB);
	col = mix(col, pixelBlur, (1-drunkAlpha));
	col = clamp(col, 0, 1);
	return col;
}

float GM_Blur_Circle(in vec2 coord, in float rad)
{
	vec2 center = vec2(0.5, 0.5);
	center.x -= (ScreenInfo.z)/ScreenInfo.x;
	center.y += (ScreenInfo.w)/ScreenInfo.y;
	float dist = distance(coord.xy, center);
	dist *= ScreenInfo.x;
	float radiusPixelW = (rad * 64.0) / 2.0;
	float gradPixelW = (radiusPixelW * 3.0) * ParamInfo.x;
	float gradientWidth = gradPixelW * (rad / 32.0 / ParamInfo.x);
	float gradientWidthAlpha = (gradientWidth * 64.0) / 2.0;
	return smoothstep( max(0.0,(gradientWidth)-gradientWidthAlpha), (gradientWidth)+gradientWidthAlpha, dist);
}

vec3 GM_Screen_Blur(in vec3 pixel)
{
	vec3 col = clamp(pixel, 0.0, 1.0);
	vec2 coord = (vec2(gl_FragCoord.x-SearchMode.z, gl_FragCoord.y-SearchMode.w) * ParamInfo.x) / ScreenInfo.xy;
	float circleAlpha = GM_Blur_Circle(coord, 3);
	col = GM_Blur(col, (BlurFactor) * circleAlpha);
	return col;
}

void main()
{
	vec2 UV =  GM_Coord.st;
	vec3 rotOffset = vec3(1.425,3.892,5.835);
	vec2 rotCoordsR = GM_Rotate(UV, timer + rotOffset.x);
	vec3 noise = vec3(GM_Perlin(vec3(rotCoordsR*vec2(GM_Screen_Width/GM_Grain_Size,GM_Screen_Height/GM_Grain_Size),0.0)));
	vec3 pixel = GM_Bicubic(DIFFUSE, GM_Coord.st).xyz;
	if (GM_Debug > 0.5) {
		gl_FragColor = vec4(GM_Debug_View(pixel, UV), 1.0);
		return;
	}
	vec4 fog = GM_Fog_At(UV);
	vec3 col = vec3(0,0,0);
	if (DrunkFactor > 0.0) {
		col = GM_Drunk_Deform(pixel);
	} else {
		col = pixel;
	}
	if (BlurFactor > 0.0) {
		col = GM_Screen_Blur(col);
	}
	if(NightVisionGoggles<0.5) {
		col = GM_Screen_World(col, noise, fog);
	} else {
		col = GM_Screen_Night(GM_Apply(col, fog), noise);
	}
	gl_FragColor = vec4(col, 1.0);
}
