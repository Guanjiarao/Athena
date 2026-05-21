import re
import sys

# 读取日志文件
with open('debug_case_001.log', 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()

turns = []
current_turn = {}

for i, line in enumerate(lines):
    # 检测轮次开始
    if '开始规划下一个问题' in line or 'turnCount=' in line:
        match = re.search(r'turnCount=(\d+)', line)
        if match:
            if current_turn:
                turns.append(current_turn)
            current_turn = {'turn': int(match.group(1)), 'lines': []}
    
    # 收集当前轮次的日志
    if current_turn:
        current_turn['lines'].append(line)

# 添加最后一个轮次
if current_turn:
    turns.append(current_turn)

# 提取每个轮次的关键信息
for turn_data in turns[:8]:  # 只处理前8轮
    turn_num = turn_data['turn']
    lines = turn_data['lines']
    
    print(f"\n{'='*60}")
    print(f"【轮次 {turn_num}】")
    print('='*60)
    
    # 提取用户输入
    for line in lines:
        if '用户输入:' in line or 'userInput:' in line:
            user_input = line.split(':', 2)[-1].strip()
            print(f"用户输入: {user_input}")
            break
    
    # 提取候选 gaps
    for line in lines:
        if '过滤 gaps 结果' in line or 'gaps:' in line and 'candidate' not in line:
            print(f"候选 gaps: {line.strip()}")
            break
    
    # 提取选择策略
    for line in lines:
        if '选择策略:' in line:
            print(f"选择策略: {line.strip()}")
            break
    
    # 提取选择的槽位
    for line in lines:
        if '选择槽位:' in line or 'pendingSlots:' in line:
            print(f"选择槽位: {line.strip()}")
            break
    
    # 提取系统提问
    for line in lines:
        if 'systemQuestion:' in line or '系统提问:' in line:
            question = line.split(':', 2)[-1].strip()
            print(f"系统提问: {question}")
            break

