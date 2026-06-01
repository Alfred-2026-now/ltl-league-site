#!/usr/bin/env python3
"""
LTL赛季数据初始化脚本
从Excel文件解析数据，生成SQL初始化语句
"""

import pandas as pd
from datetime import datetime

# Excel文件路径
EXCEL_FILE = '/Users/a58/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_3od56ftuayu622_69f2/msg/file/2026-05/LOL评分表0529.xlsx'

# 队伍state与队名映射（根据Excel中的队名确定）
TEAM_STATE_MAP = {
    '秦国队': '秦',
    '楚国队': '楚',
    '蜀国队': '蜀',
    '吴国队': '吴',
    '越国队': '越',
    '燕国队': '燕'
}

# Excel名字到数据库名字的映射（处理不一致问题）
PLAYER_NAME_MAP = {
    'ZerStaN': 'ZerstaN',
    '广寒枝（替补）': '广寒枝',
    'JAY': 'Puler',
    '万泉部诗人': '万泉诗人',
    '莫以故事丶诉于卿': '莫以故事、诉于卿',
    '坑货丶别靠近我': '坑货、别靠近我',
}

# 需要添加的自由人选手（从Excel的"玩家ID"和"当前身价"列提取）
# 这些选手的身价需要从"玩家ID"列匹配
FREE_AGENTS_VALUE_MAP = {
    '罗': 3300,      # 罗（自由人）
    '陶吉吉': 2859,   # 陶吉吉（自由人）
    'Yukari': 2818,   # Yukari（自由人）
    'Kenzhou': 1500,  # Kenzhou(教练，自由人））去掉后缀后的名字
}

def parse_excel():
    """解析Excel数据"""
    df = pd.read_excel(EXCEL_FILE, sheet_name='LTL')

    # 解析队伍数据
    teams = []
    team_rows = df[df['队名'].notna()]
    for _, row in team_rows.iterrows():
        team_name = row['队名']
        points = int(row['现在积分']) if pd.notna(row['现在积分']) else 0
        p_coins = round(row['队伍持有P币']) if pd.notna(row['队伍持有P币']) else 0
        rank = int(row['联赛排名']) if pd.notna(row['联赛排名']) else 0

        if team_name in TEAM_STATE_MAP:
            teams.append({
                'state': TEAM_STATE_MAP[team_name],
                'name': team_name,
                'points': points,
                'p_coins': p_coins,
                'rank': rank
            })

    # 解析选手数据
    players = []
    current_team = None

    for _, row in df.iterrows():
        # 检查是否是队伍行（可能有选手在同一行）
        if pd.notna(row['队名']) and row['队名'] in TEAM_STATE_MAP:
            current_team = row['队名']
            # 检查同一行是否有选手
            if pd.notna(row['成员ID']):
                player_name = row['成员ID']
                value = round(row['成员身价']) if pd.notna(row['成员身价']) else 2000
                deposit = round(row['选手持有P币']) if pd.notna(row['选手持有P币']) else 0
                players.append({
                    'team_name': current_team,
                    'name': player_name,
                    'value': value,
                    'deposit': deposit
                })
            continue

        # 解析选手（后续行）
        if pd.notna(row['成员ID']):
            player_name = row['成员ID']
            value = round(row['成员身价']) if pd.notna(row['成员身价']) else 2000
            deposit = round(row['选手持有P币']) if pd.notna(row['选手持有P币']) else 0

            players.append({
                'team_name': current_team,
                'name': player_name,
                'value': value,
                'deposit': deposit
            })

    return teams, players, df

