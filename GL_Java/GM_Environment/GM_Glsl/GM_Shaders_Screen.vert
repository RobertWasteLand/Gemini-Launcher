#version 330

layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aUV;

uniform mat4 ModelViewProjection;

out vec2 GM_Coord;

void main (void)
{
	gl_Position = ModelViewProjection * vec4(aPos.x, aPos.y, 0, 1);
	GM_Coord = aUV;
}
