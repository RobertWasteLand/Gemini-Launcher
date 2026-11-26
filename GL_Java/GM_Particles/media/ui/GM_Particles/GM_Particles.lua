GM_Effect_Define("GM_Flame", {
    rate = 110, life = {0.45, 0.7}, size = {22, 6},
    color = {{1, 0.75, 0.3}, {0.95, 0.35, 0.05}, {0.4, 0.04, 0.02}},
    alpha = 0.3, glow = true, fadein = 0.1, spread = 0.045, rise = {0.4, 0.9}, drift = 0.025, wind = 0.22,
})

GM_Effect_Define("GM_Sparks", {
    rate = 15, life = 0.6, size = {7, 2.8},
    color = {{1, 0.95, 0.55}, {1, 0.55, 0.1}},
    glow = true, fadein = 0.125, spread = 0.1, rise = {1, 2.5}, drift = 0.2, wind = 1,
})

GM_Effect_Define("GM_Smoke", {
    rate = 30, life = 3.5, size = {44, 164},
    color = {{0.18, 0.18, 0.18}, {0.45, 0.45, 0.45}},
    alpha = 0.15, fadein = 0.25, lit = true, spread = 0.25, rise = {0.5, 0.9}, drift = 0.15, wind = 1,
})

GM_Effect_Define("GM_Muzzle", {
    burst = 5, life = 0.1, size = {36, 22},
    color = {{1, 0.9, 0.5}, {1, 0.6, 0.2}},
    glow = true, fadein = 0, spread = 0.05, push = 3, drift = 0.3,
})

GM_Effect_Define("GM_Powder_Smoke", {
    burst = 8, life = 1.5, size = {28, 80},
    color = {{0.55, 0.55, 0.55}, {0.3, 0.3, 0.3}},
    alpha = 0.5, fadein = 0.17, lit = true, spread = 0.08, push = 1, rise = {0.6, 1}, drift = 0.5, wind = 1,
})

GM_Effect_Define("GM_Wisp", {
    rate = 45, life = 2.6, size = {2.4, 9.4},
    color = {{0.55, 0.55, 0.55}, {0.78, 0.78, 0.78}},
    alpha = 0.55, fadein = 0.125, lit = true, spread = 0.02, rise = {0.22, 0.34}, wind = 1,
})

return true