def generate_sql(teams, players, df):
    """生成SQL初始化语句"""

    sql_lines = []
    sql_lines.append("-- LTL赛季数据初始化SQL")
    sql_lines.append(f"-- 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    sql_lines.append("")
    sql_lines.append("START TRANSACTION;")
    sql_lines.append("")

    # 1. 更新队伍数据
    sql_lines.append("-- 1. 更新队伍数据")
    for team in teams:
        sql_lines.append(
            f"UPDATE teams SET points = {team['points']}, p_coins = {team['p_coins']}, "
            f"`rank` = {team['rank']}, updated_at = NOW() WHERE state = '{team['state']}';"
        )
    sql_lines.append("")

    # 2. 更新选手身价和存款
    sql_lines.append("-- 2. 更新选手身价和存款")
    for player in players:
        team_state = TEAM_STATE_MAP.get(player['team_name'], 'NULL')
        # 使用名字映射，如果没有映射则使用原名字
        db_name = PLAYER_NAME_MAP.get(player['name'], player['name'])
        safe_name = db_name.replace("'", "''")
        sql_lines.append(
            f"UPDATE players p "
            f"SET value = {player['value']}, deposit = {player['deposit']}, updated_at = NOW() "
            f"WHERE p.name = '{safe_name}' "
            f"AND p.team_id = (SELECT id FROM teams WHERE state = '{team_state}');"
        )
    sql_lines.append("")

    # 3. 添加自由人选手（使用正确的身价）
    sql_lines.append("-- 3. 添加自由人选手")
    # 从Excel数据中提取自由人选手存款
    free_agent_deposits = {}
    deposit_rows = df[df['玩家 ID.1'].notna()]
    for _, row in deposit_rows.iterrows():
        name = str(row['玩家 ID.1']).strip()
        # 去掉可能的标点后缀
        for suffix in ['（自由人）', '（替补）', '(教练）', '(教练，自由人））']:
            if suffix in name:
                name = name.replace(suffix, '')
                break
        deposit = round(row['个人存款']) if pd.notna(row['个人存款']) else 0
        free_agent_deposits[name] = deposit

    # 使用映射表中的身价
    # 需要一个映射：去掉后缀后的名字 -> 原始名字（用于显示）
    FREE_AGENT_DISPLAY_NAMES = {
        '罗': '罗',
        '陶吉吉': '陶吉吉',
        'Yukari': 'Yukari',
        'Kenzhou': 'Kenzhou(教练）',
    }
    for name, value in FREE_AGENTS_VALUE_MAP.items():
        deposit = free_agent_deposits.get(name, 0)
        display_name = FREE_AGENT_DISPLAY_NAMES.get(name, name)
        safe_name = display_name.replace("'", "''")
        sql_lines.append(
            f"INSERT INTO players (team_id, name, value, deposit, status, created_at, updated_at) "
            f"VALUES (NULL, '{safe_name}', {value}, {deposit}, 3, NOW(), NOW());"
        )
    sql_lines.append("")

    # 4. 创建初始化流水记录（可选）
    sql_lines.append("-- 4. 创建队伍P币初始化流水")
    for team in teams:
        sql_lines.append(
            f"INSERT INTO p_ledger "
            f"(team_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) "
            f"SELECT id, 'admin_adjustment', {team['p_coins']}, '赛季初始化', 'season_init', 0, {team['p_coins']}, NOW(), 0 "
            f"FROM teams WHERE state = '{team['state']}';"
        )
    sql_lines.append("")

    sql_lines.append("-- 5. 创建选手存款初始化流水")
    for player in players:
        team_state = TEAM_STATE_MAP.get(player['team_name'], 'NULL')
        db_name = PLAYER_NAME_MAP.get(player['name'], player['name'])
        safe_name = db_name.replace("'", "''")
        sql_lines.append(
            f"INSERT INTO player_deposit_ledger "
            f"(player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) "
            f"SELECT p.id, 'admin_adjustment', {player['deposit']}, '赛季初始化', 'season_init', 0, {player['deposit']}, NOW(), 0 "
            f"FROM players p WHERE p.name = '{safe_name}' "
            f"AND p.team_id = (SELECT id FROM teams WHERE state = '{team_state}');"
        )
    sql_lines.append("")

    sql_lines.append("COMMIT;")
    sql_lines.append("")

    # 验证查询
    sql_lines.append("-- 验证查询")
    sql_lines.append("SELECT '队伍数据:' as '';")
    sql_lines.append("SELECT state, name, points, p_coins, `rank` FROM teams WHERE deleted = 0 ORDER BY state;")
    sql_lines.append("")
    sql_lines.append("SELECT '选手数据:' as '';")
    sql_lines.append(
        "SELECT p.name, t.state, p.value, p.deposit, p.status "
        "FROM players p LEFT JOIN teams t ON p.team_id = t.id "
        "WHERE p.deleted = 0 ORDER BY t.state, p.name;"
    )
    sql_lines.append("")
    sql_lines.append("SELECT '自由人选手:' as '';")
    sql_lines.append("SELECT name, value, deposit FROM players WHERE team_id IS NULL AND deleted = 0;")

    return '\n'.join(sql_lines)

def main():
    print("正在解析Excel数据...")
    teams, players, df = parse_excel()

    print(f"\n找到 {len(teams)} 个队伍:")
    for team in teams:
        print(f"  {team['name']} - 积分:{team['points']}, P币:{team['p_coins']}, 排名:{team['rank']}")

    print(f"\n找到 {len(players)} 个选手（在队伍中）")
    print(f"需要添加 {len(FREE_AGENTS_VALUE_MAP)} 个自由人选手")

    print("\n正在生成SQL...")
    sql = generate_sql(teams, players, df)

    output_file = '/Users/a58/xinghe/ltl-league-site/scripts/init_season_data.sql'
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(sql)

    print(f"\nSQL脚本已生成: {output_file}")
    print("\n执行方法:")
    print(f"mysql -h123.57.19.160 -ultl_user -pa5201314 ltl_league < {output_file}")

if __name__ == '__main__':
    main()
