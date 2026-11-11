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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jhf.jiehuafa.MainActivity;
import com.jhf.jiehuafa.R;
import com.jhf.jiehuafa.SmoothDrawable;
import com.jhf.jiehuafa.UiStyle;
import com.kingzcheung.xime.rime.RimeCandidate;
import com.kingzcheung.xime.rime.RimeComposition;
import com.kingzcheung.xime.rime.RimeEngine;
import com.kingzcheung.xime.rime.RimeProcessResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接化发·化学输入法核心服务。
 * 拼音模式：输入中文名拼音 → 候选化学式（如 liusu-antong → 硫酸铜 → CuSO₄）。
 * 直通模式：字母直出，元素符号自动大小写；上滑输出数字角标与化学符号。
 * 候选栏：[化|符] 切换符号页 · 候选芯片（只显示候选词，不回显拼音） · 右侧两颗控制键：
 * 无输入只留 ⌄（收起键盘）；打字中同时显示「→」（展开候选字界面）与 ✕（清空候选）。
 * 长按 ⌄ 仍可展开候选。
 * 键盘底部 ⇌：调起系统输入法切换器。
 *
 * 修改日志（之后每次改动都在这里追加一条：日期 + 改动内容）：
 *   2026-10-25 元素模式默认 autoCase=true：第一个字母大写、第二个字母小写（双字母元素自动校正；
 *              连续元素如 NaCl 也正确，不会出现 NaCL）；
 *              大写键改为一次性生效（shiftOn，输出一个字母后自动恢复小写）；
 *              长按大写键期间 shiftHeld=true，另一指点字母连续输出大写，松手恢复默认大小写；
 *              注册 ACTION_WALLPAPER_CHANGED 广播，自动主题在壁纸更换后立即刷新；
 *              词库扩至 3897 条（新增有机化学基团/化合物 61 条）。
 *   2026-10-26 候选栏左上「化｜符」芯片字号 13sp → 18dp，与空格键（18 * density px）一致。
 *              新增按键触感反馈开关（主界面「通用 → 触感反馈」，prefs 键 haptic，默认开）；
 *              符号页点「mol」不再直接上屏，先进组合区并给出 mol/L、mol/mL 等复合单位候选；
 *              直通模式手打 kg、L、mol 等单位时也给出复合单位候选（UNIT_COMBOS）；
 *              单位候选排序规则：直通模式、无拼音候选、组合区为多字母单位（kg/mol…）时单位置顶；
 *              拼音模式单字母 L 仍拼音优先、单位接在后面，保证手打 L 也能选到 L/mol。
 *   2026-10-27 读取 kb_layout 布局偏好并下发键盘（0 手机默认 / 1 限宽居中 / 2 横屏分体 / 3 悬浮小键盘）；
 *              新增 syncCandBar()：限宽与悬浮模式下候选栏宽度/位移跟随键区，不再横跨整屏；
 *              横竖屏切换时重新读取偏好并重建布局模式。
 *   2026-10-27 新增上屏联想符号：沉淀化合物（AgCl/BaSO₄/Fe(OH)₃ 等）上屏后候选栏给出「↓」，
 *              气体（H₂/O₂/CO₂/NH₃ 等）给出「↑」，单独元素给出常见离子电荷（Fe → ²⁺、³⁺，
 *              Cl → ⁻ 等）；直通模式下组合区成化学式时实时联想，上屏后保留到下一次输入。
 *              所有上屏路径（拼音候选 / 空格 / 回车 / 规范化学式 / 空格直出）统一走 commitAndSuggest()；
 *              候选芯片行内边距 4dp→2dp 并关闭字体预留，修复 35dp 候选栏里副标题被裁掉的问题。
 *              直通模式支持同位素质量数写法：整串为「数字 + 单个元素」时数字写成上标，
 *              2H → ²H、14C → ¹⁴C、235U → ²³⁵U（2H2O 这类仍按系数处理）；
 *              直通模式下数字（含符号页角标的上标数字）可开头进入组合区，才能拼出上述同位素写法。
 *   2026-10-28 布局收敛为 铺满 / 左右断开 / 浮窗 三种（删除限宽居中）；候选栏不再单独对齐，
 *              因为浮窗模式下候选栏已在卡片内、随卡片整体移动；
 *              浮窗实现：输入视图铺满整屏当画布，卡片（20dp 抓手 + 候选栏 + 键盘）是画布里的
 *              一个子 View，位置由外边距决定，抓手按住后可上下左右任意拖动；
 *              onComputeInsets 把卡片外的触摸区域让回应用（浮窗不挤压、也不独占屏幕）。
 *              键盘布局在输入法显示/隐藏、横竖屏切换、设置项变化时统一走 applyLayoutMode()。
 *   2026-10-28 浮窗改为「整屏输入视图 + 卡片子 View」：原先 PopupWindow 挂在 IME 窗口下，
 *              会被按 1px 高的父窗口裁成 0 高——卡片收不到触摸、候选栏量不出高度所以空白；
 *              现在输入视图铺满整屏当画布，卡片用它自己的外边距定位，抓手拖动只改边距，
 *              上下左右都跟手（拖动中不重算 insets，松手才刷新，避免手势被重布局打断）；
 *              onComputeInsets 把可见区让回应用（浮窗不挤压应用排版），
 *              可触摸区域收窄成卡片矩形，卡片外的点击照常落到应用上。
 *   2026-10-28 浮窗卡片宽度改为固定 420dp（原来取屏宽 42%，横屏会撑到 1431px 显得过宽），
 *              横竖屏浮窗尺寸与键面大小完全一致。
 *   2026-10-28 手机不给手选布局：非平板（最小宽度 < 600dp）时横屏自动悬浮、竖屏自动平铺，
 *              旋转后由 onConfigurationChanged 重新 loadPrefs + applyLayoutMode 生效；
 *              平板仍按设置里的选择走。
 *   2026-10-29 手机横屏浮窗整卡偏大（横屏可用高度仅 375dp，420dp 宽整卡几乎占满）：
 *              新增 floatCompactScale()，手机横屏浮窗按 0.78 缩放键高/间距/字号并把卡片宽度
 *              收为 420dp×0.78，竖屏与平板保持 1.0 不变；applyLayoutMode 与键盘创建处同步下发。
 *   2026-10-29 浮窗位置不再记忆：onStartInputView 里把 floatPositioned 置回 false，
 *              每次呼出键盘都重新落到默认坐标（水平居中、距底 24dp），拖动位置只在本次唤出不保留。
 *              深色卡片底色由纯黑改为深灰 0xFF313131，与竖屏深色键盘一致。
 *   2026-10-31 圆润风格（Figma 131-4224 / 169-1539）：候选栏白底顶部圆角 18，
 *              「化｜符」/输入区/⌄ 芯片改为 #EAEAEA 圆角 9（无描边，字色跟随深浅色），
 *              键盘强调色统一 #2E94F3（键盘侧 setThemeColors 内处理），浮窗卡片圆角 18、
 *              底色 #E4E4E4。
 *   2026-10-31 深色圆润（177-1655/1242/1194）：键盘窗底 #252525；候选栏随后按 177-751/1655
 *              改为与键盘同底色（浅 #EAEAEA / 深 #252525）、无圆角无输入框底，#0E0E0E 栏底作废；
 *              「符号/返回」左芯片 75×35 白/ #484848 胶囊圆角 25、17sp，⌄ 芯片 35×35 圆角 23，
 *              图标与文字统一 白/黑。
 *   2026-10-31 新增键盘模式（左下角芯片切换，偏好 kb_mode：0 化学 / 1 普通）：
 *              化学模式沿用 PinyinMatcher + 化学符号页（度量/物质/反应/角标）；
 *              普通模式接 NormalPinyinMatcher 离线词库，字母页/长按气泡/符号页（常用/中文/
 *              英文/其他）均为普通版，仅普通模式提供数字页与工具栏页；
 *              工具栏「应用设置」跳转主界面、「键盘模式」打开模式浮层、返回键盘芯片回字母页。
 *   2026-10-31 按 187-1873 / 188-2117：圆润候选栏左芯片「符号」黑字、其余页「返回」蓝字
 *              （#2E73F3，与设计稿 text4 一致；原来统一黑字）。
 *   2026-11-01 沉浸式适配：新增 applyImeWindowChrome()，键盘窗口导航栏（含手势条区域）填键盘底色
 *              （浅 #EAEAEA / 深 #252525），
 *              Android 9+ 去掉导航栏分隔线、Android 10+ 关闭强制对比度黑纱，键盘下方不再有黑条。
 *   2026-11-01 工具栏四卡（177-952 / 177-1290）动作接线：
 *              ACT_DARK 直接切输入法深浅色（写 dark + follow=false，不再跟随系统）；
 *              ACT_FLOAT 在悬浮 / 平铺之间切换并实时 applyLayoutMode，写 kb_layout_manual=true
 *              让手机端也保留用户手动选的布局（此前手机每次 loadPrefs 都会强制回竖屏平铺）。
 *   2026-11-01 浮窗卡片高度改用一次性真实测量（floatCardH）：进入浮窗时抓手由 GONE 变 VISIBLE，
 *              原来拿平铺时旧高度算落点会让整卡下沉约 20dp，底部三个芯片被窗口下沿裁掉；
 *              切键盘模式 / 横竖屏 / 进出浮窗都会重置缓存并重新落位。
 *   2026-11-02 候选栏与候选字界面改为「只显示候选词」：组合区不再回显已输入的拼音
 *              （composingView 恒 GONE，预编辑文本仍由应用侧输入框显示）；
 *              候选芯片不再拼「词 + 拼音注释」小字；无候选时的兜底提示去掉拼音片段；
 *              普通词频/单字候选、候选字界面网格、简拼与联想候选一律只渲染词本身。
 *   2026-11-02 候选栏两颗控制键改为跟输入状态联动：无输入时只留右边一颗 ⌄（收起键盘）、
 *              左侧「→」完全隐藏；一开始打字就同时出现「→」，⌄ 换成 ✕（清空候选）；
 *              点 ✕ 清空组合区与全部候选后，图标自动复原成 ⌄、收起键盘功能回来、「→」重新隐藏。
 *   2026-11-02 候选栏连续滑动：Rime 一页只给 6 个候选，滑到右端 40dp 内就按页自动追加，
 *              候选栏可一路滑到底看全所有候选；跨页选词先按页号翻回该页再选；
 *              长按展开态仍限两行 10 个（98dp 容器放不下第三行）。
 *   2026-11-02 普通输入候选栏左侧按 Figma 216-207 / 216-397 还原：只留 35×35 白（深色 #484848）
 *              圆胶囊品牌键，内含 20×17 「化」字 + 品牌青对勾（#05BEC1，LayerDrawable 两层叠加）；
 *              原「符号」文字键在普通模式删除，符号页入口收进这颗圆键（点击行为不变）；
 *              圆键与候选区间距 14dp；按下变淡、抬手复原；化学模式保持原样。
 *   2026-11-02 圆润候选栏顶栏加高：上下内边距 9 / 8（芯片 35 + 17 = 52dp，与 216-207 一致），
 *              芯片不再贴栏边；多出的下边距让顶栏与下方键区自然拉开约一个行距，键高/行距不变。
 *   2026-11-03 新增普通输入「九键」方案（normal_scheme=2 → jhf_t9）：
 *              数字 2-9 走 T9 专用 JNI（processT9Key 内部 T9CancelSentenceScores + FlushRimeInput），
 *              普通 processKey 少了 flush，数字串不会进组合区；
 *              选词用 t9SelectCandidate（「拼音注释 + 候选文字」还原），失败才回退 selectCandidate；
 *              左栏卡空闲时「，。？！」、组合时首音节候选（点按走 t9SelectPinyin 重新解析剩余数字）；
 *              标点 / @ 在组合态下先确认首候选再直出；候选栏只渲染候选词，不显示拼音注释。
 *   2026-11-03 符号 / 数字 / 工具栏页左上统一「返回」芯片（188-2117）：化学与普通两种键盘
 *              都换成 75×35 白（深色 #484848）胶囊 + 「返回」蓝字 #2E73F3，字母页仍是
 *              207-397 的圆形品牌键；化学候选栏随之与普通模式一致，不再单留「化｜符」文字键
 *              （符号页入口由圆键承担，行为不变）。
 *   2026-11-03 化学候选栏芯片同步普通模式外观（candChipBg）：首个候选压灰块
 *              （浅 #D9D9D9 / 深 #4A4A4A）圆角 6、其余透明无描边，主字强调色、副字灰；
 *              芯片间距 3/4dp。
 *              化学式 / 复合单位 / 联想符号 / 规范化学式四类芯片统一走同一套底色。
 *   2026-11-03 候选栏与键盘之间改 1dp 分割线：删除候选栏 2dp 投影（candBar.setElevation），
 *              改为 cardColumn 里的 1dp View（浅 #D9D9D9 / 深 #3A3A3A），
 *              候选字界面展开/收起时与候选栏同步显隐，并在高度计算里一并补给面板。
 *   2026-11-04 候选栏按 216-207 / 241-265 新增四颗 35×35 快捷圆胶囊（键盘模式 / 深浅色 /
 *              悬浮键盘 / 切换输入法），替代键盘底部被删除的三颗悬浮岛；彼此间距 10dp、
 *              浅色白底黑图标、深色 #484848 底白图标（深浅色键浅色显示月亮、深色显示太阳）。
 *              打字有组合时整行隐藏、清空候选后重新出现，符号/数字/工具栏页只剩「返回」；
 *              品牌圆键单击改为打开工具栏页，长按保留符号页入口。
 *              候选字界面 NormalCandPanel 底部的同款三芯片行一并删除。
 *   2026-11-04 候选栏四颗快捷按键的图标换成客户新给的 5 个 Figma 素材（键盘 18×15 /
 *              月亮 17×17 / 太阳 21×21 / 悬浮键盘 20×16 / 双向箭头 16×15），
 *              按各自原生尺寸留内边距，图标与设计稿 1:1 显示，深浅色键在月亮与太阳间
 *              切换时同步调整内边距，保证五个素材视觉大小和谐。
 *   2026-11-04 工具栏与候选栏快捷键新增「键盘调节 / 剪切板」两项（216-726 / 248-16 /
 *              249-204 / 249-394，图标走 iconmcp：canvas-size / clipboard / close / check）：
 *              候选栏快捷键增至 6 颗（模式 / 深浅 / 悬浮 / 调节 / 剪切板 / 切换输入法）；
 *              剪切板监听系统剪贴板，最多留 50 条（偏好 clip_history，JSON 持久化），
 *              面板与键盘等高地顶替键盘视图、候选栏显示「返回」，点条目上屏、点 X 删除；
 *              键盘调节为整卡压暗的编辑态，拖四角 / 四边白色拖手实时改键盘宽高比例
 *              （宽 0.6–1.0 居中收窄、高 0.65–1.35，偏好 kb_scale_w / kb_scale_h），
 *              重置恢复默认、取消放弃、保存写偏好。
 *   2026-11-04 修复键盘调节一进编辑态输入法就整块跳到屏幕上方：调节浮层原用 MATCH_PARENT
 *              挂在 wrap_content 的卡片里，普通 View 在 AT_MOST 下会取整屏高度，把卡片撑到
 *              屏幕高；改为由 setFrameSize() 下发卡片实测尺寸并覆写 onMeasure，
 *              卡片布局变化时同步浮层尺寸（实时重排也始终盖满整卡）。
 *              拖动改为实时重排键盘（onPreview 直接下发比例，键高/间距/字号跟手），
 *              输入法底边固定不动、顶部随高度伸缩；取消回滚到已保存比例，保存保留当前尺寸；
 *              三钮图标改用客户 2026-11-04 素材（重置 28×34 / 取消 26×26 / 保存 34×21），
 *              圆钮与标签位置按 249-204 的 x103/202/301、y153 一比一还原。
 *   2026-11-04 修复键盘调节拖动时键盘「锁在顶部、纵坐标不跟手」：调节浮层改为每次测量时
 *              直接取 cardColumn 的实测内容高度（不再回填 floatCard 高度），彻底断开
 *              「浮层越高 → 卡片越高 → 浮层更高」的死循环；框按当前卡片高度实时推算，
 *              向下拖顶部拖手时键盘顶部随手指实时下移、向上拖实时上移。
 *              按键字号改为固定基准 fsText = d × compact，不再随键盘调节的纵向比例缩放。
 *   2026-11-04 键盘调节改为左右 / 底部边界独立：偏好由 kb_scale_w / kb_scale_h 升级为
 *              kb_left / kb_width / kb_height / kb_gap（旧值自动回退成居中左边界，升级不丢尺寸）；
 *              左侧拖手只改左边界、右侧只改右边界，可各自向两侧自由扩展或收窄（不再对称收窄）；
 *              底部拖手改键盘布局的底部外边距（gap），键盘底边随手指上移/下移，顶边固定不动；
 *              四边 / 四角拖手命中范围由 26dp 放宽到 44dp 并取最近边，手指不用瞄准 8px 细条；
 *              平铺模式下卡片列底色改用键盘底色，底部间隙看起来就是键盘自然收短。
 *   2026-11-04 悬浮键盘缩小：手机浮窗卡片基准宽 420dp → 380dp，紧凑比例竖屏 1.0 → 0.85
 *              （横屏仍 0.78、平板仍 1.0），键高 / 间距 / 字号与卡片一起等比收小。
 *   2026-11-05 修复输入被打断后残留字符拼接（26 键双拼 / 九键）：
 *              新增 discardPendingInput()，同时清应用侧预编辑文本、组合区、候选缓存、
 *              librime 内核组合区与九键本地数字缓冲（t9ClearComposition(1)）；
 *              覆写 onFinishInputView / onFinishInput / onWindowHidden / onUnbindInput /
 *              onUpdateSelection 与 onConfigurationChanged，焦点切换、弹窗、应用切换、
 *              收起键盘、系统事件一律立即丢弃未确认输入；
 *              onStartInputView 与 applyKeyboardMode 改走该复位（原来只清本地字段、内核没清），
 *              rimeKey / rimeT9Key 送键前再做一次内核残留兜底，保证下一次按键从空输入开始；
 *              选词 / 上屏 / 切换拼英 / 上屏前后统一走 clearAppComposing() 撤掉应用侧预编辑，
 *              修复九键选词后输入框残留数字串（「43个」）的问题。
 *   2026-11-07 中文输入尚未选候选时按空格 / 换行 / 发送：不再选首个候选、不换行、不发送，
 *              改由 commitRawTypedInput() 把已键入的字符原样上屏（去掉空格 / 撇号等分词符，
 *              只留纯英文键序列），随后复位内核组合区与候选栏。
 *              修复九键中文模式打开数字键盘后按数字仍走 T9：onCharNormal 先判断
 *              keyboard.isNumberPage()，数字页的键帽直接按字符上屏，0-9 全部正确输出，
 *              不再把 2-9 送进 processT9Key 组合区。
 *   2026-11-08 配平态候选栏按 273_218 一比一还原：品牌圆键 → 「已配平」60×31（17sp）→
 *              撤销 61×35 → 确认 61×35（左距 9）→ 空隙 → ⌄；
 *              内边距与常规候选栏完全相同（8/9/7/8），配平前后候选栏坐标与高度零变化；
 *              配平态隐藏左芯片 / 快捷行 / 候选区 / →，退出后自动恢复。
 *   2026-11-08 候选栏最左品牌圆键的 logo 对勾由品牌青 #05BEC1 改为蓝色 #2E73F3（深浅色同色）。
 *   2026-11-09 候选栏快捷按键换客户新图标（24×24 viewport，统一 19×19 显示）：
 *              键盘模式 ic_tool_kb / 深浅色 ic_tool_mode（浅）+ ic_tool_sun（深）/
 *              悬浮键盘 ic_tool_float（层叠键盘）/ 调节键盘 ic_tool_adjust（方框上下箭头）/
 *              剪切板 ic_tool_clip / 切换输入法 ic_tool_ime（左右双向箭头）。
 *   2026-11-09 剪切板面板对齐新配色与键盘高度：面板底色 浅 #E0E0E0 / 深 #252525，
 *              条目按字符键配色（浅 #FFFFFF + 下描边黑 20%，深 #606060 + 下描边纯黑）；
 *              高度改为直接取键盘实际高度，去掉 240dp 下限（键盘调节收窄 / 浮窗紧凑时不再偏高）。
 */
