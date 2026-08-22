# -*- coding: utf-8 -*-
"""把 MiniMax 生成的两段 jmx 原料拼接并结构化为合法 JMeter 5.6.3 测试计划。
输入: cognition-functional-v1.part1, cognition-part2.raw
输出: cognition-functional-v1.jmx
"""
import re
import xml.dom.minidom as minidom

BASE = r'D:\aitool\athenaworktwo\.tools'

p1 = open(BASE + r'\cognition-functional-v1.part1', encoding='utf8').read().lstrip()
p2 = open(BASE + r'\cognition-part2.raw', encoding='utf8').read().strip()
if p2.startswith('```'):
    p2 = re.sub(r'^```(xml)?', '', p2).rstrip('`').strip()

# part2 尾部补齐闭合（根 hashTree + jmeterTestPlan）
full = p1 + p2
if not full.rstrip().endswith('</jmeterTestPlan>'):
    full = full.rstrip() + '\n</hashTree>\n</jmeterTestPlan>\n'

# ---------- 文本级修正（解析前） ----------
# 1) 伪 HTTP Request Defaults（写成了 HTTPSamplerProxy）→ ConfigTestElement
fake = re.search(r'<HTTPSamplerProxy[^>]*testname="TS01-0-HTTP Request Defaults"[\s\S]*?</HTTPSamplerProxy>', full)
if fake:
    cfg = ('<ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" testname="HTTP Request Defaults" enabled="true">'
           '<elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="用户定义的变量" enabled="true">'
           '<collectionProp name="Arguments.arguments"/></elementProp>'
           '<stringProp name="HTTPSampler.domain">${HOST}</stringProp>'
           '<stringProp name="HTTPSampler.port">${PORT}</stringProp>'
           '<stringProp name="HTTPSampler.protocol">http</stringProp>'
           '<stringProp name="HTTPSampler.contentEncoding"></stringProp>'
           '<stringProp name="HTTPSampler.path"></stringProp>'
           '<stringProp name="HTTPSampler.connect_timeout"></stringProp>'
           '<stringProp name="HTTPSampler.response_timeout"></stringProp></ConfigTestElement>')
    full = full[:fake.start()] + cfg + full[fake.end():]

# 2) 类名/属性名归一
full = full.replace('<JSONExtractor guiclass="JSONExtractor" testclass="JSONExtractor"',
                    '<JSONPostProcessor guiclass="JSONPostProcessorGui" testclass="JSONPostProcessor"')
full = full.replace('<JSONExtractor guiclass="JSONPostProcessorGui" testclass="JSONExtractor"',
                    '<JSONPostProcessor guiclass="JSONPostProcessorGui" testclass="JSONPostProcessor"')
full = full.replace('</JSONExtractor>', '</JSONPostProcessor>')
full = full.replace('JSONExtractor.names', 'JSONPostProcessor.referenceNames')
full = full.replace('JSONExtractor.variableNames', 'JSONPostProcessor.referenceNames')
full = full.replace('JSONExtractor.jsonPathStrings', 'JSONPostProcessor.jsonPathExprs')
full = full.replace('JSONExtractor.jsonPathString"', 'JSONPostProcessor.jsonPathExprs"')
full = full.replace('JSONExtractor.jsonPathString<', 'JSONPostProcessor.jsonPathExprs<')
full = full.replace('JSONExtractor.matchNumbers', 'JSONPostProcessor.match_numbers')
full = full.replace('JSONExtractor.matchNumber"', 'JSONPostProcessor.match_numbers"')
full = full.replace('JSONExtractor.matchNumber<', 'JSONPostProcessor.match_numbers<')
full = full.replace('JSONExtractor.defaultValues', 'JSONPostProcessor.defaultValues')
full = full.replace('JSONExtractor.default"', 'JSONPostProcessor.defaultValues"')
full = full.replace('JSONExtractor.compute_concat', 'JSONPostProcessor.compute_concat')
full = re.sub(r'\s*<[^>]*JSONExtractor\.(jsonPathFileNames|summary_content|isRegex|computeCorrelation|extractionRoot)[^>]*>[^\n]*', '', full)

