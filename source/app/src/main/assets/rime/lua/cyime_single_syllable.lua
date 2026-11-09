--[[
  CyIME：打一个完整拼音音节（an、ai、en、ou、ang…）时，常用单字留在前面。
  这样的输入也能读成首字母（an = a'n），学过的或词频高的两字词（爱你、按钮、安娜…）会排到单字前面，
  第一页放不下 安 按 暗 岸 案。这里保证前 6 个单字排在靠首字母读出的多字词之前；完整拆成音节的词
  （xian = xi'an 西安）、英文、表情、符号和自定义短语不动，
  输入不是单个完整音节时不做任何事。
--]]
local KEEP = 6
local SCAN = 40

local SYLLABLES = {}
for _, line in ipairs({
    "a ai an ang ao ba bai ban bang bao bei ben beng bi bian biang biao bie bin bing bo bu ca cai can cang cao ce",
    "cei cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo",
    "ci cong cou cu cuan cui cun cuo da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du",
    "duan dui dun duo e ei en eng er fa fan fang fei fen feng fiao fo fou fu ga gai gan gang gao ge gei gen geng",
    "gong gou gu gua guai guan guang gui gun guo ha hai han hang hao he hei hen heng   hong hou hu hua huai",
    "huan huang hui hun huo ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun ka kai kan kang kao ke",
    "kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo la lai lan lang lao le lei leng li lia lian liang",
    "liao lie lin ling liu lo long lou lu luan lun luo lv lve  ma mai man mang mao me mei men meng mi mian miao",
    "mie min ming miu mo mou mu  na nai nan nang nao ne nei nen neng  ni nian niang niao nie nin ning niu nong",
    "nou nu nuan nuo nv nve o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu qi qia qian",
    "qiang qiao qie qin qing qiong qiu qu quan que qun ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo",
    "sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan",
    "shuang shui shun shuo si song sou su suan sui sun suo ta tai tan tang tao te tei teng ti tian tiao tie ting",
    "tong tou tu tuan tui tun tuo wa wai wan wang wei wen weng wo wu xi xia xian xiang xiao xie xin xing xiong xiu",
    "xu xuan xue xun ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun za zai zan zang zao ze zei zen zeng",
    "zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong",
    "zou zu zuan zui zun zuo",
}) do
    for s in line:gmatch("%a+") do SYLLABLES[s] = true end
end

local function han_count(text)
    local n = 0
    for _, cp in utf8.codes(text) do
        if not ((cp >= 0x3400 and cp <= 0x9FFF) or (cp >= 0x20000 and cp <= 0x3FFFF)) then return 0 end
        n = n + 1
    end
    return n
end

-- A word read from the input through an abbreviated syllable (an = a'n → 爱你; preedit "a n", n is no syllable).
-- A word whose spelling is the input split into complete syllables (xian = xi'an → 西安) is not one.
local function abbreviated(cand)
    local preedit = cand.preedit
    if not preedit or preedit == "" then return false end
    local parts = 0
    for part in preedit:gmatch("[^%s']+") do
        parts = parts + 1
        if not SYLLABLES[part:lower()] then return true end
    end
    return false
end

local function cyime_single_syllable(input, env)
    if not SYLLABLES[env.engine.context.input] then
        for cand in input:iter() do yield(cand) end
        return
    end
    -- Within the first SCAN candidates, Han words of two characters or more matched through an abbreviated
    -- syllable move to just after the KEEP-th single character (or after the last single character).
    local buffer, streaming = {}, false
    local function flush()
        local last_single, singles = 0, 0
        for i, cand in ipairs(buffer) do
            -- Rare characters (cyime_rare_chars, after every dictionary character) do not count.
            if han_count(cand.text) == 1 and cand.type ~= "rare" then
                singles = singles + 1
                last_single = i
                if singles == KEEP then break end
            end
        end
        local held = {}
        for i, cand in ipairs(buffer) do
            -- custom_phrase entries (initial_quality 99) are the user's own choice and keep their place.
            if i <= last_single and han_count(cand.text) >= 2 and cand.quality < 50 and abbreviated(cand) then
                held[#held + 1] = cand
            else
                yield(cand)
            end
            if i == last_single then
                for _, h in ipairs(held) do yield(h) end
            end
        end
    end
    for cand in input:iter() do
        if streaming then
            yield(cand)
        else
            buffer[#buffer + 1] = cand
            if #buffer >= SCAN then
                flush()
                streaming = true
            end
        end
    end
    if not streaming then flush() end
end

return cyime_single_syllable
