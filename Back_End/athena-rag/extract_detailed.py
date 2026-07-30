import re

with open('debug_case_001.log', 'r', encoding='utf-8', errors='ignore') as f:
    content = f.read()

# 按轮次分割
turns = re.split(r'turnCount=(\d+)', content)

for i in range(1, min(17, len(turns)), 2):  # 处理前8轮
    turn_num = turns[i]
    turn_content = turns[i+1]
    
    print(f"\n{'='*70}")
    print(f"【轮次 {turn_num}】")
    print('='*70)
    
    # 提取用户输入
    user_match = re.search(r'用户输入[:\s]+(.+)', turn_content)
    if user_match:
        print(f"用户输入: {user_match.group(1).strip()}")
    
    # 提取已回答槽位
    answered_match = re.search(r'answeredSlots: \[([^\]]+)\]', turn_content)
    if answered_match:
        print(f"已回答槽位: {answered_match.group(1)}")
    
    # 提取候选 gaps
    gaps_match = re.search(r'过滤 gaps 结果[^\[]*\[([^\]]+)\]', turn_content)
    if not gaps_match:
        gaps_match = re.search(r'gaps: \d+, gaps: \[([^\]]+)\]', turn_content)
    if gaps_match:
        print(f"候选 gaps: [{gaps_match.group(1)}]")
    
    # 提取选择策略
    strategy_match = re.search(r'选择策略[:\s]+([^,\n]+)', turn_content)
    if strategy_match:
        print(f"选择策略: {strategy_match.group(1).strip()}")
    
    # 提取选择的槽位
    selected_match = re.search(r'选择槽位[:\s]+(\w+)', turn_content)
    if not selected_match:
        selected_match = re.search(r'pendingSlots: \[([^\]]+)\]', turn_content)
    if selected_match:
        print(f"选择槽位: {selected_match.group(1)}")
    
    # 提取系统提问
    question_match = re.search(r'systemQuestion[:\s]+(.+?)(?=\n|$)', turn_content)
    if question_match:
        print(f"系统提问: {question_match.group(1).strip()}")

