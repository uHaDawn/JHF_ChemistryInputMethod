/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 *
 * 本程序分发时希望它有用，但不提供任何担保，甚至不含适销性或特定用途
 * 适用性的默示担保。详见 GNU 通用公共许可证。
 *
 * 你应已随本程序收到一份 GNU 通用公共许可证副本；如果没有，请见
 * <https://www.gnu.org/licenses/>。
 */
package com.jhf.jiehuafa.ime;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.jhf.jiehuafa.R;
import com.jhf.jiehuafa.SmoothCorners;
import com.jhf.jiehuafa.UiStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 接化发键盘（Figma 设计稿样式）：全直角白键 + 底影 + 主题强调色。
 *
 * 字母页：QWERTY 三排 + 功能行「拼｜素 · ， · 空格 · ←」+ 底部细条 ⇌（切换系统输入法）。
 * 符号页：左侧 4 分类竖向 tab（度量 / 物质 / 反应 / 其他）+ 右侧符号网格 + 功能行。
 * 收起键盘的入口在候选栏右侧的 ⌄ 芯片（服务侧 JhfImeService）。
 *
 * 手势：
 *   点按 = 主字符；上滑 = 化学符号（带预览气泡）；长按 = 形态三选（数字/符号 · 大写 · 小写）。
 *
 * 修改日志（之后每次改动都在这里追加一条：日期 + 改动内容）：
 *   2026-10-25 删除键/回车键改用客户提供的 SVG（ic_backspace 21×13、ic_enter 21×11），
 *              统一走 drawBitmapIcon() 按主题色着色，删除旧的手绘回车图标；
 *              新增长按删除连续退格（420ms 触发后每 70ms 一发，松手/取消立即停止）；
 *              新增 Shift 两种大写行为：点按=只大写一次；长按后另一指连续点字母=持续大写、松手恢复；
 *              副指按键闪烁高亮（flashRow/flashCol），Listener 增加 onShiftHoldChanged()；
 *              自动主题改为莫奈壁纸取色（WallpaperColors → 壁纸位图量化 → system_accent 兜底）。
 *   2026-10-26 按键震动改为受 setHapticEnabled() 控制（主界面「通用」里的「触感反馈」开关），
 *              三处震动调用统一收口到 tapFeedback()；
 *              震动实现改走 performHapticFeedback(..., FLAG_IGNORE_GLOBAL_SETTING)：
 *              系统「触摸振动」关闭时会过滤普通触感（IGNORED_FOR_SETTINGS），
 *              加该 flag 后只受本应用开关控制，保证开关打开就一定有反馈。
 *   2026-10-27 符号页带「+ / −」气泡的 N 键，长按候选里补充上标电荷符号 ⁺ / ⁻。
 *              符号页删除「角标」分类，仅保留度量 / 物质 / 反应 / 其他 4 个分页；
 *              命中测试外扩半格：行上下各探半格行距、列取最近按键中心并左右扩半格，
 *              点在按键缝隙里也会命中相邻按键（原来缝隙完全无触控事件）。
 *   2026-10-27 新增三种平板布局（setLayoutMode）：1 限宽居中（宽度上限 720dp，两侧留白）、
 *              2 横屏分体（字母三排与功能行中间断开 28dp 握持区，留白区不响应触摸）、
 *              3 悬浮小键盘（宽 66% / 上限 560dp 的直角卡片，顶部 20dp 拖动条可横向拖动，
 *              卡片外背景透明透出应用）；键区坐标统一走 contentLeft/contentW，
 *              大屏（>560dp）按键按 1.12 倍放大避免键面矮胖。
 *   2026-10-28 按参考图重构为三种平板布局：铺满 / 左右断开 / 浮窗（删除「限宽居中」）。
 *              左右断开按图逐行给出左右两套重复按键（Q..Y｜T..P、⇧A..H｜F..L、Z..B｜V..⌫、
 *              功能行 拼｜素·，·空格｜空格·←），中间 28dp 握持留白，底部 ⇌ 不分体、居中；
 *              浮窗不再由键盘自绘卡片与横向拖动，改为服务侧一张卡片（抓手 + 候选栏 + 键盘）
 *              整体上下左右拖动，候选栏随卡片一起移动，键区始终按卡片宽度铺满。
 *   2026-10-28 左右断开按 Figma 稿重新排版：两簇各占可用宽度 34.5%（原来各占半屏、键面被拉宽），
 *              中间留白约 31% 不响应触摸；右簇第三行 V B N M ⌫ 中 ⌫ 按图加宽到 2.2 倍键宽
 *              （复制一份键改权重，不影响铺满布局）。
 *   2026-10-28 三种布局显示名改为 平铺 / 分离 / 悬浮（常量值 LAYOUT_FULL=0、LAYOUT_SPLIT=1、
 *              LAYOUT_FLOAT=2 保持不变，避免旧偏好错位）。
 *   2026-10-29 新增 setCompactScale()：整体尺寸可按比例缩放（键高、间距、分类栏、图标偏移与
 *              键面字号），默认 1；手机横屏浮窗由服务侧下发 0.78，解决横屏浮窗偏大的问题。
 *              为此尺寸字段改为非 final，字号基准改走 fs = d × compact。
 *   2026-10-31 圆润风格（Figma 131-4224 / 169-1539）：强调色固定 #2E94F3
 *              （键底影 #115CBE），键盘底色 #E4E4E4，按键圆角 7、字母键底影 #00000017
 *              （符号页 #C7C7C7）、功能键底影 #00000026；符号页左侧分类卡圆角 18、
 *              选中块圆角 10 且改为 #E2E2E2 底 + 普通字色（不再用主题色填充）。
 *   2026-10-31 真机验证对齐 Figma：圆润「拼｜素」键改为黑字 + 14% 灰字（原用强调色，设计稿是黑字）；
 *              底部 ⇌ 条改画右侧「⇌ 切换输入法」胶囊（110×28 圆角 14、#D9D9D9、
 *              13sp #00000052，右距 15），触控仍整行命中 ACT_IME。
 *   2026-10-31 按客户新版 Figma（131-4224 符号面板 / 175-618 首页预览）一比一重排圆润键盘：
 *              按键圆角 6、键影下移 1px（字母页 #00000017、符号页 #D5D5D5、回车 #115CBE）；
 *              底色按页区分（字母 #EAEAEA、符号 #E4E4E4，切换分页即时刷新）；
 *              字母 20sp、角标/上滑 10sp、功能字 18sp；键高 46、行距 11.5、底部条 28（下留白 3）；
 *              符号页分类卡 107+9 宽 116、圆角 10、影 1px #00000026，选中块 #EAEAEA 圆角 10，
 *              卡内按设计固定排布（选中 49 高 + 25 高三项，偏移 9/72/123/174），分类字 20sp；
 *              符号网格左边界 132、横向间距 11（键宽 80），功能行 空格:⌫:← = 125:35:80；
 *              「其他」分类按设计改回「角标」，度量第三行「-」改「_」；
 *              底部胶囊右距 7、说明文字 12sp。
 *   2026-10-31 深色圆润配色按 177-1377/1516/1655/1194/1242/1290：键盘面 #252525、按键 #484848、
 *              主字白、角标 #FFFFFF80、键影 #000000、按下 #5A5A5A；分类选中块 #2E73F3 蓝底白字。
 *   2026-10-31 按 177-893 修正浅色符号页：键盘面与字母页统一 #EAEAEA（原符号页 #E4E4E4），
 *              分类选中块改为 #2E73F3 蓝底 + 白字（原浅色灰底黑字），与设计稿一致。
 *   2026-10-31 新增「键盘模式」（化学输入 / 普通输入）：
 *              普通字母页按 177-751（Q 行数字、A 行符号、Z 行 @·#、拼｜英 功能行、123 键）；
 *              普通符号页按 177-893（常用/中文/英文/其他 四分类，3×3 网格）；
 *              数字键盘页按 181-1791（左侧 +-×÷=% 白卡 + 3 列数字 + 符号/空格/⌫/回车），仅普通模式；
 *              工具栏页按 177-952（应用设置 / 键盘模式 两张白卡 + 返回键盘）；
 *              所有页底部统一为三芯片行：选择键盘模式｜当前模式 / 工具栏（工具栏页为返回键盘）/ ⇌ 切换输入法；
 *              所有圆角改走 Capsule G2 连续圆角（2026-11-03，Kyant0，Apache-2.0）。
 *   2026-10-31 按 187-1873（化学符号）/ 188-2117（普通符号）一比一重排圆润符号页：
 *              分类白卡 116×219（内边距 10/10/20）、圆角 10、影 1px #00000026；
 *              选中块改 #EAEAEA 圆角 5（43 高：9+25+9，卡内 y10），其余项 y70/122/174、高 25，
 *              分类字统一 17sp 黑字（原来选中块蓝底白字、20sp）；
 *              页面内边距 11、行距 11/12/12、芯片行前 12、底部留白 11（原 8 / 11.5 / 3）；
 *              空格字 17sp，删除图标 20×13、回车图标 24×17；
 *              底部芯片行右侧再留 4（170:78:110 → x8/190/280–390），模式名居中于 f[0]+132。
 *   2026-11-01 统一键面高度：五类页（化学字母 / 普通字母 / 符号 / 数字 / 工具栏）取内容最高值
 *              （不低于设计 282）作为固定高度，较矮页在顶部补白，
 *              切页时键盘总高恒定，候选栏与应用不再跟着跳动；字母页 padding-bottom 统一 13、
 *              行距 11/12/12/12（175-618 / 177-751），符号页内边距与行距对化学模式同样生效；
 *              底部三芯片行改为固定设计间距（x9 起、170 |14.5| 78 |14.5| 110、右缘 x396），
 *              数字页（运算符卡 75 + 10、列间 10）与普通符号页（分类卡 + 10、列间 10）
 *              也改用固定间距，键宽按屏宽等比缩放——375dp 窄屏上不再被反算成 0 间距贴死；
 *              芯片文字/图标按按钮实际宽度比例定位，窄屏压缩后不会溢出。
 *   2026-11-01 工具栏页按 177-952（浅色·圆润）/ 177-1290（深色·圆润）一比一重做：
 *              上边距 37、两行 84×70 卡片（行距 20）、芯片行前 46，内容高 282 与设计一致；
 *              四张卡为 应用设置 / 深色·浅色模式 / 键盘模式 / 悬浮键盘，图标 17×19 / 18×18 /
 *              19×16 / 20×16（深色「浅色模式」卡 24×24、上内边距 13、字距 7），
 *              图标下 12sp #00000080（深 #FFFFFF80）文字，卡片圆角 10、影 0 1 0（浅 #D5D5D5 / 深 #000000）；
 *              卡片横向按 402 设计宽等比缩放并居中（402 宽时 x104 / x212）。
 *              工具栏芯片行改按 177-952 的 x8 起、间距 12、右缘 390（其余页仍按 175-618 的 x9 / 14.5 / 396）；
 *              「选择键盘模式」蓝字居中于卡内 11+79 文本盒，⇌ 图标中心 x19、文字盒右距 -7；
 *              芯片行（选择键盘模式 / 工具栏 / 切换输入法）在所有页一律贴底（统一高度 - 11 - 28），
 *              下内边距统一 11，切页时三岛纵坐标完全不动（修复工具栏 / 符号 / 字母页切换时上下抖动）。
 *   2026-11-02 长按气泡按 177-751 / 224-12 / 177-1655 一比一重做（绘制见 KeyPopupView）；
 *              普通模式韵母键 A/E/I/O/U 长按追加四个汉语拼音声调
 *              （aāáǎà / eēéěè / iīíǐì / oōóǒò / uūúǔù），与设计稿 aāáǎà 串一致。
 *   2026-11-03 新增普通输入「九键」页（225-2「九宫格键盘／浅色·圆润」一比一）：
 *              左栏白卡 69×162 圆角 10（空闲写「，。？！」、组合时写首音节候选，17sp），
 *              3×3 数字键行高 46、行距 11/12、列权重 69/71/72/71/69，字母 20px；
 *              功能行 拼｜英 69 / 空格 164 / 123 59 / 回车 69（17sp，空格 20sp）；
 *              键影 #D5D5D5，⌫ 16×14、回车 26×17、⌄ 19×13 按设计稿尺寸绘制。
 *              横向改为按设计内容宽 387（402 - 左 8 - 右 7）等比缩放：375dp 屏上
 *              原来按 dynGap 反算只剩 ~1.8dp 间隙、底行 361 更被压成 0（键组贴死），
 *              现在键宽/间隙都是设计值 × u（u = 可用宽 / 387），任何屏宽保持设计稿比例。
 *   2026-11-03 九键右列按客户要求改为「删除 / 符号 / 大写」从上到下，第四格回车不变；
 *              原第一行的收起键盘 ⌄ 从键面移除，统一用候选栏右侧那颗 ⌄ 芯片收起。
 *   2026-11-04 删除所有页底部三芯片行「选择键盘模式 / 工具栏 / 切换输入法」并回收其占位：
 *              切页时不再绘制悬浮岛，键盘底部留白改回 216-207 的 13、统一高度下限 282 → 240
 *              （圆润内容最高为符号页约 243，字母页不再留出芯片条的空位）；
 *              工具栏页四卡按 177-952 的 37 上边距贴顶，统一高度多出的空间留在底部；
 *              新增 showSymbolPage()：品牌圆键单击改开工具栏后，符号页入口由长按保留。
 *   2026-11-04 工具栏页按 216-726 重做：三列两行共 6 张 90×102 白卡（列距 7、行距 8、
 *              上边距 15），新增「键盘调节 / 剪切板」两卡（iconmcp 图标 canvas-size /
 *              clipboard），原四卡图标与文字节奏按设计稿重新配平；
 *              新增 setUserScale()：键盘调节编辑态下发，宽向收窄内容并居中
 *              （contentLeft 统一进 keyRect / 分类卡 / 运算符卡 / 左栏卡 / 胶囊），
 *              高向与 compact 叠乘进 fs；新增 ACT_ADJUST / ACT_CLIP 两个动作。
 *   2026-11-04 按键字号改用固定基准 fsText = d × compact：键盘调节只缩放几何尺寸
 *              （键高 / 间距 / 分类卡），字母、功能字、角标、分类字、说明文字的字号
 *              不再随键盘放大缩小而变化。
 *   2026-11-04 键盘调节改为左右边界独立：setUserScale 换成 setUserBox(left, width, h)，
 *              contentLeft 由 userLeft 直接决定（不再强制居中），左右两侧可各自拖拽、
 *              互不影响，宽度可向两侧自由扩展或收窄。
 *   2026-11-04 几何基准拆成水平 / 垂直两套：fs = d × compact 只管左右（内边距、键宽、列间距、
 *              分类栏宽、横向偏移），fsV = d × compact × userScaleH 只管上下（行高、行距、
 *              纵向偏移、卡片高度）。键盘高度调节不再改变按键左右间距与水平位置。
 *   2026-11-04 修复普通（平铺）键盘按键文字整体消失：fsText 是新增字段，构造时漏了初始化，
 *              平铺布局 compact 恒为 1，setCompactScale(1f) 被等值判断提前 return，
 *              recomputeSizes() 从未执行 → fsText 保持 0、所有键字字号为 0（图标仍正常）；
 *              构造里补上 fsText = d，并同步 fs / fsV 初值。
 *   2026-11-05 键盘调节卡顿：setUserBox 区分「只改左右」与「改了高度」——左右边界只影响绘制
 *              坐标，改走 invalidate 不 requestLayout；只有高度变化才重测，避免拖动左右时整窗重排。
 *   2026-11-05 新增多点触控：主指保留原有的点按 / 上滑 / 长按 / 浮层流程，其余手指
 *              落到普通键上即记录并高亮，各自抬起时按点按结算（可双拇指同时打字，
 *              不会被丢掉第二个字母）；主指先抬起时同样按点按结算，不再丢字；
 *              Shift 的「按住大写 + 副指点字母」逻辑保持不变，副指滑出按键即取消。
 *   2026-11-08 反应栏交互重做：点蓝色加号后只有「输入槽 + 蓝色加号」从原输入槽位置（原芯片右缘）
 *              整体向右滑入并按线性进度渐显（位移 350ms ease-out，cubic-bezier 0,0,0.25,1），
 *              已存在的芯片全程静止、不改变透明度与样式；蓝钮始终贴在新输入槽右侧同步平移；
 *              输入槽宽度随组合区内容实时自适应；反应栏支持拖动 + 惯性，点击在抬起时结算。
 *   2026-11-08 配平态按 273_218：「已配平 / 撤销 / 确认」全部移入候选栏（服务侧负责），
 *              键盘内反应栏只保留方程式；反应栏高度保持与编辑态相同（56fsV / 行 6fsV+44fsV），
 *              配平前后键盘总高、反应栏纵坐标与高度均不变；
 *              方程式固定 17u 字号、左对齐 8u，超宽时横向滚动查看，不再自适应缩字号。
 *   2026-11-08 「发送」键不再绘制文字，改为客户提供的 24×17 回车图标（新增 ic_enter_send，
 *              手动 ACT_ENTER 与自动 ACT_SEND_EQ 共用），按 24:17 原比例绘制、不拉伸；
 *              其余 ← 回车键仍用 ic_enter，未受影响；label 仍保留「发送」作语义标识。
 *   2026-11-09 按客户新配色方案整体换色：
 *              浅色 —— 字符键帽 #FFFFFF（下描边 黑 20%）、操作键帽 #C9C9C9（下描边 黑 40%）、
 *              回车键帽 #2E73F3（下描边 #003376）、键盘底板 #E0E0E0；
 *              深色 —— 字符键帽 #606060、操作键帽 #414141、下描边纯黑、
 *              回车键帽 #2E73F3（下描边 #00142F）、键盘底板 #252525；
 *              长按字符气泡键帽跟随字符键（浅 #FFFFFF / 深 #606060）。
 *   2026-11-09 工具栏页 6 张卡图标全部换成客户提供的新图标（新增 ic_tool_kb / ic_tool_mode /
 *              ic_tool_sun / ic_tool_adjust / ic_tool_float / ic_tool_clip / ic_tool_settings，
 *              均 24×24 viewport、运行时 tint 着色）：应用设置（六边形）/ 深色·浅色模式（月牙·太阳）/
 *              键盘调节（方框双向箭头）/ 键盘模式（键盘）/ 悬浮键盘（层叠键盘）/ 剪切板（纸张）；
 *              6 个图标统一按 20×20 同比绘制不变形，textGap 重新配平保持文字纵坐标不动。
 *   2026-11-09 自动模式开关的胶囊轨道关闭态与主页开关统一：浅色 #DDDDDD / 深色 #6E6E6E
 *              （原固定 #898989），开启态仍为 #2E73F3。
 *   2026-11-22 平板专门适配（Figma 320_146 Frame 22 / 320_187 Frame 23，画板 1210×434）：
 *              宽 ≥600dp 时自动启用：字母页固定左右分离（左簇 QWERT/ASDFG/⇧ZXCV、
 *              右簇 YUIOP/HJKL/BNM⌫，中间留白约 31%），键高 69、字母 24sp、角标 10sp、
 *              行距 16/18、左右 13 / 上 10 / 下 28，全部按 fsTab = 全宽/1210 等比；
 *              普通模式「123」改为平板合并页（PAGE_TAB_NUM）：左分类卡 + 3×3 符号 + 空格
 *              + 运算符卡 + 三列数字 + 删除/回车，四分类复用普通符号页数据；
 *              平板专用页用全宽内容区（上限 1600dp），其余页仍限宽 640dp 居中。
 */
public class ChemKeyboardView extends View {

    public interface Listener {
        void onChar(String s);
        void onAction(int action);

        /**
         * 长按浮层状态变化（opts == null 表示收起）。
         * 坐标已在 IME 根布局坐标系里，交给叠在根布局上的 overlay 绘制。
         *
         * @param hover 当前手指选中的候选下标，-1 表示未选中
         * @param ax/ay 被长按按键的上边中点（浮层与按键之间的连线用）
         */
        default void onLongPressPopup(List<String> opts, RectF box, int hover, float ax, float ay) {
        }

        /**
         * 长按 Shift 的「按住大写」状态（2026-10-25）。
         * held=true：按住期间字母一律大写；held=false：松手恢复默认小写。
         */
        default void onShiftHoldChanged(boolean held) {
        }

        /** 键盘模式 / 输入方案切换（2026-11-01）：服务侧保存偏好并重置组合区。 */
        default void onInputModeChanged(int mode, int scheme) {
        }

        /** 页切换（字母 / 符号 / 数字 / 工具栏）：服务侧据此刷新候选栏左芯片文案。 */
        default void onPageChanged() {
        }

        /**
         * 九键左栏卡点按（2026-11-02）：index = 0..3。
         * 空闲时是「，。？！」标点；有组合时是首音节候选（由服务侧决定怎么处理）。
         */
        default void onT9RailItem(int index) {
        }

        /** 自动模式（2026-11-08）：取当前组合区原文（ASCII 化学式）。 */
        default String getComposingText() {
            return "";
        }

        /** 自动模式：清空组合区（确认一个反应物槽后调用）。 */
        default void clearComposing() {
        }

        /** 自动模式：把配平方程式直接上屏（绕过逐字处理）。 */
        default void commitTextDirect(String text) {
        }

        /** 自动模式撤销配平：把配平前输入槽里的原文放回组合区。 */
        default void restoreComposing(String text) {
        }

        /**
         * 配平态变化（2026-11-08）：进入配平态时服务侧在候选栏显示「撤销 / 确认」，
         * 并隐藏候选栏原有的功能按钮；确认或撤销回到编辑态后恢复原有按钮。
         */
        default void onReactionStateChanged(boolean balanced) {
        }
    }

    public static final int ACT_SHIFT = 1;
    public static final int ACT_MODE = 2;   // 拼/直
    public static final int ACT_CASE = 3;   // 化：元素自动大小写
    public static final int ACT_SPACE = 4;
    public static final int ACT_ENTER = 5;
    public static final int ACT_BACK = 6;
    public static final int ACT_PAGE = 7;   // 符号页/字母页
    /**
     * 底部 ⇌ 条 = 唤起系统输入法切换器（设计稿 §3.5.4）。
     * 客户第三轮验收明确：底部 ⇌ 就是切换输入法；当初按「客户 2026-10-11」改成的收起键盘是改错了键位。
     */
    public static final int ACT_IME = 8;
    /** 收起键盘（候选栏右侧的 ⌄ 芯片 = 客户第三轮指定的收起键盘入口）。 */
    public static final int ACT_HIDE = 9;
    /** 左下角「选择键盘模式｜普通输入」芯片：弹出模式选择浮层（由服务侧绘制）。 */
    public static final int ACT_KB_MODE = 10;
    /** 底部「工具栏」芯片：打开工具栏页（应用设置 / 键盘模式）。 */
    public static final int ACT_TOOLBAR = 11;
    /** 工具栏页底部「返回键盘」芯片。 */
    public static final int ACT_BACK_KB = 12;
    /** 普通模式功能行「123」：打开数字键盘页（仅普通输入法）。 */
    public static final int ACT_NUM = 13;
    /** 普通模式功能行「拼｜英」：中英切换。 */
    public static final int ACT_LANG = 14;
    /** 工具栏页「应用设置」卡：打开应用主界面。 */
    public static final int ACT_SETTINGS = 15;
    /** 数字键盘页「符号」键：跳符号页。 */
    public static final int ACT_SYMBOL = 16;
    /** 工具栏「深色模式 / 浅色模式」卡：切换输入法深浅色（2026-11-01，177-952 / 177-1290）。 */
    public static final int ACT_DARK = 17;
    /** 工具栏「悬浮键盘」卡：在悬浮 / 平铺之间切换（2026-11-01，177-952 / 177-1290）。 */
    public static final int ACT_FLOAT = 18;
    /** 工具栏「键盘调节」卡 / 候选栏快捷键：进入键盘调节编辑态（拖动改键盘宽高）。 */
    public static final int ACT_ADJUST = 19;
    /** 工具栏「剪切板」卡 / 候选栏快捷键：打开剪切板面板（2026-11-04，248-16）。 */
    public static final int ACT_CLIP = 20;
    /** 化学键盘「自动 / 手动」模式切换（2026-11-08）。 */
    public static final int ACT_AUTO_TOGGLE = 21;
    /** 自动模式「发生反应」：查询反应库并配平。 */
    public static final int ACT_REACT = 22;
    /** 自动模式「发送」：把配平后的方程式上屏。 */
    public static final int ACT_SEND_EQ = 23;
    /** 自动模式「拼｜素」：切换拼音 / 元素英文输入子状态。 */
    public static final int ACT_AUTO_LANG = 24;
    /** 配平态「确认」：把配平完成的方程式上屏到输入栏（2026-11-08）。 */
    public static final int ACT_CONFIRM = 25;
    /** 配平态「撤销」：取消本次配平，回退到配平前的原始输入（2026-11-08）。 */
    public static final int ACT_UNDO = 26;

    /** 键盘模式（2026-10-31 客户需求）：化学输入法 / 普通输入法，左下角芯片切换。 */
    public static final int MODE_CHEM = 0, MODE_NORMAL = 1;
    /** 普通模式的输入方案（2026-11-02）：与 RimeSetup.SCHEME_* 顺序一致。 */
    public static final int SCHEME_PINYIN = 0, SCHEME_FLYPY = 1, SCHEME_T9 = 2;
    /** 浮层四项：化学输入 / 普通·全拼 / 普通·双拼 / 普通·九键。 */
    public static final String[] MODE_NAMES = {"化学输入", "普通·全拼", "普通·双拼", "普通·九键"};

    /** 符号页分类名（顺序即 tab 顺序。2026-10-31 按新版 Figma 131-4224 恢复「角标」）。 */
    public static final String[] SYM_CAT_NAMES = {"度量", "物质", "反应", "角标"};
    /** 普通输入法符号页分类（177-893：常用 / 中文 / 英文 / 其他）。 */
    public static final String[] NORMAL_SYM_CAT_NAMES = {"常用", "中文", "英文", "其他"};