public class JhfImeService extends InputMethodService implements ChemKeyboardView.Listener {

    private static final Set<String> TWO_LETTER_ELEMENTS = new HashSet<>(Arrays.asList(
            "He", "Li", "Be", "Ne", "Na", "Mg", "Al", "Si", "Cl", "Ar", "Ca", "Sc", "Ti",
            "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr",
            "Rb", "Sr", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn",
            "Sb", "Te", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd",
            "Tb", "Dy", "Ho", "Er", "Tm", "Yb", "Lu", "Hf", "Ta", "Re", "Os", "Ir", "Pt",
            "Au", "Hg", "Tl", "Pb", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th", "Pa",
            "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm", "Md", "No", "Lr", "Rf", "Db",
            "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og"));

    private PinyinMatcher matcher;
    private ChemKeyboardView keyboard;
    /** 长按浮层（叠在根布局最上层，键盘负责算位置、这里负责画）。 */
    private KeyPopupView popupView;
    private TextView pageChip, composingView;
    /**
     * 候选栏最左的品牌圆键（2026-11-02，Figma 216-207 / 216-397 普通输入）：
     * 35×35 白（深色 #484848）圆胶囊，内含 20×17 的「化」logo，只在普通键盘模式出现。
     */
    private ImageView brandChip;
    /**
     * 候选栏快捷按键行（2026-11-04，按 Figma 216-207 / 241-265）：
     * 品牌圆键右侧依次是「键盘模式 / 深浅色 / 悬浮键盘 / 切换输入法」四颗 35×35 圆胶囊，
     * 替代键盘底部原来的三颗悬浮岛；打字时让位给候选词，候选清空后重新出现。
     */
    private LinearLayout shortcutRow;
    private ImageView shortcutMode, shortcutTheme, shortcutFloat, shortcutIme;
    /** 2026-11-04 追加两颗快捷键：键盘调节（进编辑态）与剪切板（开剪切板面板）。 */
    private ImageView shortcutAdjust, shortcutClip;
    /** 剪切板面板（2026-11-04，Figma 248-16）：顶替键盘视图，候选栏保留并显示「返回」。 */
    private ClipPanel clipPanel;
    private boolean clipOpen = false;
    /** 键盘调节编辑态浮层（2026-11-04，249-204 / 249-394）与用户保存的几何参数。 */
    private AdjustOverlay adjustOverlay;
    /** 键盘调节保存值（2026-11-04）：左边界占比 / 宽度占比 / 纵向比例 / 底部间隙（dp）。 */
    private float kbLeft = 0f, kbWidth = 1f, kbHeight = 1f, kbGapDp = 0f;
    /** 剪切板历史：最多 50 条、新的在前，持久化在偏好 clip_history（JSON 数组）。 */
    private final List<String> clips = new ArrayList<>();
    private android.content.ClipboardManager clipboardManager;
    private android.content.ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private boolean clipsLoaded = false;
    /** 收起键盘箭头（客户提供的 SVG，随主题色着色）。 */
    private ImageView collapseChip;
    private LinearLayout candBar, candList, candExpanded;
    /** 候选栏与键盘之间的 1dp 分割线（2026-11-03，替换原来的候选栏投影）。 */
    private View candDivider;
    private FrameLayout candContainer;
    private HorizontalScrollView candScroll;
    private boolean expanded = false;
    /**
     * 普通键盘候选栏右侧的「→」芯片（2026-11-01，Figma 216-397）：点它展开候选字界面。
     * 与右侧 ⌄（收起键盘）是两颗不同的芯片，⌄ 的长按（候选展开态）也不变。
     */
    private ImageView candMoreChip;
    private View candMoreFade;
    /**
     * 候选栏右侧功能键（按键b，⌄ 那颗）当前是否处于「有输入」状态（2026-11-02）：
     * 无输入时显示箭头 = 收起键盘；打字时显示 ✕ = 清空候选，点完自动退回箭头并隐藏「→」。
     */
    private boolean candInputActive = false;
    /**
     * 配平态候选栏（2026-11-08，273_218）：进入配平态时按设计稿显示
     * 「已配平 / 撤销 / 确认」三颗元素（已将两者左侧的品牌圆键与右侧 ⌄ 一并显示），
     * 候选栏原有的左芯片 / 快捷按键 / 候选区 / → 全部隐藏；确认或撤销后恢复。
     */
    private LinearLayout balancedRow;
    private TextView balancedLabel, balancedUndo, balancedConfirm;
    /** 配平态用于把 ⌄ 顶到最右的空隙（等宽 weight=1，平时 GONE 不占位）。 */
    private View balancedSpacer;
    private boolean reactBalanced = false;
    /** 无输入状态下按键b 的箭头图标（13×7）。 */
    private int collapseIdleIcon = R.drawable.ic_chevron_down_small;
    /** 按键b 当前实际显示的图标，避免每次重复 setImageResource（0 = 还没设过）。 */
    private int collapseIconShown = 0;
    /** 候选字界面（Figma 216-519）：展开时与键盘互斥显示，高度对齐键盘，卡片总高不变。 */
    private NormalCandPanel candPanel;
    private boolean candPanelOpen = false;
    /** 候选字界面里跨页收集到的候选（页偏移 + 页内下标，上屏时按它翻回对应页）。 */
    private final List<NormalCandPanel.Item> candPanelItems = new ArrayList<>();
    /** 首字母筛选列（由候选首字的声母汇总，后台建表后回填）。 */
    private final List<String> candPanelInitials = new ArrayList<>();
    private boolean initialsLoaded = false;
    private int candPanelTab = NormalCandPanel.TAB_FREQ;
    private final StringBuilder composing = new StringBuilder();
    /** 九键左栏卡当前显示的首音节候选（拼音）与对应数字长度（2026-11-02）。 */
    private final List<String> t9RailPinyin = new ArrayList<>();
    private final List<Integer> t9RailLengths = new ArrayList<>();
    private boolean pinyinMode = true;
    /**
     * 元素模式默认开启自动大小写（2026-10-25 客户要求）：每个元素首字母大写、第二个字母小写。
     * 仍可在「拼｜素」键上长按关闭。
     */
    private boolean autoCase = true;
    /** 点按 Shift 后的一次性大写（输出一个字母后自动清除）。 */
    private boolean shiftOn = false;
    /** 长按 Shift 的按住状态：期间连续输出大写，松手恢复小写。 */
    private boolean shiftHeld = false;
    private List<PinyinMatcher.Candidate> current;
    private String themeId = "cucl2";
    private boolean darkMode = false;
    /** 平板布局模式（2026-10-28）：0 铺满 / 1 左右断开 / 2 浮窗。 */
    private int kbLayout = ChemKeyboardView.LAYOUT_FULL;
    /**
     * 浮窗卡片：整张卡（抓手 + 候选栏 + 键盘）是输入视图里的一个普通子 View，
     * 位置用外边距控制（2026-10-28 二次实现：原来的 PopupWindow 会被系统按 IME
     * 窗口高度裁成 0 高，导致卡片收不到触摸、候选栏不显示）。
     */
    private FrameLayout inputHost, floatCard, gripBar;
    private View gripLineView;
    private LinearLayout cardColumn;
    private boolean floatPositioned = false;
    private int floatX, floatY;
    /**
     * 浮窗卡片高度缓存（2026-11-01）：进入浮窗时卡片上的抓手从 GONE 变 VISIBLE，
     * 若拿上一次布局（平铺、无抓手）的 getHeight() 去算落点，卡片会偏矮 20dp，
     * 整卡落到窗口下沿之外，底部三个芯片被裁掉。这里统一用一次真实测量值。
     */
    private int floatCardH = 0;
    /** 抓手拖动起点（屏幕坐标与起始边距）。 */
    private float dragRawX, dragRawY;
    private int dragStartX, dragStartY;
    private static final float GRIP_DP = 20f;
    /** 设置项变化监听：键盘布局改动需要即时下发（2026-10-27）。 */
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    /** 按键震动反馈（主界面「通用 → 触感反馈」，2026-10-26）。 */
    private boolean haptic = true;
    /** 壁纸变了：「自动」主题的缓存色作废，键盘在屏上时立即换色。 */
    private BroadcastReceiver wallpaperReceiver;

    // ---- 键盘模式（2026-10-31）：化学输入法 / 普通输入法 ----
    /** 当前模式，持久化到偏好 kb_mode（0 化学 / 1 普通）。 */
    private int kbMode = ChemKeyboardView.MODE_CHEM;
    /** 普通输入法的中文拼音引擎：首次输入时才在后台加载，避免拖慢键盘呼出。 */
    private NormalPinyinMatcher normalMatcher;
    private boolean normalMatcherLoading = false;
    /** 普通模式中文输入方案（2026-11-01）：0 全拼 / 1 小鹤双拼（九键已移除），偏好 normal_scheme。 */
    private int normalScheme = RimeSetup.SCHEME_PINYIN;
    /** Rime 内核是否可用（不可用时回退 NormalPinyinMatcher 离线词库）。 */
    private boolean rimeReady = false;
    /** Rime 当前一页候选的文本与注释（候选芯片点击按下标选词）。 */
    private final List<String> rimeCandTexts = new ArrayList<>();
    private final List<String> rimeCandComments = new ArrayList<>();
    private boolean rimeHasNextPage = false, rimeHasPrevPage = false;
    /**
     * 候选栏累计候选（2026-11-02）：Rime 一页只给 6 个，滑到右端就自动再取一页追加，
     * 这里按「候选文本 + 所属页号」累计；点候选时按页号翻回对应页再选。
     */
    private final List<String> candBarTexts = new ArrayList<>();
    private final List<Integer> candBarPages = new ArrayList<>();
    /** 引擎当前停在第几页（0 = 本次输入的第一页）。候选栏连续加载与跨页选词都靠它定位。 */
    private int rimePageIndex = 0;
    /** 候选区铺不满、根本滑不动时补页的 post 是否已排队（避免每次按键重复排队）。 */
    private boolean candFillScheduled = false;
    /** 普通模式候选缓存：同一拼音串不重复全表扫描（旧离线引擎用）。 */
    private String normalCacheKey = "";
    private final List<String> normalCache = new ArrayList<>();
    /** 已应用到 Rime 的方案下标，避免每次呼出键盘都重复切方案（-1 表示未知）。 */
    private int rimeSchemaApplied = -1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String[] THEME_NAMES = {"AgBr", "KMnO₄", "CuCl₂", "Fe₂O₃"};
    private static final String[] THEME_IDS = {"agbr", "kmno4", "cucl2", "fe2o3"};

