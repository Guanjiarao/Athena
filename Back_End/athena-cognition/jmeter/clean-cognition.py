# -*- coding: utf-8 -*-
"""清空指定用户在 cognition 11 张表里的数据（功能测试前置重置）。"""
import pymysql

USER_ID = 1024
TABLES = [
    'cognition_decision_log', 'cognition_action_feedback', 'cognition_action',
    'cognition_topic_evidence', 'cognition_topic', 'cognition_digest_evidence',
    'cognition_digest_clue', 'cognition_digest_task', 'cognition_digest',
    'cognition_evidence', 'cognition_clue',
]
conn = pymysql.connect(host='8.154.26.1', user='root', password='athena', database='athena')
cur = conn.cursor()
for t in TABLES:
    cur.execute(f'DELETE FROM {t} WHERE user_id=%s', (USER_ID,))
    print(f'{t}: {cur.rowcount} rows deleted')
conn.commit()
conn.close()
print('done')