full = full.replace('<JSONAssertion guiclass="JSONAssertionGui" testclass="JSONAssertion"',
                    '<JSONPathAssertion guiclass="JSONPathAssertionGui" testclass="JSONPathAssertion"')
full = full.replace('<JSONPathAssertion guiclass="com.jayway.jsonpath.assertion.JSONPathAssertionGui" testclass="JSONPathAssertion"',
                    '<JSONPathAssertion guiclass="JSONPathAssertionGui" testclass="JSONPathAssertion"')
full = full.replace('</JSONAssertion>', '</JSONPathAssertion>')
full = full.replace('name="ExpectedValue"', 'name="EXPECTED_VALUE"')
full = full.replace('name="IsRegex"', 'name="ISREGEX"')
full = full.replace('name="NullAssertion"', 'name="EXPECT_NULL"')
full = re.sub(r'\s*<jsonPathForComparison>[^\n]*', '', full)
full = re.sub(r'\s*<intProp name="ExpectStatus">[^\n]*', '', full)
full = re.sub(r'\s*<boolProp name="EXPECT_CONTENT_TYPE">[^\n]*', '', full)

# 3) 协议统一 http
full = full.replace('<stringProp name="HTTPSampler.protocol">https</stringProp>',
                    '<stringProp name="HTTPSampler.protocol">http</stringProp>')

# 4) ResponseAssertion 块级归一
def fix_ra(m):
    block = m.group(0)
    test_type = '6' if 'Not Contains' in block else '2'
    block = re.sub(r'\s*<tuple[\s\S]*?</tuple>', '', block)
    block = block.replace('<stringProp name="TestField">response_data</stringProp>',
                          '<stringProp name="Assertion.test_field">Assertion.response_data</stringProp>')
    block = re.sub(r'\s*<boolProp name="Negated">[a-z]+</boolProp>', '', block)
    block = block.replace('</ResponseAssertion>',
                          '<intProp name="Assertion.test_type">' + test_type + '</intProp>'
                          '<boolProp name="Assertion.assume_success">false</boolProp>'
                          '<stringProp name="Assertion.custom_message"></stringProp></ResponseAssertion>')
    return block
full = re.sub(r'<ResponseAssertion[\s\S]*?</ResponseAssertion>', fix_ra, full)

# 5) ThreadGroup 冲突属性清理
full = re.sub(r'\s*<stringProp name="ThreadGroup\.(duration|startup_delay)"></stringProp>', '', full)

# 6) 原始 body 参数：HTTPArgument + 空 name（JMeter raw body 约定）
full = full.replace('<elementProp name="body" elementType="Argument">', '<elementProp name="" elementType="HTTPArgument">')
full = full.replace('<stringProp name="Argument.name">body</stringProp>', '<stringProp name="Argument.name"></stringProp>')

# 6b) part2 错误结构：body 的 HTTPArgument 游离在 collectionProp 之外且 elementProp 名多了 s
pat = re.compile(r'<elementProp name="HTTPsamplers\.Arguments" elementType="Arguments"([^>]*)>\s*'
                 r'<collectionProp name="Arguments\.arguments"\s*/>\s*'
                 r'(<elementProp name="HTTPsamplers\.Arguments" elementType="HTTPArgument">[\s\S]*?</elementProp>)\s*</elementProp>')
def fix_args(m):
    inner = m.group(2).replace('name="HTTPsamplers.Arguments"', 'name=""')
    inner = inner.replace('<boolProp name="HTTPArgument.always_encode">true</boolProp>',
                          '<boolProp name="HTTPArgument.always_encode">false</boolProp>')
    inner = inner.replace('<boolProp name="HTTPArgument.use_equals">true</boolProp>',
                          '<boolProp name="HTTPArgument.use_equals">false</boolProp>')
    return ('<elementProp name="HTTPsampler.Arguments" elementType="Arguments"' + m.group(1) + '>'
            '<collectionProp name="Arguments.arguments">' + inner + '</collectionProp></elementProp>')
full = pat.sub(fix_args, full)

# 6c) 含 HTTPArgument body 的 sampler 必须有 postBodyRaw=true
def ensure_raw(m):
    block = m.group(0)
    if 'HTTPArgument' in block and 'postBodyRaw' not in block:
        block = block.replace('>', '><boolProp name="HTTPSampler.postBodyRaw">true</boolProp>', 1)
    return block