    @Override
    public void onCreate() {
        super.onCreate();
        matcher = new PinyinMatcher(this);
        wallpaperReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ChemKeyboardView.invalidateAutoColors();
                if (ChemKeyboardView.THEME_AUTO.equals(themeId) && keyboard != null) {
                    loadPrefs();
                    applyTheme();
                }
            }
        };
        registerReceiver(wallpaperReceiver, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        // 设置页改动「键盘布局」后立即生效：输入法进程会常驻，光靠下次重建输入视图不会刷新
        prefs = getSharedPreferences("jhf_settings", Context.MODE_PRIVATE);
        prefListener = (sp, key) -> {
            if ("kb_layout".equals(key)) {
                loadPrefs();
                applyLayoutMode();
            } else if ("kb_mode".equals(key)) {
                // 键盘模式也可能从主界面改（2026-10-31）：与键盘上的切换走同一套复位逻辑
                loadPrefs();
                applyKeyboardMode();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onDestroy() {
        if (prefs != null && prefListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
            prefs = null;
            prefListener = null;
        }
        if (wallpaperReceiver != null) {
            try {
                unregisterReceiver(wallpaperReceiver);
            } catch (Throwable ignored) {
            }
            wallpaperReceiver = null;
        }
        if (clipboardManager != null && clipListener != null) {
            try {
                clipboardManager.removePrimaryClipChangedListener(clipListener);
            } catch (Throwable ignored) {
            }
            clipListener = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // 避免横屏抽取全屏
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences("jhf_settings", Context.MODE_PRIVATE);
        themeId = p.getString("theme", "cucl2");
        boolean follow = p.getBoolean("follow", false);
        darkMode = follow ? systemDark() : p.getBoolean("dark", false);
        haptic = p.getBoolean("haptic", true);
        kbLayout = p.getInt("kb_layout", ChemKeyboardView.LAYOUT_FULL);
        kbMode = p.getInt("kb_mode", ChemKeyboardView.MODE_CHEM);
        // 旧偏好里残留的值统一夹到合法范围（0 全拼 / 1 双拼 / 2 九键）
        normalScheme = Math.max(RimeSetup.SCHEME_PINYIN,
                Math.min(RimeSetup.SCHEME_T9, p.getInt("normal_scheme", RimeSetup.SCHEME_PINYIN)));
        // 手机端不给手动选：横屏自动悬浮、竖屏自动平铺（2026-10-28）；
        // 但工具栏「悬浮键盘」卡点过之后（kb_layout_manual）以用户的选择为准（2026-11-01）
        if (!isTablet() && !p.getBoolean("kb_layout_manual", false)) {
            kbLayout = isLandscape()
                    ? ChemKeyboardView.LAYOUT_FLOAT : ChemKeyboardView.LAYOUT_FULL;
        }
        // 键盘调节（2026-11-04）：左边界 / 宽度 / 高度 / 底部间隙。
        // 旧版本存的是居中收窄的 kb_scale_w，这里回退读它并还原成居中左边界，升级后尺寸不变。
        float oldW = Math.max(0.3f, Math.min(1f, p.getFloat("kb_scale_w", 1f)));
        kbWidth = Math.max(0.3f, Math.min(1f, p.getFloat("kb_width", oldW)));
        kbLeft = Math.max(0f, Math.min(1f - kbWidth, p.getFloat("kb_left", (1f - kbWidth) / 2f)));
        kbHeight = Math.max(0.65f, Math.min(1.35f, p.getFloat("kb_height", p.getFloat("kb_scale_h", 1f))));
        kbGapDp = Math.max(0f, p.getFloat("kb_gap", 0f));
        loadClips(p);
        // 「自动」主题的颜色来自系统壁纸，进程起来后必须解析一次
        ChemKeyboardView.resolveAutoColors(this);
    }

    /** 读出剪切板历史（JSON 数组字符串；只读一次，之后以内存为准）。 */
    private void loadClips(SharedPreferences p) {
        if (clipsLoaded) return;
        clipsLoaded = true;
        clips.clear();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(p.getString("clip_history", "[]"));
            for (int i = 0; i < arr.length() && i < 50; i++) {
                String s = arr.optString(i, null);
                if (s != null && !s.trim().isEmpty()) clips.add(s);
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveClips() {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String s : clips) arr.put(s);
        prefs.edit().putString("clip_history", arr.toString()).apply();
    }

    /** 新复制内容置顶（去重、封顶 50 条），并即时持久化。 */
    private void addClip(String text) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        clips.remove(text);
        clips.add(0, text);
        while (clips.size() > 50) clips.remove(clips.size() - 1);
        saveClips();
        if (clipOpen && clipPanel != null) clipPanel.setItems(new ArrayList<>(clips));
    }

    /** 平板判定（与主界面一致）：最小宽度 ≥ 600dp。 */
    private boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean systemDark() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 把 kbMode 下发到键盘并复位输入状态（2026-10-31）：
     * 组合区、Shift、上屏联想全部清空，候选栏按新页刷新。
     */
    private void applyKeyboardMode() {
        if (keyboard == null) return;
        keyboard.setInputMode(kbMode, normalScheme);
        keyboard.setPinyinMode(pinyinMode);
        // 2026-11-05 改为走统一的打断复位：原来只清本地字段，内核组合区没清，
        // 切模式 / 重新呼出后旧输入串会被内核接着拼到新按键上（见 discardPendingInput）。
        discardPendingInput();
        restylePageChip();
        if (kbMode == ChemKeyboardView.MODE_NORMAL) startRimeIfNeeded();
        applyCandBarMode();
    }

    /**
     * 2026-11-05 输入被打断（焦点切换 / 弹窗 / 应用切换 / 系统事件 / 收起键盘）时的统一丢弃：
     * 未选候选、未上屏的组合区一律作废——键盘侧缓存、内核输入缓冲（含九键本地数字缓冲）
     * 全部清空，保证下一次按键从空输入开始，不会出现「旧拼音 + 新按键」拼接的异常输入。
     * 幂等，可在任意生命周期回调里重复调用。
     */
    private void discardPendingInput() {
        String pending = composing.toString();
        // 应用侧文本一并撤掉：先撤销组合区；有些应用在失焦瞬间已把预编辑提交成普通文本，
        // 这时再核对光标前是否正好是这截未确认字符，是就删掉，保证输入框里不残留拼音
        clearAppComposing(getCurrentInputConnection(), pending);
        composing.setLength(0);
        if (rimeReady) {
            try {
                // 九键的本地数字缓冲在内核侧单独保存，mode=1 才会一起重置
                if (isT9()) RimeEngine.get().t9ClearComposition(1);
                RimeEngine.get().clearComposition();
            } catch (Throwable ignored) {
            }
        }
        clearRimeState();
        normalCacheKey = "";
        normalCache.clear();
        current = null;
        suggestSymbols.clear();
        shiftOn = false;
        shiftHeld = false;
        if (keyboard != null) keyboard.setShiftOn(false);
        if (candPanelOpen) closeCandPanel();
        if (candList != null && composingView != null) updateComposition();
    }

    /**
     * 2026-11-05 撤掉应用侧的预编辑文本（pending 为键盘侧组合区内容）：
     * 组合区还在时 setComposingText("") 就能撤掉；有些应用（小米笔记 webview 等）
     * 在失焦 / 选词瞬间先把预编辑提交成普通文本，这时再核对光标前是否正好是这截
     * 未确认字符，是就删掉——否则选词上屏会变成「43个」这类数字串与新词拼接。
     */
    private void clearAppComposing(InputConnection ic, String pending) {
        if (ic == null || pending == null || pending.isEmpty()) return;
        int len = pending.length();
        try {
            ic.setComposingText("", 1);
            ic.finishComposingText();
            CharSequence before = ic.getTextBeforeCursor(len, 0);
            if (before != null && before.length() == len && pending.equals(before.toString())) {
                ic.deleteSurroundingText(len, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 2026-11-05 兜底：本地组合区已空、内核里却还留着旧输入（个别应用切焦点不会触发
     * 生命周期回调），送键前先把内核清干净，杜绝旧串与新按键拼接。
     */
    private void resetEngineIfStale() {
        if (!rimeReady || composing.length() > 0) return;
        try {
            String input = RimeEngine.get().getInput();
            if (input == null || input.isEmpty()) return;
            if (isT9()) RimeEngine.get().t9ClearComposition(1);
            RimeEngine.get().clearComposition();
            clearRimeState();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 候选栏按模式换装（2026-11-01）：普通键盘多出「→ 全部候选」芯片、左胶囊收窄；
     * 化学模式维持原来的「化｜符 + ⌄」两颗。切模式时若候选字界面开着就先收回去。
     */
    private void applyCandBarMode() {
        boolean normal = kbMode == ChemKeyboardView.MODE_NORMAL;
        // 2026-11-02 候选栏高度按 Figma 216-207 / 216-397：上下内边距 9 / 8，芯片 35 高，
        // 整栏 52dp（原来芯片直接贴栏边、只有 35dp，显得局促）；多出来的下边距同时把候选栏
        // 与下方键区拉开，不挤压键区、也不改键高与行距。
        if (candBar != null) applyCandBarPadding();
        if (candMoreChip != null) restyleCandMore();
        // 「→」与 ⌄/✕ 的显隐、图标由输入状态统一决定（2026-11-02）
        updateCandBarButtons();
        // 2026-11-03 左侧芯片两种键盘统一（188-2117）：字母页是品牌圆键（216-207 样式），
        // 符号 / 数字 / 工具栏页换成「返回」胶囊；化学模式不再单留「化｜符」文字键。
        restylePageChip();
        if (!normal && candPanelOpen) closeCandPanel();
    }

    /**
     * 候选栏内边距（2026-11-08）：「配平前 / 配平后」统一走 216-207 的 左 8 / 上 9 / 右 7 / 下 8，
     * 保证配平操作不改变候选栏高度与纵向坐标。
     */
    private void applyCandBarPadding() {
        if (candBar == null) return;
        float d = getResources().getDisplayMetrics().density;
        candBar.setPadding((int) (8 * d), (int) (9 * d), (int) (7 * d), (int) (8 * d));
    }

    /**
     * 候选栏两颗控制键按「有没有正在打的字」统一切换（2026-11-02）：
     * 无输入 → 只留按键b（⌄ 收起键盘），按键a「→」完全隐藏；
     * 打字中 → 按键a 出现，按键b 换成 ✕（清空候选）。
     * 组合区为空即视为无输入，所有状态变化都收敛到这一个入口，避免图标与功能不同步。
     */
    private void updateCandBarButtons() {
        boolean active = composing.length() > 0;
        candInputActive = active;
        syncShortcutVisibility();
        boolean showMore = kbMode == ChemKeyboardView.MODE_NORMAL && active;
        if (candMoreChip != null) {
            candMoreChip.setVisibility(showMore ? View.VISIBLE : View.GONE);
        }
        if (candMoreFade != null) {
            candMoreFade.setVisibility(showMore ? View.VISIBLE : View.GONE);
        }
        if (collapseChip != null) {
            int icon = active ? R.drawable.ic_clear_small : collapseIdleIcon;
            if (collapseIconShown != icon) {
                collapseChip.setImageResource(icon);
                collapseIconShown = icon;
            }
        }
        if (reactBalanced) hideOriginalCandBarButtons();
    }

    /**
     * 按键b 在「清空候选」状态下的动作（2026-11-02）：清组合区与全部候选，
     * 再把按键b 复位成箭头、隐藏按键a，回到无输入状态。
     */
    private void clearCandidatesFromChip() {
        composing.setLength(0);
        if (rimeReady) {
            try {
                RimeEngine.get().clearComposition();
            } catch (Throwable ignored) {
            }
        }
        clearRimeState();
        normalCacheKey = "";
        normalCache.clear();
        current = null;
        suggestSymbols.clear();
        if (expanded) toggleCandExpanded();
        updateComposition();
    }

    /** 「→」芯片：灰底圆角 5、黑色/白色箭头；前面的渐隐条让滚动的候选自然淡出。 */
    private void restyleCandMore() {
        if (candMoreChip == null) return;
        float d = getResources().getDisplayMetrics().density;
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(darkMode ? 0xFF3A3A3A : 0xFFD9D9D9);
        g.setCornerRadius(5 * d);
        candMoreChip.setBackground(g);
        candMoreChip.setColorFilter(darkMode ? 0xFFFFFFFF : 0xFF000000);
        if (candMoreFade != null) {
            int edge = darkMode ? 0xFF252525 : 0xFFE0E0E0;
            SmoothDrawable fade = new SmoothDrawable();
            fade.setGradient(edge & 0x00FFFFFF, edge);
            candMoreFade.setBackground(fade);
        }
    }

    // ---------- Rime 内核（2026-11-01） ----------

    /**
     * 首次进入普通模式时后台准备 Rime 内核（解包方案 + 首次部署词库）。
     * 内核不可用（非 arm64 / 部署失败）时静默回退到旧的离线词库 NormalPinyinMatcher。
     */
    private void startRimeIfNeeded() {
        if (rimeReady) {
            switchRimeScheme();
            return;
        }
        if (RimeSetup.isReady()) {
            rimeReady = true;
            switchRimeScheme();
            return;
        }
        if (RimeSetup.isStarting()) return;
        RimeSetup.start(this, normalScheme, ready -> {
            rimeReady = ready;
            // 内核刚就绪时方案可能还停在部署默认（如小鹤双拼），这里置 -1 强制再对齐一次，
            // 不能因为“prepare 里已经切过”就跳过，否则 UI 显示全拼、实际按双拼解析。
            rimeSchemaApplied = -1;
            if (!ready && kbMode == ChemKeyboardView.MODE_NORMAL) ensureNormalMatcher();
            if (ready) switchRimeScheme();
            refreshCandidates();
        });
    }

    /** 后台切方案（加载 prism 可能耗时，不阻塞按键线程），完成后再刷候选栏。 */
    private void switchRimeScheme() {
        if (!rimeReady) return;
        final int scheme = normalScheme;
        if (scheme == rimeSchemaApplied) return;
        rimeSchemaApplied = scheme;
        Thread t = new Thread(() -> {
            boolean ok = RimeSetup.switchScheme(scheme);
            if (!ok) rimeSchemaApplied = -1;
            mainHandler.post(this::refreshCandidates);
        }, "rime-scheme");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** 清空缓存的一页候选（Rime 会话已重置时用）。 */
    private void clearRimeState() {
        rimeCandTexts.clear();
        rimeCandComments.clear();
        candBarTexts.clear();
        candBarPages.clear();
        rimePageIndex = 0;
        rimeHasNextPage = false;
        rimeHasPrevPage = false;
        t9RailPinyin.clear();
        t9RailLengths.clear();
        if (keyboard != null) keyboard.setT9RailOptions(null);
    }

    /** 当前是否九键方案（普通输入 · 九键）。 */
    private boolean isT9() {
        return kbMode == ChemKeyboardView.MODE_NORMAL
                && normalScheme == ChemKeyboardView.SCHEME_T9;
    }

    /**
     * 解析内核返回的九键首音节候选（"pinyin|len,..."）：左栏卡最多显示 4 项。
     * 2026-11-02：CyIME 的左侧面板（IDLE 显示标点、INPUT/SELECTION 显示音节）在接化发里
     * 收敛成同一张卡，选项为空时回落到「，。？！」。
     */
    private void applyT9Rail(String options) {
        t9RailPinyin.clear();
        t9RailLengths.clear();
        if (options != null && options.length() > 0) {
            for (String seg : options.split(",")) {
                if (seg.isEmpty()) continue;
                int bar = seg.indexOf('|');
                String py = bar >= 0 ? seg.substring(0, bar) : seg;
                int len = 0;
                if (bar >= 0) {
                    try {
                        len = Integer.parseInt(seg.substring(bar + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (py.isEmpty() || t9RailPinyin.size() >= 4) continue;
                t9RailPinyin.add(py);
                t9RailLengths.add(len);
            }
        }
        if (keyboard != null) {
            keyboard.setT9RailOptions(t9RailPinyin.isEmpty() ? null
                    : new ArrayList<>(t9RailPinyin));
        }
    }

    /** 把当前页候选拷进候选栏累计表（新的一次按键 / 外部刷新时从这一页重新累计）。 */
    private void resetCandBarToCurrentPage() {
        candBarTexts.clear();
        candBarPages.clear();
        for (int i = 0; i < rimeCandTexts.size(); i++) {
            candBarTexts.add(rimeCandTexts.get(i));
            candBarPages.add(rimePageIndex);
        }
    }

    /** 把一次按键的 Rime 结果应用到 UI 状态：提交已上屏文本、回填组合区与候选。 */
    private void applyRimeResult(RimeProcessResult r, InputConnection ic) {
        if (r == null) return;
        if (ic != null && r.committedText.length() > 0) ic.commitText(r.committedText, 1);
        composing.setLength(0);
        composing.append(r.inputText);
        rimeCandTexts.clear();
        rimeCandComments.clear();
        for (RimeCandidate c : r.candidates) {
            if (c == null || c.text == null) continue;
            rimeCandTexts.add(c.text);
            rimeCandComments.add(c.comment == null ? "" : c.comment);
        }
        rimeHasNextPage = r.hasNextPage;
        rimeHasPrevPage = r.hasPrevPage;
        // 每次按键都从新输入的第一页重新累计（上次滑动加载的旧页作废）
        rimePageIndex = 0;
        resetCandBarToCurrentPage();
        // 九键：内核同一次 getProcessResult 里带回左栏首音节候选（2026-11-02）
        if (isT9()) applyT9Rail(r.t9SyllableOptions);
    }

    /** 把一次按键交给 Rime（全拼 / 双拼同一入口，键码为 X11 keysym）。 */
    private void rimeKey(int keycode, InputConnection ic) {
        resetEngineIfStale();
        RimeProcessResult r = null;
        try {
            r = RimeEngine.get().processKey(keycode, 0);
        } catch (Throwable ignored) {
        }
        if (r == null) {
            // 内核掉线：退回离线词库，避免键盘彻底不响应
            rimeReady = false;
            if (ic != null && keycode >= 'a' && keycode <= 'z') {
                composing.append((char) keycode);
                normalCacheKey = "";
                updateComposition();
            }
            return;
        }
        applyRimeResult(r, ic);
        updateComposition();
    }

    /**
     * 九键按键（2026-11-02）：数字 0x32-0x39 / 退格 0xff08 / 回车 0xff0d。
     * 必须走 T9 专用 JNI（processT9Key 会先 T9CancelSentenceScores 再 FlushRimeInput），
     * 普通的 processKey 少了 flush，数字串不会真正进入组合区。
     */
    private void rimeT9Key(int keycode, InputConnection ic) {
        resetEngineIfStale();
        RimeProcessResult r = null;
        try {
            r = RimeEngine.get().processT9Key(keycode);
        } catch (Throwable ignored) {
        }
        if (r == null) {
            rimeReady = false;
            if (ic != null && keycode >= '0' && keycode <= '9') {
                composing.append((char) keycode);
                updateComposition();
            }
            return;
        }
        applyRimeResult(r, ic);
        updateComposition();
    }
    /** 点候选芯片：选第 index 个候选并上屏（index 是候选栏累计表里的全局下标）。 */
    private void selectRimeCandidate(int index) {
        if (isT9()) {
            selectT9Candidate(index);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        String pending = composing.toString();
        String committed = "";
        try {
            RimeEngine e = RimeEngine.get();
            int targetPage = rimePageIndex;
            int local = index;
            if (index >= 0 && index < candBarPages.size()) {
                targetPage = candBarPages.get(index);
                int start = firstIndexInPage(targetPage);
                if (start >= 0) local = index - start;
            }
            while (rimePageIndex < targetPage) {
                if (!rimePageDown(e)) break;
            }
            while (rimePageIndex > targetPage) {
                if (!rimePageUp(e)) break;
            }
            if (e.selectCandidate(local)) committed = e.commit();
        } catch (Throwable ignored) {
        }
        composing.setLength(0);
        clearRimeState();
        // 2026-11-05 选词前先撤掉应用侧预编辑（数字串 / 拼音），
        // 否则某些应用会把组合区当普通文本留在输入框里，上屏后出现「43个」
        if (ic != null) {
            clearAppComposing(ic, pending);
            if (committed.length() > 0) ic.commitText(committed, 1);
        }
        updateComposition();
    }

    /**
     * 九键选词（2026-11-02）：T9 内核按「拼音注释 + 候选文字」还原选中项，
     * 普通的 selectCandidate(下标) 在数字码输入下不可靠。失败才回退到普通路径。
     */
    private void selectT9Candidate(int index) {
        InputConnection ic = getCurrentInputConnection();
        String pending = composing.toString();
        String committed = "";
        try {
            RimeEngine e = RimeEngine.get();
            int targetPage = rimePageIndex;
            int local = index;
            if (index >= 0 && index < candBarPages.size()) {
                targetPage = candBarPages.get(index);
                int start = firstIndexInPage(targetPage);
                if (start >= 0) local = index - start;
            }
            while (rimePageIndex < targetPage) {
                if (!rimePageDown(e)) break;
            }
            while (rimePageIndex > targetPage) {
                if (!rimePageUp(e)) break;
            }
            String text = (local >= 0 && local < rimeCandTexts.size())
                    ? rimeCandTexts.get(local) : "";
            String comment = (local >= 0 && local < rimeCandComments.size())
                    ? rimeCandComments.get(local) : "";
            if (!text.isEmpty()) {
                RimeProcessResult r = e.t9SelectCandidate(comment, text, text.length());
                if (r != null) committed = r.committedText;
            }
            if (committed.isEmpty() && e.selectCandidate(local)) committed = e.commit();
        } catch (Throwable ignored) {
        }
        composing.setLength(0);
        clearRimeState();
        // 2026-11-05 九键上屏同样先撤应用侧数字串，避免「43个」
        if (ic != null) {
            clearAppComposing(ic, pending);
            if (committed.length() > 0) ic.commitText(committed, 1);
        }
        updateComposition();
    }

    /** 某页在累计表里的首个下标（找不到返回 -1）。 */
    private int firstIndexInPage(int page) {
        for (int i = 0; i < candBarPages.size(); i++) {
            if (candBarPages.get(i) == page) return i;
        }
        return -1;
    }

    /** Rime 向下翻一页，同时维护 rimePageIndex（跨页选词与候选栏加载共用）。 */
    private boolean rimePageDown(RimeEngine e) {
        try {
            if (e.pageDown()) {
                rimePageIndex++;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Rime 向上翻一页（已在本输入第一页时不动），同时维护 rimePageIndex。 */
    private boolean rimePageUp(RimeEngine e) {
        if (rimePageIndex <= 0) return false;
        try {
            if (e.pageUp()) {
                rimePageIndex--;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 跟随系统时，系统深浅色一变，输入法要立刻跟着换肤。 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadPrefs();
        // 2026-11-05 横竖屏等配置变化属于系统中断：未确认的组合区丢弃，避免重建后接着旧串
        discardPendingInput();
        if (keyboard != null) {
            applyTheme();
            floatPositioned = false;   // 横竖屏尺寸变了，浮窗按新屏幕重新落位
            applyLayoutMode();
        }
    }

    private boolean isDarkColor(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) < 160;
    }

    private int onAccent() {
        return isDarkColor(accentColor()) ? 0xFFFFFFFF : 0xE6101418;
    }

    /** 当前强调色：圆润风格固定 #2E73F3。 */
    private int accentColor() {
        return UiStyle.ACCENT;
    }

    @Override
    public View onCreateInputView() {
        float d = getResources().getDisplayMetrics().density;
        loadPrefs();

        // 候选栏：[化|符] [候选…] [⌄]（组合区不回显拼音，2026-11-02）
        candBar = new LinearLayout(this);
        candBar.setOrientation(LinearLayout.HORIZONTAL);
        candBar.setGravity(Gravity.CENTER_VERTICAL);
        candBar.setPadding((int) (8 * d), 0, (int) (7 * d), 0);

        pageChip = outlineChip("化｜符", (int) (75 * d));
        pageChip.setOnClickListener(v -> {
            if (clipOpen) {
                closeClipPanel();
                return;
            }
            keyboard.togglePage();
            restylePageChip();
        });
        attachPressFeedback(pageChip);   // 「符号」键按下变淡、抬手复原（2026-11-02）

        // 2026-11-02 Figma 216-207 / 216-397（普通输入 · 浅色 · 圆润）：
        // 候选栏最左是一颗 35×35 白色圆胶囊，内含 20×17 的「化 + 青勾」logo
        // （胶囊内边距 7/8/8/10，正好留出 20×17 的图标区）；这里就是设计稿里那颗圆键。
        brandChip = new ImageView(this);
        brandChip.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brandChip.setPadding((int) (7 * d), (int) (8 * d), (int) (8 * d), (int) (10 * d));
        brandChip.setVisibility(View.GONE);   // 仅普通键盘模式显示，由 applyCandBarMode() 控制
        // 2026-11-04 客户要求：字母页这颗品牌圆键改开工具栏（不再开符号键盘）；
        // 符号页入口保留在长按里，避免化学模式（字母页没有符号键）再也进不去符号页。
        brandChip.setOnClickListener(v -> {
            keyboard.showToolbarPage();
            restylePageChip();
        });
        brandChip.setOnLongClickListener(v -> {
            keyboard.showSymbolPage();
            restylePageChip();
            return true;
        });
        attachPressFeedback(brandChip);
        restyleBrandChip();

        // 2026-11-04 四个快捷按键（Figma 216-207 / 241-265）：替代键盘底部原来的三颗悬浮岛，
        // 排在品牌圆键右侧，各 35×35、彼此间距 10dp；有候选时整行隐藏，候选清空后重新出现。
        shortcutRow = new LinearLayout(this);
        shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
        shortcutRow.setGravity(Gravity.CENTER_VERTICAL);
        // 内边距按新素材留出（2026-11-09 换客户新图标：24×24 viewport，统一按 19×19 显示）
        shortcutMode = shortcutChip(R.drawable.ic_tool_kb, 8, 8, 8, 8);
        shortcutTheme = shortcutChip(R.drawable.ic_tool_mode, 8, 8, 8, 8);
        shortcutFloat = shortcutChip(R.drawable.ic_tool_float, 8, 8, 8, 8);
        shortcutIme = shortcutChip(R.drawable.ic_tool_ime, 8, 8, 8, 8);
        // 键盘调节 / 剪切板（2026-11-09 客户新图标，24×24 viewport，同样按 19×19 显示）
        shortcutAdjust = shortcutChip(R.drawable.ic_tool_adjust, 8, 8, 8, 8);
        shortcutClip = shortcutChip(R.drawable.ic_tool_clip, 8, 8, 8, 8);
        shortcutMode.setOnClickListener(v -> keyboard.showModePopup());
        shortcutTheme.setOnClickListener(v -> onAction(ChemKeyboardView.ACT_DARK));
        shortcutFloat.setOnClickListener(v -> onAction(ChemKeyboardView.ACT_FLOAT));
        shortcutAdjust.setOnClickListener(v -> onAction(ChemKeyboardView.ACT_ADJUST));
        shortcutClip.setOnClickListener(v -> onAction(ChemKeyboardView.ACT_CLIP));
        shortcutIme.setOnClickListener(v -> onAction(ChemKeyboardView.ACT_IME));
        addShortcutChip(shortcutMode);
        addShortcutChip(shortcutTheme);
        addShortcutChip(shortcutFloat);
        addShortcutChip(shortcutAdjust);
        addShortcutChip(shortcutClip);
        addShortcutChip(shortcutIme);
        restyleShortcutChips();

        // 配平态候选栏（2026-11-08 / 273_218）：品牌圆键右侧依次是
        // 「已配平」60×31（左距 10 由品牌键的右边距提供）、「撤销」61×35、「确认」61×35（左距 9）。
        balancedRow = new LinearLayout(this);
        balancedRow.setOrientation(LinearLayout.HORIZONTAL);
        balancedRow.setGravity(Gravity.CENTER_VERTICAL);
        balancedRow.setVisibility(View.GONE);
        float bd = getResources().getDisplayMetrics().density;
        balancedLabel = new TextView(this);
        balancedLabel.setText("已配平");
        balancedLabel.setSingleLine(true);
        balancedLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        balancedLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        balancedLabel.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (60 * bd), (int) (31 * bd)));
        balancedUndo = balancedChip("撤销");
        balancedConfirm = balancedChip("确认");
        // 设计稿 273_218：撤销紧跟「已配平」；确认与撤销之间间距 9dp，两颗都靠左排。
        LinearLayout.LayoutParams confirmLp = (LinearLayout.LayoutParams)
                balancedConfirm.getLayoutParams();
        confirmLp.setMargins((int) (9 * bd), 0, 0, 0);
        balancedConfirm.setLayoutParams(confirmLp);
        balancedUndo.setOnClickListener(v -> {
            if (keyboard != null) keyboard.undoReact();
        });
        balancedConfirm.setOnClickListener(v -> {
            if (keyboard != null) keyboard.confirmEquation();
        });
        balancedRow.addView(balancedLabel);
        balancedRow.addView(balancedUndo);
        balancedRow.addView(balancedConfirm);
        restyleBalancedChips();

        composingView = new TextView(this);
        composingView.setTextSize(14);
        composingView.setSingleLine();
        composingView.setTextColor(darkMode ? 0xFF8A959B : 0xFF8A959B);
        composingView.setVisibility(View.GONE);

        candList = new LinearLayout(this);
        candList.setOrientation(LinearLayout.HORIZONTAL);
        candList.setGravity(Gravity.CENTER_VERTICAL);
        candScroll = new HorizontalScrollView(this);
        candScroll.setHorizontalScrollBarEnabled(false);
        candScroll.setClipToPadding(false);
        // 2026-11-02 候选栏连续滑动：滑到右端 40dp 内就向 Rime 再取一页追加，
        // 不再受「一页 6 个候选」限制；保持原来的横向滚动与芯片样式不变。
        candScroll.setOnScrollChangeListener((v, scrollX, oldScrollX, scrollY, oldScrollY) -> {
            if (rimeReady && !expanded && rimeHasNextPage && nearCandStripEnd()) {
                appendNextRimePage();
            }
        });
        LinearLayout hsvInner = new LinearLayout(this);
        hsvInner.setOrientation(LinearLayout.HORIZONTAL);
        hsvInner.setGravity(Gravity.CENTER_VERTICAL);
        hsvInner.addView(composingView);
        hsvInner.addView(candList);
        candScroll.addView(hsvInner);

        candExpanded = new LinearLayout(this);
        candExpanded.setOrientation(LinearLayout.VERTICAL);
        candExpanded.setVisibility(View.GONE);

        candContainer = new FrameLayout(this);
        LinearLayout.LayoutParams contLp = new LinearLayout.LayoutParams(
                0, (int) (35 * d), 1f);
        // 2026-11-02 去掉上下 9/8dp 的外边距：它把候选行压成一条窄带（候选字被裁、整栏显矮），
        // 现在候选区与左右芯片同高，居中显示。
        contLp.setMargins(0, 0, 0, 0);
        candContainer.setLayoutParams(contLp);
        candScroll.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        candExpanded.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        candContainer.addView(candScroll);
        candContainer.addView(candExpanded);

        // 候选栏右侧 ⌄ = 收起键盘（客户 2026-10-15 明确：底部 ⇌ 才是切换输入法，⌄ 是这个）
        // 187-1873/188-2117 用 13×7 小号箭头
        collapseIdleIcon = R.drawable.ic_chevron_down_small;
        collapseChip = iconChip(collapseIdleIcon, (int) (35 * d));
        // 2026-11-02 按键b：无输入时收起键盘；打字中变成「清空候选」（✕），点完自动复原
        collapseChip.setOnClickListener(v -> {
            if (candInputActive) clearCandidatesFromChip();
            else requestHideSelf(0);
        });
        // 展开/收起候选改挂长按，功能不丢（客户只要求 ⌄ 用作收起键盘，没要求删掉展开态）
        collapseChip.setOnLongClickListener(v -> {
            toggleCandExpanded();
            return true;
        });

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                (int) (75 * d), (int) (35 * d));
        chipLp.setMargins(0, 0, (int) (8 * d), 0);
        pageChip.setLayoutParams(chipLp);
        // 品牌圆键 35×35；与快捷按键/候选区之间按 216-207 的 margin-left 留 10dp
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                (int) (35 * d), (int) (35 * d));
        brandLp.setMargins(0, 0, (int) (10 * d), 0);
        brandChip.setLayoutParams(brandLp);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                (int) (35 * d), (int) (35 * d));
        expLp.setMargins((int) (8 * d), 0, 0, 0);
        collapseChip.setLayoutParams(expLp);

        // 普通键盘候选栏（2026-11-01，Figma 216-397）：
        // [候选区] [渐隐] [→ 全部候选] [⌄ 收起键盘]；→ 只在普通模式出现，点它进候选字界面
        candMoreFade = new View(this);
        // 2026-11-02 候选区紧贴「→」：原来 40dp 的渐隐带把候选挤短了，改成 4px 的固定间距，
        // 候选行几乎铺到「→」左边，又不会与芯片完全贴死（客户要求 3-5px 间距）。
        LinearLayout.LayoutParams fadeLp = new LinearLayout.LayoutParams(
                4, (int) (35 * d));
        candMoreFade.setLayoutParams(fadeLp);
        candMoreFade.setVisibility(View.GONE);

        candMoreChip = new ImageView(this);
        candMoreChip.setImageResource(R.drawable.ic_chevron_right_small);
        candMoreChip.setScaleType(ImageView.ScaleType.CENTER);
        candMoreChip.setVisibility(View.GONE);
        candMoreChip.setOnClickListener(v -> openCandPanel());
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(
                (int) (27 * d), (int) (35 * d));
        moreLp.setMargins(0, 0, (int) (8 * d), 0);
        candMoreChip.setLayoutParams(moreLp);

        // 顺序按 273_218：品牌圆键 → 已配平 / 撤销 / 确认 → 左芯片 → 快捷行 → 候选区 → ⌄
        candBar.addView(brandChip);
        candBar.addView(balancedRow);
        candBar.addView(pageChip);
        candBar.addView(shortcutRow);
        candBar.addView(candContainer);
        candBar.addView(candMoreFade);
        candBar.addView(candMoreChip);
        // 配平态的空隙：把 ⌄ 顶到候选栏最右（设计稿右侧留 7dp），平时 GONE 不占位
        balancedSpacer = new View(this);
        balancedSpacer.setVisibility(View.GONE);
        candBar.addView(balancedSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        candBar.addView(collapseChip);

        // 候选栏与键盘之间用 1dp 分割线（2026-11-03）：不再用投影，深浅色各一条同款细线
        candDivider = new View(this);
        restyleCandDivider();

        keyboard = new ChemKeyboardView(this, this);
        keyboard.setPinyinMode(pinyinMode);
        keyboard.setAutoCase(autoCase);
        keyboard.setLayoutMode(kbLayout);
        keyboard.setInputMode(kbMode, normalScheme);
        keyboard.setCompactScale(keyboardCompactScale());   // 只有浮窗用紧凑尺寸
        keyboard.setUserBox(kbLeft, kbWidth, kbHeight);   // 键盘调节保存的几何（2026-11-04）

        // ---- 浮窗抓手（2026-10-28）：整张卡片顶部 20dp 横条，按住可上下左右拖动整卡 ----
        gripBar = new FrameLayout(this);
        gripLineView = new View(this);
        gripBar.addView(gripLineView, new FrameLayout.LayoutParams(
                (int) (44 * d), (int) (4 * d), Gravity.CENTER));
        gripBar.setOnTouchListener((v, ev) -> handleGripTouch(v, ev));

        // 一张卡片 = 抓手 + 候选栏 + 键盘；浮窗时整张卡片在输入视图里按边距定位，候选栏随卡片移动
        cardColumn = new LinearLayout(this);
        cardColumn.setOrientation(LinearLayout.VERTICAL);
        cardColumn.addView(gripBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (GRIP_DP * d)));
        cardColumn.addView(candBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        cardColumn.addView(candDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) d)));
        // 剪切板与键盘共用同一槽位（2026-11-09）：槽位高度由键盘撑起，剪切板 MATCH_PARENT
        // 铺满整个槽位，切换只改可见性——高度天然完全一致，也跟随键盘调节实时变化。
        FrameLayout kbSlot = new FrameLayout(this);
        kbSlot.addView(keyboard, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        clipPanel = new ClipPanel(this, clipPanelListener());
        clipPanel.setVisibility(View.GONE);
        // 覆盖在键盘之上时消费触摸，避免空白处穿透到下面的键盘
        clipPanel.setClickable(true);
        kbSlot.addView(clipPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cardColumn.addView(kbSlot, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        // 候选字界面与键盘互斥：展开时键盘 GONE、面板高度 = 键盘高度，卡片总高不跳
        candPanel = new NormalCandPanel(this, panelListener());
        candPanel.setVisibility(View.GONE);
        cardColumn.addView(candPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0));

        // 卡片外层再套一层：长按浮层要盖在键区上方（键盘自己画不出界外）
        floatCard = new FrameLayout(this);
        floatCard.addView(cardColumn, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        popupView = new KeyPopupView(this);
        // 不传 LayoutParams：它自己在显示时按浮层区域设置宽高与边距。
        floatCard.addView(popupView);

        // 键盘调节编辑态浮层（2026-11-04，249-204 / 249-394）：盖在整张卡片之上。
        // 高度由浮层在 onMeasure 里按卡片内容高现算（不占用卡片测量，避免撑高整屏）
        adjustOverlay = new AdjustOverlay(this, new AdjustOverlay.Callback() {
            @Override
            public void onPreview(float left, float width, float h, float gapPx) {
                // 拖动即实时重排键盘：左右边界 / 键高 / 底部间隙全部跟手，输入法底边不动
                applyUserBox(left, width, h, gapPx);
                // 浮层不必再单独 requestLayout（2026-11-05）：键盘重排会向上冒泡到 floatCard，
                // 同一次遍历里浮层按新的卡片高重新测量；额外再请求一次只会每帧多走一趟布局，
                // 表现为拖动时的抽搐 / 卡顿。
            }

            @Override
            public void onSave(float left, float width, float h, float gapPx) {
                kbLeft = left;
                kbWidth = width;
                kbHeight = h;
                kbGapDp = gapPx / getResources().getDisplayMetrics().density;
                if (prefs != null) {
                    prefs.edit().putFloat("kb_left", left).putFloat("kb_width", width)
                            .putFloat("kb_height", h).putFloat("kb_gap", kbGapDp).apply();
                }
                applyUserBox(kbLeft, kbWidth, kbHeight,
                        kbGapDp * getResources().getDisplayMetrics().density);
                hideAdjustOverlay();
            }

            @Override
            public void onCancel() {
                // 放弃实时预览，键盘恢复为已保存的几何
                applyUserBox(kbLeft, kbWidth, kbHeight,
                        kbGapDp * getResources().getDisplayMetrics().density);
                hideAdjustOverlay();
            }
        }, new AdjustOverlay.FrameSource() {
            @Override
            public void fillFrame(android.graphics.RectF out) {
                fillAdjustFrame(out);
            }

            @Override
            public int contentHeight() {
                return cardContentHeight();
            }

            @Override
            public float bottomGap() {
                // 键盘底与卡片底的间距 = 键盘布局底部外边距（底部拖手拖出来的空白）
                if (keyboard == null) return 0f;
                ViewGroup.LayoutParams lp = keyboard.getLayoutParams();
                return lp instanceof ViewGroup.MarginLayoutParams
                        ? ((ViewGroup.MarginLayoutParams) lp).bottomMargin : 0f;
            }
        });
        adjustOverlay.setVisibility(View.GONE);
        // 宽度跟随卡片；高度由浮层自己按卡片内容高度现算（不参与卡片撑高，见 AdjustOverlay.onMeasure）
        floatCard.addView(adjustOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 输入视图宿主：铺满/断开时高度正好一张卡片；浮窗时铺满整屏当画布（触摸区域收窄见 onComputeInsets）
        inputHost = new FrameLayout(this);
        applyTheme();
        applyCandBarMode();
        applyLayoutMode();
        refreshCandidates();
        initClipboard();
        return inputHost;
    }

    /**
     * 卡片底色（浮窗时的卡片本身；直角，跟随深浅色）。
     * 2026-11-09 新配色：浅色浮窗卡片跟随键盘底板 #E0E0E0，深色 #252525。
     */
    private SmoothDrawable cardBackground() {
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(darkMode ? 0xFF252525 : 0xFFE0E0E0);
        g.setCornerRadius(18 * getResources().getDisplayMetrics().density);
        return g;
    }

    /** 候选栏/键盘分割线颜色（浅 #D9D9D9 / 深 #3A3A3A），随深浅色刷新。 */
    private void restyleCandDivider() {
        if (candDivider == null) return;
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(darkMode ? 0xFF3A3A3A : 0xFFD9D9D9);
        candDivider.setBackground(g);
    }

    /**
     * 卡片列底色（2026-11-04）：浮窗用卡片圆角底色；平铺用键盘底色，
     * 这样键盘调节把底边拖上来时，键盘下方那段间隙看起来就是键盘自然收短，不会露白。
     */
    private void restyleCardColumnBg() {
        if (cardColumn == null) return;
        SmoothDrawable g = new SmoothDrawable();
        if (kbLayout == ChemKeyboardView.LAYOUT_FLOAT) {
            cardColumn.setBackground(cardBackground());
            return;
        }
        g.setColor(keyboard != null ? keyboard.getKeyboardBg()
                : (darkMode ? 0xFF252525 : 0xFFE0E0E0));
        g.setCornerRadius(0);
        cardColumn.setBackground(g);
    }

    /** 抓手横杠颜色跟随深浅色。 */
    private void restyleGripLine() {
        if (gripLineView == null) return;
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(darkMode ? 0x59FFFFFF : 0x59000000);
        g.setCornerRadius(2 * getResources().getDisplayMetrics().density);
        gripLineView.setBackground(g);
    }

    /**
     * 按当前模式装配卡片（2026-10-28 二次实现）：
     * 铺满 / 左右断开 → 输入视图正好一张卡片高，卡片铺满宽度；
     * 浮窗 → 输入视图铺满整屏当画布，卡片在其中按边距定位、可上下左右拖动。
     */
    private void applyLayoutMode() {
        if (keyboard == null || inputHost == null || floatCard == null) return;
        keyboard.setLayoutMode(kbLayout);
        // 横竖屏切换后同步紧凑比例（浮窗 0.85/0.78，平铺与分体 1.0）
        keyboard.setCompactScale(keyboardCompactScale());
        applyUserBox(kbLeft, kbWidth, kbHeight,
                kbGapDp * getResources().getDisplayMetrics().density);
        boolean floatMode = kbLayout == ChemKeyboardView.LAYOUT_FLOAT;
        if (gripBar != null) gripBar.setVisibility(floatMode ? View.VISIBLE : View.GONE);
        restyleCardColumnBg();
        restyleGripLine();
        if (floatMode) {
            inputHost.setMinimumHeight(displaySize().y);
            if (floatCard.getParent() != inputHost) {
                inputHost.addView(floatCard, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (!floatPositioned) {
                // 卡片高度要等它真正布局完才准（未布局时手量会偏小，卡片会掉到屏幕外）
                inputHost.post(() -> {
                    if (kbLayout != ChemKeyboardView.LAYOUT_FLOAT || floatPositioned) return;
                    setFloatDefaultPosition();
                    floatPositioned = true;
                    placeFloatCard();
                });
            } else {
                placeFloatCard();
            }
        } else {
            inputHost.setMinimumHeight(1);
            floatCardH = 0;   // 平铺时抓手隐藏，下一次进浮窗要重新量卡片高度（2026-11-01）
            if (floatCard.getParent() != inputHost) {
                inputHost.addView(floatCard, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) floatCard.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.leftMargin = 0;
            lp.topMargin = 0;
            floatCard.setLayoutParams(lp);
            inputHost.requestLayout();
        }
    }

    /** 浮窗卡片宽度：手机 380dp（比平板常用的 420dp 更紧凑）；再按紧凑比例缩小。 */
    private int floatCardWidth() {
        float d = getResources().getDisplayMetrics().density;
        int sw = displaySize().x;
        float baseDp = isTablet() ? 420f : 380f;
        int w = (int) (baseDp * d * floatCompactScale());
        return Math.min(w, sw - (int) (16 * d));
    }

    /** 只有浮窗才按紧凑比例缩放；平铺 / 左右分体保持 1.0，按键与间距保持设计标准值。 */
    private float keyboardCompactScale() {
        return kbLayout == ChemKeyboardView.LAYOUT_FLOAT ? floatCompactScale() : 1f;
    }

    /**
     * 手机浮窗的尺寸比例（2026-11-04 缩小）：竖屏 0.85、横屏 0.78，
     * 键高 / 间距 / 字号与卡片宽度一起等比收小，浮窗不再几乎占满整屏；平板保持 1.0。
     */
    private float floatCompactScale() {
        if (isTablet()) return 1f;
        return isLandscape() ? 0.78f : 0.85f;
    }

    /** 屏幕真实尺寸（横屏时宽高互换，不受输入法 Resources 的陈旧配置影响）。 */
    private Point displaySize() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect b = wm.getCurrentWindowMetrics().getBounds();
            return new Point(b.width(), b.height());
        }
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    /** 按给定宽度量一次卡片高度（还没布局时用它决定默认落点与拖动边界）。 */
    private int measureCardHeight(int cardW) {
        if (floatCard == null) return 0;
        floatCard.measure(View.MeasureSpec.makeMeasureSpec(cardW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return floatCard.getMeasuredHeight();
    }

    /** 卡片高度（优先用真实测量值，避免拿到上一次布局的旧高度，2026-11-01）。 */
    private int cardHeight(int cardW) {
        if (floatCardH <= 0) floatCardH = measureCardHeight(cardW);
        return floatCardH;
    }

    /**
     * 浮窗可用高度（2026-11-01）：用输入视图自身的实际高度，而不是整屏高度。
     * 输入法窗口从状态栏下方开始，拿 displaySize().y 会让卡片按整屏算落点，
     * 整卡实际下沉约 66dp，底部「选择键盘模式 / 工具栏 / 切换输入法」三芯片被窗口下沿裁掉。
     */
    private int hostHeight() {
        int h = inputHost != null ? inputHost.getHeight() : 0;
        return h > 0 ? h : displaySize().y;
    }

    /** 首次进入浮窗：水平居中、贴底上方 24dp。 */
    private void setFloatDefaultPosition() {
        int cardW = floatCardWidth();
        floatCardH = 0;
        int cardH = cardHeight(cardW);
        Point size = displaySize();
        floatX = (size.x - cardW) / 2;
        floatY = hostHeight() - cardH - (int) (24 * getResources().getDisplayMetrics().density);
    }

    /**
     * 把 floatX / floatY 落到卡片外边距上；整卡（抓手 + 候选栏 + 键盘）一起移动。
     *
     * @param refreshTouchRegion 是否顺手刷新可触摸区域。拖动过程中不能刷：
     *                           输入法窗口一旦重算 insets 会重新布局，进行中的手势会被系统取消
     *                           （表现为拖一下就不跟手），所以拖动时只挪位置，松手才刷新。
     */
    private void placeFloatCard(boolean refreshTouchRegion) {
        if (floatCard == null || inputHost == null) return;
        int cardW = floatCardWidth();
        int cardH = cardHeight(cardW);
        clampFloat(cardW, cardH);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) floatCard.getLayoutParams();
        if (lp == null) return;
        lp.width = cardW;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.leftMargin = floatX;
        lp.topMargin = floatY;
        floatCard.setLayoutParams(lp);
        if (refreshTouchRegion) inputHost.requestLayout();
    }

    private void placeFloatCard() {
        placeFloatCard(true);
    }

    /** 把卡片位置限制在输入视图内（上下左右都不能拖出屏）。 */
    private void clampFloat(int cardW, int cardH) {
        Point size = displaySize();
        floatX = Math.max(0, Math.min(floatX, size.x - cardW));
        floatY = Math.max(0, Math.min(floatY, Math.max(0, hostHeight() - cardH)));
    }

    /** 抓手拖动：整卡（含候选栏）跟着手指上下左右走。 */
    private boolean handleGripTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragRawX = e.getRawX();
                dragRawY = e.getRawY();
                dragStartX = floatX;
                dragStartY = floatY;
                return true;
            case MotionEvent.ACTION_MOVE:
                floatX = dragStartX + Math.round(e.getRawX() - dragRawX);
                floatY = dragStartY + Math.round(e.getRawY() - dragRawY);
                placeFloatCard(false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 松手后再刷新触摸区域，避免拖动中途被窗口重布局打断
                placeFloatCard(true);
                return true;
            default:
                return true;
        }
    }

    /**
     * 浮窗不挤压应用、只让卡片本身接收触摸（2026-10-28）。
     * 输入视图在浮窗模式下铺满整屏，系统默认会把整屏都当成键盘热区，
     * 这里把可见区/内容区都顶到屏幕底部（应用按“没有键盘”排版），
     * 可触摸区域收窄成卡片矩形，卡片外的点击照常到达应用。
     */
    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (kbLayout != ChemKeyboardView.LAYOUT_FLOAT || floatCard == null
                || floatCard.getParent() != inputHost) return;
        Point size = displaySize();
        outInsets.contentTopInsets = size.y;
        outInsets.visibleTopInsets = size.y;
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        int w = floatCard.getWidth() > 0 ? floatCard.getWidth() : floatCardWidth();
        int h = cardHeight(w);
        outInsets.touchableRegion.set(floatX, floatY, floatX + w, floatY + h);
    }

    @Override
    public void onLongPressPopup(List<String> opts, android.graphics.RectF box,
                                 int hover, float ax, float ay) {
        if (popupView != null) popupView.update(opts, box, hover, ax, ay);
    }

    private TextView outlineChip(String text, int width) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        // 2026-10-26 候选栏「化｜符」芯片字号与空格键对齐：键盘 canvas 的字号是 18 * density px
        // （功能键 STYLE_SPECIAL），换算成 dp 即 18dp；这里用 COMPLEX_UNIT_DIP 而非 sp，
        // 才不会随系统字体缩放变大、与键盘字不同步。
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setMaxLines(1);
        restyleOutline(tv, width);
        return tv;
    }

    /** 图标芯片：与文字芯片同样的直角描边框，图标按主题色着色。 */
    private ImageView iconChip(int res, int width) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        restyleIconChip(iv, width);
        return iv;
    }

    private void restyleIconChip(ImageView iv, int width) {
        if (iv == null) return;
        float d = getResources().getDisplayMetrics().density;
        SmoothDrawable g = new SmoothDrawable();
        // 177-751/1655：白 / 深灰 #484848 胶囊、圆角 23，无描边，图标用普通字色
        g.setColor(darkMode ? 0xFF484848 : Color.WHITE);
        g.setCornerRadius(23 * d);
        iv.setColorFilter(darkMode ? 0xFFFFFFFF : 0xFF000000);
        iv.setBackground(g);
        if (width > 0) iv.setMinimumWidth(width);
    }

    /**
     * 品牌圆键换装（2026-11-02，Figma 216-207 / 216-397）：白 / #484848 胶囊、圆角 23，
     * 20×17 logo 随深浅色着黑 / 白。
     */
    private void restyleBrandChip() {
        if (brandChip == null) return;
        float d = getResources().getDisplayMetrics().density;
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(darkMode ? 0xFF484848 : Color.WHITE);
        g.setCornerRadius(23 * d);
        brandChip.setBackground(g);
        // 设计稿的 logo 是「化」字 + 对勾两层叠出来的，只用 ic_logo 会丢掉对勾；
        // 深色下字层换白色版本。对勾颜色 2026-11-08 按客户要求由品牌青 #05BEC1 改为蓝色 #2E73F3。
        Drawable word = getDrawable(darkMode ? R.drawable.ic_logo_dark : R.drawable.ic_logo);
        Drawable check = getDrawable(R.drawable.ic_logo_check);
        check.mutate().setTint(0xFF2E73F3);
        brandChip.setImageDrawable(new LayerDrawable(new Drawable[]{word, check}));
        brandChip.setColorFilter(null);
    }

    /** 候选栏快捷按键：35×35 圆胶囊，内边距沿用设计稿（左/上/右/下，dp）。 */
    private ImageView shortcutChip(int res, int l, int t, int r, int b) {
        float d = getResources().getDisplayMetrics().density;
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding((int) (l * d), (int) (t * d), (int) (r * d), (int) (b * d));
        attachPressFeedback(iv);
        return iv;
    }

    private void addShortcutChip(ImageView iv) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (35 * d), (int) (35 * d));
        lp.setMargins(0, 0, (int) (10 * d), 0);
        shortcutRow.addView(iv, lp);
    }

    /** 配平态候选栏胶囊：撤销 / 确认（61×35，圆角 17.5dp，17sp），外边距由调用处按设计稿设置。 */
    private TextView balancedChip(String text) {
        float d = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (61 * d), (int) (35 * d));
        lp.setMargins(0, 0, 0, 0);
        tv.setLayoutParams(lp);
        attachPressFeedback(tv);
        return tv;
    }

    /** 配平态元素配色：撤销白 / #484848 底黑 / 白字，确认固定品牌蓝 #2E73F3 底白字，
     *  「已配平」17sp 无底色，浅色黑字 / 深色白字（273_218）。 */
    private void restyleBalancedChips() {
        float d = getResources().getDisplayMetrics().density;
        if (balancedLabel != null) {
            balancedLabel.setTextColor(darkMode ? 0xFFFFFFFF : 0xFF000000);
        }
        if (balancedUndo != null) {
            SmoothDrawable g = new SmoothDrawable();
            g.setColor(darkMode ? 0xFF484848 : Color.WHITE);
            g.setCornerRadius(17.5f * d);
            balancedUndo.setBackground(g);
            balancedUndo.setTextColor(darkMode ? 0xFFFFFFFF : 0xFF000000);
        }
        if (balancedConfirm != null) {
            SmoothDrawable g = new SmoothDrawable();
            g.setColor(0xFF2E73F3);
            g.setCornerRadius(17.5f * d);
            balancedConfirm.setBackground(g);
            balancedConfirm.setTextColor(0xFFFFFFFF);
        }
    }

    /**
     * 配平态与普通输入态切换候选栏按钮（2026-11-08，273_218）：
     * 配平态按设计稿显示品牌圆键 + 「已配平 / 撤销 / 确认」+ 右侧 ⌄，
     * 隐藏左芯片、快捷行、候选区与 →；确认或撤销回到编辑态后恢复全部按钮与候选区。
     */
    private void syncReactionButtons() {
        if (balancedRow == null) return;
        if (reactBalanced) {
            balancedRow.setVisibility(View.VISIBLE);
            if (balancedSpacer != null) balancedSpacer.setVisibility(View.VISIBLE);
            applyCandBarPadding();
            restyleBalancedChips();
            hideOriginalCandBarButtons();
            return;
        }
        balancedRow.setVisibility(View.GONE);
        if (balancedSpacer != null) balancedSpacer.setVisibility(View.GONE);
        applyCandBarPadding();
        if (candContainer != null) candContainer.setVisibility(View.VISIBLE);
        if (collapseChip != null) collapseChip.setVisibility(View.VISIBLE);
        if (candMoreFade != null) candMoreFade.setVisibility(View.GONE);
        if (candMoreChip != null) candMoreChip.setVisibility(View.GONE);
        restylePageChip();
        updateCandBarButtons();
    }

    /** 配平态隐藏候选栏原有的左芯片 / 快捷行 / 候选区 / →，品牌圆键与 ⌄ 按设计稿保留。 */
    private void hideOriginalCandBarButtons() {
        View[] hidden = {pageChip, shortcutRow, candContainer, candMoreFade, candMoreChip};
        for (View v : hidden) {
            if (v != null) v.setVisibility(View.GONE);
        }
        if (brandChip != null) brandChip.setVisibility(View.VISIBLE);
        if (collapseChip != null) collapseChip.setVisibility(View.VISIBLE);
    }

    /** 快捷按键配色（2026-11-04）：浅色白底黑图标、深色 #484848 底白图标，圆角 23。 */
    private void restyleShortcutChips() {
        if (shortcutRow == null) return;
        float d = getResources().getDisplayMetrics().density;
        ImageView[] chips = {shortcutMode, shortcutTheme, shortcutFloat, shortcutIme,
                shortcutAdjust, shortcutClip};
        for (ImageView iv : chips) {
            if (iv == null) continue;
            SmoothDrawable g = new SmoothDrawable();
            g.setColor(darkMode ? 0xFF484848 : Color.WHITE);
            g.setCornerRadius(23 * d);
            iv.setColorFilter(darkMode ? 0xFFFFFFFF : 0xFF000000);
            iv.setBackground(g);
        }
        // 深浅色切换图标（2026-11-09 客户新图标）：浅色显示月牙 ic_tool_mode，深色显示太阳 ic_tool_sun，
        // 两者同为 24×24 viewport，统一 8 内边距即 19×19 显示
        if (shortcutTheme != null) {
            boolean dark = darkMode;
            shortcutTheme.setImageResource(dark ? R.drawable.ic_tool_sun : R.drawable.ic_tool_mode);
            int pad = (int) (8 * d);
            shortcutTheme.setPadding(pad, pad, pad, pad);
        }
    }

    /** 快捷按键行显隐：字母页且没有正在打的字时显示，其余情况（有候选 / 符号页等「返回」态）隐藏。 */
    private void syncShortcutVisibility() {
        if (shortcutRow == null) return;
        boolean backPage = keyboard != null && keyboard.isBackPage();
        shortcutRow.setVisibility(!backPage && !candInputActive && !clipOpen
                ? View.VISIBLE : View.GONE);
    }

    /** 按键按压反馈（2026-11-02）：按下变淡、抬手复原，不改常态外观，也不吞掉点击事件。 */
    private void attachPressFeedback(View v) {
        if (v == null) return;
        v.setOnTouchListener((vv, ev) -> {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) vv.setAlpha(0.62f);
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) vv.setAlpha(1f);
            return false;
        });
    }

    private void restyleOutline(TextView tv, int width) {
        float d = getResources().getDisplayMetrics().density;
        SmoothDrawable g = new SmoothDrawable();
        // 177-751/1655：白 / #484848 胶囊、圆角 25、字 17dp，无描边
        g.setColor(darkMode ? 0xFF484848 : Color.WHITE);
        g.setCornerRadius(25 * d);
        tv.setTextColor(darkMode ? 0xFFFFFFFF : 0xFF000000);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        tv.setBackground(g);
        tv.setMinimumWidth(width);
    }

    private void restylePageChip() {
        restyleBrandChip();   // 深浅色/风格变化时品牌圆键一起刷新（2026-11-02）
        if (pageChip == null) return;
        boolean backPage = clipOpen || (keyboard != null && keyboard.isBackPage());
        float d = getResources().getDisplayMetrics().density;
        SmoothDrawable g = new SmoothDrawable();
        // 187-1873/188-2117：白 / #484848 胶囊、圆角 25，无描边；
        // 「返回」（符号/数字/工具栏页）蓝字 #2E73F3，字母页「符号」黑字（177-751）
        g.setColor(darkMode ? 0xFF484848 : Color.WHITE);
        g.setCornerRadius(25 * d);
        pageChip.setTextColor(darkMode ? 0xFFFFFFFF
                : (backPage ? 0xFF2E73F3 : 0xFF000000));
        pageChip.setBackground(g);
        // 2026-11-03（188-2117）：符号 / 数字 / 工具栏页左上统一「返回」75×35 胶囊，
        // 化学与普通两种键盘一致；字母页不再显示这颗文字键，改为圆形品牌键（216-207）。
        pageChip.setText(backPage ? "返回" : "符号");
        pageChip.setVisibility(backPage ? View.VISIBLE : View.GONE);
        if (brandChip != null) {
            brandChip.setVisibility(backPage ? View.GONE : View.VISIBLE);
        }
        syncShortcutVisibility();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) pageChip.getLayoutParams();
        if (lp != null && lp.width != (int) (75 * d)) {
            lp.width = (int) (75 * d);   // 设计稿：53 文字 + 左右 11 = 75
            pageChip.setLayoutParams(lp);
        }
        // 配平态：原有按钮一律让位给「撤销 / 确认」，避免重复控件
        if (reactBalanced) hideOriginalCandBarButtons();
    }

    /**
     * 候选「展开态」开关（98dp / 每行 5 个）。
     * 客户 2026-10-15 要求 ⌄ 芯片做收起键盘，于是这里改挂长按；单击行为与芯片外观都不变。
     */
    private void toggleCandExpanded() {
        float d = getResources().getDisplayMetrics().density;
        expanded = !expanded;
        candScroll.setVisibility(expanded ? View.GONE : View.VISIBLE);
        candExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) candContainer.getLayoutParams();
        lp.height = (int) ((expanded ? 98 : 35) * d);
        candContainer.setLayoutParams(lp);
        refreshCandidates();
        toast(expanded ? "候选已展开（长按 ⌄ 收起）" : "候选已收起");
    }

    private void applyTheme() {
        keyboard.setThemeColors(themeId, darkMode);
        keyboard.setHapticEnabled(haptic);
        // 2026-11-02 长按气泡按 224-12 / 177-1655：蓝格固定 #2E73F3（与设计稿一致）
        if (popupView != null) {
            popupView.setTheme(darkMode, UiStyle.ACCENT);
        }
        restylePageChip();
        restyleShortcutChips();
        restyleBalancedChips();
        restyleIconChip(collapseChip, 0);
        composingView.setTextColor(darkMode ? 0x8AFFFFFF : 0xFF8A959B);
        // 候选栏与键盘同底色（2026-11-09 新配色：浅 #E0E0E0 / 深 #252525），无圆角
        SmoothDrawable barBg = new SmoothDrawable();
        barBg.setColor(darkMode ? 0xFF252525 : 0xFFE0E0E0);
        barBg.setCornerRadius(0);
        candBar.setBackground(barBg);
        restyleCandDivider();
        restyleCandMore();
        if (candPanel != null) {
            candPanel.setDark(darkMode);
            candPanel.setAccent(UiStyle.ACCENT);
        }
        // 候选栏中间不再画输入框底（设计稿只有左右两个胶囊）
        SmoothDrawable ring = new SmoothDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(0);
        candContainer.setBackground(ring);
        // 浮窗卡片底色与抓手颜色跟随深浅色
        restyleGripLine();
        restyleCardColumnBg();
        // 键盘窗口的导航栏跟随键盘底色（2026-11-01 沉浸式）
        applyImeWindowChrome();
    }

    /**
     * 输入法窗口沉浸式（2026-11-01）：导航栏（含手势条区域）填成键盘底色，并按深浅色切换
     * 图标明暗；Android 10+ 关闭系统「强制对比度」遮罩——那层半透明黑纱就是键盘下方的黑条。
     */
    private void applyImeWindowChrome() {
        try {
            // InputMethodService.getWindow() 返回 SoftInputWindow（Dialog），真正的 Window 在它里面
            android.app.Dialog dlg = getWindow();
            Window win = dlg == null ? null : dlg.getWindow();
            if (win == null) return;
            int bg = darkMode ? 0xFF252525 : 0xFFE0E0E0;
            win.setNavigationBarColor(bg);
            if (Build.VERSION.SDK_INT >= 28) win.setNavigationBarDividerColor(bg);
            if (Build.VERSION.SDK_INT >= 29) win.setNavigationBarContrastEnforced(false);
            View decor = win.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (darkMode) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        loadPrefs();
        // 2026-11-05 新一次输入一律从空状态开始：上一次会话可能已被焦点切换 / 弹窗 /
        // 应用切换打断，内核组合区与九键数字缓冲还留着旧字符，必须先清掉
        discardPendingInput();
        if (keyboard != null) {
            applyTheme();
            // 候选字界面是「与键盘互斥」的一次性展开：换输入框时一律先收回键盘
            closeCandPanel();
            // 键盘模式偏好可能在主界面改过：每次呼出都重新下发并复位组合区
            applyKeyboardMode();
            // 2026-10-29 每次呼出都回到默认坐标：拖动过的浮窗位置不跨输入框保留
            floatPositioned = false;
            applyLayoutMode();
        }
    }

    /**
     * 2026-11-05 输入被打断的几条系统回调，统一丢弃未确认的组合区：
     * onFinishInputView = 键盘视图收起（⌄ 收起 / 返回键 / 焦点移走 / 应用切走 / 系统事件）；
     * onFinishInput = 输入会话结束（换输入框）；onWindowHidden / onUnbindInput = 窗口隐藏与解绑。
     * 不这样做的话，内核里的旧输入串会在下一次按键时被接着拼接。
     */
    @Override
    public void onFinishInputView(boolean finishingInput) {
        discardPendingInput();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        discardPendingInput();
        super.onFinishInput();
    }

    @Override
    public void onWindowHidden() {
        discardPendingInput();
        super.onWindowHidden();
    }

    @Override
    public void onUnbindInput() {
        discardPendingInput();
        super.onUnbindInput();
    }

    /**
     * 2026-11-05 光标被移出组合区（用户点到别处、应用自己改了选区）视作输入被打断：
     * 丢弃未确认字符，避免旧拼音继续拼到新按键上。
     * 只在应用确实上报了组合区时才判断——不支持组合区的输入框（candidatesStart < 0）不误清。
     */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        if (composing.length() == 0) return;
        if (candidatesStart < 0 || candidatesEnd <= candidatesStart) return;
        if (newSelStart < candidatesStart || newSelStart > candidatesEnd
                || newSelEnd < candidatesStart || newSelEnd > candidatesEnd) {
            discardPendingInput();
        }
    }

    // ---------- 键盘回调 ----------

    /** 自动模式（2026-11-08）：取组合区原文（ASCII 化学式）。 */
    @Override
    public String getComposingText() {
        return composing.toString();
    }

    /** 自动模式：清空组合区（反应物槽确认后）。 */
    @Override
    public void clearComposing() {
        composing.setLength(0);
        suggestSymbols.clear();
        updateComposition();
    }

    /** 自动模式：把配平方程式直接上屏，绕过逐字处理。 */
    @Override
    public void commitTextDirect(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (composing.length() > 0) {
            composing.setLength(0);
            updateComposition();
        }
        ic.commitText(text, 1);
        clearSuggestions();
    }

    /** 自动模式撤销配平（2026-11-08）：把配平前输入槽里的原文放回组合区。 */
    @Override
    public void restoreComposing(String text) {
        composing.setLength(0);
        if (text != null) composing.append(text);
        suggestSymbols.clear();
        updateComposition();
    }

    @Override
    public void onChar(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            // 2026-11-05 连接已失效（焦点被抢走 / 应用切走）：旧缓存不能留到下一次输入
            discardPendingInput();
            return;
        }
        if (kbMode == ChemKeyboardView.MODE_NORMAL) {
            onCharNormal(s, ic);
            return;
        }
        suggestSymbols.clear();   // 重新输入后，上一次上屏的 ↓/↑/离子联想作废
        if (s.length() == 1 && ((s.charAt(0) >= 'A' && s.charAt(0) <= 'Z')
                || (s.charAt(0) >= 'a' && s.charAt(0) <= 'z'))) {
            char ch = s.charAt(0);
            if (pinyinMode) {
                composing.append(Character.toLowerCase(ch));
            } else {
                composing.append(caseFor(ch));
                // 点按 Shift 只生效一次：输出一个字母后立刻恢复默认大小写（2026-10-25）
                if (shiftOn && !shiftHeld) {
                    shiftOn = false;
                    keyboard.setShiftOn(false);
                }
            }
            updateComposition();
        } else {
            // 直通模式下数字可以开头进入组合区（2026-10-27）：用于同位素质量数，
            // 如 2H → ²H、14C → ¹⁴C、235U → ²³⁵U；拼音模式数字仍直接上屏。
            // 符号页「角标」的上标数字也按数字处理，用户点 ² 再打 H 就能得到 ²H。
            if (!pinyinMode && composing.length() == 0 && s.length() == 1) {
                char c0 = s.charAt(0);
                int sup = SUP_DIGIT_CHARS.indexOf(c0);
                if ((c0 >= '0' && c0 <= '9') || sup >= 0) {
                    composing.append(sup >= 0 ? (char) ('0' + sup) : c0);
                    updateComposition();
                    return;
                }
            }
            // 符号页的「mol」等基础单位（2026-10-26）：不直接上屏，先进组合区，
            // 候选栏给出 mol/L、mol/mL 这类复合单位；不选候选时按空格仍原样上屏。
            if (composing.length() == 0 && UNIT_COMBOS.containsKey(s.toLowerCase(Locale.ROOT))) {
                composing.append(s);
                updateComposition();
                return;
            }
            if (composing.length() == 0) ic.commitText(s, 1);
            else {
                composing.append(s);
                updateComposition();
            }
        }
    }

    /**
     * 普通输入法按键（2026-10-31）：
     * 中文（拼音模式）字母进组合区选词；英文（拼｜英 切到英）字母直出；
     * 标点与符号直出，不做元素大小写、单位联想与化学式规范化。
     */
    private void onCharNormal(String s, InputConnection ic) {
        if (s == null || s.isEmpty()) return;
        // 2026-11-07 数字页的键帽就是字符本身：直接上屏，不能再交给九键 T9 逻辑
        // （原来 2-9 会被送进 processT9Key，键帽显示数字但实际还在中文组合区）
        if (keyboard != null && keyboard.isNumberPage()) {
            if (composing.length() > 0) commitRawTypedInput(ic);
            if (ic != null) ic.commitText(s, 1);
            return;
        }
        if (isT9()) {
            onCharT9(s, ic);
            return;
        }
        suggestSymbols.clear();
        boolean letter = s.length() == 1 && isLetter(s.charAt(0));
        if (letter) {
            char ch = Character.toLowerCase(s.charAt(0));
            if (pinyinMode) {
                if (rimeReady) {
                    rimeKey(ch, ic);      // 全拼 / 双拼统一走 Rime
                } else {
                    composing.append(ch);
                    normalCacheKey = "";
                    updateComposition();
                }
            } else {
                char out = (shiftOn || shiftHeld) ? Character.toUpperCase(ch) : ch;
                ic.commitText(String.valueOf(out), 1);
                if (shiftOn && !shiftHeld) {
                    shiftOn = false;
                    keyboard.setShiftOn(false);
                }
            }
            return;
        }
        // 标点 / 数字 / 符号：先把未上屏的拼音原样上屏，再直出该字符
        if (composing.length() > 0) {
            if (rimeReady) {
                try {
                    RimeEngine.get().clearComposition();
                } catch (Throwable ignored) {
                }
                clearRimeState();
            }
            ic.commitText(composing.toString(), 1);
            composing.setLength(0);
            normalCacheKey = "";
            updateComposition();
        }
        ic.commitText(s, 1);
    }

    /**
     * 九键字符（2026-11-02）：2-9 视为数字键发送给 T9；其余（@ 与左栏标点）先确认组合区再直出。
     * 候选不带拼音注释，用户看到的就是候选词本身。
     */
    private void onCharT9(String s, InputConnection ic) {
        suggestSymbols.clear();
        if (s.length() == 1) {
            char c = s.charAt(0);
            if (c >= '2' && c <= '9') {
                if (rimeReady) {
                    rimeT9Key(c, ic);
                } else {
                    composing.append(c);
                    normalCacheKey = "";
                    updateComposition();
                }
                return;
            }
        }
        if (composing.length() > 0) {
            if (rimeReady && !rimeCandTexts.isEmpty()) {
                selectT9Candidate(0);
            } else {
                composing.setLength(0);
                clearRimeState();
                normalCacheKey = "";
                updateComposition();
            }
        }
        if (ic != null) ic.commitText(s, 1);
    }

    /**
     * 中文输入尚未选候选时按空格 / 换行 / 发送（2026-11-07）：
     * 不选默认候选、不换行、不发送，直接把当前已键入的字符以纯英文原样上屏；
     * 去掉空格、撇号等分词符，避免把 rime 的音节分隔带进输入框。
     */
    private void commitRawTypedInput(InputConnection ic) {
        String raw = composing.toString();
        if (raw.isEmpty() || ic == null) return;
        raw = raw.replace("'", "").replace(" ", "").replace("\t", "");
        if (rimeReady) {
            try {
                if (isT9()) RimeEngine.get().t9ClearComposition(1);
                RimeEngine.get().clearComposition();
            } catch (Throwable ignored) {
            }
        }
        clearRimeState();
        normalCacheKey = "";
        current = null;
        suggestSymbols.clear();
        clearAppComposing(ic, composing.toString());
        composing.setLength(0);
        if (!raw.isEmpty()) ic.commitText(raw, 1);
        updateComposition();
    }

    /**
     * 长按 Shift 的按住/松开回调（2026-10-25）。
     * held=true 时一直保持大写，另一根手指连打字母也不会消耗该状态；
     * held=false 时立即恢复默认小写。
     */
    @Override
    public void onShiftHoldChanged(boolean held) {
        shiftHeld = held;
        shiftOn = held;
        if (keyboard != null) keyboard.setShiftOn(held);
    }

    /**
     * 键盘上切换了模式（2026-10-31，左下角芯片的浮层里选）：
     * 保存偏好、清组合区与联想、刷新候选栏左芯片，并提示当前模式。
     */
    @Override
    public void onInputModeChanged(int mode, int scheme) {
        kbMode = mode;
        normalScheme = scheme;
        if (prefs != null) {
            prefs.edit().putInt("kb_mode", mode).putInt("normal_scheme", scheme).apply();
        }
        composing.setLength(0);
        clearRimeState();
        normalCacheKey = "";
        normalCache.clear();
        suggestSymbols.clear();
        shiftOn = false;
        shiftHeld = false;
        if (keyboard != null) keyboard.setShiftOn(false);
        updateComposition();
        restylePageChip();
        // 两种模式的页高不同（统一键面高度会变），浮窗卡片高度要重算并重新落位（2026-11-01）
        floatCardH = 0;
        if (kbLayout == ChemKeyboardView.LAYOUT_FLOAT && floatCard != null) {
            floatCard.post(() -> placeFloatCard());
        }
        if (kbMode == ChemKeyboardView.MODE_NORMAL) startRimeIfNeeded();
        toast(mode == ChemKeyboardView.MODE_CHEM
                ? "已切换到化学输入"
                : "已切换到普通输入 · " + RimeSetup.SCHEME_NAMES[Math.max(0,
                Math.min(RimeSetup.SCHEME_NAMES.length - 1, scheme))]);
    }

    /** 键盘切页（字母 / 符号 / 数字 / 工具栏）：候选栏左芯片文案随之切换。 */
    @Override
    public void onPageChanged() {
        if (keyboard != null && pageChip != null) restylePageChip();
    }

    /** 配平态变化（2026-11-08）：候选栏在「撤销 / 确认」与原有按钮之间切换。 */
    @Override
    public void onReactionStateChanged(boolean balanced) {
        reactBalanced = balanced;
        syncReactionButtons();
    }

    /**
     * 九键左栏卡点按（2026-11-02，对应 CyIME 左侧面板）：
     * 空闲时是「，。？！」标点；有组合时是首音节候选，点按即选定该音节（t9SelectPinyin）。
     */
    @Override
    public void onT9RailItem(int index) {
        if (!isT9() || index < 0 || index >= 4) return;
        if (t9RailPinyin.isEmpty()) {
            onCharT9(ChemKeyboardView.T9_RAIL_PUNCT[index], getCurrentInputConnection());
            return;
        }
        if (index >= t9RailPinyin.size()) return;
        RimeProcessResult r = null;
        try {
            int len = index < t9RailLengths.size() ? t9RailLengths.get(index) : 0;
            r = RimeEngine.get().t9SelectPinyin(t9RailPinyin.get(index), len);
        } catch (Throwable ignored) {
        }
        if (r != null) applyRimeResult(r, getCurrentInputConnection());
        updateComposition();
    }

    /**
     * 直通模式大小写规则（2026-10-25 更新）：
     * <ul>
     *   <li>按住 Shift（shiftHeld）或点按 Shift 的一次性状态（shiftOn）→ 大写；</li>
     *   <li>点按 Shift 只影响下一个字母，输出后自动恢复；</li>
     *   <li>默认按元素书写习惯自动大写：新元素首字母大写；同一元素第二个字母小写
 *       （如 C + u → Cu）。判断时只看「最后一个大写字母起的后缀」，因此连续元素
 *       也能正确书写（Na + C + l → NaCl，而不是 NaCL）。</li>
 * </ul>
 */
    private String caseFor(char ch) {
        char c = Character.toLowerCase(ch);
        if (shiftHeld || shiftOn) return String.valueOf(Character.toUpperCase(c));
        if (!autoCase) return String.valueOf(c);
        if (continuesTwoLetterElement(c)) {
            return String.valueOf(c);                       // 元素续写：C + u → Cu / NaC + l → NaCl
        }
        return String.valueOf(Character.toUpperCase(c));    // 新元素首字母自动大写
    }

    /**
     * 判断当前字母是否应作为「双字母元素的第二个字母」输出小写。
     * 取组合区末尾连续字母中最后一个大写字母起的后缀：后缀只剩 1 个字母
     * 且与大写字母拼成的元素存在时，才小写。这样 NaCl 的 l 会小写，
     * 而 Cu 之后的 S（新元素）仍保持大写。
     */
    private boolean continuesTwoLetterElement(char c) {
        int end = composing.length();
        int start = end;
        while (start > 0 && isLetter(composing.charAt(start - 1))) start--;
        int lastUpper = -1;
        for (int k = start; k < end; k++) {
            if (Character.isUpperCase(composing.charAt(k))) lastUpper = k;
        }
        if (lastUpper < 0 || end - lastUpper != 1) return false;
        return TWO_LETTER_ELEMENTS.contains(composing.charAt(lastUpper) + String.valueOf(c));
    }

    private static boolean isLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    @Override
    public void onAction(int action) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            // 2026-11-05 连接已失效（焦点被抢走 / 应用切走）：旧缓存不能留到下一次输入
            discardPendingInput();
            return;
        }
        // 普通输入法（2026-10-31）：空格 / 回车 / 退格 / 语言切换走普通输入逻辑
        if (kbMode == ChemKeyboardView.MODE_NORMAL && onActionNormal(action, ic)) return;
        switch (action) {
            case ChemKeyboardView.ACT_SPACE:
                if (pinyinMode && current != null && !current.isEmpty()) {
                    commitCandidate(current.get(0));
                } else if (composing.length() > 0) {
                    // 直接上屏也走联想：沉淀/气体/单质元素上屏后给出 ↓、↑ 或离子符号
                    commitAndSuggest(composing.toString());
                } else {
                    ic.commitText(" ", 1);
                    clearSuggestions(); // 空组合区再按空格：上一轮联想符号作废
                }
                break;
            case ChemKeyboardView.ACT_ENTER:
                if (composing.length() > 0) {
                    commitAndSuggest(composing.toString());
                }
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                break;
            case ChemKeyboardView.ACT_BACK:
                if (composing.length() > 0) {
                    composing.deleteCharAt(composing.length() - 1);
                    updateComposition();
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                    clearSuggestions(); // 删除已上屏内容后，旧联想符号不再适用
                }
                break;
            case ChemKeyboardView.ACT_SHIFT:
                // 一次性大写：再点一次可取消（长按「按住大写」由 onShiftHoldChanged 处理）
                shiftOn = !shiftOn;
                shiftHeld = false;
                keyboard.setShiftOn(shiftOn);
                toast(shiftOn ? "Shift：下一个字母大写" : "Shift：小写");
                break;
            case ChemKeyboardView.ACT_CASE:
                autoCase = !autoCase;
                keyboard.setAutoCase(autoCase);
                toast(autoCase ? "元素自动大小写：开（新元素首字母自动大写）"
                        : "元素自动大小写：关（Shift 决定大小写）");
                break;
            case ChemKeyboardView.ACT_MODE:
                pinyinMode = !pinyinMode;
                shiftOn = false;
                shiftHeld = false;
                keyboard.setPinyinMode(pinyinMode);
                composing.setLength(0);
                suggestSymbols.clear(); // 换模式后旧联想不再适用
                updateComposition();
                toast(pinyinMode ? "拼音模式：输入中文名，如 liusu-antong → CuSO₄"
                        : "直通模式：字母直出，元素自动大小写");
                break;
            case ChemKeyboardView.ACT_PAGE:
                keyboard.togglePage();
                restylePageChip();
                break;
            case ChemKeyboardView.ACT_IME: {
                // 底部 ⇌ = 切换输入法（设计稿 §3.5.4）
                // 2026-11-05 系统输入法选择弹窗属于输入打断：先丢弃未确认的组合区
                discardPendingInput();
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
                break;
            }
            case ChemKeyboardView.ACT_HIDE:
                // 候选栏右侧 ⌄ = 收起键盘（客户 2026-10-15 明确）
                // 2026-11-05 收起键盘属于输入打断：立即丢弃未确认的组合区
                discardPendingInput();
                requestHideSelf(0);
                break;
            case ChemKeyboardView.ACT_SETTINGS: {
                // 工具栏「应用设置」（2026-10-31）：打开主界面
                // 2026-11-05 跳转应用属于输入打断：先丢弃未确认的组合区
                discardPendingInput();
                Intent it = new Intent(this, MainActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                break;
            }
            case ChemKeyboardView.ACT_DARK: {
                // 工具栏「深色模式 / 浅色模式」卡（2026-11-01）：直接切输入法深浅色，不再跟随系统
                darkMode = !darkMode;
                getSharedPreferences("jhf_settings", Context.MODE_PRIVATE).edit()
                        .putBoolean("follow", false)
                        .putBoolean("dark", darkMode)
                        .apply();
                applyTheme();
                break;
            }
            case ChemKeyboardView.ACT_FLOAT: {
                // 工具栏「悬浮键盘」卡（2026-11-01）：悬浮 ⇄ 平铺，实时换布局
                kbLayout = (kbLayout == ChemKeyboardView.LAYOUT_FLOAT)
                        ? ChemKeyboardView.LAYOUT_FULL : ChemKeyboardView.LAYOUT_FLOAT;
                getSharedPreferences("jhf_settings", Context.MODE_PRIVATE).edit()
                        .putInt("kb_layout", kbLayout)
                        .putBoolean("kb_layout_manual", true)
                        .apply();
                floatPositioned = false;
                applyLayoutMode();
                break;
            }
            case ChemKeyboardView.ACT_ADJUST:
                // 工具栏卡 / 候选栏快捷键「键盘调节」（2026-11-04）：进编辑态
                showAdjustOverlay();
                break;
            case ChemKeyboardView.ACT_CLIP:
                // 工具栏卡 / 候选栏快捷键「剪切板」（2026-11-04）：开剪切板面板
                openClipPanel();
                break;
        }
    }

    /**
     * 普通输入法的动作（2026-10-31）：返回 true 表示已处理，
     * 其余动作（Shift / 切换输入法 / 收起键盘 / 打开设置）沿用化学模式分支。
     */
    private boolean onActionNormal(int action, InputConnection ic) {
        switch (action) {
            case ChemKeyboardView.ACT_SPACE:
                // 2026-11-07 有未确认输入时空格不再选候选：直接原样上屏已键入字符
                if (composing.length() > 0) {
                    commitRawTypedInput(ic);
                } else {
                    ic.commitText(" ", 1);
                }
                return true;
            case ChemKeyboardView.ACT_ENTER:
                // 2026-11-07 有未确认输入时回车 / 发送不换行、不发送：只把已键入字符原样上屏
                if (composing.length() > 0) {
                    commitRawTypedInput(ic);
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                }
                return true;
            case ChemKeyboardView.ACT_BACK:
                if (composing.length() > 0) {
                    if (rimeReady) {
                        if (isT9()) rimeT9Key(0xff08, ic);
                        else rimeKey(0xff08, ic);
                    } else {
                        composing.deleteCharAt(composing.length() - 1);
                        normalCacheKey = "";
                        updateComposition();
                    }
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
                return true;
            case ChemKeyboardView.ACT_LANG:
                // 拼｜英：中文拼音选词 / 英文字母直出
                pinyinMode = !pinyinMode;
                shiftOn = false;
                shiftHeld = false;
                keyboard.setShiftOn(false);
                keyboard.setPinyinMode(pinyinMode);
                // 2026-11-05 切换拼/英前先撤掉应用侧预编辑，否则旧拼音留在输入框里
                clearAppComposing(ic, composing.toString());
                composing.setLength(0);
                if (rimeReady) {
                    try {
                        RimeEngine.get().clearComposition();
                    } catch (Throwable ignored) {
                    }
                }
                clearRimeState();
                normalCacheKey = "";
                updateComposition();
                toast(pinyinMode ? "中文输入：拼音选词" : "英文输入：字母直出");
                return true;
            default:
                return false;
        }
    }

    /** 普通输入法上屏：清组合区与候选缓存，再刷新候选栏。 */
    private void commitNormal(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // 2026-11-05 先撤应用侧预编辑，再上屏，避免拼音 / 数字串残留在输入框里
        clearAppComposing(ic, composing.toString());
        ic.commitText(text, 1);
        composing.setLength(0);
        if (rimeReady) {
            try {
                RimeEngine.get().clearComposition();
            } catch (Throwable ignored) {
            }
        }
        clearRimeState();
        normalCacheKey = "";
        normalCache.clear();
        updateComposition();
    }

    // ---------- 组词与候选 ----------

    private void updateComposition() {
        InputConnection ic = getCurrentInputConnection();
        boolean auto = keyboard != null && keyboard.isChemAutoMode();
        // 自动配平模式：输入内容只进反应栏，不写入正文（避免残留拼音）
        if (ic != null && !auto) {
            if (composing.length() == 0) ic.finishComposingText();
            else ic.setComposingText(composing.toString(), 1);
        }
        refreshCandidates();
        // 自动配平模式的反应栏需要跟随组合区实时刷新
        if (auto) keyboard.invalidate();
    }

    private void refreshCandidates() {
        candList.removeAllViews();
        candExpanded.removeAllViews();
        current = null;
        boolean hasComposing = composing.length() > 0;
        // 候选栏两颗控制键跟输入状态走（2026-11-02）：打字中 → 出现、⌄ 变 ✕；空组合区 → 反向复原
        updateCandBarButtons();
        // 2026-11-02 候选栏纯净显示：不回显已输入的拼音（预编辑文本由应用侧输入框自己显示），
        // 候选栏与候选字界面里只出现候选词本身，不再出现任何拼音字符/片段/标注。
        composingView.setVisibility(View.GONE);
        composingView.setText("");

        if (kbMode == ChemKeyboardView.MODE_NORMAL) {
            refreshNormalCandidates(hasComposing);
            return;
        }

        List<PinyinMatcher.Candidate> list = null;
        if (pinyinMode && hasComposing) {
            current = matcher.match(composing.toString(), 20);
            list = current;
        }
        // 直通模式：识别化学式并给出规范化候选（CuSO4 → CuSO₄）
        String formulaCand = (!pinyinMode && hasComposing)
                ? normalizeFormula(composing.toString()) : null;
        // 复合单位候选（2026-10-26）：点符号页「mol」或手打 kg、L、g 等基础单位时给出
        List<String> unitCands = hasComposing ? unitCandidates(composing.toString()) : null;

        // 单位候选优先规则（2026-10-26）：直通模式、没有拼音候选、或组合区已是多字母单位
        // （kg / ml / mol…，不会与拼音前缀冲突）时，单位排到最前；
        // 只有拼音模式下的单字母 L 仍把拼音候选放前面，单位接在后面，避免打 li、liu 时单位抢位。
        boolean unitsFirst = unitCands != null
                && (!pinyinMode || list == null || list.isEmpty() || composing.length() >= 2);

        if (unitsFirst) {
            addUnitChips(unitCands, true);
            if (list != null && !list.isEmpty()) addPinyinChips(list);
        } else if (list != null && !list.isEmpty()) {
            addPinyinChips(list);
            if (unitCands != null) addUnitChips(unitCands, false);
        } else {
            // 联想符号（2026-10-27）：直通模式下组合区已成化学式时实时给出 ↓/↑/离子；
            // 上屏后组合区为空，则继续保留刚上屏内容的联想符号，直到下一次输入。
            List<String> symbolCands = hasComposing
                    ? composingSuggestions()
                    : (suggestSymbols.isEmpty() ? null : suggestSymbols);
            if (formulaCand != null) {
                // 规范化学式优先占首位，符号候选紧跟其后
                candList.addView(makeFormulaCand(formulaCand));
                if (symbolCands != null) addSymbolChips(symbolCands, false);
            } else if (hasComposing) {
                // 「空格直接上屏」的提示条很宽，符号候选必须排在它前面才不会被挤出屏幕
                if (symbolCands != null) addSymbolChips(symbolCands, false);
                candList.addView(makeFallback());
            } else if (symbolCands != null) {
                addSymbolChips(symbolCands, true);
            }
        }
    }

    // ---------- 普通输入法候选（2026-10-31） ----------

    /** 普通输入法候选：拼音模式下列出中文词；英文直出与空组合区不出候选。 */
    private void refreshNormalCandidates(boolean hasComposing) {
        if (!hasComposing || !pinyinMode) return;
        if (rimeReady) {
            if (candBarTexts.isEmpty()) resetCandBarToCurrentPage();
            if (candBarTexts.isEmpty()) {
                candList.addView(makeFallback());
                return;
            }
            // 用累计表渲染：滑动加载的后续页也会一起出现在候选栏里
            addNormalChips(candBarTexts);
            scheduleCandStripFill();
            return;
        }
        String typed = composing.toString();
        if (normalMatcher == null) {
            ensureNormalMatcher();   // 首次输入：后台加载词库，先给「直接上屏」提示
            candList.addView(makeFallback());
            return;
        }
        if (!typed.equals(normalCacheKey)) {
            normalCache.clear();
            normalCache.addAll(normalMatcher.match(typed, 20));
            normalCacheKey = typed;
        }
        if (normalCache.isEmpty()) {
            candList.addView(makeFallback());
            return;
        }
        addNormalChips(normalCache);
    }

    // ---------- 候选字界面（2026-11-01，Figma 216-397 / 216-519） ----------

    private NormalCandPanel.Listener panelListener() {
        return new NormalCandPanel.Listener() {
            @Override
            public void onPanelBack() {
                closeCandPanel();
            }

            @Override
            public void onPanelHide() {
                // 2026-11-05 收起键盘属于输入打断：先丢弃未确认的组合区再隐藏
                discardPendingInput();
                closeCandPanel();
                requestHideSelf(0);
            }

            @Override
            public void onPanelSelect(NormalCandPanel.Item item) {
                selectPanelItem(item);
            }

            @Override
            public void onPanelPage(boolean next) {
                turnPanelPage(next);
            }

            @Override
            public void onPanelMode() {
                closeCandPanel();
                if (keyboard != null) keyboard.post(() -> keyboard.showModePopup());
            }

            @Override
            public void onPanelIme() {
                // 2026-11-05 系统输入法选择弹窗属于输入打断：先丢弃未确认的组合区
                discardPendingInput();
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }

            @Override
            public void onPanelTab(int tab) {
                candPanelTab = tab;
            }

            @Override
            public void onPanelToolbar() {
                closeCandPanel();
                if (keyboard != null) keyboard.showToolbarPage();
            }
        };
    }

    /** 打开候选字界面：面板整体顶替候选栏 + 键区（设计稿 216-519 自带顶栏），卡片总高不变。 */
    private void openCandPanel() {
        if (candPanel == null || candPanelOpen
                || kbMode != ChemKeyboardView.MODE_NORMAL) return;
        float d = getResources().getDisplayMetrics().density;
        int h = keyboard.getHeight();
        if (h <= 0) h = (int) (280 * d);
        // 候选栏会一起隐藏，把它的高度补给面板，整张卡片高度才不跳
        if (candBar != null && candBar.getHeight() > 0) h += candBar.getHeight();
        // 分割线也一起隐藏，同样补给面板（否则展开时整卡会矮 1dp）
        if (candDivider != null && candDivider.getVisibility() == View.VISIBLE) {
            h += candDivider.getHeight();
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) candPanel.getLayoutParams();
        lp.height = h;
        candPanel.setLayoutParams(lp);
        candPanel.setDark(darkMode);
        candPanel.setAccent(accentColor());
        candPanelTab = NormalCandPanel.TAB_FREQ;
        candPanel.setTab(candPanelTab);
        // 候选栏连续滑动可能把引擎停在第 N 页，进面板前先退回第一页，面板总是从首候选开始
        resetRimeToFirstPage();
        collectPanelItems();
        loadPanelInitials();
        candPanelOpen = true;
        candBar.setVisibility(View.GONE);
        if (candDivider != null) candDivider.setVisibility(View.GONE);
        keyboard.setVisibility(View.GONE);
        candPanel.setVisibility(View.VISIBLE);
    }

    /** 收起候选字界面，回到候选栏 + 键盘（组合区与候选不丢，还能接着打）。 */
    private void closeCandPanel() {
        if (!candPanelOpen) return;
        candPanelOpen = false;
        if (candPanel != null) candPanel.setVisibility(View.GONE);
        if (candBar != null) candBar.setVisibility(View.VISIBLE);
        if (candDivider != null) candDivider.setVisibility(View.VISIBLE);
        if (keyboard != null) keyboard.setVisibility(View.VISIBLE);
    }

    // ---- 剪切板（2026-11-04，248-16「键盘／普通输入／浅色·圆润／剪切板」）----

    /** 注册系统剪贴板监听：复制的文本即时进历史（去重置顶、封顶 50 条）。 */
    private void initClipboard() {
        if (clipboardManager != null) return;
        clipboardManager = (android.content.ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) return;
        clipListener = () -> {
            try {
                android.content.ClipData cd = clipboardManager.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0) {
                    CharSequence t = cd.getItemAt(0).coerceToText(this);
                    if (t != null) addClip(t.toString());
                }
            } catch (Throwable ignored) {
            }
        };
        clipboardManager.addPrimaryClipChangedListener(clipListener);
    }

    private ClipPanel.Listener clipPanelListener() {
        return new ClipPanel.Listener() {
            @Override
            public void onPick(String text) {
                // 点条目直接上屏，面板保持打开，可连续粘贴多条
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(text, 1);
            }

            @Override
            public void onDelete(String text) {
                clips.remove(text);
                saveClips();
                if (clipPanel != null) clipPanel.setItems(new ArrayList<>(clips));
            }
        };
    }

    private void openClipPanel() {
        if (clipPanel == null || keyboard == null) return;
        if (candPanelOpen) closeCandPanel();
        clipOpen = true;
        clipPanel.setDark(darkMode);
        clipPanel.setItems(new ArrayList<>(clips));
        // 2026-11-09 与键盘同槽位：只切可见性并置于键盘之上，高度由键盘撑起，完全一致
        clipPanel.setVisibility(View.VISIBLE);
        clipPanel.bringToFront();
        restylePageChip();
        syncShortcutVisibility();
    }

    private void closeClipPanel() {
        if (!clipOpen) return;
        clipOpen = false;
        if (clipPanel != null) clipPanel.setVisibility(View.GONE);
        restylePageChip();
        syncShortcutVisibility();
    }

    // ---- 键盘调节编辑态（2026-11-04，249-204 浅色 / 249-394 深色）----

    private void showAdjustOverlay() {
        if (adjustOverlay == null || floatCard == null) return;
        if (candPanelOpen) closeCandPanel();
        if (clipOpen) closeClipPanel();
        adjustOverlay.begin(kbLeft, kbWidth, kbHeight);
        adjustOverlay.setVisibility(View.VISIBLE);
        adjustOverlay.bringToFront();
    }

    private void hideAdjustOverlay() {
        if (adjustOverlay != null) adjustOverlay.setVisibility(View.GONE);
    }

    /**
     * 卡片内容真实高度（抓手 + 候选栏 + 分割线 + 键盘），供调节浮层测量使用。
     * 用 cardColumn 的实测高度而不是 floatCard 的高度：floatCard 高度会包含浮层自身，
     * 若拿它回填会形成「浮层越高 → 卡片越高 → 浮层更高」的死锁，键盘顶部将无法跟随拖动。
     */
    private int cardContentHeight() {
        if (cardColumn == null) return 0;
        int h = cardColumn.getMeasuredHeight();
        return h > 0 ? h : cardColumn.getHeight();
    }

    /**
     * 下发键盘调节几何：左右边界 / 竖向比例交给键盘，底部间隙落成键盘的底部外边距。
     * 间隙让卡片多长出一段空白，输入法窗口底边固定、整卡向上伸，键盘底边就随拖手下移/上移，
     * 从而实现底部拖手对底边界的独立控制（2026-11-04）。
     */
    private void applyUserBox(float left, float width, float height, float gapPx) {
        if (keyboard == null) return;
        keyboard.setUserBox(left, width, height);
        // keyboard 自 2026-11-09 起包在 kbSlot（FrameLayout）里，LayoutParams 不再是 LinearLayout 的；
        // 只改底部外边距，用两者共同的父类 MarginLayoutParams，避免启动即 ClassCastException。
        ViewGroup.LayoutParams rawLp = keyboard.getLayoutParams();
        if (!(rawLp instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) rawLp;
        int gap = Math.max(0, Math.round(gapPx));
        if (lp.bottomMargin != gap) {
            lp.bottomMargin = gap;
            keyboard.setLayoutParams(lp);
        }
    }

    /** 调节浮层的键盘框：取键盘视图在卡片内的位置 + 宽度缩放后的内容左右缘。 */
    private void fillAdjustFrame(android.graphics.RectF out) {
        if (keyboard == null || cardColumn == null) {
            out.set(0, 0, 0, 0);
            return;
        }
        float left = cardColumn.getLeft() + keyboard.getLeft() + keyboard.getContentLeft();
        float top = cardColumn.getTop() + keyboard.getTop();
        out.set(left, top, left + keyboard.getContentW(), top + keyboard.getHeight());
    }

    /** 把 Rime 退回本次输入的第一页（候选栏滑动加载后进候选字界面时用）。 */
    private void resetRimeToFirstPage() {
        if (!rimeReady) return;
        RimeEngine e;
        try {
            e = RimeEngine.get();
        } catch (Throwable t) {
            return;
        }
        while (rimePageIndex > 0) {
            if (!rimePageUp(e)) break;
        }
        readRimeComposition();
    }

    /**
     * 收集候选字界面的数据：从当前页起往后翻最多 5 页（5×5 网格够用），
     * 每项记下「页偏移 + 页内下标」，上屏时按偏移翻回对应页再选。
     */
    private void collectPanelItems() {
        candPanelItems.clear();
        if (rimeReady) {
            RimeEngine e;
            try {
                e = RimeEngine.get();
            } catch (Throwable t) {
                e = null;
            }
            if (e != null) {
                int moved = 0;
                appendRimePage(0);
                while (moved < 4 && rimeHasNextPage) {
                    if (!rimePageDown(e)) break;
                    moved++;
                    readRimeComposition();
                    appendRimePage(moved);
                }
                for (int i = 0; i < moved; i++) {
                    if (!rimePageUp(e)) break;
                }
                readRimeComposition();
            }
        } else {
            String typed = composing.toString();
            List<String> list = typed.isEmpty() ? null
                    : (normalMatcher != null ? normalMatcher.match(typed, 25) : normalCache);
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    candPanelItems.add(new NormalCandPanel.Item(list.get(i), "", 0, i));
                }
            }
        }
        if (candPanel != null) candPanel.setData(candPanelItems, candPanelInitials);
    }

    private void appendRimePage(int pageOffset) {
        for (int i = 0; i < rimeCandTexts.size(); i++) {
            candPanelItems.add(new NormalCandPanel.Item(
                    rimeCandTexts.get(i),
                    i < rimeCandComments.size() ? rimeCandComments.get(i) : "",
                    pageOffset, i));
        }
    }

    /** 点候选格子：翻到该项所在的页再选它上屏，随后收起面板并清空组合区。 */
    private void selectPanelItem(NormalCandPanel.Item item) {
        String text = item.text;
        if (rimeReady) {
            try {
                RimeEngine e = RimeEngine.get();
                int cur = 0;
                while (cur < item.pageOffset) {
                    if (!rimePageDown(e)) break;
                    cur++;
                }
                if (e.selectCandidate(item.indexInPage)) {
                    String committed = e.commit();
                    if (committed.length() > 0) text = committed;
                }
            } catch (Throwable ignored) {
            }
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text.length() > 0) ic.commitText(text, 1);
        composing.setLength(0);
        if (rimeReady) {
            try {
                RimeEngine.get().clearComposition();
            } catch (Throwable ignored) {
            }
        }
        clearRimeState();
        normalCacheKey = "";
        closeCandPanel();
        updateComposition();
    }

    /** 面板右下角翻页：Rime 翻一页后从新页重新收 25 个候选。 */
    private void turnPanelPage(boolean next) {
        if (!rimeReady) return;
        try {
            RimeEngine e = RimeEngine.get();
            boolean ok = next ? rimePageDown(e) : rimePageUp(e);
            if (!ok) return;
        } catch (Throwable ignored) {
            return;
        }
        collectPanelItems();
        refreshCandidates();
    }

    /** 首字母表（字 → 声母）首次使用时后台加载，建好后回填筛选列。 */
    private void loadPanelInitials() {
        buildPanelInitials();
        if (candPanel != null) candPanel.setData(candPanelItems, candPanelInitials);
        if (initialsLoaded) return;
        Thread t = new Thread(() -> {
            NormalPinyinMatcher.loadInitials(getApplicationContext());
            mainHandler.post(() -> {
                initialsLoaded = true;
                buildPanelInitials();
                if (candPanelOpen && candPanel != null) {
                    candPanel.setData(candPanelItems, candPanelInitials);
                }
            });
        }, "jhf-initials");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** 汇总当前候选首字的声母（a→z 顺序），空表时筛选列不显示。 */
    private void buildPanelInitials() {
        candPanelInitials.clear();
        java.util.TreeSet<String> set = new java.util.TreeSet<>();
        for (NormalCandPanel.Item it : candPanelItems) {
            String ini = NormalPinyinMatcher.initialOf(it.text);
            if (ini != null && !ini.isEmpty()) set.add(ini);
        }
        candPanelInitials.addAll(set);
    }

    /** 普通中文候选芯片：只显示候选词本身，首个高亮（空格上屏首个候选）。 */
    private void addNormalChips(List<String> list) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = null;
        // 展开态容器只有 98dp（两行 46dp），多加载的页不往里塞，避免第三行被裁掉
        int count = expanded ? Math.min(list.size(), 10) : list.size();
        for (int i = 0; i < count; i++) {
            if (expanded && i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                candExpanded.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) (46 * d)));
            }
            View chip = makeNormalCand(list.get(i), i == 0, i);
            if (expanded) row.addView(chip);
            else candList.addView(chip);
        }
        // 2026-11-02 候选栏里不再放「‹ ›」翻页芯片（客户要求删掉那颗 “›” 按钮）：
        // 一行只显示候选词本身，翻页走候选字界面（长按 ⌄ 或点「→」后面的网格 ‹ ›）。
    }

    // ---------- 候选栏连续滑动（2026-11-02） ----------

    /** 候选区离右端是否只剩 40dp（滑到这里就去取下一页）。 */
    private boolean nearCandStripEnd() {
        View child = candScroll.getChildAt(0);
        if (child == null) return false;
        int viewW = candScroll.getWidth();
        if (viewW <= 0) return false;
        int remain = child.getMeasuredWidth() - (candScroll.getScrollX() + viewW);
        return remain <= (int) (40 * getResources().getDisplayMetrics().density);
    }

    /** 向 Rime 再取一页，追加到候选栏累计表并重建芯片行；没有下一页时什么也不做。 */
    private void appendNextRimePage() {
        if (!rimeReady || expanded || !rimeHasNextPage) return;
        RimeEngine e;
        try {
            e = RimeEngine.get();
        } catch (Throwable t) {
            return;
        }
        if (!rimePageDown(e)) {
            rimeHasNextPage = false;
            return;
        }
        readRimeComposition();
        for (int i = 0; i < rimeCandTexts.size(); i++) {
            candBarTexts.add(rimeCandTexts.get(i));
            candBarPages.add(rimePageIndex);
        }
        renderRimeCandidates();
    }

    /** 只重建候选芯片行（不重置累计表，也不重新跑拼音匹配）。 */
    private void renderRimeCandidates() {
        candList.removeAllViews();
        candExpanded.removeAllViews();
        if (candBarTexts.isEmpty()) {
            candList.addView(makeFallback());
            return;
        }
        addNormalChips(candBarTexts);
        scheduleCandStripFill();
    }

    /**
     * 候选区被 6 个候选铺得很窄、根本滑不动时自动补一页（补到能滑动或没有下一页为止），
     * 保证「只滑动、不点展开」也能翻到后面的候选。
     */
    private void scheduleCandStripFill() {
        if (candFillScheduled) return;
        candFillScheduled = true;
        candScroll.post(() -> {
            candFillScheduled = false;
            if (expanded || !rimeReady || !rimeHasNextPage) return;
            if (candBar == null || candBar.getVisibility() != View.VISIBLE) return;
            if (candScroll.getVisibility() != View.VISIBLE) return;
            // 自动补页封顶 8 页，再往后的页让用户滑动按需加载，避免超窄条无限补页
            if (rimePageIndex >= 7) return;
            View child = candScroll.getChildAt(0);
            int viewW = candScroll.getWidth();
            if (child == null || viewW <= 0) return;
            // 内容还没铺满候选区：滑不动，补一页再试（appendNextRimePage 末尾会再排一次）
            if (child.getMeasuredWidth() <= viewW) appendNextRimePage();
        });
    }

    private View makeNormalCand(String word, boolean primary, int index) {
        float d = getResources().getDisplayMetrics().density;
        int accent = accentColor();
        TextView tv = new TextView(this);
        // 2026-11-02 候选词只显示词本身：不再拼「词 + 拼音注释」的小字（Rime 的 comment 一律不渲染）
        tv.setText(word);
        tv.setTextSize(16);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setSingleLine();
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int) (12 * d), (int) (5 * d), (int) (12 * d), (int) (5 * d));
        // 普通键盘 · 浅色圆润（2026-11-01，Figma 216-397）：候选是一行 20dp 的大字，
        // 首个候选压在灰色圆角块（#D9D9D9 radius 6）上，其余直接落在候选栏底色上。
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        tv.setPadding((int) (8 * d), (int) (4 * d), (int) (8 * d), (int) (4 * d));
        SmoothDrawable g = new SmoothDrawable();
        if (primary) {
            g.setColor(darkMode ? 0xFF4A4A4A : 0xFFD9D9D9);
            tv.setTextColor(accent);
        } else {
            g.setColor(Color.TRANSPARENT);
            tv.setTextColor(darkMode ? 0xFFE9EDF0 : 0xFF000000);
        }
        g.setCornerRadius(6 * d);
        tv.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (3 * d), 0, (int) (3 * d), 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            if (rimeReady) selectRimeCandidate(index);
            else commitNormal(word);
        });
        return tv;
    }

    /** 从内核读回当前组合区与候选（翻页、外部改动后同步用）。 */
    private void refreshRimeFromEngine() {
        readRimeComposition();
        updateComposition();
    }

    /** 只读内核状态到字段，不刷界面（候选字界面跨页收集时用，避免每翻一页重建一遍候选栏）。 */
    private void readRimeComposition() {
        if (!rimeReady) return;
        try {
            RimeComposition comp = RimeEngine.get().getComposition();
            if (comp != null) {
                composing.setLength(0);
                composing.append(comp.input == null ? "" : comp.input);
                rimeCandTexts.clear();
                rimeCandComments.clear();
                if (comp.candidates != null) {
                    for (RimeCandidate c : comp.candidates) {
                        if (c == null || c.text == null) continue;
                        rimeCandTexts.add(c.text);
                        rimeCandComments.add(c.comment == null ? "" : c.comment);
                    }
                }
                rimeHasNextPage = comp.hasNextPage;
                rimeHasPrevPage = comp.hasPrevPage;
            }
        } catch (Throwable ignored) {
        }
    }

    /** 首次中文输入时后台加载词库（1.7MB 文本，避免阻塞键盘呼出）。 */
    private void ensureNormalMatcher() {
        if (normalMatcher != null || normalMatcherLoading) return;
        normalMatcherLoading = true;
        Thread t = new Thread(() -> {
            NormalPinyinMatcher m = null;
            try {
                m = new NormalPinyinMatcher(getApplicationContext());
            } catch (Throwable ignored) {
            }
            NormalPinyinMatcher loaded = m;
            mainHandler.post(() -> {
                normalMatcherLoading = false;
                normalMatcher = loaded;
                if (normalMatcher != null && kbMode == ChemKeyboardView.MODE_NORMAL
                        && pinyinMode && composing.length() > 0 && keyboard != null) {
                    normalCacheKey = "";
                    refreshCandidates();
                }
            });
        }, "jhf-normal-pinyin");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * 直通模式下组合区内容的联想符号：沉淀 → ↓、气体 → ↑、单独元素 → 常见离子。
     * 拼音模式的组合区是拼音串，不参与化学式判断，返回 null。
     */
    private List<String> composingSuggestions() {
        if (pinyinMode || composing.length() == 0) return null;
        String raw = composing.toString();
        String norm = normalizeFormula(raw);
        return suggestionsFor(norm != null ? norm : raw);
    }

    /**
     * 根据化学式生成联想符号：沉淀化合物 → ↓、气体 → ↑、单独元素 → 常见离子电荷符号。
     * 无匹配时返回 null。
     */
    private List<String> suggestionsFor(String formula) {
        if (formula == null || formula.isEmpty()) return null;
        if (PRECIPITATE_FORMULAS.contains(formula)) return Arrays.asList("↓");
        if (GAS_FORMULAS.contains(formula)) return Arrays.asList("↑");
        String[] ions = ELEMENT_IONS.get(formula);
        return ions == null ? null : Arrays.asList(ions);
    }

    /** 铺拼音候选芯片：展开态按 5 个一行，收起态单行横向滚动。 */
    private void addPinyinChips(List<PinyinMatcher.Candidate> list) {
        int perRow = expanded ? 5 : 20;
        LinearLayout row = null;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < list.size(); i++) {
            if (expanded && i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                candExpanded.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) (46 * d)));
            }
            View chip = makeCand(list.get(i), i == 0);
            if (expanded) row.addView(chip);
            else candList.addView(chip);
        }
    }

    /** 铺复合单位芯片；head 为 true 表示排在最前、第一个高亮。 */
    private void addUnitChips(List<String> units, boolean head) {
        if (expanded) {
            LinearLayout urow = new LinearLayout(this);
            urow.setOrientation(LinearLayout.HORIZONTAL);
            urow.setGravity(Gravity.CENTER_VERTICAL);
            candExpanded.addView(urow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (46 * getResources().getDisplayMetrics().density)));
            for (int i = 0; i < units.size(); i++) {
                urow.addView(makeUnitCand(units.get(i), head && i == 0));
            }
        } else {
            for (int i = 0; i < units.size(); i++) {
                candList.addView(makeUnitCand(units.get(i), head && i == 0));
            }
        }
    }

    /** 铺联想符号芯片（↓ / ↑ / 离子）；head 为 true 时首个高亮。 */
    private void addSymbolChips(List<String> symbols, boolean head) {
        if (expanded) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            candExpanded.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (46 * getResources().getDisplayMetrics().density)));
            for (int i = 0; i < symbols.size(); i++) {
                row.addView(makeSymbolCand(symbols.get(i), head && i == 0));
            }
        } else {
            for (int i = 0; i < symbols.size(); i++) {
                candList.addView(makeSymbolCand(symbols.get(i), head && i == 0));
            }
        }
    }

    // ---------- 复合单位候选（2026-10-26） ----------

    /**
     * 基础单位 → 常见复合单位。键统一用小写（直通模式自动大写会把 kg 变成 KG，
     * 这里按小写查表）；字母大小写与上下标按化学/物理书写习惯固定，不受输入影响。
     */
    private static final Map<String, String[]> UNIT_COMBOS = new LinkedHashMap<>();

    static {
        UNIT_COMBOS.put("mol", new String[]{"mol/L", "mol/mL", "mol/m³", "mol/kg",
                "mol/(L·s)", "mmol/L", "μmol/L"});
        UNIT_COMBOS.put("mmol", new String[]{"mmol/L", "mmol/mL", "mmol/kg"});
        UNIT_COMBOS.put("kg", new String[]{"kg/m³", "kg/mol", "kg/m²", "kg/(m·s)"});
        UNIT_COMBOS.put("g", new String[]{"g/mol", "g/L", "g/mL", "g/cm³"});
        UNIT_COMBOS.put("mg", new String[]{"mg/L", "mg/mL", "mg/kg"});
        UNIT_COMBOS.put("l", new String[]{"L/mol", "L/(mol·s)", "L/min", "L/s"});
        UNIT_COMBOS.put("ml", new String[]{"mL/min", "mL/s", "mL/mol"});
        UNIT_COMBOS.put("m", new String[]{"m/s", "m/s²", "m³", "m³/s"});
        UNIT_COMBOS.put("cm", new String[]{"cm/s", "cm³", "cm²"});
        UNIT_COMBOS.put("s", new String[]{"s⁻¹", "s/mol", "s/(mol·L)"});
        UNIT_COMBOS.put("pa", new String[]{"Pa·s", "Pa/(m·s)", "Pa·m³/mol"});
        UNIT_COMBOS.put("n", new String[]{"N/m²", "N·m", "N/m"});
        UNIT_COMBOS.put("j", new String[]{"J/mol", "J/(mol·K)", "J/(kg·K)", "J/K"});
        UNIT_COMBOS.put("kj", new String[]{"kJ/mol", "kJ/(kg·K)", "kJ/kg"});
        UNIT_COMBOS.put("w", new String[]{"W/m²", "W/(m·K)", "W/(m²·K)"});
        UNIT_COMBOS.put("v", new String[]{"V/m", "V/mol"});
        UNIT_COMBOS.put("a", new String[]{"A/m²", "A·h", "A·h/kg"});
    }

    // ---------- 上屏联想符号（2026-10-27） ----------

    /** 常见气体（规范化化学式）：上屏后在联想栏给出「↑」。 */
    private static final Set<String> GAS_FORMULAS = new HashSet<>(Arrays.asList(
            "H₂", "O₂", "N₂", "F₂", "Cl₂", "O₃", "CO", "CO₂", "SO₂", "SO₃", "NO", "NO₂",
            "N₂O", "NH₃", "HCl", "H₂S", "HF", "HBr", "HI", "CH₄", "C₂H₄", "C₂H₂"));

    /** 常见难溶物/沉淀（规范化化学式）：上屏后在联想栏给出「↓」。 */
    private static final Set<String> PRECIPITATE_FORMULAS = new HashSet<>(Arrays.asList(
            "AgCl", "AgBr", "AgI", "Ag₂CO₃", "Ag₂S", "BaSO₄", "BaSO₃", "BaCO₃", "BaCrO₄",
            "CaCO₃", "Ca₃(PO₄)₂", "Cu(OH)₂", "CuCO₃", "CuS", "Fe(OH)₂", "Fe(OH)₃", "FeS",
            "FeCO₃", "Mg(OH)₂", "MgCO₃", "Al(OH)₃", "Zn(OH)₂", "ZnS", "PbSO₄", "PbI₂",
            "PbS", "MnCO₃"));

    /** 单独元素 → 该单质常见离子电荷符号（Fe → ²⁺、³⁺；Cl → ⁻）。 */
    private static final Map<String, String[]> ELEMENT_IONS = new LinkedHashMap<>();
    static {
        ELEMENT_IONS.put("H", new String[]{"⁺"});
        ELEMENT_IONS.put("Na", new String[]{"⁺"});
        ELEMENT_IONS.put("K", new String[]{"⁺"});
        ELEMENT_IONS.put("Ag", new String[]{"⁺"});
        ELEMENT_IONS.put("Mg", new String[]{"²⁺"});
        ELEMENT_IONS.put("Ca", new String[]{"²⁺"});
        ELEMENT_IONS.put("Ba", new String[]{"²⁺"});
        ELEMENT_IONS.put("Zn", new String[]{"²⁺"});
        ELEMENT_IONS.put("Cu", new String[]{"²⁺", "⁺"});
        ELEMENT_IONS.put("Fe", new String[]{"²⁺", "³⁺"});
        ELEMENT_IONS.put("Al", new String[]{"³⁺"});
        ELEMENT_IONS.put("F", new String[]{"⁻"});
        ELEMENT_IONS.put("Cl", new String[]{"⁻"});
        ELEMENT_IONS.put("Br", new String[]{"⁻"});
        ELEMENT_IONS.put("I", new String[]{"⁻"});
        ELEMENT_IONS.put("O", new String[]{"²⁻"});
        ELEMENT_IONS.put("S", new String[]{"²⁻"});
        ELEMENT_IONS.put("N", new String[]{"³⁻"});
    }

    /** 上屏后保留在联想栏的符号（↓ / ↑ / 离子）；重新输入任何字符即清空。 */
    private final List<String> suggestSymbols = new ArrayList<>();

    /**
     * 组合区内容正好是一个基础单位时，返回对应的复合单位列表；否则 null。
     * 单字母只放行 L（升），避免输入元素符号 N、S、J 时被单位候选干扰。
     */
    private List<String> unitCandidates(String raw) {
        if (raw.length() < 2 && !raw.equalsIgnoreCase("L")) return null;
        String[] arr = UNIT_COMBOS.get(raw.toLowerCase(Locale.ROOT));
        return arr == null ? null : Arrays.asList(arr);
    }

    /** 单位候选芯片：副标题标明「复合单位」，点选上屏并清空组合区。 */
    private View makeUnitCand(String unit, boolean primary) {
        View box = makeMiniCand(unit, "复合单位", primary);
        box.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(unit, 1);
                composing.setLength(0);
                updateComposition();
            }
        });
        return box;
    }

    /**
     * 联想符号芯片（2026-10-27）：↓ 沉淀符号 / ↑ 气体符号 / 离子电荷。
     * 直通模式点选上屏符号本身（Fe 之后点 ³⁺ → Fe³⁺）；
     * 自动配平模式（2026-11-08）组合区还没上屏，必须追加到组合区，
     * 否则会把已输入的 Fe 丢掉、导致离子无法组成完整化学式。
     */
    private View makeSymbolCand(String symbol, boolean primary) {
        String subtitle = "↓".equals(symbol) ? "沉淀符号"
                : "↑".equals(symbol) ? "气体符号" : "离子";
        View box = makeMiniCand(symbol, subtitle, primary);
        box.setOnClickListener(v -> {
            if (keyboard != null && keyboard.isChemAutoMode()) {
                composing.append(symbol);
                suggestSymbols.clear();
                updateComposition();
                return;
            }
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(symbol, 1);
                composing.setLength(0);
                suggestSymbols.clear();
                updateComposition();
            }
        });
        return box;
    }

    /**
     * 候选芯片底色（2026-11-03）：化学候选栏与普通候选栏共用同一套外观。
     * 首个候选压灰块（浅 #D9D9D9 / 深 #4A4A4A）圆角 6，其余透明。
     */
    private SmoothDrawable candChipBg(boolean primary) {
        float d = getResources().getDisplayMetrics().density;
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(primary ? (darkMode ? 0xFF4A4A4A : 0xFFD9D9D9) : Color.TRANSPARENT);
        g.setCornerRadius(6 * d);
        return g;
    }

    /** 候选小芯片的公共外观（主文本 + 副标题 + 直角描边），点击行为由调用方挂。 */
    private View makeMiniCand(String main, String subtitle, boolean primary) {
        float d = getResources().getDisplayMetrics().density;
        int accent = accentColor();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        // 候选栏容器只有 35dp 高，两行芯片必须收紧上下内边距，否则副标题会被裁掉
        box.setPadding((int) (12 * d), (int) (1 * d), (int) (12 * d), (int) (1 * d));

        TextView f = new TextView(this);
        f.setText(main);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        f.setIncludeFontPadding(false);
        f.setTextColor(primary ? accent : (darkMode ? 0xFFE9EDF0 : 0xFF1B2226));
        TextView n = new TextView(this);
        n.setText(subtitle);
        n.setTextSize(10);
        n.setSingleLine();
        n.setIncludeFontPadding(false);
        n.setTextColor(primary ? (darkMode ? 0xFFB8C2C8 : 0xFF6B767D)
                : (darkMode ? 0x80FFFFFF : 0xFF9AA6AC));
        box.addView(f);
        box.addView(n);

        box.setBackground(candChipBg(primary));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // 上下不留边距，否则两行芯片高度超过 35dp 候选栏会被裁切
        float m = 3f;
        lp.setMargins((int) (m * d), 0, (int) (m * d), 0);
        box.setLayoutParams(lp);
        return box;
    }

    private View makeCand(PinyinMatcher.Candidate c, boolean primary) {
        float d = getResources().getDisplayMetrics().density;
        int accent = accentColor();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (12 * d), (int) (2 * d), (int) (12 * d), (int) (2 * d));

        TextView f = new TextView(this);
        f.setText(c.formula);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        f.setIncludeFontPadding(false);
        TextView n = new TextView(this);
        n.setText(c.entry.name);
        n.setTextSize(10);
        n.setSingleLine();
        n.setIncludeFontPadding(false);
        box.addView(f);
        box.addView(n);

        f.setTextColor(primary ? accent : (darkMode ? 0xFFE9EDF0 : 0xFF000000));
        n.setTextColor(primary ? (darkMode ? 0xFFB8C2C8 : 0xFF6B767D)
                : (darkMode ? 0x80FFFFFF : 0xFF9AA6AC));
        box.setBackground(candChipBg(primary));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        float m = 3f;
        lp.setMargins((int) (m * d), 0, (int) (m * d), 0);
        box.setLayoutParams(lp);

        PinyinMatcher.Candidate cand = c;
        box.setOnClickListener(v -> commitCandidate(cand));
        box.setOnLongClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(cand.entry.name, 1);
                composing.setLength(0);
                updateComposition();
                toast("已输入名称：" + cand.entry.name);
            }
            return true;
        });
        return box;
    }

    private View makeFallback() {
        float d = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText("空格：直接上屏");
        tv.setTextSize(13);
        tv.setTextColor(darkMode ? 0x80FFFFFF : 0xFF9AA6AC);
        tv.setPadding((int) (10 * d), 0, (int) (10 * d), 0);
        tv.setSingleLine();
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setOnClickListener(v -> commitAndSuggest(composing.toString()));
        return tv;
    }

    private void commitCandidate(PinyinMatcher.Candidate c) {
        commitAndSuggest(c.formula);
    }

    /**
     * 上屏文本并挂上联想符号（2026-10-27）：沉淀化合物 → ↓、气体 → ↑、
     * 单独元素 → 常见离子电荷符号。所有「上屏」路径都走这里，
     * 保证无论是选拼音候选、按空格还是点规范化学式，联想栏都能给出对应符号。
     */
    private void commitAndSuggest(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // 自动配平模式（2026-11-08）：只把文本累积到反应栏输入槽，不自动生成芯片；
        // 只有用户点蓝色加号（ChemKeyboardView.confirmCurrentSlot）才确认成一个反应物
        if (keyboard != null && keyboard.isChemAutoMode()) {
            // 字母逐键输入走 onChar 直接累积；这里只处理候选 / 空格 / 回车 / 规范化学式的确认，
            // 用确认结果覆盖输入槽内容，仍不生成芯片
            composing.setLength(0);
            composing.append(text);
            suggestSymbols.clear();
            updateComposition();
            return;
        }
        ic.commitText(text, 1);
        composing.setLength(0);
        suggestSymbols.clear();
        List<String> s = suggestionsFor(text);
        if (s != null) suggestSymbols.addAll(s);
        updateComposition();
    }

    /** 清掉上屏后的联想符号并刷新候选栏（空格/删除导致联想失效时调用）。 */
    private void clearSuggestions() {
        if (suggestSymbols.isEmpty()) return;
        suggestSymbols.clear();
        refreshCandidates();
    }

    // ---------- 直通模式的化学式规范化 ----------

    private static final String[] SUB_DIGITS = {"₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉"};

    /** 全部元素符号（含历史名），用于判断一段字母串是不是化学式。 */
    private static final Set<String> ALL_ELEMENTS = new HashSet<>(Arrays.asList(
            "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne", "Na", "Mg", "Al", "Si",
            "P", "S", "Cl", "Ar", "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni",
            "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr", "Nb", "Mo",
            "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn", "Sb", "Te", "I", "Xe", "Cs", "Ba",
            "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb",
            "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg", "Tl", "Pb", "Bi", "Po",
            "At", "Rn", "Fr", "Ra", "Ac", "Th", "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf",
            "Es", "Fm", "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn",
            "Nh", "Fl", "Mc", "Lv", "Ts", "Og"));

    private static final String[] SUP_DIGITS = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"};
    /** 上标数字串（符号页「角标」键的顺序），用于把 ⁿ 还原成普通数字。 */
    private static final String SUP_DIGIT_CHARS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    private static final Pattern FORMULA_SHAPE =
            Pattern.compile("^[0-9]*([A-Z][a-z]?[0-9]*|\\([^)]*\\)[0-9]*)+$");
    /** 离子电荷尾巴（ASCII）：Fe^3+ / OH^- / SO4^2-（2026-11-08）。 */
    private static final Pattern ION_CHARGE_TAIL =
            Pattern.compile("^(.+)\\^([0-9]*[+-])$");
    /** 同位素写法：质量数 + 单个元素符号，如 2H、14C、235U（2026-10-27）。 */
    private static final Pattern ISOTOPE_SHAPE = Pattern.compile("^(\\d{1,3})([A-Z][a-z]?)$");
    private static final Pattern ELEM_TOKEN = Pattern.compile("[A-Z][a-z]?");

    /**
     * 直通模式下的化学式规范化：把 CuSO4 → CuSO₄、Fe2O3 → Fe₂O₃、Ca(OH)2 → Ca(OH)₂。
     * 开头系数保留为普通数字。不是化学式（或无需改动）时返回 null。
     */
    private String normalizeFormula(String raw) {
        String t = raw.trim();
        if (t.isEmpty()) return null;
        // 2026-11-08 离子支持：先统一成 ASCII（Fe³⁺ / Fe3+ → Fe^3+），再拆出尾部电荷，
        // 主体仍按化学式规范化，最后把电荷还原成上标显示，离子也能正确识别。
        String charge = "";
        String ascii = ChemKeyboardView.toAsciiFormula(t);
        Matcher cm = ION_CHARGE_TAIL.matcher(ascii);
        if (cm.matches()) {
            ascii = cm.group(1);
            charge = cm.group(2);
        }
        t = ascii;
        if (t.isEmpty() || !FORMULA_SHAPE.matcher(t).matches()) return null;
        if (t.length() < 2 && charge.isEmpty()) return null;
        // 至少要含一个真实元素符号，避免把英文单词误判成化学式
        Matcher m = ELEM_TOKEN.matcher(t);
        int elems = 0;
        while (m.find()) {
            if (ALL_ELEMENTS.contains(m.group())) elems++;
        }
        if (elems == 0) return null;

        // 同位素质荷数（2026-10-27）：整串只有「数字 + 单个元素」时数字是质量数，写成上标，
        // 如 2H → ²H、14C → ¹⁴C、235U → ²³⁵U；带其他原子的 2H2O 仍按系数处理，不误伤配平系数。
        Matcher iso = ISOTOPE_SHAPE.matcher(t);
        if (iso.matches() && ALL_ELEMENTS.contains(iso.group(2))) {
            String mass = iso.group(1);
            StringBuilder sb = new StringBuilder(mass.length() + 2);
            for (int i = 0; i < mass.length(); i++) sb.append(SUP_DIGITS[mass.charAt(i) - '0']);
            return sb.append(iso.group(2)).toString();
        }

        StringBuilder sb = new StringBuilder(t.length() + 4);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean afterAtom = i > 0
                    && (Character.isLetter(t.charAt(i - 1)) || t.charAt(i - 1) == ')');
            if (c >= '0' && c <= '9' && afterAtom) {
                sb.append(SUB_DIGITS[c - '0']);
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString();
        if (!charge.isEmpty()) out += prettyCharge(charge);
        return out.equals(raw.trim()) ? null : out;
    }

    /** ASCII 电荷 → 上标显示：3+ → ³⁺、2- → ²⁻、+ → ⁺。 */
    private static String prettyCharge(String c) {
        StringBuilder sb = new StringBuilder(c.length());
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(SUP_DIGITS[ch - '0']);
            else if (ch == '+') sb.append('⁺');
            else if (ch == '-') sb.append('⁻');
        }
        return sb.toString();
    }

    /** 规范化候选芯片（与化学式候选同风格，副标题说明用途）。 */
    private View makeFormulaCand(String formula) {
        float d = getResources().getDisplayMetrics().density;
        int accent = accentColor();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (12 * d), (int) (2 * d), (int) (12 * d), (int) (2 * d));

        TextView f = new TextView(this);
        f.setText(formula);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        f.setIncludeFontPadding(false);
        f.setTextColor(accent);
        TextView n = new TextView(this);
        n.setText("规范化学式");
        n.setTextSize(10);
        n.setSingleLine();
        n.setIncludeFontPadding(false);
        n.setTextColor(darkMode ? 0xFFB8C2C8 : 0xFF6B767D);
        box.addView(f);
        box.addView(n);

        box.setBackground(candChipBg(true));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        float m = 3f;
        lp.setMargins((int) (m * d), 0, (int) (m * d), 0);
        box.setLayoutParams(lp);

        box.setOnClickListener(v -> commitAndSuggest(formula));
        return box;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // 供主题名展示使用（保持常量引用，避免裁剪）
    static {
        if (THEME_NAMES.length != THEME_IDS.length) throw new IllegalStateException();
    }
}
