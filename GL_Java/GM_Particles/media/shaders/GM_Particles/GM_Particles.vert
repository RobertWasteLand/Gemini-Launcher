#version 330

layout (location = 0) in vec2 aCorner;
layout (location = 1) in vec3 aPlace;
layout (location = 2) in vec2 aLife;
layout (location = 3) in vec3 aTint;

uniform mat4 GM_Matrix;
uniform vec2 GM_Size;

out vec2 GM_Spot;
out float GM_Age;
out vec3 GM_Tint;
out float GM_Here;
out float GM_Reach;

void main (void)
{
	float size = mix(GM_Size.x, GM_Size.y, aLife.x);
	float c = cos(aLife.y);
	float s = sin(aLife.y);
	vec2 turned = vec2(aCorner.x * c - aCorner.y * s, aCorner.x * s + aCorner.y * c);
	gl_Position = GM_Matrix * vec4(aPlace.xy + turned * size * 0.5, 0.0, 1.0);
	gl_Position.z = aPlace.z * 2.0 - 1.0;
	GM_Spot = aCorner;
	GM_Age = aLife.x;
	GM_Tint = aTint;
	GM_Here = aPlace.z;
	GM_Reach = size;
}
