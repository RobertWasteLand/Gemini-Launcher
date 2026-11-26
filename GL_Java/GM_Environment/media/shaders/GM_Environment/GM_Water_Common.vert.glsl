#version 330

layout (location = 0) in vec2 vertex;
layout (location = 1) in vec4 color;
layout (location = 2) in float aDepth;
layout (location = 3) in float aFlow;
layout (location = 4) in float aSpeed;
layout (location = 5) in float aExternal;
layout (location = 6) in float aFragDepth;

uniform mat4 ModelViewProjection;
uniform float GM_Tile_Scale;

out vec4 GM_Shade;
out float GM_Water_Depth;
out vec2 GM_Water_Flow;
out float GM_Water_Speed;
out float GM_Water_Open;
out float GM_Water_Plane;
out vec2 GM_World;

void GM_Water_Vertex(void)
{
	gl_Position = ModelViewProjection * vec4(vertex.xy, 0, 1);
	GM_Shade = color;
	GM_Water_Open = aExternal;
	GM_Water_Speed = GM_Water_Open * aSpeed;
	GM_Water_Depth = aDepth;
	GM_Water_Flow = vec2(sin(GM_Water_Open * aFlow), cos(GM_Water_Open * aFlow));
	GM_Water_Plane = aFragDepth;
	float ts = GM_Tile_Scale > 0.5 ? GM_Tile_Scale : 2.0;
	float sx = vertex.x / (32.0 * ts);
	float sy = vertex.y / (16.0 * ts);
	GM_World = vec2(sx + sy, sy - sx) * 0.5;
}
