# GL_Stubs

Empty copies of the game and LWJGL classes the addons call, with the exact signatures of Build 42 (checked with `javap` against `projectzomboid.jar`). They exist only so the addons compile without the game installed; `GL_Bundle.mjs` compiles them to a scratch folder, puts that folder on the classpath, and never ships them. At runtime the real classes are used.

When a game update changes one of these signatures, the addon logs the failure once and stays out of the way.