    // ---- 平板布局模式（2026-10-28 按参考图收敛为三种）----
    // 0 平铺：按可用宽度铺满（手机/平板默认）
    // 1 分离：字母行与功能行左右各一套重复按键，中间留握持区
    // 2 悬浮：整张卡片（抓手 + 候选栏 + 键盘）由服务侧上下左右拖动
    public static final int LAYOUT_FULL = 0, LAYOUT_SPLIT = 1, LAYOUT_FLOAT = 2;
    /** 显示名按索引对应上面三个常量（浮层里的显示顺序见 MainActivity.KB_ORDER）。 */
    public static final String[] LAYOUT_NAMES = {"平铺", "分离", "悬浮"};

    /* 页（2026-10-31 新增数字键盘页与工具栏页；数字页仅普通输入法有） */
    private static final int PAGE_LETTER = 0, PAGE_SYMBOL = 1, PAGE_NUMBER = 2, PAGE_TOOLBAR = 3;
    /** 平板合并页（2026-11-22）：数字 + 符号同屏，宽 ≥600dp 时取代数字页。 */
    public static final int PAGE_TAB_NUM = 4;
    /** pressedRow 标记：分类 tab / 数字页运算符卡 / 工具栏卡片（都不是普通按键行）。 */
    private static final int ROW_TAB = -2, ROW_OPS = -3, ROW_CARD = -4, ROW_RAIL = -5,
            ROW_TABNUM = -6;

    /** STYLE_CHIP = 底部三芯片行（2026-10-31）：圆角胶囊 + 12sp 灰字，单独绘制。 */
    private static final int STYLE_NORMAL = 0, STYLE_SPECIAL = 1, STYLE_ACCENT = 2, STYLE_PLAIN = 3,
            STYLE_CHIP = 4,
            /** 浅灰底黑字（设计稿 267_316「发生反应」#CACACA）。 */
            STYLE_LIGHT = 5;

    private static class Key {
        String label, swipe, chem, alt;
        /** 非空时点按上屏该字符串（九键：label 显示 ABC、实际发送数字 2）。 */
        String input;
        int action = 0;
        float weight = 1f;
        int style = STYLE_NORMAL;
        /** 单键自定义字号（dp，0 = 按页面默认）。 */
        float textSize;
        /** 角标与主字同轴上下堆叠（设计稿的「，/。」键），而非贴右下角。 */
        boolean stackChem = false;
        /** 占位键：只占宽度、不绘制也不响应触摸（左右断开的行首缩进用）。 */
        boolean spacer = false;
        /** 「自动模式」开关键（设计稿 267_316）：左侧两行小字 + 右侧蓝色胶囊开关，由绘制层特判。 */
        boolean switchToggle = false;
        /** 平板设计稿逐键宽度与左间距（px@1210 画板，2026-11-22）；0 = 该行仍按权重均分。 */
        float tabW, tabGap;
    }

    private static Key mk(String label, String swipe, String chem) {
        return mk(label, swipe, chem, null);
    }

    /** alt = 长按首选项（数字/符号的普通形态，设计稿：T → 5 / T / t）。 */
    private static Key mk(String label, String swipe, String chem, String alt) {
        Key key = new Key();
        key.label = label;
        key.swipe = swipe;
        key.chem = chem;
        key.alt = alt;
        return key;
    }

    private static Key sp(String label, int action) {
        Key key = new Key();
        key.label = label;
        key.action = action;
        key.style = STYLE_SPECIAL;
        return key;
    }

    private static Key sp(String label, int action, float weight) {
        Key key = sp(label, action);
        key.weight = weight;
        return key;
    }

    private static Key accentKey(String label, int action) {
        return accentKey(label, action, 1f);
    }

    private static Key accentKey(String label, int action, float weight) {
        Key key = sp(label, action, weight);
        key.style = STYLE_ACCENT;
        return key;
    }

    private static List<Key> row(Key... keys) {
        return new ArrayList<>(Arrays.asList(keys));
    }

    // ---------------- 页 / 分类状态 ----------------
    private int page = PAGE_LETTER;
    private int symbolCat = 0;
    /** 键盘模式（2026-10-31）：化学输入法 / 普通输入法。 */
    private int mode = MODE_CHEM;
    /** 化学键盘自动模式（2026-11-08）：false=手动（现状），true=自动配平反应。 */
    private boolean chemAutoMode = false;
    /** 自动模式输入子状态：false=拼音输入（无符号键面），true=元素英文输入（全符号键面）。 */
    private boolean autoEnglish = false;
    /** 反应状态机：0 编辑中 / 1 已配平。 */
    private int reactionState = 0;
    private static final int REACT_EDITING = 0, REACT_BALANCED = 1;
    /** 反应栏：已输入的反应物（ASCII 化学式）列表。 */
    private final List<String> reactionSlots = new ArrayList<>();
    /* 2026-11-08 新增反应物后的入场动画：只有「输入槽 + 蓝色加号」整体从旧输入槽位置向右滑入并渐显，
       已存在的芯片全程静止；位移用 ease-out（cubic-bezier 0,0,0.25,1），透明度按线性进度同步。 */
    private static final long SLOT_ANIM_DUR = 350;
    private static final Interpolator SLOT_EASE_OUT =
            new PathInterpolator(0f, 0f, 0.25f, 1f);
    private ValueAnimator slotAnimator;
    /** 线性进度 0→1；1 表示无动画（透明度按此线性变化）。 */
    private float slotAnimRaw = 1f;
    /** 动画起点：点击前输入槽左边缘的内容坐标（不含滚动偏移）。 */
    private float slotAnimFromX = 0f;
    /** 上一帧输入槽左边缘的内容坐标（每次点击前记录，作为下一次动画起点）。 */
    private float reactSlotContentX = 0f;
    /** 配平结果（reactionState==BALANCED 时有效）。 */
    private ReactionEngine.Reaction balancedReaction = null;
    /** 反应库引擎（延迟加载）。 */
    private ReactionEngine reactionEngine;
    /** 自动模式两种子状态的键面行。 */
    private List<List<Key>> autoPinyinRows, autoEnglishRows;
    /** 普通模式输入方案（2026-11-01）：0 全拼 / 1 小鹤双拼。 */
    private int normalScheme = SCHEME_PINYIN;
    /* 2026-10-29 符号页分类导航块的滑动过渡：tabFrom = 上一个分类，tabT = 0→1 进度 */
    private static final long TAB_DUR = 240;
    private static final Interpolator TAB_EASE = new PathInterpolator(0.16f, 0.84f, 0.22f, 1f);
    private int tabFrom = -1;
    private float tabT = 1f;
    private ValueAnimator tabAnimator;

    private List<List<Key>> letterRows;
    private final List<List<List<Key>>> symbolCats = new ArrayList<>();
    /** 普通输入法字母行与符号分类（177-751/177-893）。 */
    private List<List<Key>> normalLetterRows;
    /** 九键字母页（2026-11-02，225_2）：左栏卡 + 3×3 数字键 + 功能行 + 芯片行。 */
    private List<List<Key>> t9Rows;
    /** 九键左栏卡的四项文本：默认「，。？！」；有组合时为首音节候选。 */
    public static final String[] T9_RAIL_PUNCT = {"，", "。", "？", "！"};
    private List<String> t9RailOptions;
    /** 九键收起键盘图标（225_2 右上角 19×13）。 */
    private Drawable t9HideIcon;
    private final List<List<List<Key>>> normalSymCats = new ArrayList<>();
    /** 普通输入法数字页（181-1791）：4 行数字 + 1 行底部芯片。 */
    private final List<List<Key>> numberRows = new ArrayList<>();
    /** 工具栏页（177-952）：两张白卡 + 底部芯片行。 */
    private final List<List<Key>> toolbarRows = new ArrayList<>();
    /** 普通数字页左侧运算符（+ - × ÷ = %，设计稿 181-1791 的白卡）。 */
    private static final String[] NUMBER_OPS = {"+", "-", "×", "÷", "=", "%"};

    private boolean shiftOn = false;
    private boolean pinyinMode = true;
    private boolean autoCase = true;
    private boolean darkMode = false;
    /** 按键触感反馈开关（主界面「通用 → 触感反馈」，2026-10-26）。 */
    private boolean hapticEnabled = true;

    // 主题色
    private int accent = 0xFF05BEC1;
    private int accentDark = 0xFF05696B;
    private int kbBg = 0xFFEAEAEA, keyBgC = 0xFFFFFFFF, keySpecialC = 0xFFF4F4F4;
    private int labelColor = 0xFF000000, hintColor = 0x66000000;
    private int shadowColor = 0x26000000;
    private int offColor = 0x24000000;

    private final Listener listener;
    private final float d;
    /* 2026-10-29 尺寸可整体缩放：手机横屏浮窗用紧凑比例，故不再 final */
    private float padH, padBottom, gap, vGap, keyH, imeH, radius, tabW, tabGap;
    private float tabRadius, tabSelRadius;
    /** 尺寸缩放比（1 = 标准）。 */
    private float compact = 1f;
    /** 用户键盘调节（2026-11-04）：左右边界独立定位，userLeft/userWidth 为全宽的占比，
     *  userScaleH 缩放全部纵向尺寸（与 compact 叠乘）。默认 0 / 1 / 1，持久化在服务侧偏好。 */
    private float userLeft = 0f, userWidth = 1f, userScaleH = 1f;
    /** 水平几何基准 = d * compact：只跟紧凑比例，键盘高度调节不会改变左右间距与按键宽度。 */
    private float fs;
    /** 垂直几何基准 = d * compact * userScaleH：键盘高度调节只缩放它（行高 / 行距 / 纵向偏移）。 */
    private float fsV;
    /**
     * 字号基准（2026-11-04）：只跟随紧凑比例（手机横屏浮窗），不随键盘调节尺寸缩放，
     * 按键字号在放大 / 缩小时始终保持固定值。
     */
    private float fsText;

    private final Paint bgNormal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgSpecial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 符号页分类选中块底色（#E2E2E2）。 */
    private final Paint bgTabSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPressed = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 浅灰底键（「发生反应」：深色 #CACACA 黑字／浅色 #949494 白字）。 */
    private final Paint bgLight = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 自动模式开关胶囊 / 圆点（避免污染全局按键底色 Paint）。 */
    private final Paint togglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 反应栏芯片底（浅色 #D7D7D7 / 深色 #484848），与按键底色 Paint 分离。 */
    private final Paint reactChipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 「发生反应」键专用下描边（#2E73F3），与分类卡的 shadowPaint 分离避免相互污染。 */
    private final Paint reactShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // 分类卡内分隔线
    /** 圆润底部「⇌ 切换输入法」胶囊底色（Figma 129-1780：110×28 圆角 14）。 */
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 底部切换输入法图标（客户提供的 SVG，已转为 vector drawable）。 */
    private Drawable imeSwitchIcon;
    /** 删除键 / 回车键图标（客户 2026-10-25 提供的 SVG，随主题色/白字着色）。 */
    private Drawable backspaceIcon, enterIcon;
    /** 「发送」键的回车图标（客户 2026-11-08 提供 24×17 SVG，替代原来的「发送」文字）。 */
    private Drawable enterSendIcon;
    /**
     * 工具栏卡片图标（2026-11-09 换客户新图标）：
     * 设置 / 深浅色（月牙·太阳）/ 键盘调节（箭头方框）/ 键盘模式 / 悬浮键盘（层叠键盘）/ 剪切板。
     */
    private Drawable tbIconSettings, tbIconMode, tbIconSun, tbIconKeyboard, tbIconFloat;
    private Drawable tbIconAdjust, tbIconClipboard;
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();
    /** G2 连续圆角（2026-11-03，移植 Capsule）：所有圆角共用一个 Path 复用。 */
    private final android.graphics.Path smoothPath = new android.graphics.Path();
    /** 模式选择浮层（画在键盘 View 内部、左下角芯片上方）。 */
    private boolean modePopupOpen = false;
    private int modePopupPressed = -1;
    private final RectF modePopupBox = new RectF();

    private int pressedRow = -1, pressedCol = -1;
    private float downX, downY, curX, curY;

    // ---- 2026-11-05 多点触控 ----
    /** 主指（第一根落下的手指）的 pointerId：点按 / 上滑 / 长按 / 浮层只认主指。 */
    private int primaryPointerId = -1;
    /** 其余手指当前按住的键（pointerId → {row, col}）：抬起时按点按结算，滑出按键即取消。 */
    private final SparseArray<int[]> extraPointers = new SparseArray<>();

    // ---- 平板布局（2026-10-28）----
    /** 当前布局模式，见 LAYOUT_* 常量。 */
    private int layoutMode = LAYOUT_FULL;
    /** 键区宽度与左边界：各模式都按自身可用宽度铺满（浮窗的宽度由服务侧卡片决定）。 */
    private float contentW = 0f, contentLeft = 0f;
    /** 大屏按键放大系数（1 表示不放大）。 */
    private float keyScale = 1f;
    /** 统一键面高度（2026-11-01）：所有页共用，避免切页时键盘高度变化导致界面跳动。 */
    private float uniformH = 0f;
    /** 左右断开：左半 / 右半各行的按键（可复用同一 Key 对象，故允许重复键）与拼接后的整行。 */
    private final List<List<Key>> splitLeftRows = new ArrayList<>();
    private final List<List<Key>> splitRightRows = new ArrayList<>();
    private final List<List<Key>> splitRows = new ArrayList<>();
    /**
     * 左右断开时每一簇占「可用宽度」的比例（2026-10-28 按 Figma 稿量取：
     * 左簇 11→354 / 996、右簇 637→985 / 996，两簇各约 34.5%，中间留白约 31%）。
     */
    private static final float SPLIT_RATIO = 0.345f;
    /**
     * 键区内容最大宽度（dp，2026-11-11）：平板 / 横屏全屏平铺时可用宽度很大，
     * 键宽若按权重一直铺满会被拉得极宽、键帽又宽又扁；超过该宽度后不再拉宽，
     * 而是两侧留白居中，单键宽度与高度比例保持在可读范围内。
     */
    private static final float MAX_CONTENT_DP = 640f;
    // ---- 平板专门适配（2026-11-22，Figma Frame 22 / 23，画板 1210×434）----
    /** 是否平板宽度（可用宽度 ≥ 600dp）：平板放开内容宽上限并按 1210 基准等比铺满。 */
    private boolean tablet = false;
    /** 平板设计基准比例：contentW / 1210，键高 69、字母 24sp、角标 10sp、行距 16/18 都乘它。 */
    private float fsTab = 1f;
    /** 平板专用页的内容宽与左偏移（全宽，上限 1600dp）；其余页仍限宽 640dp 居中。 */
    private float tabContentW = 0f, tabContentLeft = 0f;
    /** 平板分离式字母页：左簇 / 右簇（普通输入与化学输入各一套，左右共用同一 Key 对象）。 */
    /** 平板分离式字母页：左簇 / 右簇（普通输入）。 */
    private final List<List<Key>> tabLeftRows = new ArrayList<>();
    private final List<List<Key>> tabRightRows = new ArrayList<>();
    private final List<List<Key>> tabSplitRows = new ArrayList<>();
    /** 平板分离式字母页：左簇 / 右簇（化学输入）。 */
    private final List<List<Key>> tabLeftRowsChem = new ArrayList<>();
    private final List<List<Key>> tabRightRowsChem = new ArrayList<>();
    private final List<List<Key>> tabSplitRowsChem = new ArrayList<>();
    /** 平板合并页（数字 + 符号）：左分类下标、运算符列与数字键列都由绘制代码按设计稿排。 */
    private int tabCat = 0;
    private boolean swipeFired = false, longPressDone = false;
    private String bubbleChar = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable longPressRunnable = this::fireLongPress;

    // ---- 2026-10-25 新增：长按删除连发 / 按住 Shift 双指大写 ----
    /** 副指点按字母时的短暂高亮（主指正按住 Shift，不能用 pressedRow 抢状态）。 */
    private int flashRow = -1, flashCol = -1;
    /** 按住 Shift 的那根手指的 pointerId；-1 表示当前没有按住。 */
    private int shiftPointerId = -1;
    private boolean shiftHeld = false;
    private final Runnable clearFlash = new Runnable() {
        @Override
        public void run() {
            flashRow = -1;
            flashCol = -1;
            invalidate();
        }
    };
    /** 每 70ms 删一个字符，直到 ACTION_UP / CANCEL 移除回调。 */
    private final Runnable backspaceRepeat = new Runnable() {
        @Override
        public void run() {
            if (pressedRow < 0 || pressedCol < 0) return;
            List<Key> keys = rowKeys(pressedRow);
            if (pressedCol >= keys.size() || keys.get(pressedCol).action != ACT_BACK) return;
            listener.onAction(ACT_BACK);
            handler.postDelayed(this, 70);
        }
    };

    /** 长按浮层状态（自绘 + 滑动选择，客户意见 3/6）。opts 为 null 表示未显示。 */
    private List<String> popupOpts;
    private int popupIndex = -1;
    private final RectF popupBox = new RectF();
    private float popupAnchorX, popupAnchorY;

    public ChemKeyboardView(Context ctx, Listener l) {
        super(ctx);
        listener = l;
        d = getResources().getDisplayMetrics().density;
        fs = d;
        fsV = d;
        fsText = d;
        uDesign = d;
        padH = 8 * d;
        padBottom = 3 * d;       // 底部留白 3
        gap = 7.5f * d;
        vGap = 11.5f * d;
        keyH = 46 * d;
        imeH = 28 * d;           // 底部 ⇌ 条高度 28
        radius = 6 * d;          // 按键圆角 6
        tabW = 116 * d;          // 符号页左侧分类栏宽度 116
        tabGap = 11.5f * d;
        imeSwitchIcon = getContext().getDrawable(R.drawable.ic_ime_switch);
        backspaceIcon = getContext().getDrawable(R.drawable.ic_backspace);
        enterIcon = getContext().getDrawable(R.drawable.ic_enter);
        enterSendIcon = getContext().getDrawable(R.drawable.ic_enter_send);
        t9HideIcon = getContext().getDrawable(R.drawable.ic_chevron_down_small);
        tbIconSettings = getContext().getDrawable(R.drawable.ic_tool_settings);
        tbIconMode = getContext().getDrawable(R.drawable.ic_tool_mode);
        tbIconSun = getContext().getDrawable(R.drawable.ic_tool_sun);
        tbIconKeyboard = getContext().getDrawable(R.drawable.ic_tool_kb);
        tbIconFloat = getContext().getDrawable(R.drawable.ic_tool_float);
        tbIconAdjust = getContext().getDrawable(R.drawable.ic_tool_adjust);
        tbIconClipboard = getContext().getDrawable(R.drawable.ic_tool_clip);
        bubbleBorder.setStyle(Paint.Style.STROKE);
        bubbleBorder.setStrokeWidth(d);
        dividerPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hintPaint.setTextAlign(Paint.Align.RIGHT);
        chemPaint.setTextAlign(Paint.Align.RIGHT);
        tabPaint.setTextAlign(Paint.Align.CENTER);
        tabPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bubbleText.setTextAlign(Paint.Align.CENTER);
        bubbleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setLongClickable(false);
        reactScroller = new OverScroller(ctx);
        reactTouchSlop = (int) (6 * d);
        buildPages();
        applyThemeColors();
    }

    /** 「自动」主题：跟随系统壁纸取色（客户 2026-10-11 意见 11）。 */
    public static final String THEME_AUTO = "auto";
    private static int autoAccent = 0xFF5C6BC0;
    private static int autoAccentDark = 0xFF3949AB;
    /** 取色结果缓存：壁纸没变就不必反复解码位图。收到壁纸变更广播时置脏。 */
    private static boolean autoResolved = false;

    /** 壁纸换了：丢弃缓存，下次 resolveAutoColors 重新取色。 */
    public static void invalidateAutoColors() {
        autoResolved = false;
    }

