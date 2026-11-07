local GL_Menu_Folder = "media/ui/GL_Core/"
local GL_Menu_Height = 1359
local GL_Menu_First = 529
local GL_Menu_Step = 54
local GL_Menu_Plaque_W = 323
local GL_Menu_Plaque_H = 46
local GL_Menu_Gear_Gap = 15
local GL_Menu_Label_Scale = 2
local GL_Menu_Label_Top = 9
local GL_Menu_Title_Scale = 2.2517
local GL_Menu_Title_Top = 90
local GL_Menu_Items = {
    { field = "onlineOption", label = "GM_Label_Multiplayer", group = 1, gap = 0 },
    { field = "survivalOption", label = "GM_Label_Play_Alone", group = 2, gap = 27 },
    { field = "latestSaveOption", label = "GM_Label_Continue", group = 2, gap = 27 },
    { field = "loadOption", label = "GM_Label_Load", group = 2, gap = 27 },
    { field = "onlineCoopOption", label = "GM_Label_Host", group = 3, gap = 45 },
    { field = "tutorialOption", label = "GM_Label_Tutorial", group = 4, gap = 27 },
    { field = "creditOption", label = "GM_Label_Credits", group = 5, gap = 27 },
    { field = "workshopOption", label = "GM_Label_Workshop", group = 6, gap = 49, optional = true },
    { field = "modsOption", label = "GM_Label_Mods", group = 6, gap = 49 },
    { field = "exitOption", label = "GM_Label_Quit", group = 7, gap = 49 },
}

local function GL_Menu_Round(v)
    return math.floor(v + 0.5)
end

for _, name in ipairs({ "instantiate", "prerender", "onGainJoypadFocus", "prerenderBottomPanelLabel" }) do
    if not (MainScreen and MainScreen[name]) then
        return "MainScreen." .. name .. " is missing"
    end
end