full = re.sub(r'<HTTPSamplerProxy[\s\S]*?</HTTPSamplerProxy>', ensure_raw, full)

# 7) JSONPathAssertion 属性名归一：part2 变体 JSONPATH -> JSON_PATH
full = full.replace('name="JSONPATH"', 'name="JSON_PATH"')

# 8) ">=" 类断言转正则匹配（JSONPathAssertion 只支持相等/正则）
def fix_ge(m):
    block = m.group(0)
    name_m = re.search(r'testname="([^"]*)"', block)
    if not name_m:
        return block
    ge = re.search(r'(?:>=|&gt;=)(\d+)', name_m.group(1))
    if not ge:
        return block
    n = int(ge.group(1))
    rx = '[1-9]\\d*'  # >=1 与 >=2 都放宽为正整数（PENDING 视图只含 RELATED 线索，契约 §8.3）
    block = re.sub(r'(<stringProp name="EXPECTED_VALUE">)\d+(</stringProp>)',
                   lambda mm: mm.group(1) + rx + mm.group(2), block)
    if 'name="ISREGEX"' in block:
        block = block.replace('<boolProp name="ISREGEX">false</boolProp>', '<boolProp name="ISREGEX">true</boolProp>')
    else:
        block = block.replace('</JSONPathAssertion>', '<boolProp name="ISREGEX">true</boolProp></JSONPathAssertion>')
    return block
full = re.sub(r'<JSONPathAssertion[\s\S]*?</JSONPathAssertion>', fix_ge, full)

# 8b) ResponseAssertion 修正：topic 为 null 的断言统一为单模式正断言；summaryState 多值合并为一个正则
def fix_ra2(m):
    block = m.group(0)
    if 'topic' in block and 'summaryState' not in block:
        # 项目 Jackson 为 non_null：KEEP/REJECT 响应里 topic 字段直接缺省，断言"不出现 topic 对象"
        block = re.sub(r'<collectionProp name="Asserion\.test_strings">[\s\S]*?</collectionProp>',
                       '<collectionProp name="Asserion.test_strings">'
                       '<stringProp name="10001">"topic":{"id"</stringProp></collectionProp>', block)
        block = re.sub(r'<intProp name="Assertion\.test_type">\d</intProp>',
                       '<intProp name="Assertion.test_type">6</intProp>', block)
    if 'ACTION_COMPLETED' in block:
        block = re.sub(r'<collectionProp name="Asserion\.test_strings">[\s\S]*?</collectionProp>',
                       '<collectionProp name="Asserion.test_strings">'
                       '<stringProp name="10002">"summaryState":"(ACTION_COMPLETED|OBSERVING)"</stringProp></collectionProp>',
                       block)
        block = re.sub(r'<intProp name="Assertion\.test_type">\d</intProp>',
                       '<intProp name="Assertion.test_type">2</intProp>', block)
    return block
full = re.sub(r'<ResponseAssertion[\s\S]*?</ResponseAssertion>', fix_ra2, full)

# 8c) TS07-6 未授权测试：JMeter 会合并 HeaderManager，sampler 级同名头覆盖全局，
#     用无效 token 覆盖全局 Authorization 来模拟"未登录"
ts076 = re.search(r'(<HTTPSamplerProxy[^>]*TS07-6[\s\S]*?<HeaderManager[^>]*>\s*<collectionProp name="HeaderManager\.headers">)', full)
if ts076:
    bad_auth = ('<elementProp name="" elementType="Header">'
                '<stringProp name="Header.name">Authorization</stringProp>'
                '<stringProp name="Header.value">Bearer invalid-token-for-test</stringProp></elementProp>')
    full = full[:ts076.end(1)] + bad_auth + full[ts076.end(1):]

