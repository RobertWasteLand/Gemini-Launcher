#version 330

uniform vec3 GM_Color0;
uniform vec3 GM_Color1;
uniform vec3 GM_Color2;
uniform float GM_Colors;
uniform float GM_Alpha;
uniform float GM_Fadein;
uniform sampler2D GM_Picture;
uniform float GM_Has_Picture;
uniform vec4 GM_UV;
uniform float GM_Glow;
uniform sampler2D GM_Depth;
uniform vec2 GM_Fbo;
uniform float GM_Soft;

in vec2 GM_Spot;
in float GM_Age;
in vec3 GM_Tint;
in float GM_Here;
in float GM_Reach;

void main (void)
{
	vec3 ramp = GM_Color0;
	if (GM_Colors > 2.5)
	{
		ramp = GM_Age < 0.5 ? mix(GM_Color0, GM_Color1, GM_Age * 2.0) : mix(GM_Color1, GM_Color2, GM_Age * 2.0 - 1.0);
	}
	else if (GM_Colors > 1.5)
	{
		ramp = mix(GM_Color0, GM_Color1, GM_Age);
	}
	float shape;
	if (GM_Has_Picture > 0.5)
	{
		vec4 picture = texture2D(GM_Picture, mix(GM_UV.xy, GM_UV.zw, GM_Spot * 0.5 + 0.5));
		ramp *= picture.rgb;
		shape = picture.a;
	}
	else
	{
		float r2 = dot(GM_Spot, GM_Spot);
		shape = r2 < 1.0 ? pow(1.0 - r2, 1.5) : 0.0;
	}
	float rise = GM_Fadein > 0.0 ? clamp(GM_Age / GM_Fadein, 0.0, 1.0) : 1.0;
	float fall = GM_Fadein < 1.0 ? clamp((1.0 - GM_Age) / (1.0 - GM_Fadein), 0.0, 1.0) : 1.0;
	float scene = texture2D(GM_Depth, gl_FragCoord.xy / GM_Fbo).r;
	float soft = clamp((scene - GM_Here) / max(GM_Soft * GM_Reach, 0.000001), 0.0, 1.0);
	float a = GM_Alpha * rise * fall * shape * soft;
	gl_FragColor = vec4(ramp * GM_Tint * a, a * (1.0 - GM_Glow));
}
