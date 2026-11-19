#!/usr/bin/env python3
"""Single source of the window / Stage copy (design content.tone, D28: short spoken sentences, no 「首先 / 其次」).
Writes app/src/main/res/values/strings.xml (English) and values-zh/strings.xml (Chinese).
  python3 -I tools/strings.py
"""
import os, re
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")

S = [
 # design sample screens.js STR (copy kept as written, D28), + what the sample did not cover in the same tone
 ("app_name", "包扎课", "Bandage Wrap"),
 ("tab_wraps", "包扎", "Wraps"),
 ("tab_settings", "设置", "Settings"),
 ("loading", "准备中…", "Getting ready…"),
 ("load_tip", "一会儿它就在这条手臂上缠给你看。", "In a moment it wraps this arm while you watch."),
 ("pick", "挑一种包扎", "Pick a wrap"),
 ("pick_sub", "看它在手臂上一圈一圈缠", "Watch it go round the arm, turn by turn"),
 ("turns_about", "约 %1$d 圈 · %2$s", "About %1$d turns · %2$s"),
 ("turn_of", "第 %1$d / %2$d 圈", "Turn %1$d of %2$d"),
 ("play", "播放", "Play"),
 ("pause", "暂停", "Pause"),
 ("prev", "上一圈", "Back"),
 ("next", "下一圈", "Next"),
 ("slow", "慢放", "Slow"),
 ("slow_on", "慢放 0.5×", "Slow 0.5×"),
 ("why_lab", "为什么：", "Why:"),
 ("hint_table", "按住拖动手臂 · 摇杆左右转动", "Hold and drag the arm · stick left or right to turn"),
 ("hint_own", "在你手臂上 · 手臂别挪", "On your arm · keep it still"),
 ("more", "更多", "More"),
 ("m_on_me", "放到我手臂上", "Put it on my arm"),
 ("m_table", "回到桌上看", "Back to the table arm"),
 ("m_xray", "透视", "See-through"),
 ("m_spin", "自动转一圈", "Spin it once"),
 ("m_sound", "声音", "Sound"),
 ("m_table_short", "回到桌上看", "Table arm"),
 ("m_realign", "重新对位", "Line up again"),
 ("on", "开", "On"),
 ("off", "关", "Off"),
 ("close_menu", "收起", "Close"),
 ("building", "正在把绷带摆到手臂上…", "Laying the bandage on the arm…"),
 ("done_title", "缠好了。转着看看。", "Done. Turn it round and have a look."),
 ("how", "怎么走", "How it goes"),
 ("why", "为什么", "Why"),
 ("seen", "7 种里看过 %d 种", "%d of 7 watched"),
 ("next_wrap", "下一种：%s", "Next: %s"),
 ("to_list", "回到列表", "Back to the list"),
 ("again", "再看一遍", "Watch again"),
 ("align_big", "把手臂搭稳，别动。看到它亮起来就对上了。", "Rest your arm and hold still. When it lights up, you're lined up."),
 ("align_hint_fingers", "手指并拢、伸直，拇指自然张开。", "Fingers together and straight, thumb relaxed."),
 ("see_left", "看到左手 ✓", "Left hand found ✓"),
 ("see_right", "看到右手 ✓", "Right hand found ✓"),
 ("no_left", "还没看到左手：肘搭桌上，掌心朝下", "No left hand yet: elbow on the desk, palm down"),
 ("no_right", "还没看到右手：肘搭桌上，掌心朝下", "No right hand yet: elbow on the desk, palm down"),
 ("manual_short", "手动对一下", "Line it up myself"),
 ("slow_find", "要不先在桌上看？", "Watch it on the table arm for now?"),
 ("wait", "再等等", "Keep waiting"),
 ("manual_big", "捏住小球，拖到你手腕上。摇杆左右转动。", "Pinch a dot and drag it onto your wrist. Stick left or right to turn."),
 ("aligned", "对上了", "Lined up"),
 ("no_track", "这台设备现在拿不到手部追踪。", "This headset can't see your hands right now."),
 ("no_track_sub", "在桌上的手臂上看也一样。", "It looks just the same on the table arm."),
 ("manual", "手动对位", "Line it up myself"),
 ("retry", "再试一次", "Try again"),
 ("s_lang", "语言", "Language"),
 ("lang_auto", "跟系统", "System"),
 ("lang_zh", "中文", "中文"),
 ("lang_en", "English", "English"),
 ("s_side", "看哪只手臂", "Which arm"),
 ("side_left", "左手臂", "Left arm"),
 ("side_right", "右手臂", "Right arm"),
 ("s_arm", "我的手臂", "My arm"),
 ("s_arm_v", "放到我手臂上时用", "Used for “on my arm”"),
 ("forearm", "前臂", "Forearm"),
 ("forearm_auto", "按手估", "Auto"),
 ("wrist", "手腕", "Wrist"),
 ("girth_thin", "细", "S"),
 ("girth_mid", "中", "M"),
 ("girth_thick", "粗", "L"),
 ("s_sound", "声音", "Sound"),
 ("vol_low", "轻", "Low"),
 ("vol_mid", "中", "Mid"),
 ("vol_high", "响", "High"),
 ("s_front", "窗口回到面前", "Bring the window back"),
 ("s_front_v", "按你现在面朝的方向重摆", "Re-places it where you face now"),
 ("look_ahead", "看正前方，别动", "Look straight ahead and hold still"),
 ("level_hint", "平视前方，窗口就出来", "Look straight ahead and the window comes up"),
 ("open_panel", "打开窗口", "Open window"),
 ("s_about", "关于", "About"),
 ("s_about_v", "%s · 出处 · 免责", "%s · sources · disclaimer"),
 ("about_body", "步骤参考 St John Ambulance 公开急救指引（2025-04 临床复核版），已改写；反折螺旋、拇指人字按护理学通用手法写，未核对 SJA。手臂是程序生成的参数化模型；声音是 CC0 录音（Kenney）。只教手法，不替代急救培训，不做伤情判断。", "Steps follow St John Ambulance public first-aid guides (clinically reviewed 04/2025), rewritten; the reverse spiral and thumb spica follow common nursing technique, not checked against SJA. The arm is a procedural model; sounds are CC0 recordings (Kenney). Technique only. Not first aid training, no injury advice."),
 ("about_free", "与 St John Ambulance、Kenney 无关联，未获其认可。", "Not affiliated with or endorsed by St John Ambulance or Kenney."),
 ("safety", "只教手法，不替代急救培训。", "Technique only. Not first aid training."),
 ("s_howto", "怎么操作", "How to use"),
 ("how_to_title", "怎么操作", "How to use"),
 ("how_to_pick", "挑一种：看着列表里的一行捏一下，或者手柄指着按扳机。", "Pick a wrap: look at a row and pinch, or point the controller and pull the trigger."),
 ("how_to_play", "播放 / 暂停、上一圈、下一圈、慢放：窗口里的大按钮，捏一下或扳机。", "Play / pause, back, next, slow: the big keys in the window, pinch or trigger."),
 ("how_to_drag", "挪手臂：看着手臂捏住拖，或者手柄指着按住扳机拖。松手就停在那里。", "Move the arm: look at it, pinch and drag, or hold the trigger on it and drag. It stays where you let go."),
 ("how_to_turn", "转手臂：手柄摇杆左右推。或者「更多」里点「自动转一圈」。", "Turn the arm: push the stick left or right, or tap More → Spin it once."),
 ("how_to_xray", "看下面几层：「更多」里打开「透视」。", "See the layers below: More → See-through."),
 ("how_to_mine", "放到自己手臂上：「更多」里点「放到我手臂上」，手臂搭稳 1 秒。", "On your own arm: More → Put it on my arm, then hold your arm still for a second."),
 ("back", "返回", "Back"),
 ("s_exit", "退出应用", "Quit"),
 ("exit_q", "要走了？", "Leaving?"),
 ("cancel", "取消", "Cancel"),
 ("exit", "退出", "Quit"),
 ("finished", "缠好了", "Done"),
 ("part_forearm", "前臂", "Forearm"),
 ("part_wrist", "手腕", "Wrist"),
 ("part_hand", "手背", "Hand"),
 ("part_thumb", "拇指", "Thumb"),
 ("part_elbow", "肘", "Elbow"),
 ("seen_mark", "看过", "Watched"),
]

def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'").replace('"', '\\"')

def write(path, idx):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- generated by tools/strings.py: edit there -->\n<resources>\n')
        for row in S:
            f.write(f'    <string name="{row[0]}">{esc(row[idx])}</string>\n')
        f.write("</resources>\n")

if __name__ == "__main__":
    names = [r[0] for r in S]
    assert len(names) == len(set(names)), "duplicate string ids"
    for r in S:
        for t in r[1:]:
            assert not re.search(r"首先|其次|Embodit", t), r
    write(os.path.join(RES, "values", "strings.xml"), 2)
    write(os.path.join(RES, "values-zh", "strings.xml"), 1)
    print(len(S), "strings")