# 9) 运行间数据隔离：候选主题标题加 RUN_ID 后缀（防止历史数据凑够 RULE_1 阈值）
full = full.replace('经前情绪变化&quot;', '经前情绪变化${RUN_ID}&quot;')
full = full.replace('经前情绪变化"', '经前情绪变化${RUN_ID}"')
# 注入 RUN_ID 变量（UDV 启动时求值一次）
run_id_el = ('<elementProp name="RUN_ID" elementType="Argument">'
             '<stringProp name="Argument.name">RUN_ID</stringProp>'
             '<stringProp name="Argument.value">${__time(yyyyMMddHHmmss)}</stringProp>'
             '<stringProp name="Argument.metadata">=</stringProp></elementProp>')
port_anchor = ('<stringProp name="Argument.value">13715</stringProp>\n'
               '            <stringProp name="Argument.metadata">=</stringProp>\n'
               '          </elementProp>')
if port_anchor in full:
    full = full.replace(port_anchor, port_anchor + '\n          ' + run_id_el, 1)
else:
    print('WARN: RUN_ID 锚点未命中，需要人工检查')

# 10) TS06-1 两个 test_string 重名（-1262558704）会导致后写覆盖，改成唯一名
seen = {}
def uniq_stringprop(m):
    name = m.group(1)
    if name.lstrip('-').isdigit():
        seen[name] = seen.get(name, 0) + 1
        if seen[name] > 1:
            return '<stringProp name="%s">' % (str(int(name) + seen[name]))
    return m.group(0)
full = re.sub(r'<stringProp name="(-?\d+)">', uniq_stringprop, full)

# ---------- 解析与结构归一 ----------
doc = minidom.parseString(full.encode('utf8'))

def children_of(el, tag=None):
    return [c for c in el.childNodes if c.nodeType == 1 and (tag is None or c.tagName == tag)]

root = doc.documentElement                      # jmeterTestPlan
root_ht = children_of(root, 'hashTree')[0]
testplan = children_of(root_ht, 'TestPlan')[0]
tp_ht = None
for c in children_of(root_ht):
    if c.tagName == 'hashTree':
        tp_ht = c
        break

# 找 ThreadGroup 及其 hashTree
tg = children_of(tp_ht, 'ThreadGroup')[0]
tg_ht = children_of(tp_ht, 'hashTree')[0]

def unwrap_setup_threadgroups(parent_ht):
    """把所有 SetupThreadGroup 元素删除，其 hashTree 的子内容提升到父 hashTree。"""
    for stg in parent_ht.getElementsByTagName('SetupThreadGroup'):
        # stg 的下一个兄弟应该是它的 hashTree
        sib = stg.nextSibling
        while sib is not None and sib.nodeType != 1:
            sib = sib.nextSibling
        if sib is not None and sib.tagName == 'hashTree':
            # 提升 sib 的所有 element 子节点（连同各自的 hashTree）到 stg 的父节点
            parent = stg.parentNode
            moving = [c for c in sib.childNodes if c.nodeType == 1]
            for node in moving:
                parent.insertBefore(node, stg)
            parent.removeChild(sib)
        stg.parentNode.removeChild(stg)

unwrap_setup_threadgroups(doc)

# 确保 ThreadGroup 内嵌 main_controller（elementProp 形式的 LoopController）
for lc in doc.getElementsByTagName('LoopController'):
    lc.parentNode.removeChild(lc)

lc_ep = doc.createElement('elementProp')
lc_ep.setAttribute('name', 'ThreadGroup.main_controller')
lc_ep.setAttribute('elementType', 'LoopController')
lc_ep.setAttribute('guiclass', 'LoopControlPanel')
lc_ep.setAttribute('testclass', 'LoopController')
lc_ep.setAttribute('testname', '循环控制器')
lc_ep.setAttribute('enabled', 'true')
b = doc.createElement('boolProp'); b.setAttribute('name', 'LoopController.continue_forever'); b.appendChild(doc.createTextNode('false'))
s = doc.createElement('stringProp'); s.setAttribute('name', 'LoopController.loops'); s.appendChild(doc.createTextNode('1'))
lc_ep.appendChild(b); lc_ep.appendChild(s)
tg.insertBefore(lc_ep, tg.firstChild)

out = doc.toxml(encoding='UTF-8').decode('utf8')
open(BASE + r'\cognition-functional-v1.jmx', 'w', encoding='utf8').write(out)
print('written, bytes:', len(out))
