#version 330

layout (location = 0) in vec2 aPos;

out vec2 GM_Coord;

void main (void)
{
	GM_Coord = aPos * 0.5 + 0.5;
	gl_Position = vec4(aPos, 0.0, 1.0);
}