local GL_Menu_Pictures = {}
local GL_Menu_Wanted = { "GM_Title", "GM_Menu_Button", "GM_Settings" }
for _, entry in ipairs(GL_Menu_Items) do
    GL_Menu_Wanted[#GL_Menu_Wanted + 1] = entry.label
end
for _, name in ipairs(GL_Menu_Wanted) do
    GL_Menu_Pictures[name] = getTexture(GL_Menu_Folder .. name .. ".png")
    if not GL_Menu_Pictures[name] then
        return name .. ".png is missing"
    end
end

local GL_Instantiate = MainScreen.instantiate
local GL_Prerender = MainScreen.prerender
local GL_Focus = MainScreen.onGainJoypadFocus
local GL_Label_Prerender = MainScreen.prerenderBottomPanelLabel
local GL_Menu_Scale = 1
local GL_Menu_Stopped = false

local function GL_Menu_Stop(screen, why)
    if GL_Menu_Stopped then
        return
    end
    GL_Menu_Stopped = true
    MainScreen.instantiate = GL_Instantiate
    MainScreen.prerender = GL_Prerender
    MainScreen.onGainJoypadFocus = GL_Focus
    for _, item in ipairs(screen and screen.GL_Items or {}) do
        item.prerender = GL_Label_Prerender
        item:setX(item.GL_Home_X)
        item:setY(item.GL_Home_Y)
        item:setHeight(item.GL_Home_H)
    end
    if screen and screen.GL_Panel_H then
        screen.bottomPanel:setHeight(screen.GL_Panel_H)
    end
    print("[GL_Java] GL_Core menu off: " .. tostring(why))
end

local function GL_Menu_Item_Draw(item)
    item.fade:update()
    local alpha = 0.5 * item.fade:fraction()
    if item.joypadFocused and alpha > 0 then
        item:drawRect(-6, 0, item:getWidth() + 12, item:getHeight(), alpha, 0.3, 0.3, 0.3)
    end
    if item.GL_Gear then
        item:drawTextureScaled(item.GL_Gear, 0, 0, item:getWidth(), item:getHeight(), 1, 1, 1, 1)
        return
    end
    item:drawTextureScaled(GL_Menu_Pictures.GM_Menu_Button, 0, 0, item:getWidth(), item:getHeight(), 1, 1, 1, 1)
    local label = item.GL_Label
    local w = GL_Menu_Round(label:getWidth() * GL_Menu_Label_Scale * GL_Menu_Scale)
    local h = GL_Menu_Round(label:getHeight() * GL_Menu_Label_Scale * GL_Menu_Scale)
    item:drawTextureScaled(label, GL_Menu_Round((item:getWidth() - w) / 2), GL_Menu_Round(GL_Menu_Label_Top * GL_Menu_Scale), w, h, 1, 1, 1, 1)
end

local function GL_Menu_Item_Prerender(item)
    local ok, err = pcall(GL_Menu_Item_Draw, item)
    if not ok then
        GL_Menu_Stop(MainScreen.instance, err)
    end
end

local function GL_Menu_Dress(screen)
    for _, entry in ipairs(GL_Menu_Items) do
        if not entry.optional and not screen[entry.field] then
            error(entry.field .. " is missing")
        end
    end
    for _, field in ipairs({ "optionsOption", "bottomPanel" }) do
        if not screen[field] then
            error(field .. " is missing")
        end
    end
    screen.GL_Items = {}
    screen.GL_Rank = {}
    screen.GL_Panel_H = screen.bottomPanel:getHeight()
    for i, entry in ipairs(GL_Menu_Items) do
        local item = screen[entry.field]
        if item then
            item.GL_Label = GL_Menu_Pictures[entry.label]
            screen.GL_Rank[item] = i
            screen.GL_Items[#screen.GL_Items + 1] = item
        end
    end
    screen.optionsOption.GL_Gear = GL_Menu_Pictures.GM_Settings
    screen.GL_Rank[screen.optionsOption] = #GL_Menu_Items + 1
    screen.GL_Items[#screen.GL_Items + 1] = screen.optionsOption
    for _, item in ipairs(screen.GL_Items) do
        item.GL_Home_X = item:getX()
        item.GL_Home_Y = item:getY()
        item.GL_Home_H = item:getHeight()
        item.prerender = GL_Menu_Item_Prerender
    end
end

local function GL_Menu_Layout(screen)
    local core = getCore()
    local s = core:getScreenHeight() / GL_Menu_Height
    GL_Menu_Scale = s
    local panel = screen.bottomPanel
    local pw = GL_Menu_Round(GL_Menu_Plaque_W * s)
    local ph = GL_Menu_Round(GL_Menu_Plaque_H * s)
    local left = GL_Menu_Round((core:getScreenWidth() - pw) / 2)
    local first = GL_Menu_Round(GL_Menu_First * s)
    local top, group, bottom = nil, nil, first
    for _, entry in ipairs(GL_Menu_Items) do
        local item = screen[entry.field]
        if item and item:isVisible() then
            top = top and top + GL_Menu_Step + (entry.group ~= group and entry.gap or 0) or GL_Menu_First
            group = entry.group
            local y = GL_Menu_Round(top * s)
            item:setX(0)
            item:setY(y - first)
            item:setWidth(pw)
            item:setHeight(ph)
            bottom = y + ph
        end
    end
    local gear = screen.optionsOption
    local gap = GL_Menu_Round(GL_Menu_Gear_Gap * s)
    local gw = GL_Menu_Round(GL_Menu_Pictures.GM_Settings:getWidth() * s)
    local gh = GL_Menu_Round(GL_Menu_Pictures.GM_Settings:getHeight() * s)
    gear:setX(pw + gap)
    gear:setY(screen.exitOption:getY() + math.floor((ph - gh) / 2))
    gear:setWidth(gw)
    gear:setHeight(gh)
    panel:setX(left)
    panel:setY(first)
    panel:setWidth(pw + gap + gw)
    panel:setHeight(bottom - first)
    if panel:isVisible() then
        local title = GL_Menu_Pictures.GM_Title
        local tw = GL_Menu_Round(title:getWidth() * GL_Menu_Title_Scale * s)
        local th = GL_Menu_Round(title:getHeight() * GL_Menu_Title_Scale * s)
        screen:drawTextureScaled(title, GL_Menu_Round((core:getScreenWidth() - tw) / 2), GL_Menu_Round(GL_Menu_Title_Top * s), tw, th, 1, 1, 1, 1)
    end
end

MainScreen.instantiate = function(self)
    GL_Instantiate(self)
    if self.inGame then
        return
    end
    local ok, err = pcall(GL_Menu_Dress, self)
    if not ok then
        GL_Menu_Stop(self, err)
    end
end

MainScreen.prerender = function(self)
    if self.inGame then
        return GL_Prerender(self)
    end
    self.warningFade = self.warningFadeMax
    GL_Prerender(self)
    local ok, err = pcall(GL_Menu_Layout, self)
    if not ok then
        GL_Menu_Stop(self, err)
    end
end

MainScreen.onGainJoypadFocus = function(self, joypadData)
    GL_Focus(self, joypadData)
    if self.inGame then
        return
    end
    local rank = self.GL_Rank
    table.sort(self.joypadButtonsY, function(a, b)
        return (rank[a[1]] or 99) < (rank[b[1]] or 99)
    end)
    for k, v in ipairs(self.joypadButtonsY) do
        if v[1] == self.defaultJoypadOption then
            self.joypadIndexY = k
            self.joypadButtons = v
        end
    end
end

return true