    /**
     * 解析「自动」主题的颜色并缓存到静态字段。
     *
     * 取色顺序（由准到稳）：
     * 1. Android 12+ 的 {@link WallpaperColors} —— 系统 Monet 调色板的种子色，最贴合壁纸；
     * 2. 壁纸位图量化 —— 部分 OEM（实测 HyperOS）不做系统级取色
     *    （dumpsys wallpaper 里 isColorExtracted=false），此时 WallpaperColors 为 null，
     *    只能自己降采样统计主色调；
     * 3. 系统 accent1_500 资源 —— 最后兜底。
     *
     * 取到的种子色统一做饱和度/明度归一化，保证主题色够鲜艳、且白字对比度足够。
     */
    public static void resolveAutoColors(Context ctx) {
        if (autoResolved) return;
        int seed = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            seed = wallpaperColorsSeed(ctx);
        }
        if (seed == 0) {
            seed = wallpaperBitmapSeed(ctx);
        }
        if (seed == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                seed = ctx.getResources().getColor(android.R.color.system_accent1_500, ctx.getTheme());
            } catch (Throwable ignored) {
            }
        }
        int a = normalizeAccent(seed);
        if (a != 0) {
            autoAccent = a;
            autoAccentDark = darkerAccent(a);
        }
        autoResolved = true;
    }

    /** Monet 种子：主/次/三级色里挑最鲜艳、明度适中的那个。 */
    private static int wallpaperColorsSeed(Context ctx) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return 0;
            Color[] colors = {wc.getPrimaryColor(), wc.getSecondaryColor(), wc.getTertiaryColor()};
            int best = 0;
            float bestScore = 0f;
            float[] hsv = new float[3];
            for (Color c : colors) {
                if (c == null) continue;
                int rgb = c.toArgb();
                Color.RGBToHSV(Color.red(rgb), Color.green(rgb), Color.blue(rgb), hsv);
                if (hsv[1] < 0.12f || hsv[2] < 0.10f || hsv[2] > 0.97f) continue;
                float score = hsv[1] * (1f - Math.abs(hsv[2] - 0.55f));
                if (score > bestScore) {
                    bestScore = score;
                    best = rgb;
                }
            }
            return best;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 壁纸位图降采样后按色相分桶，取占比最高的鲜艳色。 */
    private static int wallpaperBitmapSeed(Context ctx) {
        Bitmap small = null;
        try {
            Drawable d = WallpaperManager.getInstance(ctx).getDrawable();
            if (d == null) return 0;
            small = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            d.setBounds(0, 0, 64, 64);
            d.draw(c);

            final int bins = 24; // 每桶 15°
            int[] weight = new int[bins];
            long[] r = new long[bins], g = new long[bins], b = new long[bins];
            float[] hsv = new float[3];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int p = small.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv);
                    // 跳过灰、过暗、过曝的像素（都不适合当主题色）
                    if (hsv[1] < 0.18f || hsv[2] < 0.12f || hsv[2] > 0.97f) continue;
                    int idx = (int) (hsv[0] / (360f / bins)) % bins;
                    int w = (int) (hsv[1] * 100);
                    weight[idx] += w;
                    r[idx] += (long) Color.red(p) * w;
                    g[idx] += (long) Color.green(p) * w;
                    b[idx] += (long) Color.blue(p) * w;
                }
            }
            int best = -1, bw = 0;
            for (int i = 0; i < bins; i++) {
                // 相邻桶合并，避免主色刚好跨桶时被拆散
                int sum = weight[i] + weight[(i + 1) % bins];
                if (sum > bw) {
                    bw = sum;
                    best = i;
                }
            }
            // 鲜艳像素太少（几乎纯灰壁纸）就不硬造颜色
            if (best < 0 || bw < 1200) return 0;
            int i2 = (best + 1) % bins;
            long tw = weight[best] + weight[i2];
            return Color.rgb((int) ((r[best] + r[i2]) / tw),
                    (int) ((g[best] + g[i2]) / tw),
                    (int) ((b[best] + b[i2]) / tw));
        } catch (Throwable t) {
            return 0;
        } finally {
            if (small != null) small.recycle();
        }
    }

    /** 把任意种子色调成「适合当强调色」的鲜艳中间调；灰色返回 0。 */
    private static int normalizeAccent(int seed) {
        if (seed == 0) return 0;
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(seed), Color.green(seed), Color.blue(seed), hsv);
        if (hsv[1] < 0.12f) return 0;
        hsv[1] = Math.min(0.92f, Math.max(0.55f, hsv[1] * 1.15f + 0.10f));
        hsv[2] = 0.78f;
        int c = Color.HSVToColor(hsv);
        // 主题条上是白字，明度压到白字可读为止（与 AgBr 黄的处理一致）
        while (luminance(c) > 158 && hsv[2] > 0.40f) {
            hsv[2] -= 0.04f;
            c = Color.HSVToColor(hsv);
        }
        return c;
    }

    /** 同色相/饱和度，压暗得到深色变体（键底影、按压、状态栏）。 */
    private static int darkerAccent(int accent) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(accent), Color.green(accent), Color.blue(accent), hsv);
        hsv[2] = Math.max(0.28f, hsv[2] * 0.58f);
        return Color.HSVToColor(hsv);
    }

    private static float luminance(int c) {
        return 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c);
    }

    /** Figma 设计稿四主题 + 「自动」（壁纸取色）：AgBr 金 / KMnO₄ 紫 / CuCl₂ 青 / Fe₂O₃ 红。 */
    public static int themeColor(String id) {
        if (THEME_AUTO.equals(id)) return autoAccent;
        switch (id) {
            case "agbr": return 0xFFE7C400;
            case "kmno4": return 0xFF8F2DB9;
            case "fe2o3": return 0xFFB92D2D;
            case "cucl2":
            default: return 0xFF05BEC1;
        }
    }

    /** 主题深色（键底影 / 按压 / 状态栏）。 */
    public static int themeColorDark(String id) {
        if (THEME_AUTO.equals(id)) return autoAccentDark;
        switch (id) {
            case "agbr": return 0xFF8F7A00;
            case "kmno4": return 0xFF55196F;
            case "fe2o3": return 0xFF6E1A1A;
            case "cucl2":
            default: return 0xFF05696B;
        }
    }

    public void setThemeColors(String themeId, boolean dark) {
        // 圆润风格统一用 #2E73F3 强调色（Figma 131-4224），不随四主题变化
        accent = UiStyle.ACCENT;
        accentDark = 0xFF115CBE;
        darkMode = dark;
        applyThemeColors();
        invalidate();
    }

    /** 主题色偏暗时用白字，偏亮时用深字，保证对比度。 */
    private int onAccentColor() {
        return isDarkColor(accent) ? 0xFFFFFFFF : 0xE6101418;
    }

    private static boolean isDarkColor(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) < 160;
    }

    private void applyThemeColors() {
        radius = 6f * d;              // 按键圆角 6
        tabRadius = 10f * d;          // 分类卡圆角 10
        tabSelRadius = 5f * d;        // 分类选中块圆角 5（187-1873/188-2117）
        // 分类栏 116、底部条 28、底部留白 3
        tabW = 116f * fs;
        imeH = 28f * fsV;
        padBottom = 3f * fsV;
        if (darkMode) {
            // 2026-11-09 新配色（深色）：键盘底板 #252525、字符键键帽 #606060、
            // 操作键键帽 #414141、下描边 #000000（100%）、主字白、角标 #FFFFFF80
            kbBg = 0xFF252525;
            keyBgC = 0xFF606060;
            keySpecialC = 0xFF414141;
            labelColor = 0xFFFFFFFF;
            hintColor = 0xFFFFFFFF;
            shadowColor = 0xFF000000;
            offColor = 0x80FFFFFF;
            bgPressed.setColor(0xFF5A5A5A);
            letterShadow.setColor(0xFF000000);
        } else {
            // 2026-11-09 新配色（浅色）：键盘底板 #E0E0E0、字符键键帽 #FFFFFF、
            // 操作键键帽 #C9C9C9、下描边 黑 20%/40%、主字黑
            kbBg = 0xFFE0E0E0;
            keyBgC = 0xFFFFFFFF;
            keySpecialC = 0xFFC9C9C9;
            labelColor = 0xFF000000;
            hintColor = 0xFF000000;
            shadowColor = 0x66000000;
            offColor = 0x24000000;
        }
        bgNormal.setColor(keyBgC);
        bgSpecial.setColor(keySpecialC);
        bgAccent.setColor(accent);
        bgLight.setColor(0xFFCACACA);
        // 按下态：浅色 #D6D6D6（比底板 #E0E0E0 深一档，白键按下仍能看出形状）/ 深 #5A5A5A
        bgPressed.setColor(darkMode ? 0xFF5A5A5A : 0xFFD6D6D6);
        shadowPaint.setColor(shadowColor);
        // 键下描边（2026-11-09 新配色）：浅色 字符键 黑 20% / 操作键 黑 40%；深色统一纯黑
        letterShadow.setColor(darkMode ? 0xFF000000 : 0x33000000);
        // 回车键下描边：浅色 #003376 / 深色 #00142F（回车键帽统一 #2E73F3）
        shadowAccent.setColor(darkMode ? 0xFF00142F : 0xFF003376);
        // 分类选中块：按 187-1873/188-2117 浅灰 #EAEAEA + 黑字（深色取 #5A5A5A）
        bgTabSel.setColor(darkMode ? 0xFF5A5A5A : 0xFFEAEAEA);
        textPaint.setColor(labelColor);
        hintPaint.setColor(labelColor);
        chemPaint.setColor(accent);
        // 长按字符气泡键帽跟随字符键：浅 #FFFFFF / 深 #606060（外阴影不变）
        bubblePaint.setColor(darkMode ? 0xFF606060 : 0xFFFFFFFF);
        bubbleBorder.setColor(accent);
        bubbleText.setColor(labelColor);
        dividerPaint.setColor(darkMode ? 0x1AFFFFFF : 0x14000000);
        // 悬浮模式的卡片外区域要透出下方应用，其余模式铺满键盘底色
        setBackgroundColor(layoutMode == LAYOUT_FLOAT ? Color.TRANSPARENT : kbBg);
    }

    // ---------------- 页数据 ----------------

    private void buildPages() {
        buildLetterRows();
        buildNormalLetterRows();
        buildT9Rows();
        buildSplitRows();
        buildTabletSplitRows();
        buildSymbolCats();
        buildNormalSymbolCats();
        buildNumberRows();
        buildToolbarRows();
    }

    /**
     * 左右断开用的重复键行（2026-10-28，对齐参考图）：
     * 复用原键对象，因此同一个键会同时出现在左右两半；行首占位键只占宽度不绘制。
     */
    private void buildSplitRows() {
        splitLeftRows.clear();
        splitRightRows.clear();
        splitRows.clear();
        if (letterRows == null || letterRows.size() < 4) return;
        List<Key> r0 = letterRows.get(0), r1 = letterRows.get(1),
                r2 = letterRows.get(2), rf = letterRows.get(3);

        splitLeftRows.add(row(keyOf(r0, "Q"), keyOf(r0, "W"), keyOf(r0, "E"),
                keyOf(r0, "R"), keyOf(r0, "T"), keyOf(r0, "Y")));
        splitRightRows.add(row(keyOf(r0, "T"), keyOf(r0, "Y"), keyOf(r0, "U"),
                keyOf(r0, "I"), keyOf(r0, "O"), keyOf(r0, "P")));

        splitLeftRows.add(row(keyOf(r1, "A"), keyOf(r1, "S"), keyOf(r1, "D"),
                keyOf(r1, "F"), keyOf(r1, "G"), keyOf(r1, "H")));
        splitRightRows.add(row(keyOf(r1, "F"), keyOf(r1, "G"), keyOf(r1, "H"),
                keyOf(r1, "J"), keyOf(r1, "K"), keyOf(r1, "L")));

        splitLeftRows.add(row(keyOf(r2, "⇧"), keyOf(r2, "Z"), keyOf(r2, "X"),
                keyOf(r2, "C"), keyOf(r2, "V"), keyOf(r2, "B")));
        // 右簇只有 5 键，参考图里 ⌫ 约为普通键 2.2 倍宽；复制一份改权重，避免影响铺满布局
        splitRightRows.add(row(keyOf(r2, "V"), keyOf(r2, "B"), keyOf(r2, "N"),
                keyOf(r2, "M"), withWeight(keyOf(r2, "⌫"), 2.2f)));

        Key space = keyOf(rf, "空格");
        splitLeftRows.add(row(keyOfAction(rf, ACT_MODE), keyOf(rf, "，"), space));
        splitRightRows.add(row(space, keyOfAction(rf, ACT_ENTER)));

        for (int i = 0; i < splitLeftRows.size(); i++) {
            List<Key> both = new ArrayList<>(splitLeftRows.get(i));
            both.addAll(splitRightRows.get(i));
            splitRows.add(both);
        }
    }

    private static Key keyOf(List<Key> keys, String label) {
        for (Key k : keys) if (label.equals(k.label)) return k;
        return null;
    }

    /**
     * 平板分离式字母页（Figma 320_146 / Frame 22，1210×434 画板）：
     * 第一排 Q W E R T ｜ Y U I O P、第二排 A S D F G ｜ H J K L、
     * 第三排 ⇧ Z X C V ｜ B N M ⌫；功能行左「拼｜英 · ，。 · 空格」、右「空格 · 123 · 回车」
     * （化学输入按同一规则拆现有 letterRows：左「拼｜素 · 开关 · 空格」、右「空格 · 发送」）。
     * 两簇共用同一批 Key 对象，空格键在左右各出现一次。
     */
    private void buildTabletSplitRows() {
        buildTabletSplit(tabLeftRows, tabRightRows, tabSplitRows, normalLetterRows);
        buildTabletSplit(tabLeftRowsChem, tabRightRowsChem, tabSplitRowsChem, letterRows);
        applyTabletMetrics();
    }

    /**
     * 平板普通字母页功能行键宽（px@1210 画板，2026-11-22，Figma 320_146）。
     * 三排字母键统一 67 宽 / 17 间距，由 keyRectRaw 计算，不在此逐键赋值。
     */
    private void applyTabletMetrics() {
        // 功能行：拼｜英 165 · ， 73 · 空格 167 ｜ 空格 165 · 123 100 · 回车 139，间距统一 20
        if (tabSplitRows.size() > 3) {
            float[] fw = {165, 73, 167, 165, 100, 139};
            List<Key> row = tabSplitRows.get(3);
            for (int j = 0; j < row.size() && j < fw.length; j++) {
                row.get(j).tabW = fw[j];
            }
        }
    }

    /** 平板字母行单键宽：字母统一 67，第三排 ⇧ 96 / ⌫ 98（设计稿 320_146）。 */
    private float tabLetterKeyW(int row, boolean inLeft, int idx, int count) {
        if (row == 2) {
            if (inLeft && idx == 0) return 96 * fsTab;
            if (!inLeft && idx == count - 1) return 98 * fsTab;
        }
        return 67 * fsTab;
    }

    private void buildTabletSplit(List<List<Key>> leftAll, List<List<Key>> rightAll,
                                  List<List<Key>> merged, List<List<Key>> src) {
        leftAll.clear();
        rightAll.clear();
        merged.clear();
        if (src == null || src.size() < 4) return;

        for (int r = 0; r < 3; r++) {
            List<Key> letters = new ArrayList<>();
            for (Key k : src.get(r)) if (k != null && !k.spacer) letters.add(k);
            int n = letters.size();
            // 按设计稿（320_146）：每簇固定 5 键、字母键等宽；
            // 第一排 QWERT｜YUIOP（10 键不重叠），第二排 ASDFG｜GHJKL、第三排 ⇧ZXCV｜VBNM⌫
            // （9 键时中间的 G / V 左右两簇都有，右簇从下标 4 开始）
            int cut, rightStart;
            if (n >= 10) {
                cut = 5;
                rightStart = 5;
            } else if (n == 9) {
                cut = 5;
                rightStart = 4;
            } else {
                cut = (n + 1) / 2;
                rightStart = cut;
            }
            leftAll.add(new ArrayList<>(letters.subList(0, Math.min(cut, n))));
            rightAll.add(new ArrayList<>(letters.subList(Math.min(rightStart, n), n)));
        }

        // 功能行：左 = 前两颗功能键 + 空格；右 = 空格 + 其余键
        List<Key> f = new ArrayList<>();
        for (Key k : src.get(3)) if (k != null && !k.spacer) f.add(k);
        Key space = null;
        for (Key k : f) if (k.action == ACT_SPACE) { space = k; break; }
        List<Key> lf = new ArrayList<>();
        List<Key> rf = new ArrayList<>();
        if (space != null) {
            lf.add(f.get(0));
            if (f.size() > 1) lf.add(f.get(1));
            lf.add(space);
            rf.add(space);
            for (int i = 2; i < f.size(); i++) if (f.get(i) != space) rf.add(f.get(i));
        } else {
            int mid = f.size() / 2;
            lf.addAll(f.subList(0, mid));
            rf.addAll(f.subList(mid, f.size()));
        }
        leftAll.add(lf);
        rightAll.add(rf);

        for (int i = 0; i < leftAll.size(); i++) {
            List<Key> both = new ArrayList<>(leftAll.get(i));
            both.addAll(rightAll.get(i));
            merged.add(both);
        }
    }

    private static Key keyOfAction(List<Key> keys, int action) {
        for (Key k : keys) if (k.action == action) return k;
        return null;
    }

    /** 复制一个键并改权重（左右断开里同一键在两簇中的宽度可能不同）。 */
    private static Key withWeight(Key src, float weight) {
        if (src == null) return null;
        Key key = mk(src.label, src.swipe, src.chem, src.alt);
        key.input = src.input;
        key.textSize = src.textSize;
        key.action = src.action;
        key.style = src.style;
        key.stackChem = src.stackChem;
        key.weight = weight;
        return key;
    }

    /**
     * 字母页：QWERTY 三排 + 功能行 + 底部 ⇌ 细条。
     * r1 右上=上标数字、右下=下标数字，长按 alt=普通数字（设计稿 T → 5 / T / t）。
     * r2/r3 右上=上滑化学符号、右下=另一化学符号。
     */
    private void buildLetterRows() {
        letterRows = new ArrayList<>();

        letterRows.add(row(
                mk("Q", "¹", "₁", "1"), mk("W", "²", "₂", "2"),
                mk("E", "³", "₃", "3"), mk("R", "⁴", "₄", "4"),
                mk("T", "⁵", "₅", "5"), mk("Y", "⁶", "₆", "6"),
                mk("U", "⁷", "₇", "7"), mk("I", "⁸", "₈", "8"),
                mk("O", "⁹", "₉", "9"), mk("P", "⁰", "₀", "0")));

        letterRows.add(row(
                mk("A", "α", "Å"), mk("S", "σ", "Σ"),
                mk("D", "[", "]"), mk("F", "{", "}"),
                mk("G", "(", ")"), mk("H", "←", "→"),
                mk("J", "↑", "↓"), mk("K", "⇌", "↔"),
                mk("L", "·", "×")));

        letterRows.add(row(
                sp("⇧", ACT_SHIFT),
                mk("Z", "Δ", "°"), mk("X", "℃", "±"),
                mk("C", "ν", "λ"), mk("V", "σ", "π"),
                mk("B", "=", "≡"), mk("N", "+", "−"),
                mk("M", "%", "‰"),
                sp("⌫", ACT_BACK)));

        // 功能行一比一复刻设计稿（手动模式 design）：拼｜素 78 · 自动模式开关 85 · 空格 · 发送 85。
        // 2026-11-08 开关置于「拼｜素」右侧，与设计稿顺序一致；
        // 三颗功能键字号与自动模式统一 17sp（手动模式原来 20sp，明显偏大）。
        Key langKey = sp("拼｜素", ACT_MODE, 78f);
        langKey.textSize = 17f;
        Key autoSw = sp("", ACT_AUTO_TOGGLE, 85f);
        autoSw.switchToggle = true;
        Key spaceKey = sp("空格", ACT_SPACE, 117f);
        spaceKey.textSize = 17f;
        Key sendKey = accentKey("发送", ACT_ENTER, 85f);
        sendKey.textSize = 17f;
        letterRows.add(row(langKey, autoSw, spaceKey, sendKey));

    }

    /** 自动模式两套键面：拼音（无化学符号角标）与元素英文（完整化学符号）。 */
    private void ensureAutoRows() {
        if (autoPinyinRows != null && autoEnglishRows != null) return;

        // 拼音子状态：纯 QWERTY，无化学符号角标
        autoPinyinRows = new ArrayList<>();
        autoPinyinRows.add(row(
                mk("Q", null, null), mk("W", null, null), mk("E", null, null),
                mk("R", null, null), mk("T", null, null),
                mk("Y", null, null), mk("U", null, null), mk("I", null, null),
                mk("O", null, null), mk("P", null, null)));
        autoPinyinRows.add(row(
                mk("A", null, null), mk("S", null, null), mk("D", null, null),
                mk("F", null, null), mk("G", null, null),
                mk("H", null, null), mk("J", null, null), mk("K", null, null),
                mk("L", null, null)));
        autoPinyinRows.add(row(
                sp("⇧", ACT_SHIFT),
                mk("Z", null, null), mk("X", null, null), mk("C", null, null),
                mk("V", null, null),
                mk("B", null, null), mk("N", null, null), mk("M", null, null),
                sp("⌫", ACT_BACK)));
        autoPinyinRows.add(autoFuncRow());

        // 元素英文子状态：复用现有化学字母页的三排（带符号角标）+ 自动功能行
        autoEnglishRows = new ArrayList<>();
        autoEnglishRows.add(new ArrayList<>(letterRows.get(0)));
        autoEnglishRows.add(new ArrayList<>(letterRows.get(1)));
        autoEnglishRows.add(new ArrayList<>(letterRows.get(2)));
        autoEnglishRows.add(autoFuncRow());
    }

    /**
     * 自动模式功能行（设计稿 267_316 一比一）：
     * 拼｜素 78 · 自动模式开关 85 · 发生反应 115 · 发送 85，合计 363，间隙 8×3。
     */
    private List<Key> autoFuncRow() {
        Key toggle = sp("", ACT_AUTO_TOGGLE, 85f);
        toggle.switchToggle = true;

        Key react = sp("发生反应", ACT_REACT, 115f);
        react.style = STYLE_LIGHT;
        react.textSize = 17f;

        Key send = accentKey("发送", ACT_SEND_EQ, 85f);
        send.textSize = 17f;

        Key lang = sp("拼｜素", ACT_AUTO_LANG, 78f);
        lang.textSize = 17f;

        return row(lang, toggle, react, send);
    }

    /**
     * 普通输入法字母页（2026-10-31，按 177-751）：
     * 第 1 行 Q..P 右上角小数字 1-0；第 2 行 A..L 右上角 - / : ; ( ) “ ” ~；
     * 第 3 行 ⇧ Z..M ⌫ 右上角 @ · # 、 ？ ！ ...；
     * 功能行 拼｜英（中英切换）· ，/。 · 空格 · 123（数字键盘）· 回车。
     * 顶部小字使用 alt 字段：点按上屏字母，长按浮层给出小字/大写/小写。
     */
    private void buildNormalLetterRows() {
        normalLetterRows = new ArrayList<>();

        normalLetterRows.add(row(
                nk("Q", "1"), nk("W", "2"), nk("E", "3"), nk("R", "4"), nk("T", "5"),
                nk("Y", "6"), nk("U", "7"), nk("I", "8"), nk("O", "9"), nk("P", "0")));

        // 第 2 行左右各缩进 13 / 14（设计稿 autoWrapper2 的 padding），用占位键吸收宽度
        normalLetterRows.add(row(
                spacer(13f),
                nk("A", "-"), nk("S", "/"), nk("D", ":"), nk("F", ";"),
                nk("G", "("), nk("H", ")"), nk("J", "“"), nk("K", "”"), nk("L", "~"),
                spacer(14f)));

        normalLetterRows.add(row(
                spacer(1f), sp("⇧", ACT_SHIFT, 45f),
                nk("Z", "@"), nk("X", "·"), nk("C", "#"), nk("V", "、"),
                nk("B", "？"), nk("N", "！"), nk("M", "..."),
                sp("⌫", ACT_BACK, 46f), spacer(1f)));

        // 功能行：拼｜英 78 · ，/。 35 · 122 · 47 · 回车 65（177-751 autoWrapper4）
        // 2026-11-09 逗号键归类为操作键（#C9C9C9 / 深 #414141），不再用字符键底色
        Key comma = sp("，", 0, 35f);
        comma.alt = "。";
        normalLetterRows.add(row(
                sp("拼｜英", ACT_LANG, 78f),
                comma,
                sp("空格", ACT_SPACE, 122f),
                sp("123", ACT_NUM, 47f),
                accentKey("←", ACT_ENTER, 65f)));

    }

    /** 普通输入法字母键：alt = 右上角小字（数字/符号），长按只给「小字 · 大写 · 小写」。 */
    private static Key nk(String label, String alt) {
        Key key = mk(label, null, null, alt);
        // 设计稿 177-751 字母键宽 33（px 权重），与 ⇧45 / ⌫46 / 缩进 13 同一坐标系，
        // 行内间距按「填满可用宽度」自适应，正好复现原稿 6.3 / 7.9 / 3.3 的三行间距。
        key.weight = 33f;
        return key;
    }

    /** 占位键：只占宽度、不绘制也不响应触摸（行首缩进用）。 */
    private static Key spacer(float weight) {
        Key key = new Key();
        key.spacer = true;
        key.weight = weight;
        return key;
    }

    /**
     * 九键字母页（2026-11-02，Figma 225_2「九宫格键盘／浅色·圆润」一比一）：
     * 左栏 69×162 白卡（，。？！，有组合时换首音节候选），右侧 3×3 数字键 71/72/71、
     * 右列收起键盘 / 符号 / 删除，底行 拼｜英 69 · 空格 164 · 123 59 · ← 69。
     * 数字键 label 显示字母组（ABC…），实际发送数字 2-9（Key.input）。
     */
    private void buildT9Rows() {
        t9Rows = new ArrayList<>();

        // 2026-11-03 右列顺序按客户要求：删除 → 符号 → 大写；收起键盘（⌄）移出键面，
        // 统一收在候选栏右侧那颗 ⌄ 芯片（原来门在键面第一行，占掉一个功能位）。
        Key del = sp("", ACT_BACK, 69f);
        t9Rows.add(row(spacer(69f), t9("@#", "@", 71f), t9("ABC", "2", 72f),
                t9("DEF", "3", 71f), del));

        Key symKey = sp("符号", ACT_SYMBOL, 69f);
        symKey.textSize = 17f;
        t9Rows.add(row(spacer(69f), t9("GHI", "4", 71f), t9("JKL", "5", 72f),
                t9("MNO", "6", 71f), symKey));

        Key shift = sp("⇧", ACT_SHIFT, 69f);
        shift.textSize = 20f;
        t9Rows.add(row(spacer(69f), t9("PQRS", "7", 71f), t9("TUV", "8", 72f),
                t9("WXYZ", "9", 71f), shift));

        Key lang = sp("拼｜英", ACT_LANG, 69f);
        lang.textSize = 17f;
        Key space = sp("空格", ACT_SPACE, 164f);
        space.textSize = 20f;
        Key numKey = sp("123", ACT_NUM, 59f);
        numKey.textSize = 17f;
        t9Rows.add(row(lang, space, numKey, accentKey("←", ACT_ENTER, 69f)));

    }

    /** 九键数字键：label 为字母组，input 为实际发送的数字；20sp（225_2）。 */
    private static Key t9(String label, String input, float weight) {
        Key key = new Key();
        key.label = label;
        key.input = input;
        key.weight = weight;
        key.textSize = 20f;
        return key;
    }

    /**
     * 符号页 4 分类，每分类 3 行内容 + 1 行功能键（空格 / ⌫ / ←）。
     * 「度量 / 物质 / 反应 / 其他」取自设计稿；「其他」第三行为拟定内容。
     * 2026-10-27 客户要求删除「角标」分类（上/下标可由字母键长按浮层与符号页其他分页补齐）。
     */
    private void buildSymbolCats() {
        symbolCats.clear();

        symbolCats.add(cat(          // 0 度量
                row(mk("°F", null, null), mk("°C", null, null), mk("μ", null, null)),
                row(mk("mol", null, null), mk("ε", null, null), mk("ρ", null, null)),
                row(mk("/", null, null), mk("Δ", null, null), mk("_", null, null))));

        symbolCats.add(cat(          // 1 物质
                row(sym("H°"), sym("G°"), sym("α"), sym("β"), sym("γ"), sym("δ")),
                row(sym("ε"), sym("ζ"), sym("θ"), sym("μ"), sym("ν"), sym("π")),
                row(sym("ρ"), sym("σ"), sym("τ"), sym("ψ"), sym("ω"), sym("Ĥ"))));

        symbolCats.add(cat(          // 2 反应
                row(sym("‡"), sym("∵"), sym("∴"), sym("⋮"), sym("≡"), sym("Δ")),
                row(sym("×"), sym("⬡"), sym("⬢"), sym("⇅"), sym("⇌"), sym("↺")),
                row(sym("→"), sym("←"), sym("↔"), sym("⇄"), sym("↑"), sym("↓"))));

        symbolCats.add(cat(          // 3 其他
                row(sym("a"), sym("β"), sym("⊕"), sym("⊖"), sym("λ"), sym("%")),
                row(sym("-"), sym("="), sym("≡"), sym("⊓")),
                row(sym("("), sym(")"), sym("["), sym("]"), sym("{"), sym("}"))));

        // 每个分类统一「3 行内容 + 功能行」，保证切分类高度一致。
        for (List<List<Key>> c : symbolCats) {
            c.add(symFuncRow());
        }
    }

    /**
     * 普通输入法符号页（2026-10-31，按 177-893）：
     * 左侧 116 宽分类卡（常用 / 中文 / 英文 / 其他，选中块 58 高蓝底白字），
     * 右侧 4 行 × 3 列：前 3 行是 9 个符号，末行 空格 / ⌫ / 回车。
     */
    private void buildNormalSymbolCats() {
        normalSymCats.clear();

        normalSymCats.add(cat(          // 0 常用（Figma 原稿：，。？ / ：；( / @%&）
                row(symN("，"), symN("。"), symN("？")),
                row(symN("："), symN("；"), symN("(")),
                row(symN("@"), symN("%"), symN("&")),
                symFuncRowPx()));

        normalSymCats.add(cat(          // 1 中文标点
                row(symN("，"), symN("。"), symN("、")),
                row(symN("；"), symN("："), symN("？")),
                row(symN("！"), symN("“"), symN("”")),
                symFuncRowPx()));

        normalSymCats.add(cat(          // 2 英文标点
                row(symN(","), symN("."), symN("?")),
                row(symN(":"), symN(";"), symN("(")),
                row(symN("@"), symN("%"), symN("&")),
                symFuncRowPx()));

        normalSymCats.add(cat(          // 3 其他
                row(symN("±"), symN("×"), symN("÷")),
                row(symN("≈"), symN("√"), symN("∞")),
                row(symN("•"), symN("§"), symN("©")),
                symFuncRowPx()));
    }

    /** 普通符号页符号键：宽 80（px 权重，配合按行自适应间距）。 */
    private static Key symN(String label) {
        Key key = sym(label);
        key.weight = 80f;
        return key;
    }

    /** 普通符号页功能行：空格 125 / ⌫ 35 / 回车 80（px 权重）。 */
    private static List<Key> symFuncRowPx() {
        return row(
                sp("空格", ACT_SPACE, 125f),
                sp("⌫", ACT_BACK, 35f),
                accentKey("←", ACT_ENTER, 80f));
    }

    /**
     * 普通输入法数字页（2026-10-31，按 181-1791，仅普通模式）：
     * 左侧运算符白卡（+ - × ÷ = %）+ 4 行 × 4 列（1 4 7 / · 2 5 8 0 · 3 6 9 . · 符号 空格 ⌫ 回车）。
     */
    private void buildNumberRows() {
        numberRows.clear();
        numberRows.add(row(num("1"), num("2"), num("3"), sp("符号", ACT_SYMBOL, 61f)));
        numberRows.add(row(num("4"), num("5"), num("6"), sp("空格", ACT_SPACE, 61f)));
        numberRows.add(row(num("7"), num("8"), num("9"), sp("⌫", ACT_BACK, 61f)));
        Key slash = sp("/", 0, 71f);
        slash.style = STYLE_SPECIAL;   // 设计稿除号 17sp，比数字小一号
        numberRows.add(row(slash, num("0"), num("."), accentKey("←", ACT_ENTER, 61f)));
    }

    /** 数字键：宽 71，数字 20sp。 */
    private static Key num(String label) {
        Key key = new Key();
        key.label = label;
        key.style = STYLE_NORMAL;
        key.weight = 71f;
        return key;
    }

    /**
     * 工具栏页（2026-11-01，按 177-952 / 177-1290 一比一）：
     * 两行 84×70 白卡（应用设置 / 深色模式 / 键盘模式 / 悬浮键盘），卡片由 drawToolbarCards 绘制；
     * 返回键盘由候选栏左上「返回」芯片承担。
     */
    private void buildToolbarRows() {
        toolbarRows.clear();
        toolbarRows.add(row(spacer(1f)));   // 卡片第 1 行（上距 37、高 70）
        toolbarRows.add(row(spacer(1f)));   // 卡片第 2 行（上距 20、高 70）
    }

    @SafeVarargs
    private static List<List<Key>> cat(List<Key>... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    /** 符号页纯符号键：无上滑/角标，长按不弹窗。 */
    private static Key sym(String label) {
        Key key = new Key();
        key.label = label;
        return key;
    }

    private static List<Key> symFuncRow() {
        // 按新版 Figma 131-4224：空格 125 / ⌫ 35 / ← 80（总宽 240，比例 1.5625 : 0.4375 : 1）
        return row(
                sp("空格", ACT_SPACE, 1.5625f),
                sp("⌫", ACT_BACK, 0.4375f),
                accentKey("←", ACT_ENTER));
    }

    // ---------------- 对外控制 ----------------

    public boolean isSymbolPage() {
        return page == PAGE_SYMBOL;
    }

    public int getSymbolCategory() {
        return symbolCat;
    }

    /**
     * 切换符号分类。2026-10-29 增加导航块滑动过渡：新块从旧块位置平滑移到新位置，
     * 分类字色同步交叉渐变，时长 240ms、缓出曲线。
     */
    public void setSymbolCategory(int index) {
        if (index < 0 || index >= currentSymCats().size() || index == symbolCat) return;
        if (tabAnimator != null) tabAnimator.cancel();   // 先停旧动画，免得它的收尾把 from 清掉
        tabFrom = symbolCat;
        tabT = 0f;
        symbolCat = index;
        tabAnimator = ValueAnimator.ofFloat(0f, 1f);
        tabAnimator.setDuration(TAB_DUR);
        tabAnimator.setInterpolator(TAB_EASE);
        tabAnimator.addUpdateListener(a -> {
            tabT = (float) a.getAnimatedValue();
            invalidate();
        });
        tabAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                tabFrom = -1;
                tabT = 1f;
                invalidate();
            }
        });
        tabAnimator.start();
        invalidate();
    }

    public void togglePage() {
        // 只在字母 / 符号两页之间来回；数字页与工具栏页走「返回」回字母页
        setPage(page == PAGE_LETTER ? PAGE_SYMBOL : PAGE_LETTER);
    }

    /** 切页（2026-10-31）：数字页仅普通输入法可进入，切页后立即重排并通知服务侧。 */
    public void setPage(int p) {
        if ((p == PAGE_NUMBER || p == PAGE_TAB_NUM) && mode != MODE_NORMAL) p = PAGE_LETTER;
        if (p < PAGE_LETTER || p > PAGE_TAB_NUM) p = PAGE_LETTER;
        if (page == p) return;
        page = p;
        if (page == PAGE_SYMBOL) {
            int cats = currentSymCats().size();
            if (symbolCat >= cats) symbolCat = 0;
        }
        modePopupOpen = false;
        modePopupPressed = -1;
        extraPointers.clear();   // 切页后键位变了，副指记录作废（2026-11-05）
        applyThemeColors();
        requestLayout();
        invalidate();
        if (listener != null) listener.onPageChanged();
    }

    public int getPage() {
        return page;
    }

    /** 当前是否数字页（2026-11-07 供输入法判断数字键直接上屏，不走九键 T9）。 */
    public boolean isNumberPage() {
        return page == PAGE_NUMBER || page == PAGE_TAB_NUM;
    }

    /** 当前页是否不是字母页（候选栏左芯片在这些页显示「返回」）。 */
    public boolean isBackPage() {
        return page != PAGE_LETTER;
    }

    public int getKeyboardMode() {
        return mode;
    }

    /** 浮层里当前选中项（0 化学 / 1 全拼 / 2 双拼）。 */
    public int getInputMode() {
        return mode == MODE_CHEM ? 0 : 1 + normalScheme;
    }

    /** 芯片上的模式名：化学输入 / 普通·全拼 / 普通·双拼。 */
    private String modeLabel() {
        int i = getInputMode();
        return MODE_NAMES[i >= 0 && i < MODE_NAMES.length ? i : 0];
    }

    /**
     * 由服务侧下发输入模式与方案（2026-11-01）：化学模式忽略 scheme；
     * 普通模式按 scheme 切 全拼 / 双拼。
     */
    public void setInputMode(int m, int scheme) {
        if (m != MODE_CHEM && m != MODE_NORMAL) m = MODE_CHEM;
        if (scheme < SCHEME_PINYIN || scheme > SCHEME_T9) scheme = SCHEME_PINYIN;
        if (mode == m && normalScheme == scheme) {
            modePopupOpen = false;
            modePopupPressed = -1;
            invalidate();
            return;
        }
        mode = m;
        normalScheme = scheme;
        page = PAGE_LETTER;
        symbolCat = 0;
        tabFrom = -1;
        tabT = 1f;
        modePopupOpen = false;
        modePopupPressed = -1;
        pressedRow = -1;
        pressedCol = -1;
        extraPointers.clear();   // 模式切换后键位变了，副指记录作废（2026-11-05）
        applyThemeColors();
        requestLayout();
        invalidate();
    }

    /** 浮层点选（由键盘自己处理）：改模式/方案并通知服务侧保存偏好。 */
    private void selectInputMode(int item) {
        int newMode = item == 0 ? MODE_CHEM : MODE_NORMAL;
        int newScheme = item == 0 ? normalScheme : item - 1;
        if (newMode == mode && newScheme == normalScheme) {
            modePopupOpen = false;
            modePopupPressed = -1;
            invalidate();
            return;
        }
        setInputMode(newMode, newScheme);
        if (listener != null) listener.onInputModeChanged(mode, normalScheme);
    }

    /** 九键左栏卡内容（由服务侧回填首音节候选；null = 显示默认「，。？！」）。 */
    public void setT9RailOptions(List<String> opts) {
        t9RailOptions = opts;
        invalidate();
    }

    public void setShiftOn(boolean on) {
        shiftOn = on;
        invalidate();
    }

    public void setAutoCase(boolean on) {
        autoCase = on;
        invalidate();
    }

    /** 打开/关闭按键震动反馈（2026-10-26「通用 → 触感反馈」开关）。 */
    public void setHapticEnabled(boolean on) {
        hapticEnabled = on;
    }

    /**
     * 统一的按键触感：开关关闭时什么都不做。
     * 2026-10-26 修正：设备上系统「触摸振动」是关闭的（haptic_feedback_enabled=0），
     * 普通 performHapticFeedback 会被系统过滤（日志状态 IGNORED_FOR_SETTINGS），导致完全没手感。
     * 这里加 FLAG_IGNORE_GLOBAL_SETTING，只受本应用「通用 → 触感反馈」开关控制。
     */
    private void tapFeedback() {
        if (!hapticEnabled) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    public void setPinyinMode(boolean on) {
        pinyinMode = on;
        invalidate();
    }

    // ---------------- 布局计算 ----------------

    private int rowCount() {
        switch (page) {
            case PAGE_SYMBOL:
                return currentSymCats().get(symbolCat).size();
            case PAGE_NUMBER:
                return numberRows.size();
            case PAGE_TOOLBAR:
                return toolbarRows.size();
            default:
                return currentLetterRows().size();
        }
    }

    private List<List<Key>> currentLetterRows() {
        if (mode != MODE_CHEM) {
            return normalScheme == SCHEME_T9 ? t9Rows : normalLetterRows;
        }
        if (chemAutoMode) {
            ensureAutoRows();
            return autoEnglish ? autoEnglishRows : autoPinyinRows;
        }
        return letterRows;
    }

    /** 当前是否为九键方案（普通输入 · 九键）。 */
    private boolean isT9() {
        return mode == MODE_NORMAL && normalScheme == SCHEME_T9;
    }

    private List<List<List<Key>>> currentSymCats() {
        return mode == MODE_NORMAL ? normalSymCats : symbolCats;
    }

    private String[] currentCatNames() {
        return mode == MODE_NORMAL ? NORMAL_SYM_CAT_NAMES : SYM_CAT_NAMES;
    }

    /**
     * 某行当前要绘制的按键序列。
     * 左右断开时返回「左半 + 右半」拼接后的整行（含重复键与行首占位键），
     * 因此按下坐标、绘制、长按逻辑都用同一套下标。
     */
    private List<Key> rowKeys(int row) {
        switch (page) {
            case PAGE_SYMBOL:
                return currentSymCats().get(symbolCat).get(row);
            case PAGE_NUMBER:
                return numberRows.get(row);
            case PAGE_TOOLBAR:
                return toolbarRows.get(row);
            default:
                if (splitActive() && row < activeSplitRows().size()) {
                    return activeSplitRows().get(row);
                }
                return currentLetterRows().get(row);
        }
    }

    /** 底部三芯片行（选择键盘模式 / 工具栏 / 切换输入法）。 */
    private boolean isChipRow(int row) {
        List<Key> keys = rowKeys(row);
        return !keys.isEmpty() && keys.get(0).style == STYLE_CHIP;
    }

    private float rowH(int row) {
        if (isChipRow(row)) return imeH;
        if (page == PAGE_NUMBER) return keyH * keyScale;
        if (page == PAGE_TOOLBAR) return 102f * fsV;  // 216-726：两行 102 高卡片
        if (tabletLetter()) return 69f * fsTab;       // 平板 1210 画板：键高 69
        return rowKeys(row).get(0).style == STYLE_PLAIN ? imeH : keyH * keyScale;
    }

    /** 反应栏高度（自动模式，含上下边距）：编辑态 / 配平态都是 44dp 芯片 + 上下各 6dp，
     *  保证配平操作前后反应栏高度与纵坐标完全一致（2026-11-08）。 */
    private float reactionBarH() {
        if (mode != MODE_CHEM || !chemAutoMode) return 0f;
        return 56f * fsV;
    }

    /** 页面上边距：字母 8；符号页 11（187-1873 / 188-2117 / 177-893）；数字 11；工具栏 37（177-952）。 */
    private float padTopPx() {
        if (page == PAGE_NUMBER) return 11f * fsV;
        if (page == PAGE_TOOLBAR) return 15f * fsV;   // 216-726：上边距 15
        if (page == PAGE_SYMBOL) return 11f * fsV;
        if (page == PAGE_LETTER && isT9()) return 11f * fsV;   // 225_2 surface padding-top 11
        if (tabletLetter()) return 10f * fsTab;       // 平板 1210 画板：上边距 10
        return 8f * fsV + reactionBarH();
    }

    private float padBottomPx() {
        // 悬浮岛删除后底部留白按 216-207 的设计值 13（原来 11 是给芯片行贴底用的）
        if (tabletLetter()) return 28f * fsTab;       // 平板 1210 画板：下边距 28
        return 13f * fsV;
    }

    /** 第 row 行上方的行间距（行 0 取 0）。 */
    private float rowGap(int row) {
        if (row <= 0) return 0f;
        if (page == PAGE_NUMBER) return 11f * fsV;
        if (page == PAGE_TOOLBAR) return 8f * fsV;    // 216-726：两排卡片行距 8
        if (page == PAGE_SYMBOL) {
            // 187-1873 / 188-2117：行距 11 / 12 / 12
            return (row == 1 ? 11f : 12f) * fsV;
        }
        if (page == PAGE_LETTER) {
            if (isT9()) {
                // 225_2：行 y 61 / 118 / 176 / 233 / 291 → 行距 11 / 12 / 11 / 12
                return ((row == 1 || row == 3) ? 11f : 12f) * fsV;
            }
            if (tabletLetter()) return (row == 1 ? 16f : 18f) * fsTab;  // 平板 1210 画板
            return (row == 1 ? 11f : 12f) * fsV;   // 175-618 / 177-751：A 行 +11，其余 +12
        }
        return vGap;
    }

    /** 该行权重是否为「px 权重」（普通模式的按键按设计稿像素宽度书写）。 */
    private boolean isPxRow(int row) {
        if (isChipRow(row)) return true;
        if (page == PAGE_NUMBER) return true;
        if (page == PAGE_SYMBOL && mode == MODE_NORMAL) return true;
        return page == PAGE_LETTER && mode == MODE_NORMAL;
    }

    /** 按「填满 left..right」反算行内间距：px 权重之和 × fs 之外的宽度均分给间隙。 */
    private float dynGap(List<Key> keys, float left, float right) {
        int n = keys.size();
        if (n <= 1) return 0f;
        float sum = 0;
        for (Key k : keys) sum += k.weight;
        return Math.max(0f, (right - left - sum * fs) / (n - 1));
    }

    /** 符号页右侧网格左边界（跳过左侧分类栏；Figma 131-4224 = 8+116+8 = 132）。 */
    private float symGridLeft() {
        return padH + tabW + padH;
    }

    /** 设置平板布局模式（见 LAYOUT_*）。 */
    public void setLayoutMode(int mode) {
        if (mode < LAYOUT_FULL || mode > LAYOUT_FLOAT) mode = LAYOUT_FULL;
        layoutMode = mode;
        setBackgroundColor(mode == LAYOUT_FLOAT ? Color.TRANSPARENT : kbBg);
        requestLayout();
        invalidate();
    }

    /**
     * 整体尺寸缩放（2026-10-29）：手机横屏浮窗的卡片偏大，按紧凑比例缩小键高、
     * 间距与字号；1 = 标准尺寸。仅改尺寸，不涉及配色与按键排布。
     */
    public void setCompactScale(float s) {
        if (s <= 0) s = 1f;
        if (Math.abs(s - compact) < 0.001f) return;
        compact = s;
        recomputeSizes(true);   // 紧凑比例改的是字号与行高，必须重测
    }

    /**
     * 键盘调节编辑态下发（2026-11-04，249-204 / 249-394）：
     * left / width 为内容区左边界与宽度占全宽的比例（左右两侧可分别拖动、不再强制居中），
     * h 缩放全部纵向尺寸（0.65–1.35）。
     */
    public void setUserBox(float left, float width, float h) {
        if (width <= 0) width = 1f;
        if (h <= 0) h = 1f;
        width = Math.max(0.3f, Math.min(1f, width));
        left = Math.max(0f, Math.min(1f - width, left));
        if (Math.abs(left - userLeft) < 0.001f && Math.abs(width - userWidth) < 0.001f
                && Math.abs(h - userScaleH) < 0.001f) return;
        // 只有纵向比例变了才需要重测（视图高度 = 行的总高）；左右边界只改内容区的
        // 绘制坐标，走 invalidate 即可，省掉整窗的重排（拖动左右时的卡顿来源，2026-11-05）
        boolean heightChanged = Math.abs(h - userScaleH) >= 0.001f;
        userLeft = left;
        userWidth = width;
        userScaleH = h;
        recomputeSizes(heightChanged);
    }

    /** 统一重算所有派生尺寸：fs（水平）/ fsV（垂直）变化后调用。 */
    private void recomputeSizes(boolean needLayout) {
        // 水平基准不含 userScaleH：调整键盘高度时按键宽度、左右间距、水平位置一律不变
        fs = d * compact;
        // 垂直基准含 userScaleH：高度调节只影响行高、行距与纵向偏移
        fsV = d * compact * userScaleH;
        fsText = d * compact;
        padH = 8 * fs;
        padBottom = 3f * fsV;
        gap = 7.5f * fs;
        vGap = 11.5f * fsV;
        keyH = 46 * fsV;
        imeH = 28f * fsV;
        tabW = 116f * fs;
        tabGap = 11.5f * fs;
        // 横向比例立即生效：不等下一趟 onMeasure，浮层框与绘制坐标同帧对齐
        if (getWidth() > 0) computeContent(getWidth());
        if (needLayout) requestLayout();
        invalidate();
    }

    /** 键盘调节：当前内容区左缘 / 宽度（调节浮层画键盘框用）。 */
    public float getContentLeft() {
        return contentLeft;
    }

    /** 键盘底色（键盘调节的底部间隙用它填色，视觉上就是键盘自然收短）。 */
    public int getKeyboardBg() {
        return kbBg;
    }

    public float getContentW() {
        return contentW;
    }

    /** 分体布局是否生效：化学模式（分离设置）或平板（≥600dp，自动分离）。 */
    private boolean splitActive() {
        if (page != PAGE_LETTER || isT9() || chemAutoMode) return false;
        if (tablet) return true;
        return layoutMode == LAYOUT_SPLIT && mode == MODE_CHEM
                && contentW >= 560 * d;
    }

    /** 当前生效的分离行 / 左簇 / 右簇（平板按输入模式取各自的一套）。 */
    private List<List<Key>> activeSplitRows() {
        if (tablet) return mode == MODE_NORMAL ? tabSplitRows : tabSplitRowsChem;
        return splitRows;
    }

    private List<List<Key>> activeSplitLeft() {
        if (tablet) return mode == MODE_NORMAL ? tabLeftRows : tabLeftRowsChem;
        return splitLeftRows;
    }

    private List<List<Key>> activeSplitRight() {
        if (tablet) return mode == MODE_NORMAL ? tabRightRows : tabRightRowsChem;
        return splitRightRows;
    }

    /** 该行是否要在中间断开（功能行跟随断开，底部芯片行不断）。 */
    private boolean isSplitRow(int row) {
        return splitActive() && row < activeSplitRows().size();
    }

    /**
     * 算出键区宽度与左边界。
     * 三种模式都按自身可用宽度铺满：铺满/断开用整屏，浮窗用服务侧卡片的宽度。
     */
    private void computeContent(int w) {
        // 平板 / 横屏平铺：可用宽度很大时限制内容区最大宽度并居中（2026-11-11），
        // 避免每个键被横向拉得极宽；键盘调节收窄过的用户框内同样按居中处理。
        // 2026-11-22 平板（≥600dp）：另算一套全宽内容区给「分离字母页 / 合并页」，
        // 其余页仍限宽 640dp 居中，避免符号页、工具栏等按 402 设计基准被横向拉扁。
        tablet = w >= 600 * d;
        float want = w * userWidth;
        float actual = Math.min(want, MAX_CONTENT_DP * d);
        contentW = actual;
        contentLeft = w * userLeft + (want - actual) / 2f;
        tabContentW = Math.min(want, 1600f * d);
        tabContentLeft = w * userLeft + (want - tabContentW) / 2f;
        fsTab = Math.max(0.01f, tabContentW / 1210f);
        // 配平态反应栏按 402 设计宽等比缩放，且不超过常规设计单位，窄屏上也不会溢出
        uDesign = Math.min(fs, contentW / 402f);
        // 大屏按键略微加高，避免键面「矮胖」；平铺 / 浮窗 / 分体一致适用
        keyScale = w > 560 * d ? 1.12f : 1f;
    }

    /** 平板分离式字母页是否生效（平板 · 字母页 · 非九键）。 */
    private boolean tabletLetter() {
        return tablet && page == PAGE_LETTER && !isT9();
    }

    /** 平板字母页的左右内边距（设计稿 13）；其余页沿用常规 padH。 */
    private float tabPadH() {
        return tabletLetter() ? 13f * fsTab : padH;
    }

    /** 所有横向排布矩形统一右移 contentLeft（键盘调节收窄后内容居中）。 */
    private float[] shiftX(float[] f) {
        f[0] += contentLeft;
        f[2] += contentLeft;
        return f;
    }

    /** 当前页内容总高（上内边距 + 各行高 + 行距 + 下内边距）。 */
    private float contentHeight() {
        // 平板合并页自绘（12 上 + 328 键区 + 26 下 = 366，与平板字母页等高）
        if (page == PAGE_TAB_NUM) return 366f * fsTab;
        float h = padTopPx() + padBottomPx();
        for (int r = 0; r < rowCount(); r++) h += rowH(r) + rowGap(r);
        return h;
    }

    /**
     * 统一键面高度（2026-11-01）：化学字母 / 普通字母 / 符号 / 数字 / 工具栏五类页的
     * 行数、行高与芯片行高不同，若各页按自身内容测量，切页时键盘高度会跟着变，
     * 顶着候选栏与应用界面一起跳动。这里取所有页内容高度的最大值（且不低于设计稿
     * 282）作为固定高度，内容较矮的页在顶部补空白，芯片行仍贴设计稿下内边距。
     */
    private float uniformKeyboardH() {
        int saved = page;
        float h = 0f;
        int[] pages = {PAGE_LETTER, PAGE_SYMBOL, PAGE_NUMBER, PAGE_TOOLBAR, PAGE_TAB_NUM};
        for (int p : pages) {
            page = p;
            h = Math.max(h, contentHeight());
        }
        page = saved;
        return Math.max(h, 240f * fsV);
    }

    /** 内容比统一高度矮时的顶部补白（底部芯片行保持设计稿的下内边距）。 */
    private float topExtraPx() {
        return Math.max(0f, uniformH - contentHeight());
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        computeContent(w);
        uniformH = uniformKeyboardH();
        setMeasuredDimension(w, (int) uniformH);
    }

    /** 在给定起点与可用宽度里，按权重排第 idx 个键（hGap = 键间横向间距）。 */
    private float[] keyRectIn(List<Key> group, int idx, float start, float avail,
                              float y, float rowHeight, float hGap) {
        float sumW = 0;
        for (Key key : group) sumW += key.weight;
        float x = start;
        for (int i = 0; i < idx; i++) x += avail * (group.get(i).weight / sumW) + hGap;
        float kw = avail * (group.get(idx).weight / sumW);
        return new float[]{x, y, x + kw, y + rowHeight};
    }

    private float[] keyRect(int row, int col, int w) {
        return shiftX(keyRectRaw(row, col, w));
    }

    private float[] keyRectRaw(int row, int col, int w) {
        List<Key> r = rowKeys(row);
        float y = rowTop(row);
        float rh = rowH(row);

        // 普通字母页（177-751）：A 行左右缩进 13 / 14、Z 行 1 / 1 是「行内 padding」，
        // 不是额外的一个间隙——跳过行首行尾占位键后再均分间距，才能与设计稿对齐。
        if (page == PAGE_LETTER && mode == MODE_NORMAL && normalScheme != SCHEME_T9
                && !isChipRow(row)) {
            int n = r.size();
            boolean lead = r.get(0).spacer;
            boolean trail = r.get(n - 1).spacer;
            if (lead || trail) {
                float x0 = padH + (lead ? r.get(0).weight * fs : 0f);
                float x1 = contentW - padH - (trail ? r.get(n - 1).weight * fs : 0f);
                int from = lead ? 1 : 0, to = trail ? n - 1 : n;
                if (col < from || col >= to) {
                    float sx = col == 0 ? padH : x1;
                    float sw = col == 0 ? x0 - padH : contentW - padH - x1;
                    return new float[]{sx, y, sx + sw, y + rh};
                }
                List<Key> group = r.subList(from, to);
                float g = dynGap(group, x0, x1);
                return keyRectIn(group, col - from, x0,
                        x1 - x0 - (group.size() - 1) * g, y, rh, g);
            }
        }

        // 左右断开：本行按参考图分左右两套按键，两簇各占 34.5%，中间留握持白
        if (isSplitRow(row)) {
            List<Key> left = activeSplitLeft().get(row), right = activeSplitRight().get(row);
            // 平板分离页用全宽内容区，其余（化学分离设置）沿用常规内容区
            boolean tab = tabletLetter();
            float cw = tab ? tabContentW : contentW;
            float shift = tab ? (tabContentLeft - contentLeft) : 0f;
            // 平板普通字母页：字母键等宽等间距（67 / 17），三排按设计稿各自左右缩进，
            // Q 与 ⇧ 左对齐，A 行整体内缩 27（右缘内缩 31），所以三排并不逐列对齐
            if (tab && mode == MODE_NORMAL && !left.isEmpty()) {
                boolean inLeft = col < left.size();
                List<Key> keys = inLeft ? left : right;
                int i = inLeft ? col : col - left.size();
                float x, kw;
                if (row < 3) {
                    // 字母键统一 67 宽、间距 17；第三排 ⇧ 96 / ⌫ 98（操作键略宽）
                    float kg = 17 * fsTab;
                    float lIn = (row == 1 ? 27 : row == 0 ? 0 : 1) * fsTab;
                    float rIn = (row == 1 ? 31 : row == 0 ? 0 : 1) * fsTab;
                    float clusterW = 0f;
                    for (int j = 0; j < keys.size(); j++) {
                        clusterW += tabLetterKeyW(row, inLeft, j, keys.size());
                    }
                    clusterW += (keys.size() - 1) * kg;
                    x = inLeft ? tabPadH() + lIn : cw - tabPadH() - rIn - clusterW;
                    kw = 67 * fsTab;
                    for (int j = 0; j < keys.size(); j++) {
                        float wj = tabLetterKeyW(row, inLeft, j, keys.size());
                        if (j == i) {
                            kw = wj;
                            break;
                        }
                        x += wj + kg;
                    }
                } else {
                    // 功能行与第三排同网格：左簇与 ⇧ 行同起止、右簇与 ⌫ 行同起止，
                    // 空格键边缘与上方字母键对齐；键间距与字母行一致（17），
                    // 各键按设计稿宽度比例（拼｜英165 / ，73 / 空格167 / 123100 / 回车139）分配簇宽
                    float kg = 17 * fsTab;
                    float leftW = tabLetterKeyW(2, true, 0, 5) + 4 * 67 * fsTab + 4 * kg;
                    float rightW = 4 * 67 * fsTab + tabLetterKeyW(2, false, 4, 5) + 4 * kg;
                    float total = inLeft ? leftW : rightW;
                    x = inLeft ? tabPadH() + 1 * fsTab : cw - tabPadH() - 1 * fsTab - total;
                    float avail = total - (keys.size() - 1) * kg;
                    float sumW = 0f;
                    for (Key k : keys) sumW += k.tabW;
                    kw = 0f;
                    for (int j = 0; j < keys.size(); j++) {
                        float wj = sumW > 0 ? avail * (keys.get(j).tabW / sumW) : avail / keys.size();
                        if (j == i) {
                            kw = wj;
                            break;
                        }
                        x += wj + kg;
                    }
                }
                // shiftX 外层会再加 contentLeft，这里只补平板全宽区与常规区的左偏移差
                return new float[]{shift + x, y, shift + x + kw, y + rh};
            }
            float clusterW = (cw - 2 * tabPadH()) * SPLIT_RATIO;
            float cGap = tab ? 17f * fsTab : gap;
            if (col < left.size()) {
                return keyRectIn(left, col, tabPadH() + shift,
                        clusterW - (left.size() - 1) * cGap, y, rh, cGap);
            }
            return keyRectIn(right, col - left.size(), cw - tabPadH() - clusterW + shift,
                    clusterW - (right.size() - 1) * cGap, y, rh, cGap);
        }

        float left, right, hGap;
        if (isPxRow(row)) {
            // 普通模式与芯片行：权重仍按设计稿像素宽书写，但键间间距取设计稿固定值（2026-11-01）。
            // 原来按「填满可用宽度」反算间距，窄屏（375dp）可用宽度不足时会被算成 0 → 按键贴死。
            if (isChipRow(row)) {
                if (page == PAGE_TOOLBAR) {
                    // 177-952：工具栏页 surface padding 左右 8 / 12，芯片 8–178 | 190–268 | 280–390
                    left = padH;
                    right = contentW - 12f * fs;
                    hGap = 12f * fs;
                } else {
                    // 175-618 / 188-2117：芯片行 x9，170 |14| 78 |15| 110，右缘 x396
                    left = padH + 1f * fs;
                    right = contentW - 6f * fs;
                    hGap = 14.5f * fs;
                }
            } else if (page == PAGE_NUMBER) {
                // 181-1791：运算符卡 75 + 间隙 10 → x93，四列间 10，右缘 x394
                left = padH + 75f * fs + 10f * fs;
                right = contentW - padH;
                hGap = 10f * fs;
            } else if (page == PAGE_SYMBOL) {
                // 188-2117：分类卡 116 + 间隙 10 → x134，三列 80、列间 10
                left = padH + tabW + 10f * fs;
                right = contentW - padH;
                hGap = 10f * fs;
            } else if (page == PAGE_LETTER && isT9()) {
                // 225_2：设计 surface 402、左右内边距 8 / 7 → 内容宽 387。
                // 键宽与间隙按 387 等比缩放：375dp 屏上原 dynGap 只剩约 1.8dp，
                // 左右键组贴死；底行 69+164+59+69=361 更会把间隙算成 0。
                // 等比后键宽 = 设计宽 × u、间隙 = 设计间隙 × u，任何屏宽都保持设计稿比例。
                left = padH;                       // 225_2 左内边距 8
                right = contentW - 7f * fs;        // 225_2 右内边距 7（其余页仍用 8）
                float u = (right - left) / (387f * fs);
                float sumW = 0f;
                for (Key k : r) sumW += k.weight;
                int n = r.size();
                hGap = n > 1 ? Math.max(0f, (387f - sumW) / (n - 1)) * fs * u : 0f;
            } else {
                left = padH;
                right = contentW - padH;
                hGap = dynGap(r, left, right);
            }
        } else {
            boolean sym = page == PAGE_SYMBOL;
            // 符号页横向间距按 Figma 131-4224：11（402 宽下键宽恰为 80）
            hGap = sym ? 11f * fs : gap;
            left = sym ? symGridLeft() : padH;
            right = contentW - padH;
        }
        float avail = Math.max(0f, right - left - (r.size() - 1) * hGap);
        return keyRectIn(r, col, left, avail, y, rh, hGap);
    }

    /**
     * 分类栏内容区高度 = 内容行高度 + 行间距（不含底部芯片行）。
     * 底边与右侧功能行底边严格对齐。
     */
    private float tabContentH() {
        int rows = rowCount();
        if (rows > 0 && isChipRow(rows - 1)) rows--;
        float h = 0;
        for (int r = 0; r < rows; r++) {
            if (r > 0) h += rowGap(r);
            h += rowH(r);
        }
        return h;
    }

    /**
     * 分类 tab 的文本行矩形（也是点击命中矩形）。
     * 卡内固定排布，首项文本 y19（选中块 9px 内边距），
     * 其余 y70 / 122 / 174，高 25；选中块的 43 高位置见 tabHiRect()。
     */
    private float[] tabRect(int i) {
        return shiftX(tabRectRaw(i));
    }

    private float[] tabRectRaw(int i) {
        float x0 = padH + 10f * fs;
        float x1 = padH + tabW - 10f * fs;
        float y = rowTop(0) + (i == 0 ? 19f : i == 1 ? 70f : i == 2 ? 122f : 174f) * fsV;
        return new float[]{x0, y, x1, y + 25f * fsV};
    }

    /** 分类选中块：43 高（9 + 文本 25 + 9），卡内 y10（其余项上探 9 与文本居中）。 */
    private float[] tabHiRect(int i) {
        float x0 = contentLeft + padH + 10f * fs;
        float x1 = contentLeft + padH + tabW - 10f * fs;
        float y = rowTop(0) + (i == 0 ? 10f : i == 1 ? 61f : i == 2 ? 113f : 165f) * fsV;
        return new float[]{x0, y, x1, y + 43f * fsV};
    }

    /** 225_2 九键横向等比系数：设计内容宽 387（402 - 左 8 - 右 7）。 */
    private float t9Unit() {
        return (contentW - 15f * fs) / (387f * fs);
    }

    /** 九键左栏白卡（225_2：69×162，跨前 3 行）。 */
    private float[] t9RailRect() {
        float top = rowTop(0);
        return new float[]{contentLeft + padH, top, contentLeft + padH + 69f * fs * t9Unit(),
                rowTop(2) + rowH(2)};
    }

    /** 左栏卡第 i 项文本盒（225_2：58×25，y 偏移 4 / 44 / 84 / 124，卡内左 9）。 */
    private float[] t9RailItemRect(int i) {
        float[] r = t9RailRect();
        float u = t9Unit();
        float x = r[0] + 9f * fs * u;
        float y = r[1] + (4f + 40f * i) * fsV;
        return new float[]{x, y, x + 58f * fs * u, y + 25f * fsV};
    }

    // ---------------- 绘制 ----------------

    /** 反应栏各可点击区域（用于触摸命中）。 */
    private final RectF reactAddBtn = new RectF();
    private final List<RectF> reactChipDel = new ArrayList<>();
    /* 2026-11-08 反应栏横向滑动：交互与候选栏一致（拖动 + 惯性 + 点击判定在抬起时） */
    private float reactScrollX = 0f, reactMaxScroll = 0f;
    private float reactDownX, reactDownY, reactDownScroll;
    private boolean reactDragCandidate = false, reactDragging = false;
    private OverScroller reactScroller;
    private VelocityTracker reactVelocity;
    private int reactTouchSlop;
    /** 配平态反应栏横向比例单位（402 设计宽等比，且不超过 fs）与配平前快照。 */
    private float uDesign;
    private List<String> preReactSlots;
    private String preReactComposing = "";
    /** 配平结果显示串（离子场景为净离子式，由 ReactionEngine.displayEquation 决定）。 */
    private String balancedDisplay = "";

    /** ASCII 化学式 → 显示串（数字下标、^电荷上标、水合物系数保持正体）。 */
    static String prettyFormula(String f) {
        if (f == null) return "";
        StringBuilder sb = new StringBuilder();
        // 电荷
        int car = f.indexOf('^');
        String body = car >= 0 ? f.substring(0, car) : f;
        String charge = car >= 0 ? f.substring(car + 1) : null;
        // 按水合物分隔符分段
        String[] parts = body.split("·", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('·');
            if (i == 0) {
                sb.append(subDigits(parts[i]));
            } else {
                // 开头的数字是结晶水系数，保持正体
                int j = 0;
                while (j < parts[i].length() && Character.isDigit(parts[i].charAt(j))) j++;
                sb.append(parts[i], 0, j).append(subDigits(parts[i].substring(j)));
            }
        }
        if (charge != null && !charge.isEmpty()) {
            for (int i = 0; i < charge.length(); i++) {
                char ch = charge.charAt(i);
                if (ch >= '0' && ch <= '9') sb.append(SUP[ch - '0']);
                else if (ch == '+') sb.append('⁺');
                else if (ch == '-') sb.append('⁻');
            }
        }
        return sb.toString();
    }

    private static final char[] SUB = {'₀','₁','₂','₃','₄','₅','₆','₇','₈','₉'};
    private static final char[] SUP = {'⁰','¹','²','³','⁴','⁵','⁶','⁷','⁸','⁹'};

    private static String subDigits(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') b.append(SUB[c - '0']);
            else b.append(c);
        }
        return b.toString();
    }

    /**
     * 显示串 → ASCII 化学式（配平库 key 用）。
     * 下标 ₀-₉ → 数字；上标电荷 ⁰-⁹/⁺/⁻ → ^数字±；水合物 · 保留。
     * 2026-11-08 补齐离子支持：全角 ＋－ 归一；普通写法 Fe3+ / SO42- / NH4+ / OH-
     * 也补上 ^ 电荷标记，让离子反应能正常进库检索。
     */
    static String toAsciiFormula(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        // 连续的上标数字 / 电荷符号合并成一个 ^ 前缀（Fe³⁺ → Fe^3+，不能变成 Fe^3^+）
        boolean inCharge = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '₀' && c <= '₉') {
                b.append((char) ('0' + (c - '₀')));
                inCharge = false;
            } else if (c >= '⁰' && c <= '⁹') {
                if (!inCharge) b.append('^');
                b.append((char) ('0' + (c - '⁰')));
                inCharge = true;
            } else if (c == '¹' || c == '²' || c == '³') {
                // 注意：¹²³ 的码位是 U+00B9/B2/B3，不在 ⁰-⁹ 的 U+2070 区间，必须单独处理（2026-11-08）
                if (!inCharge) b.append('^');
                b.append(c == '¹' ? '1' : c == '²' ? '2' : '3');
                inCharge = true;
            } else if (c == '⁺') {
                if (!inCharge) b.append('^');
                b.append('+');
                inCharge = true;
            } else if (c == '⁻') {
                if (!inCharge) b.append('^');
                b.append('-');
                inCharge = true;
            } else if (c == '＋') {
                b.append('+');
                inCharge = false;
            } else if (c == '－' || c == '−') {
                b.append('-');
                inCharge = false;
            } else {
                b.append(c);
                inCharge = false;
            }
            i++;
        }
        return withChargeMark(b.toString());
    }

    private static final java.util.regex.Pattern SINGLE_ATOM =
            java.util.regex.Pattern.compile("^[A-Z][a-z]?$");

    /** 给普通写法的离子补 ^ 电荷标记：Fe3+ → Fe^3+、SO42- → SO4^2-、NH4+ → NH4^+。 */
    private static String withChargeMark(String f) {
        if (f == null || f.isEmpty() || f.indexOf('^') >= 0) return f == null ? "" : f;
        char sign = f.charAt(f.length() - 1);
        if (sign != '+' && sign != '-') return f;
        String body = f.substring(0, f.length() - 1);
        if (body.isEmpty()) return f;
        int k = body.length();
        while (k > 0 && body.charAt(k - 1) >= '0' && body.charAt(k - 1) <= '9') k--;
        int digits = body.length() - k;
        String num = "";
        if (digits == 1 && k > 0 && SINGLE_ATOM.matcher(body.substring(0, k)).matches()) {
            // Fe3+ / Cu2+：单原子元素后的数字是电荷
            num = body.substring(k);
            body = body.substring(0, k);
        } else if (digits >= 2) {
            // SO42- / PO43-：最后一位数字是电荷，前面的数字仍属于主体
            num = body.substring(body.length() - 1);
            body = body.substring(0, body.length() - 1);
        }
        char last = body.charAt(body.length() - 1);
        if (!(Character.isLetter(last) || (last >= '0' && last <= '9')
                || last == ')' || last == ']')) return f;
        return body + "^" + num + sign;
    }

    /**
     * 自动模式开关（设计稿 267_316 autoWrapper7，85×44）：
     * 左侧「自动 / 模式」两行 10sp 半透明字，右侧 40×22 蓝色胶囊 + 18 白点。
     */
    private void drawAutoToggle(Canvas c, float[] f) {
        float w = f[2] - f[0], h = f[3] - f[1];
        float cy = (f[1] + f[3]) / 2f;

        // 左侧两行小字（268_552：10sp，浅色 #00000066 / 深色 #FFFFFF66）
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(10f * fsText);
        textPaint.setColor(darkMode ? 0x66FFFFFF : 0x66000000);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float lineH = 12f * fsV;
        float base = cy - lineH / 2f - (fm.ascent + fm.descent) / 2f;
        float textX = f[0] + 7f * fs;
        c.drawText("自动", textX, base, textPaint);
        c.drawText("模式", textX, base + lineH, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // 右侧胶囊：40×22，右内边距 9（268_552 padding 11px 9px 11px 36px）
        float pillW = w * 40f / 85f;
        float pillH = h * 22f / 44f;
        float pillX = f[2] - 9f * fs - pillW;
        float pillY = cy - pillH / 2f;
        rect.set(pillX, pillY, pillX + pillW, pillY + pillH);
        // 开启 #2E73F3 ／ 关闭色与应用主页开关胶囊统一（浅 #DDDDDD / 深 #6E6E6E）
        togglePaint.setColor(chemAutoMode ? 0xFF2E73F3 : (darkMode ? 0xFF6E6E6E : 0xFFDDDDDD));
        c.drawRoundRect(rect, pillH / 2f, pillH / 2f, togglePaint);

        // 白色圆点 18×18，距胶囊内边距 2
        float dotR = pillH * 18f / 22f / 2f;
        float inset = pillH * 2f / 22f + dotR;
        float dotCx = chemAutoMode ? pillX + pillW - inset : pillX + inset;
        togglePaint.setColor(0xFFFFFFFF);
        c.drawCircle(dotCx, cy, dotR, togglePaint);
    }

    /** 绘制反应栏（自动模式，字母页顶部）。 */
    private void drawReactionBar(Canvas c, int w) {
        float top = 6f * fsV;
        float barH = 44f * fsV;
        float y = top;
        float cy = y + barH / 2f;
        reactChipDel.clear();
        reactAddBtn.setEmpty();

        if (reactionState == REACT_BALANCED && balancedReaction != null) {
            drawBalancedBar(c, w);
            return;
        }

        // ---- 编辑中（273_364 Frame 14 / 273_366 Component 1）：芯片 + 连接加号 + 输入槽 + 蓝色加号 ----
        // 内容超宽时可横向滑动（与候选栏一致的拖动 + 惯性），绘制裁剪到反应栏内
        c.save();
        c.clipRect(0f, y - 2f * fsV, w, y + barH + 2f * fsV);
        float x = padH - reactScrollX;
        float addW = 40f * fs;
        float gapX = 8f * fs;
        // 芯片之间的连接加号：15.97×15.97，纯视觉标识反应关系，不参与任何点击
        float plusW = 15.97f * fs;
        float plusGap = 6f * fs;
        float plusBlock = plusGap * 2f + plusW;
        // 芯片内左侧删除叉号 6×6，固定位于芯片左段（唯一删除入口）
        float crossSize = 6f * fs;
        float chipPadL = 9f * fs;
        float crossGap = 7f * fs;
        float chipPadR = 11f * fs;
        int chipBg = darkMode ? 0xFF484848 : 0xFFD7D7D7;

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(17f * fs);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textBase = cy - (fm.ascent + fm.descent) / 2f;

        for (int i = 0; i < reactionSlots.size(); i++) {
            String display = prettyFormula(reactionSlots.get(i));
            // 宽度完全自适应：仅由内容实测宽度 + 固定内边距决定，无最小 / 最大宽度限制
            float chipW = chipPadL + crossSize + crossGap
                    + textPaint.measureText(display) + chipPadR;

            // 已存在的芯片位置与样式完全固定，任何新增都不会让它产生位移
            rect.set(x, y, x + chipW, y + barH);
            reactChipPaint.setColor(chipBg);
            fillRound(c, rect, 6f * fs, reactChipPaint);

            // 删除叉号：芯片内左侧固定位置 6×6（唯一可触发删除的控件）
            float crossCx = x + chipPadL + crossSize / 2f;
            drawChipCross(c, crossCx, cy);

            textPaint.setColor(darkMode ? 0xFFFFFFFF : 0xFF000000);
            c.drawText(display, x + chipPadL + crossSize + crossGap, textBase, textPaint);

            // 叉号热区外扩到 22dp 便于点按，仍限定在芯片左段，不侵入文字与连接加号
            float hot = 22f * fs;
            reactChipDel.add(new RectF(crossCx - hot / 2f, cy - hot / 2f,
                    crossCx + hot / 2f, cy + hot / 2f));

            // 连接加号（两个反应物之间的「＋」，不可点击）
            x += chipW + plusGap;
            drawPlus(c, x + plusW / 2f, cy, 6.5f * fs, darkMode ? 0xFFFFFFFF : 0xFF000000);
            x += plusW + plusGap;
        }

        // 输入槽：宽度实时自适应内容——空态按提示词宽度，点键帽后立刻按当前字符宽度收放
        String comp = listener.getComposingText();
        boolean hasComp = comp != null && !comp.isEmpty();
        String show = hasComp ? comp : (autoEnglish ? "输入化学式" : "输入中文名");
        textPaint.setTextSize(17f * fs);
        Paint.FontMetrics hfm = textPaint.getFontMetrics();
        float hb = cy - (hfm.ascent + hfm.descent) / 2f;
        float barW = textPaint.measureText("|");
        float slotW = 20f * fs + barW + 6f * fs + textPaint.measureText(show);

        // 新增反应物后，新输入槽从原输入槽（即原芯片右缘）的位置平移到目标位置，
        // 透明度随线性进度 0→100% 同步渐显，位移走 ease-out；已有芯片全程不动。
        float slotTargetX = x;
        float slotX = slotTargetX;
        int slotAlpha = 255;
        if (slotAnimRaw < 1f) {
            float eased = SLOT_EASE_OUT.getInterpolation(slotAnimRaw);
            float fromDraw = slotAnimFromX - reactScrollX;
            slotX = fromDraw + (slotTargetX - fromDraw) * eased;
            slotAlpha = (int) (255f * slotAnimRaw);
        }
        reactSlotContentX = slotX + reactScrollX;

        rect.set(slotX, y, slotX + slotW, y + barH);
        reactChipPaint.setColor(chipBg);
        reactChipPaint.setAlpha(slotAlpha);
        fillRound(c, rect, 6f * fs, reactChipPaint);
        reactChipPaint.setAlpha(255);

        int slotTextColor = hasComp ? (darkMode ? 0xFFFFFFFF : 0xFF000000)
                : (darkMode ? 0x66FFFFFF : 0x66000000);
        textPaint.setColor(slotTextColor);
        textPaint.setAlpha((int) (((slotTextColor >>> 24) * slotAlpha) / 255f));
        c.drawText("|", slotX + 10f * fs, hb, textPaint);
        c.drawText(show, slotX + 10f * fs + barW + 6f * fs, hb, textPaint);
        textPaint.setAlpha(255);

        // 蓝色加号圆钮（40×40，#2E73F3）：与新输入槽保持固定相对位置，始终在其右侧同步平移
        float addX = slotX + slotW + gapX;
        reactAddBtn.set(addX, cy - addW / 2f, addX + addW, cy + addW / 2f);
        togglePaint.setColor(0xFF2E73F3);
        c.drawCircle(reactAddBtn.centerX(), reactAddBtn.centerY(), addW / 2f, togglePaint);
        drawPlus(c, reactAddBtn.centerX(), reactAddBtn.centerY(), 8f * fs, 0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // 可滚动内容宽度按目标布局计算（不计动画偏移，避免动画期间可滑范围抖动）
        float contentEnd = slotTargetX + slotW + gapX + addW;
        reactMaxScroll = Math.max(0f, contentEnd + reactScrollX + padH - w);
        // 只在静止时收敛越界值：拖动 / 惯性期间由 clampReactScroll 收口，
        // 绘制阶段反写 scrollX 会与手势算出的位置来回打架，表现为滑动抽搐
        if (!reactDragging && reactScroller.isFinished()) {
            reactScrollX = clampReactScroll(reactScrollX);
        }
        c.restore();
    }

    /**
     * 已配平态反应栏（2026-11-08）：撤销 / 确认 / 「已配平」均已移入候选栏（273_218），
     * 这里只保留方程式灰块；反应栏总高与方程式纵坐标保持与配平前完全一致（84u / 30u），
     * 配平操作不改变键盘整体高度、反应栏位置与候选栏尺寸。
     */
    private void drawBalancedBar(Canvas c, int w) {
        float u = uDesign;
        int fg = darkMode ? 0xFFFFFFFF : 0xFF000000;
        int blockBg = darkMode ? 0xFF484848 : 0xFFD7D7D7;

        String eq = balancedDisplay != null ? balancedDisplay : "";
        int eqAt = eq.indexOf('=');
        String left = eqAt >= 0 ? eq.substring(0, eqAt).trim() : eq;
        String right = eqAt >= 0 ? eq.substring(eqAt + 1).trim() : "";
        String ls = spaceCoeffs(left), rs = spaceCoeffs(right);

        // 方程式行（2026-11-08）：左对齐，框内左右 12u、框与「=」左右各 12u；
        // 字号固定 17u，不再自适应缩小，内容超宽时交给反应栏水平滚动查看。
        float padX = 12f * u;
        float gapEq = 12f * u;
        float eqSize = 28f * u;
        float fontSize = 17f * u;
        textPaint.setTextSize(fontSize);
        float lw = textPaint.measureText(ls) + padX * 2f;
        float rw = textPaint.measureText(rs) + padX * 2f;
        textPaint.setTextSize(eqSize);
        float eqW = textPaint.measureText("=");
        float total = lw + gapEq + eqW + gapEq + rw;

        float startX = 8f * u;
        reactMaxScroll = Math.max(0f, startX + total + 8f * u - w);
        if (!reactDragging && reactScroller.isFinished()) {
            reactScrollX = clampReactScroll(reactScrollX);
        }
        float sx = startX - reactScrollX;
        float rowTop = 6f * fsV, rowH = 44f * fsV, rowCy = rowTop + rowH / 2f;

        rect.set(sx, rowTop, sx + lw, rowTop + rowH);
        reactChipPaint.setColor(blockBg);
        fillRound(c, rect, 6f * u, reactChipPaint);
        float rightX = sx + lw + gapEq + eqW + gapEq;
        rect.set(rightX, rowTop, rightX + rw, rowTop + rowH);
        fillRound(c, rect, 6f * u, reactChipPaint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(fg);
        textPaint.setTextSize(fontSize);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textBase = rowCy - (fm.ascent + fm.descent) / 2f;
        c.drawText(ls, sx + padX, textBase, textPaint);
        c.drawText(rs, rightX + padX, textBase, textPaint);

        // 等号：左右与两块保持相同间距
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(eqSize);
        fm = textPaint.getFontMetrics();
        c.drawText("=", sx + lw + gapEq + eqW / 2f,
                rowCy - (fm.ascent + fm.descent) / 2f, textPaint);
        // 键帽文字按 CENTER 绘制：配平态绘制完必须复位，否则后续所有键帽右移
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** 系数与化学式之间补空格（2Na → 2 Na），只处理 ASCII 数字，避免误伤下标。 */
    private static String spaceCoeffs(String s) {
        if (s == null) return "";
        return s.replaceAll("(?<![A-Za-z])" + "(\\d+)(?=[A-Z(])", "$1 ");
    }

    /** 反应栏是否包含该点的纵坐标（自动模式字母页）。 */
    private boolean inReactionBar(float ty) {
        if (page != PAGE_LETTER || mode != MODE_CHEM || !chemAutoMode) return false;
        float top = 6f * fsV;
        float barH = 44f * fsV;
        return ty >= top - 4f * fsV && ty <= top + barH + 4f * fsV;
    }

    /** 反应栏滚动位置统一钳制到 [0, reactMaxScroll]，拖动、惯性与绘制期都走这里。 */
    private float clampReactScroll(float x) {
        if (x <= 0f) return 0f;
        return Math.min(x, Math.max(0f, reactMaxScroll));
    }

    /** 反应栏回到最左端（配平切态、撤销、上屏复位都走这里，保证默认左对齐首屏）。 */
    private void resetReactScroll() {
        reactScrollX = 0f;
        reactMaxScroll = 0f;
        reactDownScroll = 0f;
        reactDragging = false;
        if (reactScroller != null) reactScroller.forceFinished(true);
    }

    /** 只重绘反应栏区域：横向滑动时不必整键盘重绘，避免掉帧带来的抽搐感。 */
    private void invalidateReactionBar() {
        float bottom = 6f * fsV + 44f * fsV + 4f * fsV;
        invalidate(0, 0, getWidth(), (int) Math.ceil(bottom));
    }

    @Override
    public void computeScroll() {
        if (reactScroller != null && reactScroller.computeScrollOffset()) {
            reactScrollX = clampReactScroll(reactScroller.getCurrX());
            invalidateReactionBar();
        }
    }

    /** 反应栏点击结算：芯片内叉号删除 / 蓝色加号确认；连接加号不参与任何命中。 */
    private boolean handleReactionBarTap(float tx, float ty) {
        for (int i = 0; i < reactChipDel.size(); i++) {
            if (reactChipDel.get(i).contains(tx, ty)) {
                if (i < reactionSlots.size()) reactionSlots.remove(i);
                tapFeedback();
                invalidate();
                return true;
            }
        }
        // 加号热区外扩 8dp：典型手指落点常在圆钮边缘
        float hot = 8f * fs;
        if (!reactAddBtn.isEmpty()
                && tx >= reactAddBtn.left - hot && tx <= reactAddBtn.right + hot
                && ty >= reactAddBtn.top - hot && ty <= reactAddBtn.bottom + hot) {
            confirmCurrentSlot();
            tapFeedback();
            invalidate();
            return true;
        }
        return true;
    }

    /**
     * 芯片内左侧删除叉号（273_366 Component 1：6×6，颜色很淡）。
     */
    private void drawChipCross(Canvas c, float cx, float cy) {
        int color = darkMode ? 0x59FFFFFF : 0x40000000;
        float arm = 3f * fs;
        togglePaint.setColor(color);
        togglePaint.setStyle(Paint.Style.STROKE);
        togglePaint.setStrokeWidth(1.5f * fs);
        togglePaint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, togglePaint);
        c.drawLine(cx - arm, cy + arm, cx + arm, cy - arm, togglePaint);
        togglePaint.setStyle(Paint.Style.FILL);
        togglePaint.setStrokeCap(Paint.Cap.BUTT);
    }

    /** 正交加号。 */
    private void drawPlus(Canvas c, float cx, float cy, float arm, int color) {
        togglePaint.setColor(color);
        togglePaint.setStyle(Paint.Style.STROKE);
        togglePaint.setStrokeWidth(2.1f * fs);
        togglePaint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx - arm, cy, cx + arm, cy, togglePaint);
        c.drawLine(cx, cy - arm, cx, cy + arm, togglePaint);
        togglePaint.setStyle(Paint.Style.FILL);
        togglePaint.setStrokeCap(Paint.Cap.BUTT);
    }


    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        if (page == PAGE_TAB_NUM) {
            drawTabNum(c, w);
            return;
        }
        if (page == PAGE_LETTER && mode == MODE_CHEM && chemAutoMode) drawReactionBar(c, w);
        if (page == PAGE_SYMBOL) drawCategoryTabs(c);
        if (page == PAGE_NUMBER) drawOpsCard(c);
        if (page == PAGE_TOOLBAR) drawToolbarCards(c);
        if (page == PAGE_LETTER && isT9()) drawT9Rail(c);
        int rows = rowCount();
        // 键下描边（2026-11-09 新配色）：浅色 字符键 黑 20% / 操作键 黑 40%；深色统一纯黑
        letterShadow.setColor(darkMode ? 0xFF000000 : 0x33000000);
        // 键影偏移 1px（设计稿 0 1px 0）
        float shadowDy = 1f * d;
        for (int r = 0; r < rows; r++) {
            List<Key> keys = rowKeys(r);
            for (int i = 0; i < keys.size(); i++) {
                Key key = keys.get(i);
                if (key.spacer) continue;   // 行首占位键：只占宽度，不画也不可点
                float[] f = keyRect(r, i, w);
                rect.set(f[0], f[1], f[2], f[3]);
                boolean pressed = (r == pressedRow && i == pressedCol)
                        || (r == flashRow && i == flashCol)
                        || isExtraPressed(r, i);
                if (key.style != STYLE_PLAIN) {
                    // 「发生反应」键（2026-11-08）：浅色 #D5E3FF / 深色 #183B7A 键帽，
                    // 字色与下描边统一 #2E73F3；其余浅灰键保持 深色 #CACACA 黑字 / 浅色 #949494 白字
                    if (key.style == STYLE_LIGHT) {
                        if (key.action == ACT_REACT) {
                            bgLight.setColor(darkMode ? 0xFF183B7A : 0xFFD5E3FF);
                            reactShadow.setColor(0xFF2E73F3);
                        } else {
                            bgLight.setColor(darkMode ? 0xFFCACACA : 0xFF949494);
                        }
                    }
                    // 操作键（2026-11-09 新配色）：拼｜素 / 拼｜英 / ，。 / 123 / ⇧ / ⌫ 等
                    // 用 #C9C9C9（深 #414141）+ 黑 40% 下描边；空格按客户清单归字符键。
                    boolean opKey = key.style == STYLE_SPECIAL && key.action != ACT_SPACE;
                    Paint bg = key.style == STYLE_ACCENT ? bgAccent
                            : key.style == STYLE_LIGHT ? bgLight
                            : opKey ? bgSpecial : bgNormal;
                    // 键底影：字符键 黑 20%、操作键 黑 40%、回车 #003376 / #00142F
                    if (!pressed) {
                        rect2.set(f[0], f[1] + shadowDy, f[2], f[3] + shadowDy);
                        fillRound(c, rect2, radius,
                                key.style == STYLE_ACCENT ? shadowAccent
                                        : (key.style == STYLE_LIGHT)
                                                ? (key.action == ACT_REACT ? reactShadow : shadowPaint)
                                        : (opKey ? shadowPaint : letterShadow));
                    }
                    fillRound(c, rect, radius, pressed ? bgPressed : bg);
                }

                float cx = (f[0] + f[2]) / 2, cy = (f[1] + f[3]) / 2;
                boolean letterKey = key.action == 0 && key.chem != null;
                boolean normalLetterKey = mode == MODE_NORMAL && key.action == 0
                        && key.label != null && key.label.length() == 1
                        && isAsciiLetter(key.label.charAt(0));
                // 字号整体上调（客户意见 7：设计稿里键字与功能字明显更大）
                // 按新版 Figma：字母 20sp、功能 18sp；平板 1210 画板：字母与功能字 24、逗号 20
                if (tabletLetter()) {
                    textPaint.setTextSize((key.stackChem ? 20 : 24) * fsTab);
                } else if (key.textSize > 0) {
                    textPaint.setTextSize(key.textSize * fsText);
                } else {
                    // 187-1873/188-2117：符号页空格字 17sp，其余功能字 18sp、字母 20sp
                    textPaint.setTextSize((key.style == STYLE_NORMAL ? 20
                            : (page == PAGE_SYMBOL && key.action == ACT_SPACE ? 17 : 18)) * fsText);
                }
                int tc = labelColor;
                boolean stateOn = false;
                if (key.action == ACT_SHIFT && shiftOn) stateOn = true;
                if (key.action == ACT_CASE) stateOn = autoCase;
                if (stateOn) tc = accent;
                if (key.style == STYLE_ACCENT) tc = Color.WHITE;
                if (key.style == STYLE_LIGHT) {
                    // 「发生反应」键字色固定 #2E73F3（深浅色同值）
                    tc = key.action == ACT_REACT ? 0xFF2E73F3
                            : (darkMode ? 0xFF000000 : 0xFFFFFFFF);
                }
                textPaint.setColor(tc);
                Paint.FontMetrics fm = textPaint.getFontMetrics();

                if (key.switchToggle) {
                    // 自动模式开关（267_316）：左侧「自动/模式」两行小字 + 右侧蓝色胶囊
                    drawAutoToggle(c, f);
                } else if (key.action == ACT_BACK) {
                    // 删除键：客户 2026-10-25 提供的 SVG，按主题文字色着色（九键 16×14，其余 20×13）
                    boolean t9 = page == PAGE_LETTER && isT9();
                    if (tabletLetter()) {
                        // 平板 1210 画板：删除图标约 26×16（保持 ic_backspace 21×13 同比）
                        drawBitmapIcon(c, backspaceIcon, cx, cy, 26 * fsTab, 16 * fsTab, tc);
                    } else {
                        drawBitmapIcon(c, backspaceIcon, cx, cy,
                                (t9 ? 16 : 20) * d, (t9 ? 14 : 13) * d, tc);
                    }
                } else if (key.action == ACT_ENTER || key.action == ACT_SEND_EQ) {
                    if (key.label != null && key.label.startsWith("发送")) {
                        // 发送键（手动模式 ACT_ENTER / 自动模式 ACT_SEND_EQ）：
                        // 2026-11-08 客户要求改为 24×17 回车图标，不再绘制「发送」文字；
                        // label 仍保留「发送」作为语义标识。
                        drawBitmapIcon(c, enterSendIcon, cx, cy, 24 * d, 17 * d, Color.WHITE);
                    } else {
                        // 回车键：强调色键上用白色（九键 26×17，其余 24×17；平板 32×26）
                        boolean t9 = page == PAGE_LETTER && isT9();
                        if (tabletLetter()) {
                            // 平板 1210 画板：回车图标约 30×21（保持 ic_enter 24×17 同比）
                            drawBitmapIcon(c, enterIcon, cx, cy, 30 * fsTab, 21 * fsTab, Color.WHITE);
                        } else {
                            drawBitmapIcon(c, enterIcon, cx, cy,
                                    (t9 ? 26 : 24) * d, 17 * d, Color.WHITE);
                        }
                    }
                } else if (key.action == ACT_HIDE) {
                    // 九键右上角「收起键盘」图标（225_2：19×13）
                    drawBitmapIcon(c, t9HideIcon, cx, cy, 19 * d, 13 * d, tc);
                } else if ((key.action == ACT_MODE || key.action == ACT_LANG
                        || key.action == ACT_AUTO_LANG)
                        && key.label != null && key.label.contains("｜")) {
                    // 拼｜素 / 拼｜英 双色：当前模式字色、另一模式弱化。
                    // ⚠ textPaint 的对齐是 CENTER，这里必须临时切到 LEFT 再按左边界逐字绘制，
                    // 否则每个字都会以传入的 x 为中心再左移半个字宽，整串看起来明显偏左。
                    String a = String.valueOf(key.label.charAt(0));
                    String bar = String.valueOf(key.label.charAt(1));
                    String b = String.valueOf(key.label.charAt(2));
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    textPaint.setTextSize((tabletLetter() ? 24
                            : (key.textSize > 0 ? key.textSize : 18)) * (tabletLetter() ? fsTab : fsText));
                    fm = textPaint.getFontMetrics();
                    float base = cy - (fm.ascent + fm.descent) / 2;
                    float w0 = textPaint.measureText(a);
                    float wBar = textPaint.measureText(bar);
                    float w1 = textPaint.measureText(b);
                    float x0 = cx - (w0 + wBar + w1) / 2;
                    // 自动模式的「拼｜素」用 autoEnglish 判断当前子态
                    boolean atPinyin = key.action == ACT_AUTO_LANG ? !autoEnglish : pinyinMode;
                    textPaint.setColor(atPinyin ? labelColor : offColor);
                    c.drawText(a, x0, base, textPaint);
                    textPaint.setColor(offColor);
                    c.drawText(bar, x0 + w0, base, textPaint);
                    textPaint.setColor(atPinyin ? offColor : labelColor);
                    c.drawText(b, x0 + w0 + wBar, base, textPaint);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                } else {
                    drawKeyLabel(c, key, cx, cy, fm);
                }

                if (key.stackChem) {
                    // 「，/。」键：句号与逗号同一纵轴、居其下方（设计稿 52218da0 底部功能行）
                    if (key.chem != null && !key.chem.isEmpty()) {
                        chemPaint.setTextSize((tabletLetter() ? 20 * fsTab : 10 * fsText));
                        chemPaint.setTextAlign(Paint.Align.LEFT);
                        Paint.FontMetrics cfm = chemPaint.getFontMetrics();
                        c.drawText(key.chem, cx - punctInkCx(chemPaint, key.chem),
                                cy + (tabletLetter() ? 11 * fsTab : 9 * fsV)
                                        - (cfm.ascent + cfm.descent) / 2, chemPaint);
                        chemPaint.setTextAlign(Paint.Align.RIGHT);
                    }
                } else if (normalLetterKey) {
                    // 普通键盘（177-751）：右上角只有一个小字（数字/符号），无右下角标
                    // 平板 1210 画板：角标 10、距右 8、距顶 19
                    hintPaint.setTextSize((tabletLetter() ? 10 * fsTab : 10 * fsText));
                    if (key.alt != null && !key.alt.isEmpty()) {
                        c.drawText(key.alt, f[2] - (tabletLetter() ? 8 * fsTab : 3 * fs),
                                f[1] + (tabletLetter() ? 19 * fsTab : 14 * fsV), hintPaint);
                    }
                } else if (letterKey) {
                    // 化学键盘：右上=长按可得的形态（alt 优先）；右下=上滑输出的化学符号（主题色）
                    hintPaint.setTextSize((tabletLetter() ? 10 * fsTab : 10 * fsText));
                    String hint = (key.alt != null && !key.alt.isEmpty()) ? key.alt : key.swipe;
                    if (hint != null && !hint.isEmpty()) {
                        c.drawText(hint, f[2] - (tabletLetter() ? 8 * fsTab : 3 * fs),
                                f[1] + (tabletLetter() ? 19 * fsTab : 14 * fsV), hintPaint);
                    }
                    chemPaint.setTextSize((tabletLetter() ? 10 * fsTab : 10 * fsText));
                    if (key.chem != null && !key.chem.isEmpty()) {
                        c.drawText(key.chem, f[2] - 4 * fs, f[3] - 5 * fsV, chemPaint);
                    }
                }
            }
        }
        drawModePopup(c);
        drawSwipeBubble(c, w);
    }

    /** 圆角矩形填充：走 Capsule G2 连续圆角。 */
    private void fillRound(Canvas c, RectF r, float rad, Paint p) {
        if (rad > 0f) {
            SmoothCorners.build(smoothPath, r, rad);
            c.drawPath(smoothPath, p);
        } else {
            c.drawRoundRect(r, rad, rad, p);
        }
    }

    // ---------------- 普通模式 / 工具栏 / 数字页绘制（2026-10-31） ----------------

    /**
     * 九键左栏卡（2026-11-02，225_2）：白卡 69×162 圆角 10、影 1px；
     * 空闲时 4 项「，。？！」17sp；有组合时换成首音节候选（点按由服务侧选音节）。
     */
    private void drawT9Rail(Canvas c) {
        float[] f = t9RailRect();
        float shadowDy = 1f * d;
        rect.set(f[0], f[1], f[2], f[3]);
        rect2.set(rect.left, rect.top + shadowDy, rect.right, rect.bottom + shadowDy);
        fillRound(c, rect2, tabRadius, shadowPaint);
        fillRound(c, rect, tabRadius, bgNormal);

        List<String> opts = t9RailOptions != null && !t9RailOptions.isEmpty()
                ? t9RailOptions : Arrays.asList(T9_RAIL_PUNCT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(17 * fsText);
        textPaint.setColor(labelColor);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        for (int i = 0; i < 4; i++) {
            float[] it = t9RailItemRect(i);
            if (pressedRow == ROW_RAIL && pressedCol == i) {
                rect.set(it[0], it[1], it[2], it[3]);
                fillRound(c, rect, 5 * fs, bgPressed);
            }
            String s = i < opts.size() ? opts.get(i) : "";
            c.drawText(s, (it[0] + it[2]) / 2f,
                    (it[1] + it[3]) / 2f - (fm.ascent + fm.descent) / 2f, textPaint);
        }
    }

    /** 数字页左侧运算符白卡（75×219，圆角 10、影 #00000026，6 项 25 高、间隔 10）。 */
    private void drawOpsCard(Canvas c) {
        float x = contentLeft + padH, y = rowTop(0), w = 75 * fs, h = 219 * fsV;
        rect.set(x, y, x + w, y + h);
        rect2.set(rect.left, rect.top + 1f * d,
                rect.right, rect.bottom + 1f * d);
        fillRound(c, rect2, 10 * fs, shadowPaint);
        fillRound(c, rect, 10 * fs, bgNormal);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(17 * fsText);
        textPaint.setColor(labelColor);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        for (int i = 0; i < NUMBER_OPS.length; i++) {
            float top = y + (6 + i * 35) * fsV;
            if (pressedRow == ROW_OPS && pressedCol == i) {
                rect.set(x + 6 * fs, top, x + w - 6 * fs, top + 25 * fsV);
                fillRound(c, rect, 6 * fs, bgPressed);
            }
            c.drawText(NUMBER_OPS[i], x + w / 2f,
                    top + 12.5f * fsV - (fm.ascent + fm.descent) / 2f, textPaint);
        }
    }

    /**
     * 工具栏页六张 90×102 白卡（2026-11-04，216-726 一比一，三列两行）：
     * 应用设置 / 深色模式 / 键盘调节 / 键盘模式 / 悬浮键盘 / 剪切板，图标在上、12sp 说明文字在下。
     * 卡片横坐标按 402 设计宽等比缩放（先原分辨率，再自适应）。
     */
    private void drawToolbarCards(Canvas c) {
        // 2026-11-09 换客户新图标：6 个图标统一 24×24 viewport，按 20×20 同比绘制；
        // textGap 按 padTop + ih + textGap 与原节奏配平，保证文字纵坐标不变。
        int[] iw = {20, 20, 20, 20, 20, 20};
        int[] ih = {20, 20, 20, 20, 20, 20};
        int[] padTop = {31, 31, 32, 32, 32, 30};
        int[] textGap = {9, 9, 8, 6, 6, 8};
        String[] labels = {"应用设置", darkMode ? "浅色模式" : "深色模式", "键盘调节",
                "键盘模式", "悬浮键盘", "剪切板"};
        Drawable[] icons = {tbIconSettings, darkMode ? tbIconSun : tbIconMode, tbIconAdjust,
                tbIconKeyboard, tbIconFloat, tbIconClipboard};
        int fg = darkMode ? 0x80FFFFFF : 0x80000000;
        // 卡片下描边（2026-11-09 新配色）：浅 黑 20% / 深 纯黑，偏移 1px
        letterShadow.setColor(darkMode ? 0xFF000000 : 0x33000000);
        float shadowDy = 1f * d;
        textPaint.setTextSize(12 * fsText);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        for (int i = 0; i < 6; i++) {
            float[] f = toolbarCardRect(i);
            rect.set(f[0], f[1], f[2], f[3]);
            rect2.set(rect.left, rect.top + shadowDy, rect.right, rect.bottom + shadowDy);
            fillRound(c, rect2, 10 * fs, letterShadow);
            fillRound(c, rect, 10 * fs,
                    (pressedRow == ROW_CARD && pressedCol == i) ? bgPressed : bgNormal);

            float iconCy = f[1] + (padTop[i] * fsV) + ih[i] * fsV / 2f;
            drawBitmapIcon(c, icons[i], (f[0] + f[2]) / 2f, iconCy,
                    iw[i] * fsV, ih[i] * fsV, fg);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(fg);
            float textCy = f[1] + (padTop[i] + ih[i] + textGap[i]) * fsV + 6 * fsV;
            c.drawText(labels[i], (f[0] + f[2]) / 2f,
                    textCy - (fm.ascent + fm.descent) / 2f, textPaint);
        }
    }

    /**
     * 工具栏卡片矩形（216-726：402 宽时 90×102，列 x60 / 157 / 254（间距 7），
     * 行 y15 / y125（间距 8）；i%3 = 列、i/3 = 行）。
     * 窄屏按 402 设计宽等比横向缩放并居中，纵向尺寸恒为设计值，避免高度抖动。
     */
    private float[] toolbarCardRect(int i) {
        float sx = contentW > 0 ? contentW / (402f * fs) : 1f;
        float cardW = 90f * fs * sx;
        float gapX = 7f * fs * sx;
        float block = cardW * 3 + gapX * 2;
        float x0 = contentLeft + (contentW - block) / 2f;
        float x = x0 + (i % 3) * (cardW + gapX);
        float y = rowTop(0) + (i / 3) * (102f + 8f) * fsV;
        return new float[]{x, y, x + cardW, y + 102f * fsV};
    }

    /**
     * 平板合并页（2026-11-22，Figma 320_187 / Frame 23，1210×434 画板）：
     * 从左到右＝分类卡（常用/中文/英文/其他）· 3×3 符号网格 + 空格 · 运算符卡（+ - × ÷ = %）
     * · 三列数字（1 4 7 / 2 5 8 0 / 3 6 9 .）· 删除 + 回车。
     * 所有尺寸以 69 键高为基准、乘 fsTab 等比缩放；总高与其他页一致（366）。
     */
    private static final class TabCell {
        float l, t, r, b;
        String label;
        int action;      // 0 = 直接上屏 label
        int style;       // STYLE_NORMAL / STYLE_SPECIAL / STYLE_ACCENT
        int catIdx = -1; // ≥0 表示分类项（点它只切分类，不上屏）
        int glyph;       // 1 = 删除图标、2 = 回车图标
    }

    private List<TabCell> tabNumCells() {
        List<TabCell> out = new ArrayList<>();
        float kh = 69 * fsTab;
        float h = getHeight() > 0 ? getHeight() : 366 * fsTab;
        float top = 12 * fsTab + topExtraPx();
        float bottom = h - 26 * fsTab;
        float left = tabContentLeft + 13 * fsTab;
        float right = tabContentLeft + tabContentW - 13 * fsTab;
        float contentH = bottom - top;

        // 分类卡
        float catW = 171 * fsTab;
        float catBottom = top + contentH;
        TabCell catCard = new TabCell();
        catCard.l = left; catCard.t = top; catCard.r = left + catW; catCard.b = catBottom;
        catCard.catIdx = -2;   // -2 = 卡片底（不可点）
        out.add(catCard);

        // 符号网格
        float opsW = 112 * fsTab, numW = 89 * fsTab, numGap = 13 * fsTab, delW = 89 * fsTab;
        float fixed = (15 + 40 + 112 + 15 + 3 * 89 + 2 * 13 + 14 + 89) * fsTab;
        float symX = left + catW + 15 * fsTab;
        // 符号区吃满剩余宽度，右侧各列固定 89/112/89，末尾回到 right（右边距 13）
        float symW = Math.max(180 * fsTab, right - symX - fixed + 15 * fsTab - 13 * fsTab);
        float sGap = 18 * fsTab;
        float[] symRowY = {
                top,
                top + kh + 16 * fsTab,
                top + (kh + 16 * fsTab) + kh + 18 * fsTab,
                top + 3 * kh + 34 * fsTab + 18 * fsTab
        };
        float cellW = (symW - 2 * sGap) / 3f;
        List<List<Key>> cat = normalSymCats.get(Math.max(0, Math.min(tabCat, normalSymCats.size() - 1)));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                TabCell cell = new TabCell();
                cell.l = symX + c * (cellW + sGap);
                cell.t = symRowY[r];
                cell.r = cell.l + cellW;
                cell.b = cell.t + kh;
                cell.style = STYLE_NORMAL;
                try {
                    cell.label = cat.get(r).get(c).label;
                } catch (Exception e) {
                    cell.label = "";
                }
                out.add(cell);
            }
        }
        // 空格（跨三列）
        TabCell space = new TabCell();
        space.l = symX; space.t = symRowY[3]; space.r = symX + symW; space.b = symRowY[3] + kh;
        space.label = "空格";
        space.action = ACT_SPACE;
        space.style = STYLE_NORMAL;
        out.add(space);

        // 运算符卡
        float opsX = symX + symW + 40 * fsTab;
        TabCell opsCard = new TabCell();
        opsCard.l = opsX; opsCard.t = top; opsCard.r = opsX + opsW; opsCard.b = catBottom;
        opsCard.catIdx = -2;
        out.add(opsCard);
        String[] ops = {"+", "-", "×", "÷", "=", "%"};
        float itemH = 36 * fsTab;
        for (int i = 0; i < ops.length; i++) {
            TabCell cell = new TabCell();
            float cy = top + (10 + 18) * fsTab + i * (itemH + 16 * fsTab);
            cell.l = opsX; cell.t = cy - itemH / 2; cell.r = opsX + opsW; cell.b = cy + itemH / 2;
            cell.label = ops[i];
            cell.style = STYLE_NORMAL;
            out.add(cell);
        }

        // 三列数字（列主序：1 4 7 / 2 5 8 0 / 3 6 9 .）
        float numX = opsX + opsW + 15 * fsTab;
        String[][] nums = {{"1", "4", "7"}, {"2", "5", "8", "0"}, {"3", "6", "9", "."}};
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 4; r++) {
                TabCell cell = new TabCell();
                float x = numX + c * (numW + numGap);
                // 行距：1→4 +16、4→7 +18、7→0 +18（列内四行与设计稿 16/18/18 对齐）
                float gaps = r == 1 ? 16 * fsTab : r == 2 ? 34 * fsTab : r == 3 ? 52 * fsTab : 0f;
                float y = top + r * kh + gaps;
                cell.l = x; cell.t = y; cell.r = x + numW; cell.b = y + kh;
                cell.label = r < nums[c].length ? nums[c][r] : "";
                cell.style = STYLE_NORMAL;
                out.add(cell);
            }
        }

        // 删除列：删除（高 239）+ 回车（69）
        float delX = numX + 3 * (numW + numGap) - numGap + 14 * fsTab;
        TabCell del = new TabCell();
        del.l = delX; del.t = top; del.r = delX + delW; del.b = top + (contentH - kh - 20 * fsTab);
        del.action = ACT_BACK;
        del.style = STYLE_SPECIAL;
        del.glyph = 1;
        out.add(del);
        TabCell enter = new TabCell();
        enter.l = delX; enter.t = bottom - kh; enter.r = delX + delW; enter.b = bottom;
        enter.action = ACT_ENTER;
        enter.style = STYLE_ACCENT;
        enter.glyph = 2;
        out.add(enter);
        return out;
    }

    private void drawTabNum(Canvas c, int w) {
        List<TabCell> cells = tabNumCells();
        float catW = 171 * fsTab;
        float cardRadius = 10 * fsTab;
        float keyRadius = 6 * fsTab;
        letterShadow.setColor(darkMode ? 0xFF000000 : 0x33000000);
        int fg = labelColor;

        // 两张白卡（分类 / 运算符）先画底与影
        for (TabCell cell : cells) {
            if (cell.catIdx != -2) continue;
            rect.set(cell.l, cell.t, cell.r, cell.b);
            rect2.set(rect.left, rect.top + 1f * d, rect.right, rect.bottom + 1f * d);
            fillRound(c, rect2, cardRadius, letterShadow);
            fillRound(c, rect, cardRadius, bgNormal);
        }

        // 分类选中块
        TabCell catCard = cells.get(0);
        float[] centers = {47, 123, 199, 275};
        for (int i = 0; i < 4; i++) {
            float cy = catCard.t + centers[i] * fsTab;
            if (i == tabCat) {
                rect.set(catCard.l + 15 * fsTab, cy - 32 * fsTab,
                        catCard.l + catW - 14 * fsTab, cy + 32 * fsTab);
                fillRound(c, rect, 5 * fsTab, bgTabSel);
            }
            textPaint.setTextSize(24 * fsTab);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(i == tabCat && darkMode ? Color.WHITE : fg);
            Paint.FontMetrics cfm = textPaint.getFontMetrics();
            String name = NORMAL_SYM_CAT_NAMES[Math.min(i, NORMAL_SYM_CAT_NAMES.length - 1)];
            c.drawText(name, (catCard.l + catCard.r) / 2f,
                    cy - (cfm.ascent + cfm.descent) / 2f, textPaint);
        }

        // 其余单元（符号 / 空格 / 运算符 / 数字 / 删除 / 回车）
        for (int idx = 0; idx < cells.size(); idx++) {
            TabCell cell = cells.get(idx);
            if (cell.catIdx == -2) continue;
            boolean pressed = pressedRow == ROW_TABNUM && pressedCol == idx;
            if (cell.style != 0) {
                rect.set(cell.l, cell.t, cell.r, cell.b);
                boolean op = cell.style == STYLE_SPECIAL;
                Paint bg = cell.style == STYLE_ACCENT ? bgAccent : op ? bgSpecial : bgNormal;
                if (!pressed) {
                    rect2.set(rect.left, rect.top + 1f * d, rect.right, rect.bottom + 1f * d);
                    fillRound(c, rect2, keyRadius, cell.style == STYLE_ACCENT ? shadowAccent : letterShadow);
                }
                fillRound(c, rect, keyRadius, pressed ? bgPressed : bg);
            }
            float cx = (cell.l + cell.r) / 2f, cy = (cell.t + cell.b) / 2f;
            if (cell.glyph == 1) {
                drawBitmapIcon(c, backspaceIcon, cx, cy, 26 * fsTab, 16 * fsTab, fg);
                continue;
            }
            if (cell.glyph == 2) {
                drawBitmapIcon(c, enterIcon, cx, cy, 30 * fsTab, 21 * fsTab, Color.WHITE);
                continue;
            }
            textPaint.setTextSize(24 * fsTab);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(cell.style == STYLE_ACCENT ? Color.WHITE : fg);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            c.drawText(cell.label, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    /** 合并页命中：返回 {ROW_TABNUM, 单元下标}。 */
    private int[] tabNumHit(float x, float y) {
        List<TabCell> cells = tabNumCells();
        for (int i = cells.size() - 1; i >= 0; i--) {
            TabCell cell = cells.get(i);
            if (cell.catIdx == -2) continue;
            if (x >= cell.l && x <= cell.r && y >= cell.t && y <= cell.b) {
                return new int[]{ROW_TABNUM, i};
            }
        }
        // 分类卡命中区域：分成 4 行判断
        TabCell catCard = cells.get(0);
        if (x >= catCard.l && x <= catCard.r && y >= catCard.t && y <= catCard.b) {
            float[] centers = {47, 123, 199, 275};
            for (int i = 0; i < 4; i++) {
                float cy = catCard.t + centers[i] * fsTab;
                if (Math.abs(y - cy) <= 38 * fsTab) {
                    return new int[]{ROW_TABNUM, -(i + 1)};   // 负下标 = 分类项
                }
            }
        }
        return null;
    }

    /** 合并页单元点击：分类只切换，其余按动作 / 字符送出。 */
    private void fireTabCell(int idx) {
        if (idx < 0) {
            tabCat = Math.max(0, Math.min(3, -idx - 1));
            invalidate();
            return;
        }
        List<TabCell> cells = tabNumCells();
        if (idx >= cells.size()) return;
        TabCell cell = cells.get(idx);
        if (cell.action != 0) fireAction(cell.action);
        else if (cell.label != null && !cell.label.isEmpty()) listener.onChar(cell.label);
    }

    /** 模式浮层：底部芯片①上方 170 宽，逐行列出当前可选项，当前项蓝字 + ✓。 */
    private void drawModePopup(Canvas c) {
        if (!modePopupOpen) return;
        computeModePopupBox();
        rect.set(modePopupBox);
        rect2.set(rect.left, rect.top + 1f * d,
                rect.right, rect.bottom + 1f * d);
        fillRound(c, rect2, 10 * fs, shadowPaint);
        pillPaint.setColor(darkMode ? 0xFF484848 : 0xFFFFFFFF);
        fillRound(c, rect, 10 * fs, pillPaint);

        int current = getInputMode();
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(15 * fsText);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        for (int i = 0; i < MODE_NAMES.length; i++) {
            float top = modePopupBox.top + (1.5f + i * 34) * fsV;
            if (modePopupPressed == i) {
                rect.set(modePopupBox.left + 3 * fs, top,
                        modePopupBox.right - 3 * fs, top + 32 * fsV);
                fillRound(c, rect, 6 * fs, bgPressed);
            }
            textPaint.setColor(i == current ? accent : labelColor);
            c.drawText(MODE_NAMES[i], modePopupBox.left + 14 * fs,
                    top + 16 * fsV - (fm.ascent + fm.descent) / 2f, textPaint);
            if (i == current) {
                textPaint.setColor(accent);
                textPaint.setTextAlign(Paint.Align.RIGHT);
                c.drawText("✓", modePopupBox.right - 14 * fs,
                        top + 16 * fsV - (fm.ascent + fm.descent) / 2f, textPaint);
                textPaint.setTextAlign(Paint.Align.LEFT);
            }
        }
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void computeModePopupBox() {
        float[] f = keyRect(rowCount() - 1, 0, getWidth());
        float w = 170 * fs, h = (1.5f * 2 + 34f * MODE_NAMES.length) * fsV;
        float x = f[0];
        float y = Math.max(0f, f[1] - 6 * fsV - h);
        modePopupBox.set(x, y, x + w, y + h);
    }

    /** 浮层第几个模式（-1 = 不在浮层内）。 */
    private int modePopupItemAt(float y) {
        if (y < modePopupBox.top || y > modePopupBox.bottom) return -1;
        int idx = (int) ((y - modePopupBox.top - 1.5f * fsV) / (34 * fsV));
        return (idx >= 0 && idx < MODE_NAMES.length) ? idx : -1;
    }

    // ---------------- 文字与图标绘制 ----------------

    /** 半角/全角标点：字形在字框左下角，按字框居中会明显偏左（客户意见 8）。 */
    private static final String PUNCT_CHARS = "，。、；：,.";

    private static boolean isPunct(String s) {
        return s != null && s.length() == 1 && PUNCT_CHARS.indexOf(s.charAt(0)) >= 0;
    }

    /** 标点墨迹中心相对「文本原点（Advance 左缘）」的水平偏移。 */
    private static float punctInkCx(Paint p, String s) {
        if (!isPunct(s)) return 0f;
        android.graphics.Rect b = new android.graphics.Rect();
        p.getTextBounds(s, 0, 1, b);
        return b.exactCenterX();
    }

    /**
     * 绘制键主字符：标点按「墨迹水平居中」，其它字符按字框居中。
     * 垂直方向一律用 baseline 居中（设计稿里逗号的墨迹本来就略偏上，这与字形有关）。
     *
     * ⚠ 标点必须切到 Align.LEFT 再按「墨迹左缘」定位：
     * Align.CENTER 居中的是**步进宽度**，不是墨迹，直接减墨迹偏移会少算半个步进
     * （实测「，」会再偏左 20px ≈ 7.6dp）。
     */
    private void drawKeyLabel(Canvas c, Key key, float cx, float cy, Paint.FontMetrics fm) {
        String s = key.label;
        if (s == null) return;
        float base = cy - (fm.ascent + fm.descent) / 2;
        if (isPunct(s)) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            c.drawText(s, cx - punctInkCx(textPaint, s), base, textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        } else {
            c.drawText(s, cx, base, textPaint);
        }
    }

    /**
     * 矢量图标统一绘制入口（删除键 / 回车键 / 底部切换输入法）。
     * 图标资源本身是黑色，这里用 tint 染成主题文字色或白色。
     */
    private void drawBitmapIcon(Canvas c, Drawable icon, float cx, float cy,
                                float w, float h, int color) {
        if (icon == null) return;
        icon.setTint(color);
        icon.setBounds((int) (cx - w / 2), (int) (cy - h / 2),
                (int) (cx + w / 2), (int) (cy + h / 2));
        icon.draw(c);
    }

    /** 底部 ⇌ 条图标：客户 2026-10-25 提供的 SVG（14×10），按 24dp 宽等比绘制。 */
    private void drawSwitchImeIcon(Canvas c, float cx, float cy, int color) {
        drawBitmapIcon(c, imeSwitchIcon, cx, cy, 24 * d, 24 * d * 10f / 14f, color);
    }

    /**
     * 圆润底部「⇌ 切换输入法」胶囊（新版 Figma 131-4224 / 175-618）：
     * 110×28 圆角 14 灰底（#D9D9D9），右距 7，图标 14×10 + 12sp 说明文字（#00000080）；
     * 整行沿用 ACT_IME 触控。
     */
    private void drawImePill(Canvas c, float[] f) {
        float h = 28 * fsV, w = 110 * fs;
        float right = contentLeft + contentW - 7 * fs;
        float top = f[1] + (f[3] - f[1] - h) / 2f;
        rect.set(right - w, top, right, top + h);
        pillPaint.setColor(darkMode ? 0xFF3A3A3A : 0xFFD9D9D9);
        fillRound(c, rect, h / 2f, pillPaint);

        int fg = darkMode ? 0xB3FFFFFF : 0x80000000;
        drawBitmapIcon(c, imeSwitchIcon, rect.left + 21 * fs, top + h / 2f, 14 * fsV, 10 * fsV, fg);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(12 * fsText);
        textPaint.setColor(fg);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        c.drawText("切换输入法", rect.left + 32 * fs,
                top + h / 2f - (fm.ascent + fm.descent) / 2f, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(23 * fsText);
        textPaint.setColor(labelColor);
    }

    /**
     * 符号页左侧分类栏（187-1873 / 188-2117）：116×219 白卡，选中块 #EAEAEA 圆角 5（43 高）、17sp 黑字。
     */
    private void drawCategoryTabs(Canvas c) {
        String[] names = currentCatNames();
        int n = names.length;
        tabPaint.setTextSize(17 * fsText);
        Paint.FontMetrics fm = tabPaint.getFontMetrics();

        float top = rowTop(0);
        rect.set(contentLeft + padH, top, contentLeft + padH + tabW, top + 219 * fsV);
        rect2.set(rect.left, rect.top + 1f * d, rect.right, rect.bottom + 1f * d);
        fillRound(c, rect2, tabRadius, shadowPaint);
        fillRound(c, rect, tabRadius, bgNormal);
        // 选中块：动画中在旧/新分类之间插值，结束后正好罩住当前项
        float[] target = tabHiRect(symbolCat);
        if (tabFrom >= 0 && tabT < 1f) {
            float[] from = tabHiRect(tabFrom);
            rect.set(lerp(from[0], target[0], tabT), lerp(from[1], target[1], tabT),
                    lerp(from[2], target[2], tabT), lerp(from[3], target[3], tabT));
        } else {
            rect.set(target[0], target[1], target[2], target[3]);
        }
        fillRound(c, rect, tabSelRadius, bgTabSel);

        tabPaint.setColor(labelColor);
        for (int i = 0; i < n; i++) {
            float[] f = tabRect(i);
            c.drawText(names[i], (f[0] + f[2]) / 2,
                    (f[1] + f[3]) / 2 - (fm.ascent + fm.descent) / 2, tabPaint);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 两个颜色按 t∈[0,1] 线性混合（各通道插值）。 */
    private static int mixColor(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        return Color.argb(
                (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    /** 上滑预览气泡（52×56dp，圆角 10dp，主题色描边）。 */
    private void drawSwipeBubble(Canvas c, int w) {
        if (!swipeFired || bubbleChar == null) return;
        float bw = 52 * d, bh = 56 * d;
        float bx = Math.max(padH, Math.min(curX - bw / 2, w - bw - padH));
        float by = curY - keyH * 0.9f - bh;
        rect.set(bx, by, bx + bw, by + bh);
        fillRound(c, rect, 10 * d, bubblePaint);
        fillRound(c, rect, 10 * d, bubbleBorder);
        bubbleText.setTextSize(30 * d);
        Paint.FontMetrics fm = bubbleText.getFontMetrics();
        c.drawText(bubbleChar, bx + bw / 2, by + bh / 2 - (fm.ascent + fm.descent) / 2, bubbleText);
    }

    // ---------------- 命中测试 ----------------

    /** 第 row 行顶边 y（含页面上边距、统一高度的顶部补白与行间距离）。 */
    private float rowTop(int row) {
        if (uniformH > 0 && isChipRow(row)) {
            // 2026-11-01 底部悬浮岛统一贴底：不管当前页内容多高，芯片行底边恒为
            // 「统一高度 - 11」，切页时三岛纵坐标完全不动（原来随各页行数/间距浮动）。
            return uniformH - 11f * fsV - rowH(row);
        }
        // 工具栏页按 177-952 的 37 上边距贴顶排版，统一高度多出的空间留在底部，
        // 不把四张卡片整体下推（悬浮岛删除后底部本来就该空出来）。
        float y = padTopPx() + (page == PAGE_TOOLBAR ? 0f : topExtraPx());
        for (int i = 0; i < row; i++) y += rowH(i) + rowGap(i + 1);
        return y;
    }

    /** 数字页左侧运算符白卡矩形。 */
    private float[] opsCardRect() {
        float y = rowTop(0);
        return new float[]{contentLeft + padH, y, contentLeft + padH + 75 * fs, y + 219 * fsV};
    }

    /**
     * 返回 {row, col}；命中特殊区域时 row 为
     * ROW_TAB（分类 tab）/ ROW_OPS（数字页运算符）/ ROW_CARD（工具栏卡片）。
     */
    private int[] hitKey(float x, float y) {
        int w = getWidth();
        if (page == PAGE_TAB_NUM) return tabNumHit(x, y);
        if (page == PAGE_SYMBOL) {
            String[] names = currentCatNames();
            float cardTop = rowTop(0);
            float cardBottom = cardTop + (mode == MODE_NORMAL ? 219 * fsV : tabContentH());
            // 分类卡整块范围：块与块之间有空隙，落卡内时取最近的一项
            if (x >= contentLeft + padH && x <= contentLeft + padH + tabW
                    && y >= cardTop && y <= cardBottom) {
                int bestI = 0;
                float bestD = Float.MAX_VALUE;
                for (int i = 0; i < names.length; i++) {
                    float[] f = tabRect(i);
                    if (x >= f[0] && x <= f[2] && y >= f[1] && y <= f[3]) return new int[]{ROW_TAB, i};
                    float dist = Math.abs(y - (f[1] + f[3]) / 2);
                    if (dist < bestD) {
                        bestD = dist;
                        bestI = i;
                    }
                }
                return new int[]{ROW_TAB, bestI};
            }
        }
        if (page == PAGE_TOOLBAR) {
            for (int i = 0; i < 6; i++) {
                float[] f = toolbarCardRect(i);
                if (x >= f[0] && x <= f[2] && y >= f[1] && y <= f[3]) return new int[]{ROW_CARD, i};
            }
        }
        if (page == PAGE_NUMBER) {
            float[] card = opsCardRect();
            if (x >= card[0] && x <= card[2] && y >= card[1] && y <= card[3]) {
                int idx = (int) ((y - card[1] - 6 * fsV) / (35 * fsV));
                if (idx >= 0 && idx < NUMBER_OPS.length) return new int[]{ROW_OPS, idx};
            }
        }
        // 九键左栏卡（225_2）：先于行扫描判定，避免被当成行首占位键旁边的数字键
        if (page == PAGE_LETTER && isT9()) {
            float[] rail = t9RailRect();
            if (x >= rail[0] && x <= rail[2] && y >= rail[1] && y <= rail[3]) {
                int idx = (int) ((y - rail[1] - 4f * fsV) / (40f * fsV));
                return new int[]{ROW_RAIL, Math.max(0, Math.min(3, idx))};
            }
        }
        // 触控范围外扩（2026-10-27）：行向上探半格、向下覆盖整条行间隙；列取最近的按键中心。
        // 这样点在按键缝隙里也会命中相邻键，不再出现「点在缝里没反应」。
        if (y < rowTop(0) - vGap / 2) return null;
        int rows = rowCount(), row = -1;
        for (int r = 0; r < rows; r++) {
            float top = rowTop(r), bot = top + rowH(r);
            float up = (r == 0 ? vGap / 2 : rowGap(r) / 2);
            float down = (r == rows - 1 ? vGap / 2 : rowGap(r + 1) / 2);
            if (y >= top - up && y < bot + down) {
                row = r;
                break;
            }
        }
        if (row < 0) return null;
        List<Key> keys = rowKeys(row);
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        float[] bestRect = null;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).spacer) continue;   // 占位键不可命中
            float[] f = keyRect(row, i, w);
            float dist = Math.abs(x - (f[0] + f[2]) / 2);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
                bestRect = f;
            }
        }
        if (best < 0 || bestRect == null) return null;
        // 列方向只外扩半个间隙：更远的位置（如符号页左侧分类栏、分体中间留白）不算命中
        float tol = Math.max(gap, 6 * fs) / 2;
        if (x < bestRect[0] - tol || x > bestRect[2] + tol) return null;
        return new int[]{row, best};
    }

    // ---------------- 触摸 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dismissPopup();
                primaryPointerId = e.getPointerId(0);
                extraPointers.clear();
                // 模式浮层优先（2026-10-31）：点浮层内选中模式，点浮层外只收起浮层、不穿透到键盘
                if (modePopupOpen) {
                    if (modePopupBox.contains(e.getX(), e.getY())) {
                        int idx = modePopupItemAt(e.getY());
                        if (idx >= 0) {
                            modePopupPressed = idx;
                            invalidate();
                            return true;
                        }
                    }
                    modePopupOpen = false;
                    modePopupPressed = -1;
                    invalidate();
                    return true;
                }
                // 自动模式反应栏（2026-11-08）：支持横向拖动，点击判定放到抬起时（与候选栏一致）
                if (inReactionBar(e.getY())) {
                    reactDragCandidate = true;
                    reactDragging = false;
                    reactDownX = e.getX();
                    reactDownY = e.getY();
                    // 惯性未停时先按当前帧位置落点再终止，否则 scrollX 仍是旧值，按住瞬间内容会回跳
                    if (reactScroller != null && !reactScroller.isFinished()) {
                        reactScrollX = clampReactScroll(reactScroller.getCurrX());
                        reactScroller.forceFinished(true);
                    }
                    reactDownScroll = reactScrollX;
                    if (reactVelocity == null) reactVelocity = VelocityTracker.obtain();
                    else reactVelocity.clear();
                    reactVelocity.addMovement(e);
                    return true;
                }
                int[] hit = hitKey(e.getX(), e.getY());
                if (hit == null) return false;
                pressedRow = hit[0];
                pressedCol = hit[1];
                downX = curX = e.getX();
                downY = curY = e.getY();
                swipeFired = false;
                longPressDone = false;
                bubbleChar = null;
                tapFeedback();
                handler.removeCallbacks(backspaceRepeat);
                // 芯片行与特殊卡（分类 / 运算符 / 工具栏）不参与长按
                if (pressedRow >= 0 && !isChipRow(pressedRow)) {
                    handler.postDelayed(longPressRunnable, 420);
                    if (rowKeys(pressedRow).get(pressedCol).action == ACT_SHIFT) {
                        shiftPointerId = e.getPointerId(0);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 主指按住 Shift 时，副指点字母 = 直接上屏大写字母（不进入长按/滑动流程）
                if (shiftHeld) {
                    int idx = e.getActionIndex();
                    int[] hit = hitKey(e.getX(idx), e.getY(idx));
                    if (hit == null || hit[0] < 0 || isChipRow(hit[0])) return true;
                    Key k = rowKeys(hit[0]).get(hit[1]);
                    if (k.action == 0 && k.label != null && k.label.length() == 1
                            && isAsciiLetter(k.label.charAt(0))) {
                        flashRow = hit[0];
                        flashCol = hit[1];
                        handler.removeCallbacks(clearFlash);
                        handler.postDelayed(clearFlash, 90);
                        tapFeedback();
                        listener.onChar(k.label);
                        invalidate();
                    }
                    return true;
                }
                // 多点触控（2026-11-05）：副指落在普通键上先记录 + 高亮，抬起时按点按结算
                if (modePopupOpen || popupShowing()) return true;   // 浮层只由主指操作
                int idx = e.getActionIndex();
                int[] hit = hitKey(e.getX(idx), e.getY(idx));
                if (hit == null || hit[0] < 0 || isChipRow(hit[0])) return true;
                Key k = rowKeys(hit[0]).get(hit[1]);
                if (k.spacer || k.action == ACT_SHIFT) return true;
                extraPointers.put(e.getPointerId(idx), new int[]{hit[0], hit[1]});
                tapFeedback();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                int pid = e.getPointerId(idx);
                // 抬起的是按住 Shift 的那根手指 → 结束「按住大写」
                if (shiftHeld && pid == shiftPointerId) {
                    endShiftHold();
                    return true;
                }
                int[] extra = extraPointers.get(pid);
                if (extra != null) {
                    // 副指抬起：仍在原键上才算点中，滑出按键则取消（震动已在按下时给过）
                    extraPointers.remove(pid);
                    int[] hit = popupShowing() ? null : hitKey(e.getX(idx), e.getY(idx));
                    if (hit != null && hit[0] == extra[0] && hit[1] == extra[1]) {
                        Key k = rowKeys(hit[0]).get(hit[1]);
                        if (k != null && !k.spacer) fireSecondaryKey(k);
                    }
                    invalidate();
                    return true;
                }
                if (pid == primaryPointerId) {
                    // 主指先抬起、其它手指还按着：主指的这次点按照常结算，不能丢字
                    primaryPointerId = -1;
                    handlePrimaryUp(e.getX(idx), e.getY(idx));
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (reactDragCandidate) {
                    if (reactVelocity != null) reactVelocity.addMovement(e);
                    float dx = e.getX() - reactDownX;
                    float dy = e.getY() - reactDownY;
                    if (!reactDragging) {
                        // 横向拖动且内容确实可滚时才接管：纵向/斜向滑按、或手指微抖都不算滚动，
                        // 内容未超宽时保持纯点击语义（点芯片叉号不会被误判成滑动）
                        if (Math.abs(dx) >= reactTouchSlop && Math.abs(dx) > Math.abs(dy)
                                && reactMaxScroll > 0f) {
                            reactDragging = true;
                        }
                    }
                    if (reactDragging) {
                        // 扣掉触发阈值再跟手：内容从原位平滑起步，不做越过 slop 时的突跳
                        float effectiveDx = dx - Math.signum(dx) * reactTouchSlop;
                        reactScrollX = clampReactScroll(reactDownScroll - effectiveDx);
                        invalidateReactionBar();
                    }
                    return true;
                }
                if (shiftHeld) return true;   // 按住 Shift 时忽略主指的滑动判定
                if (modePopupOpen) {
                    // 长按/滑动的两指流程之外：浮层内跟着手指高亮，便于确认松手结果
                    int idx = modePopupBox.contains(e.getX(), e.getY())
                            ? modePopupItemAt(e.getY()) : -1;
                    if (idx != modePopupPressed) {
                        modePopupPressed = idx;
                        invalidate();
                    }
                    return true;
                }
                cancelExtrasSlidOff(e);
                curX = e.getX();
                curY = e.getY();
                if (pressedRow < 0) return true;        // 分类 / 运算符 / 工具栏卡片：不做上滑判定
                if (isChipRow(pressedRow)) return true; // 芯片行只做点按
                if (popupShowing()) {
                    // 长按后不松手、直接滑到目标候选上，松手即输出（客户意见 3）
                    int idx = popupHit(curX, getTop() + curY);
                    if (idx != popupIndex) {
                        popupIndex = idx;
                        notifyPopup();
                    }
                    return true;
                }
                if (Math.abs(curX - downX) > 18 * d || Math.abs(curY - downY) > 18 * d) {
                    handler.removeCallbacks(longPressRunnable);
                }
                Key key = rowKeys(pressedRow).get(pressedCol);
                String up = (key.chem != null && !key.chem.isEmpty()) ? key.chem : key.swipe;
                if (!swipeFired && up != null && downY - curY > keyH * 0.55f) {
                    swipeFired = true;
                    bubbleChar = up;
                    tapFeedback();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (reactDragCandidate) {
                    reactDragCandidate = false;
                    boolean dragged = reactDragging;
                    reactDragging = false;
                    float tx = e.getX(), ty = e.getY();
                    if (!dragged) {
                        // 未拖动 → 按点击结算：芯片内叉号删除 / 撤销 / 蓝色加号
                        if (reactVelocity != null) {
                            reactVelocity.recycle();
                            reactVelocity = null;
                        }
                        return handleReactionBarTap(tx, ty);
                    }
                    // 拖动结束 → 按松手速度做惯性滑动
                    if (reactVelocity != null) {
                        reactVelocity.addMovement(e);
                        reactVelocity.computeCurrentVelocity(1000);
                        float vx = reactVelocity.getXVelocity();
                        reactVelocity.recycle();
                        reactVelocity = null;
                        reactScrollX = clampReactScroll(reactScrollX);
                        // 松手速度过小不启动惯性：手指微抖不再让内容来回滑（静止更稳）
                        if (Math.abs(vx) >= reactTouchSlop * 20f) {
                            reactScroller.fling((int) reactScrollX, 0, (int) -vx, 0,
                                    0, (int) Math.max(0f, reactMaxScroll), 0, 0);
                        }
                        invalidateReactionBar();
                    }
                    return true;
                }
                int pid = e.getPointerId(0);
                int[] extra = extraPointers.get(pid);
                if (extra != null) {
                    // 主指已先抬起，这次 ACTION_UP 是最后一根副指：补发它的按键
                    extraPointers.remove(pid);
                    int[] hit = popupShowing() ? null : hitKey(e.getX(), e.getY());
                    if (hit != null && hit[0] == extra[0] && hit[1] == extra[1]) {
                        Key k = rowKeys(hit[0]).get(hit[1]);
                        if (k != null && !k.spacer) fireSecondaryKey(k);
                    }
                    handler.removeCallbacks(longPressRunnable);
                    handler.removeCallbacks(backspaceRepeat);
                    handler.removeCallbacks(clearFlash);
                    flashRow = -1;
                    flashCol = -1;
                    extraPointers.clear();
                    primaryPointerId = -1;
                    invalidate();
                    return true;
                }
                handlePrimaryUp(e.getX(), e.getY());
                extraPointers.clear();
                primaryPointerId = -1;
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (reactDragCandidate) {
                    reactDragCandidate = false;
                    reactDragging = false;
                    if (reactVelocity != null) {
                        reactVelocity.recycle();
                        reactVelocity = null;
                    }
                }
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(backspaceRepeat);
                handler.removeCallbacks(clearFlash);
                dismissPopup();
                modePopupPressed = -1;
                swipeFired = false;
                bubbleChar = null;
                flashRow = -1;
                flashCol = -1;
                extraPointers.clear();
                primaryPointerId = -1;
                if (shiftHeld) {
                    endShiftHold();
                } else {
                    shiftPointerId = -1;
                    pressedRow = -1;
                    invalidate();
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    /**
     * 主指抬起结算（2026-11-05 从 ACTION_UP 抽出，供主指先离开屏幕时复用）。
     * 点按 / 上滑 / 长按浮层 / 分类卡与工具栏卡的松手判定都与原来一致。
     */
    private void handlePrimaryUp(float x, float y) {
        handler.removeCallbacks(longPressRunnable);
        handler.removeCallbacks(backspaceRepeat);
        handler.removeCallbacks(clearFlash);
        flashRow = -1;
        flashCol = -1;
        // 主指在反应栏按下、另一指还按着时先抬起：同样要在抬起时结算点击
        if (reactDragCandidate) {
            reactDragCandidate = false;
            boolean dragged = reactDragging;
            reactDragging = false;
            if (reactVelocity != null) {
                reactVelocity.recycle();
                reactVelocity = null;
            }
            if (!dragged) handleReactionBarTap(x, y);
            return;
        }
        if (modePopupOpen || modePopupPressed >= 0) {
            int target = modePopupPressed;
            modePopupOpen = false;
            modePopupPressed = -1;
            if (target >= 0) selectInputMode(target);
            else invalidate();
            return;
        }
        if (shiftHeld) {
            endShiftHold();
            return;
        }
        shiftPointerId = -1;
        if (pressedRow == ROW_TAB) {
            int[] hit = hitKey(x, y);
            if (hit != null && hit[0] == ROW_TAB) setSymbolCategory(hit[1]);
            pressedRow = -1;
            invalidate();
            return;
        }
        if (pressedRow == ROW_OPS) {
            // 数字页左侧运算符卡：松手位置仍在该项才算点中
            int[] hit = hitKey(x, y);
            pressedRow = -1;
            invalidate();
            if (hit != null && hit[0] == ROW_OPS) listener.onChar(NUMBER_OPS[hit[1]]);
            return;
        }
        if (pressedRow == ROW_CARD) {
            // 应用设置 / 深色·浅色模式 / 键盘调节 / 键盘模式 / 悬浮键盘 / 剪切板
            int[] hit = hitKey(x, y);
            int idx = (hit != null && hit[0] == ROW_CARD) ? hit[1] : -1;
            pressedRow = -1;
            if (idx >= 0) invalidate();
            switch (idx) {
                case 0:
                    listener.onAction(ACT_SETTINGS);
                    break;
                case 1:
                    listener.onAction(ACT_DARK);
                    break;
                case 2:
                    listener.onAction(ACT_ADJUST);
                    break;
                case 3:
                    openModePopup();
                    break;
                case 4:
                    listener.onAction(ACT_FLOAT);
                    break;
                case 5:
                    listener.onAction(ACT_CLIP);
                    break;
                default:
                    break;
            }
            return;
        }
        if (pressedRow == ROW_TABNUM) {
            // 平板合并页：松手位置仍在同一单元才算点中（分类项用负下标区分）
            int[] hit = hitKey(x, y);
            int idx = (hit != null && hit[0] == ROW_TABNUM) ? hit[1] : Integer.MIN_VALUE;
            pressedRow = -1;
            invalidate();
            if (idx != Integer.MIN_VALUE) fireTabCell(idx);
            return;
        }
        if (pressedRow == ROW_RAIL) {
            // 九键左栏卡：松手位置仍在同一项才算点中
            int[] hit = hitKey(x, y);
            int idx = (hit != null && hit[0] == ROW_RAIL) ? hit[1] : -1;
            pressedRow = -1;
            invalidate();
            if (idx >= 0) listener.onT9RailItem(idx);
            return;
        }
        if (pressedRow < 0) return;
        Key key = rowKeys(pressedRow).get(pressedCol);
        if (popupShowing()) {
            String val = (popupIndex >= 0 && popupIndex < popupOpts.size())
                    ? popupOpts.get(popupIndex) : null;
            dismissPopup();
            if (val != null) listener.onChar(val);
            pressedRow = -1;
            invalidate();
            return;
        }
        if (swipeFired) {
            listener.onChar(bubbleChar);
        } else if (!longPressDone) {
            if (key.action != 0) fireAction(key.action);
            else listener.onChar(key.input != null ? key.input : key.label);
        }
        swipeFired = false;
        bubbleChar = null;
        pressedRow = -1;
        invalidate();
    }

    /**
     * 副指点按结算（2026-11-05）：只产出字符与常用编辑键，
     * 涉及切页 / 模式浮层 / 设置的动作仍交给主指单击，避免多指时页面被意外切走。
     */
    private void fireSecondaryKey(Key key) {
        if (key == null) return;
        if (key.action == 0) {
            listener.onChar(key.input != null ? key.input : key.label);
            return;
        }
        switch (key.action) {
            case ACT_SPACE:
            case ACT_ENTER:
            case ACT_BACK:
            case ACT_LANG:
            case ACT_CASE:
            case ACT_MODE:
                listener.onAction(key.action);
                break;
            default:
                break;
        }
    }

    /** 副指滑出原按键即取消（2026-11-05）：避免按住后滑到别的键仍然误上屏。 */
    private void cancelExtrasSlidOff(MotionEvent e) {
        if (extraPointers.size() == 0) return;
        for (int n = extraPointers.size() - 1; n >= 0; n--) {
            int id = extraPointers.keyAt(n);
            int idx = e.findPointerIndex(id);
            if (idx < 0) continue;
            int[] hit = hitKey(e.getX(idx), e.getY(idx));
            int[] was = extraPointers.valueAt(n);
            if (hit == null || hit[0] != was[0] || hit[1] != was[1]) {
                extraPointers.removeAt(n);
                invalidate();
            }
        }
    }

    /** 该键当前是否被某一根副指按住（只用于绘制按下态）。 */
    private boolean isExtraPressed(int row, int col) {
        for (int n = 0; n < extraPointers.size(); n++) {
            int[] h = extraPointers.valueAt(n);
            if (h[0] == row && h[1] == col) return true;
        }
        return false;
    }

    /**
     * 按键动作分发（2026-10-31）：页面切换与模式浮层在键盘内处理，
     * 其余（空格 / 回车 / 退格 / 上屏语言 / 应用设置 …）交给服务侧。
     */
    private void fireAction(int action) {
        switch (action) {
            case ACT_KB_MODE:
                openModePopup();
                return;
            case ACT_NUM:
                // 平板（≥600dp）普通输入改用「数字 + 符号」合并页
                setPage(tablet && mode == MODE_NORMAL ? PAGE_TAB_NUM : PAGE_NUMBER);
                return;
            case ACT_SYMBOL:
                setPage(PAGE_SYMBOL);
                return;
            case ACT_BACK_KB:
                setPage(PAGE_LETTER);
                return;
            case ACT_TOOLBAR:
                setPage(page == PAGE_TOOLBAR ? PAGE_LETTER : PAGE_TOOLBAR);
                return;
            case ACT_AUTO_TOGGLE:
                toggleAutoMode();
                return;
            case ACT_AUTO_LANG:
                autoEnglish = !autoEnglish;
                ensureAutoRows();
                listener.onAction(ACT_MODE);   // 同步服务侧 pinyinMode
                invalidate();
                return;
            case ACT_REACT:
                performReact();
                return;
            case ACT_SEND_EQ:
                sendEquation();
                return;
            case ACT_CONFIRM:
                confirmEquation();
                return;
            case ACT_UNDO:
                undoReact();
                return;
            default:
                listener.onAction(action);
        }
    }

    /** 切换自动 / 手动化学输入模式。 */
    private void toggleAutoMode() {
        chemAutoMode = !chemAutoMode;
        reactionState = REACT_EDITING;
        reactionSlots.clear();
        cancelSlotAnim();
        balancedReaction = null;
        balancedDisplay = "";
        preReactSlots = null;
        preReactComposing = "";
        resetReactScroll();
        notifyReactionState(false);
        if (chemAutoMode) {
            // 进入自动模式时，子状态与服务侧 pinyinMode 对齐：pinyin→拼音子态，element→元素子态
            autoEnglish = !pinyinMode;
            ensureAutoRows();
            setPage(PAGE_LETTER);
        }
        // 反应栏改变了键盘内容高度，必须重新测量（setPage 在已处于字母页时会提前 return）
        requestLayout();
        invalidate();
    }

    /** 确认当前组合区内容为一个反应物槽。 */
    private void confirmCurrentSlot() {
        String text = listener.getComposingText();
        if (text != null && !text.trim().isEmpty()) {
            addReactionSlot(text.trim());
            listener.clearComposing();
        }
    }

    /** 供服务侧在自动模式下把候选/空格确认的文本加入反应物槽。 */
    public void addReactionSlot(String text) {
        if (text == null || text.trim().isEmpty()) return;
        reactionSlots.add(toAsciiFormula(text.trim()));
        animateSlotIn();
        invalidate();
    }

    /**
     * 新增反应物后的入场动画（2026-11-08）：已有芯片全程静止、不改变样式；
     * 只有「输入槽 + 蓝色加号」从原输入槽位置（原芯片右缘）整体向右平移并渐显。
     * 动画器保持线性进度：透明度按线性 0→100%，位移在绘制时套 ease-out，350ms。
     */
    private void animateSlotIn() {
        if (slotAnimator != null) slotAnimator.cancel();
        slotAnimFromX = reactSlotContentX;
        slotAnimRaw = 0f;
        final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SLOT_ANIM_DUR);
        anim.addUpdateListener(a -> {
            slotAnimRaw = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (slotAnimator != a) return;   // 已被新动画取代：不重置新动画状态
                slotAnimRaw = 1f;
                invalidate();
            }
        });
        slotAnimator = anim;
        anim.start();
    }

    /** 清空反应物时一并结束输入槽动画，避免残留进度叠到新的布局上。 */
    private void cancelSlotAnim() {
        if (slotAnimator != null) slotAnimator.cancel();
        slotAnimator = null;
        slotAnimRaw = 1f;
    }

    /** 是否处于化学自动配平模式（服务侧据此决定上屏走槽位还是直出）。 */
    public boolean isChemAutoMode() {
        return chemAutoMode;
    }

    /** 延迟加载反应库（数据量小，主线程同步读即可）。 */
    private void ensureEngine() {
        if (reactionEngine == null) {
            reactionEngine = new ReactionEngine();
            reactionEngine.load(getContext());
        }
    }

    /** 「发生反应」：查库 → 0/1/N 条匹配处理。 */
    private void performReact() {
        ensureEngine();
        // 配平前快照：撤销时完全回到这一刻的输入（芯片 + 未确认的组合区）
        String pending = listener.getComposingText();
        preReactSlots = new ArrayList<>(reactionSlots);
        preReactComposing = pending == null ? "" : pending;
        // 若还有未确认的组合区，先并入槽位
        if (pending != null && !pending.trim().isEmpty()) {
            reactionSlots.add(toAsciiFormula(pending.trim()));
            listener.clearComposing();
            invalidate();
        }
        if (reactionSlots.isEmpty()) {
            Toast.makeText(getContext(), "请先输入反应物", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ReactionEngine.Reaction> results = reactionEngine.query(reactionSlots);
        if (results.isEmpty()) {
            Toast.makeText(getContext(), "未找到匹配的反应", Toast.LENGTH_SHORT).show();
            return;
        }
        if (results.size() == 1) {
            balancedReaction = results.get(0);
            balancedDisplay = balancedReaction.displayEquation(reactionSlots);
            reactionState = REACT_BALANCED;
            resetReactScroll();
            // 配平态反应栏为两行，高度变化需重新测量；候选栏同步换成撤销/确认
            requestLayout();
            invalidate();
            notifyReactionState(true);
            return;
        }
        showReactionPicker(results);
    }

    /** 多条匹配时弹窗让用户选择。 */
    private void showReactionPicker(final List<ReactionEngine.Reaction> results) {
        String[] items = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            items[i] = results.get(i).displayEquation(reactionSlots);
        }
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("选择反应")
                .setItems(items, (d, which) -> {
                    balancedReaction = results.get(which);
                    balancedDisplay = results.get(which).displayEquation(reactionSlots);
                    reactionState = REACT_BALANCED;
                    resetReactScroll();
                    requestLayout();
                    invalidate();
                    notifyReactionState(true);
                })
                .create();
        // 输入法窗口没有 Activity token，直接 show() 会抛 BadTokenException；
        // 改成附着在键盘窗口的对话框类型，并复用键盘 View 的 window token。
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            lp.token = getWindowToken();
            dialog.getWindow().setAttributes(lp);
        }
        dialog.show();
    }

    /** 通知服务侧切换候选栏按钮（配平态显示撤销/确认，其余状态恢复原有按钮）。 */
    private void notifyReactionState(boolean balanced) {
        if (listener != null) listener.onReactionStateChanged(balanced);
    }

    /** 「确认」：把配平完成的方程式直接上屏到输入栏，然后回到编辑态。 */
    public void confirmEquation() {
        if (reactionState != REACT_BALANCED || balancedReaction == null) {
            Toast.makeText(getContext(), "请先点「发生反应」", Toast.LENGTH_SHORT).show();
            return;
        }
        listener.commitTextDirect(currentBalancedEquation());
        resetAfterCommit();
    }

    /** 「撤销」：取消本次配平，完全回退到配平操作执行前的原始输入状态。 */
    public void undoReact() {
        if (reactionState != REACT_BALANCED) return;
        reactionState = REACT_EDITING;
        balancedReaction = null;
        balancedDisplay = "";
        if (preReactSlots != null) {
            reactionSlots.clear();
            reactionSlots.addAll(preReactSlots);
        }
        if (preReactComposing != null && !preReactComposing.isEmpty()) {
            listener.restoreComposing(preReactComposing);
        }
        cancelSlotAnim();
        resetReactScroll();
        requestLayout();
        invalidate();
        notifyReactionState(false);
    }

    /** 确认上屏后的复位：回编辑态、清空槽位与配平前快照。 */
    private void resetAfterCommit() {
        reactionState = REACT_EDITING;
        balancedReaction = null;
        balancedDisplay = "";
        reactionSlots.clear();
        preReactSlots = null;
        preReactComposing = "";
        cancelSlotAnim();
        resetReactScroll();
        requestLayout();
        invalidate();
        notifyReactionState(false);
    }

    /** 当前要上屏的配平串：离子场景优先净离子式，否则回到条目自带方程式。 */
    private String currentBalancedEquation() {
        if (!balancedDisplay.isEmpty()) return balancedDisplay;
        return balancedReaction == null ? "" : balancedReaction.equation;
    }

    /**
     * 「发送」：等同回车——先确认当前输入（配平式或组合区原文）上屏，
     * 再走系统回车直接发送，不再与「确认」抢同一套上屏逻辑。
     */
    private void sendEquation() {
        if (reactionState == REACT_BALANCED && balancedReaction != null) {
            listener.commitTextDirect(currentBalancedEquation());
            resetAfterCommit();
        } else {
            String comp = listener.getComposingText();
            if (comp != null && !comp.trim().isEmpty()) {
                listener.commitTextDirect(prettyFormula(toAsciiFormula(comp.trim())));
                preReactComposing = "";
            }
        }
        listener.onAction(ACT_ENTER);
    }

    /** 打开模式选择浮层（左下角芯片①上方）。 */
    private void openModePopup() {
        computeModePopupBox();
        modePopupOpen = true;
        modePopupPressed = -1;
        invalidate();
    }

    /** 供候选字界面底部「选择键盘模式」调用（2026-11-01）：与键盘上的芯片走同一浮层。 */
    public void showModePopup() {
        openModePopup();
    }

    /** 供服务侧品牌圆键长按调用（2026-11-04）：品牌键单击改开工具栏后，符号页入口由长按保留。 */
    public void showSymbolPage() {
        setPage(PAGE_SYMBOL);
    }

    /** 供候选字界面底部「工具栏」调用（2026-11-01）：回到键盘的工具栏页。 */
    public void showToolbarPage() {
        setPage(PAGE_TOOLBAR);
    }

    /** 结束「按住 Shift」：恢复默认大小写，并清掉主指状态（2026-10-25）。 */
    private void endShiftHold() {
        if (!shiftHeld) return;
        shiftHeld = false;
        shiftPointerId = -1;
        handler.removeCallbacks(longPressRunnable);
        pressedRow = -1;
        pressedCol = -1;
        invalidate();
        listener.onShiftHoldChanged(false);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    // ---------------- 长按多选 ----------------

    private void fireLongPress() {
        if (pressedRow < 0 || pressedCol < 0 || swipeFired) return;
        if (isChipRow(pressedRow)) return;
        List<Key> keys = rowKeys(pressedRow);
        if (pressedCol >= keys.size()) return;
        Key key = keys.get(pressedCol);
        if (key.spacer) return;
        if (key.action == ACT_MODE) {
            // 长按「拼｜素」= 开关元素自动大小写（设计稿 §3.6.2）
            longPressDone = true;
            listener.onAction(ACT_CASE);
            return;
        }
        if (key.action == ACT_BACK) {
            // 长按删除：先删一个，之后每 70ms 连续删除，松手停止（2026-10-25 新需求）
            longPressDone = true;
            listener.onAction(ACT_BACK);
            handler.postDelayed(backspaceRepeat, 70);
            return;
        }
        if (key.action == ACT_SHIFT) {
            // 长按 Shift：进入「按住大写」；另一根手指可连续点字母，松手恢复小写
            longPressDone = true;
            shiftHeld = true;
            listener.onShiftHoldChanged(true);
            return;
        }
        if (key.action != 0) return;
        List<String> opts = longPressOptions(key);
        if (opts.size() < 2) return;
        longPressDone = true;
        showPopup(opts);
    }

    /**
     * 字母的「上角标」形态（Unicode 缺字的不收录：Q/X/Y/Z 无上角标）。
     * 每段格式固定为「大写字母 + 上角标字符」，上角标都是 BMP 单字符。
     */
    private static final String SUPER_LETTERS =
            "Aᴬ|Bᴮ|Cᶜ|Dᴰ|Eᴱ|Fᶠ|Gᴳ|Hᴴ|Iᴵ|Jᴶ|Kᴷ|Lᴸ|Mᴹ|Nᴺ|Oᴼ|Pᴾ|"
            + "Rᴿ|Sˢ|Tᵀ|Uᵁ|Vⱽ|Wᵂ";

    private static String superscriptOf(String label) {
        if (label == null) return null;
        for (String seg : SUPER_LETTERS.split("\\|")) {
            if (seg.length() == 2 && seg.charAt(0) == label.charAt(0)) {
                return String.valueOf(seg.charAt(1));
            }
        }
        return null;
    }

    /**
     * 长按候选项（对齐设计稿 4488aa83 与客户意见 10）：
     * 数字形态 · 数字上角标 · 数字下角标 · 字母上角标 · 大写 · 小写。
     * 例：T → 5 / ⁵ / ₅ / ᵀ / T / t。
     */
    private List<String> longPressOptions(Key key) {
        List<String> opts = new ArrayList<>();
        if (isT9()) return opts;   // 九键：数字键点按即输入，无长按形态
        if (mode == MODE_NORMAL) {
            // 普通键盘（177-751 / 224-12 气泡）：符号 → 大写 → 小写（+ 四声调）
            addOpt(opts, key.alt);
            String label = key.label;
            if (label != null && label.length() == 1 && Character.isLetter(label.charAt(0))) {
                addOpt(opts, label.toUpperCase());
                addOpt(opts, label.toLowerCase());
                // 2026-11-02 客户要求：韵母键 A/E/I/O/U 长按列出四个汉语拼音声调
                addToneOpts(opts, Character.toLowerCase(label.charAt(0)));
            } else {
                addOpt(opts, label);
            }
            return opts;
        }
        addOpt(opts, key.alt);     // 数字/符号的常规形态
        addOpt(opts, key.swipe);   // 上角标形态
        addOpt(opts, key.chem);    // 下角标形态
        // 符号页里带「+ / −」气泡的 N 键：长按候选补上标电荷符号 ⁺ / ⁻（2026-10-27）
        if ("N".equals(key.label) && "+".equals(key.swipe)) {
            addOpt(opts, "⁺");
            addOpt(opts, "⁻");
        }
        String label = key.label;
        if (label != null && label.length() == 1 && Character.isLetter(label.charAt(0))) {
            addOpt(opts, superscriptOf(label));           // 字母上角标
            addOpt(opts, label.toUpperCase());            // 大写
            addOpt(opts, label.toLowerCase());            // 小写
        } else {
            addOpt(opts, label);
        }
        return opts;
    }

    private static void addOpt(List<String> opts, String v) {
        if (v != null && !v.isEmpty() && !opts.contains(v)) opts.add(v);
    }

    /**
     * 韵母键的四个汉语拼音声调（2026-11-02，客户：A/E/I/O/U 长按显示声调字母）。
     * 每段格式固定为「小写韵母 + 阴平 + 阳平 + 上声 + 去声」。
     */
    private static final String[] PINYIN_TONES = {
            "aāáǎà", "eēéěè", "iīíǐì", "oōóǒò", "uūúǔù"
    };

    private static void addToneOpts(List<String> opts, char lower) {
        for (String t : PINYIN_TONES) {
            if (t.charAt(0) == lower) {
                for (int i = 1; i < t.length(); i++) addOpt(opts, String.valueOf(t.charAt(i)));
                return;
            }
        }
    }

    /** 浮层是否正在显示。 */
    private boolean popupShowing() {
        return popupOpts != null;
    }

    /**
     * 显示长按浮层。
     *
     * 坐标换算到 **IME 根布局坐标系**：键盘 View 画不出自己上方的内容（会被父容器裁掉），
     * 所以真正的绘制交给服务侧叠在根布局上的 overlay，键盘只负责算位置和跟踪手指。
     * 气泡尺寸按 177-751 / 224-12 设计稿（56 高，三项 111 宽 / 带四声调 233 宽）。
     */
    private void showPopup(List<String> opts) {
        popupOpts = opts;
        popupIndex = -1;
        float[] f = keyRect(pressedRow, pressedCol, getWidth());
        float bw = KeyPopupView.width(opts.size()) * d;
        float bh = KeyPopupView.POPUP_H * d;
        float bx = (f[0] + f[2]) / 2 - bw / 2;
        bx = Math.max(contentLeft + padH, Math.min(bx, contentLeft + contentW - bw - padH));
        float by = getTop() + f[1] - bh - 4 * d;
        if (by < 0) by = 0;                        // 上方空间不足就贴顶（可盖住候选栏）
        popupBox.set(bx, by, bx + bw, by + bh);
        popupAnchorX = (f[0] + f[2]) / 2;
        popupAnchorY = getTop() + f[1];
        notifyPopup();
        invalidate();
    }

    private void dismissPopup() {
        if (popupOpts == null) return;
        popupOpts = null;
        popupIndex = -1;
        notifyPopup();
        invalidate();
    }

    private void notifyPopup() {
        if (listener != null) {
            listener.onLongPressPopup(popupOpts, popupBox, popupIndex, popupAnchorX, popupAnchorY);
        }
    }

    /** 手指落在浮层第几个候选上（-1 = 不在浮层内）。入参是 IME 根布局坐标。 */
    private int popupHit(float rootX, float rootY) {
        if (popupOpts == null || !popupBox.contains(rootX, rootY)) return -1;
        int n = popupOpts.size();
        float x = rootX - popupBox.left;
        float symL = KeyPopupView.itemLeft(0, n) * d;
        float blueL = KeyPopupView.itemLeft(1, n) * d;
        float runL = KeyPopupView.itemLeft(2, n) * d;
        float mid1 = (symL + KeyPopupView.SYM_W * d + blueL) / 2;      // 符号｜蓝格
        float mid2 = (blueL + KeyPopupView.BLUE_W * d + runL) / 2;     // 蓝格｜变体串
        if (x < mid1) return 0;
        if (n < 3 || x < mid2) return 1;
        float slot = (KeyPopupView.wide(n) ? KeyPopupView.SLOT_W : KeyPopupView.CHAR_W) * d;
        int idx = 2 + (int) ((x - runL) / slot);
        return Math.max(2, Math.min(idx, n - 1));
    }
}
