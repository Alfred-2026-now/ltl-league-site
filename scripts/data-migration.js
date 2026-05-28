/**
 * LTL League数据迁移脚本
 * 将league.js数据迁移到MySQL数据库
 *
 * 使用方法：
 * 1. 安装依赖: npm install mysql2
 * 2. 运行脚本: node scripts/data-migration.js
 */

import mysql from 'mysql2/promise';
import {
  leagueStats,
  announcements,
  teams,
  rules,
  schedule
} from '../src/data/league.js';

// 数据库配置
const dbConfig = {
  host: '123.57.19.160',
  port: 3306,
  user: 'ltl_user',
  password: 'a5201314',
  database: 'ltl_league',
  multipleStatements: true
};

// 创建数据库连接
async function createConnection() {
  return await mysql.createConnection(dbConfig);
}

// 清空现有数据
async function clearExistingData(connection) {
  console.log('清空现有数据...');

  const tables = [
    'p_ledger',
    'valuation_changes',
    'attachments',
    'game_participants',
    'games',
    'matches',
    'rules',
    'announcements',
    'players',
    'teams'
  ];

  for (const table of tables) {
    await connection.execute(`DELETE FROM ${table}`);
    await connection.execute(`ALTER TABLE ${table} AUTO_INCREMENT = 1`);
  }

  console.log('现有数据已清空');
}

// 迁移队伍数据
async function migrateTeams(connection) {
  console.log('开始迁移队伍数据...');

  for (const team of teams) {
    const sql = `
      INSERT INTO teams (state, name, p_coins, points, \`rank\`, logo_url)
      VALUES (?, ?, ?, ?, ?, ?)
    `;

    const logoUrl = `/assets/${team.state.toLowerCase()}.png`;
    const values = [team.state, team.name, team.p, team.points, team.rank, logoUrl];

    await connection.execute(sql, values);
  }

  console.log(`队伍数据迁移完成，共 ${teams.length} 条记录`);
}

// 迁移选手数据
async function migratePlayers(connection) {
  console.log('开始迁移选手数据...');

  // 获取队伍ID映射
  const [teamRows] = await connection.execute('SELECT id, state FROM teams');
  const teamIdMap = {};
  for (const row of teamRows) {
    teamIdMap[row.state] = row.id;
  }

  let playerCount = 0;

  for (const team of teams) {
    const teamId = teamIdMap[team.state];

    for (const player of team.players) {
      const [playerName, playerValue] = player;
      const isSubstitute = playerName.includes('（替补）');
      const cleanPlayerName = playerName.replace('（替补）', '');

      const sql = `
        INSERT INTO players (team_id, name, value, is_substitute, status)
        VALUES (?, ?, ?, ?, 1)
      `;

      const values = [teamId, cleanPlayerName, playerValue, isSubstitute ? 1 : 0];
      await connection.execute(sql, values);
      playerCount++;
    }
  }

  console.log(`选手数据迁移完成，共 ${playerCount} 条记录`);
}

// 迁移公告数据
async function migrateAnnouncements(connection) {
  console.log('开始迁移公告数据...');

  for (const announcement of announcements) {
    const sql = `
      INSERT INTO announcements (title, content, announce_date, is_active)
      VALUES (?, ?, ?, ?)
    `;

    const date = new Date(announcement.date);
    const values = [
      announcement.title,
      announcement.content,
      date,
      announcement.active ? 1 : 0
    ];

    await connection.execute(sql, values);
  }

  console.log(`公告数据迁移完成，共 ${announcements.length} 条记录`);
}

// 迁移规则数据
async function migrateRules(connection) {
  console.log('开始迁移规则数据...');

  for (let i = 0; i < rules.length; i++) {
    const rule = rules[i];
    const sql = `
      INSERT INTO rules (title, content, display_order, is_open)
      VALUES (?, ?, ?, ?)
    `;

    const values = [
      rule.title,
      rule.content,
      i + 1,
      rule.open ? 1 : 0
    ];

    await connection.execute(sql, values);
  }

  console.log(`规则数据迁移完成，共 ${rules.length} 条记录`);
}

// 迁移比赛数据
async function migrateMatches(connection) {
  console.log('开始迁移比赛数据...');

  // 获取队伍ID映射
  const [teamRows] = await connection.execute('SELECT id, state FROM teams');
  const teamIdMap = {};
  for (const row of teamRows) {
    teamIdMap[row.state] = row.id;
  }

  for (const match of schedule) {
    const homeTeamId = teamIdMap[match.homeTeam];
    const awayTeamId = teamIdMap[match.awayTeam];

    const sql = `
      INSERT INTO matches (
        match_id, season, round, round_label, match_date, format, status,
        home_team_id, away_team_id, home_score, away_score, live_url, notes, source, version
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `;

    let matchDate = null;
    if (match.date && match.date !== '待定') {
      matchDate = new Date(match.date);
    }

    const values = [
      match.id,
      match.season,
      match.round,
      match.roundLabel,
      matchDate,
      match.format,
      match.status,
      homeTeamId,
      awayTeamId,
      match.score?.home || null,
      match.score?.away || null,
      match.live?.url || null,
      match.notes || null,
      match.source || null,
      match.version || null
    ];

    await connection.execute(sql, values);

    // 如果比赛有游戏数据，迁移游戏数据
    if (match.games && match.games.length > 0) {
      await migrateGames(connection, match, teamIdMap);
    }

    // 迁移P币流水
    if (match.pLedger && match.pLedger.length > 0) {
      await migratePLedger(connection, match, teamIdMap);
    }

    // 迁移身价变化
    if (match.valuationChanges && match.valuationChanges.length > 0) {
      await migrateValuationChanges(connection, match, teamIdMap);
    }

    // 迁移附件
    if (match.attachments && match.attachments.length > 0) {
      await migrateAttachments(connection, match);
    }
  }

  console.log(`比赛数据迁移完成，共 ${schedule.length} 条记录`);
}

// 迁移小局数据
async function migrateGames(connection, match, teamIdMap) {
  // 获取比赛ID
  const [matchRows] = await connection.execute(
    'SELECT id FROM matches WHERE match_id = ?',
    [match.id]
  );

  if (matchRows.length === 0) return;

  const matchId = matchRows[0].id;

  for (const game of match.games) {
    const sql = `
      INSERT INTO games (
        match_id, game_index, winner, blue_team, red_team,
        home_team, away_team, duration_seconds, source_game_id, game_version
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `;

    const values = [
      matchId,
      game.index,
      game.winner,
      game.blueTeam,
      game.redTeam,
      game.homeTeam,
      game.awayTeam,
      game.durationSeconds || null,
      game.source?.gameId || null,
      game.source?.gameVersion || null
    ];

    await connection.execute(sql, values);

    // 迁移选手参与数据
    if (game.lineups && game.lineups.home && game.lineups.away) {
      await migrateGameParticipants(connection, game, matchId, teamIdMap);
    }
  }
}

// 迁移小局参与数据
async function migrateGameParticipants(connection, game, matchId, teamIdMap) {
  // 获取游戏ID
  const [gameRows] = await connection.execute(
    'SELECT id FROM games WHERE match_id = ? AND game_index = ?',
    [matchId, game.index]
  );

  if (gameRows.length === 0) return;

  const gameId = gameRows[0].id;

  // 获取选手ID映射
  const [playerRows] = await connection.execute('SELECT id, name FROM players');
  const playerIdMap = {};
  for (const row of playerRows) {
    playerIdMap[row.name] = row.id;
  }

  const allParticipants = [...game.lineups.home, ...game.lineups.away];

  for (const participant of allParticipants) {
    const playerName = participant.mappedPlayer.playerName;
    const playerId = playerIdMap[playerName];

    if (!playerId) {
      console.warn(`选手 ${playerName} 不存在于数据库中，跳过`);
      continue;
    }

    const teamId = teamIdMap[participant.rosterContext.representingTeam];
    const sourceTeamId = teamIdMap[participant.rosterContext.sourceTeam];

    const sql = `
      INSERT INTO game_participants (
        game_id, player_id, team_id, source_team_id, position,
        champion, is_loan, is_substitute, kills, deaths, assists,
        cs, gold_earned, damage_dealt, damage_taken, vision_score, kill_participation
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `;

    const values = [
      gameId,
      playerId,
      teamId,
      sourceTeamId,
      participant.position,
      participant.loadout.champion.name || null,
      participant.rosterContext.isLoan ? 1 : 0,
      participant.rosterContext.isSubstitute ? 1 : 0,
      participant.combatStats.kills || 0,
      participant.combatStats.deaths || 0,
      participant.combatStats.assists || 0,
      participant.economyStats.totalMinionsKilled || 0,
      participant.economyStats.goldEarned || 0,
      participant.combatStats.damageDealtToChampions || 0,
      participant.combatStats.totalDamageTaken || 0,
      participant.visionStats.visionScore || 0,
      participant.derivedStats.killParticipation || 0
    ];

    await connection.execute(sql, values);
  }
}

// 迁移P币流水
async function migratePLedger(connection, match, teamIdMap) {
  // 获取比赛ID
  const [matchRows] = await connection.execute(
    'SELECT id FROM matches WHERE match_id = ?',
    [match.id]
  );

  if (matchRows.length === 0) return;

  const matchId = matchRows[0].id;

  for (const ledger of match.pLedger) {
    const teamId = teamIdMap[ledger.team];

    const sql = `
      INSERT INTO p_ledger (team_id, match_id, type, amount, reason, version)
      VALUES (?, ?, ?, ?, ?, ?)
    `;

    const values = [
      teamId,
      matchId,
      ledger.type,
      ledger.amount,
      ledger.reason || null,
      match.version || 'v1'
    ];

    await connection.execute(sql, values);
  }
}

// 迁移身价变化
async function migrateValuationChanges(connection, match, teamIdMap) {
  // 获取比赛ID
  const [matchRows] = await connection.execute(
    'SELECT id FROM matches WHERE match_id = ?',
    [match.id]
  );

  if (matchRows.length === 0) return;

  const matchId = matchRows[0].id;

  // 获取选手ID映射
  const [playerRows] = await connection.execute('SELECT id, name FROM players');
  const playerIdMap = {};
  for (const row of playerRows) {
    playerIdMap[row.name] = row.id;
  }

  for (const change of match.valuationChanges) {
    const playerId = playerIdMap[change.playerName];

    if (!playerId) {
      console.warn(`选手 ${change.playerName} 不存在于数据库中，跳过身价变化记录`);
      continue;
    }

    const sql = `
      INSERT INTO valuation_changes (
        match_id, player_id, before_value, objective_delta,
        subjective_delta, subjective_reason, after_value, version
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `;

    const values = [
      matchId,
      playerId,
      change.before,
      change.objective || change.objectiveDelta || 0,
      change.subjective || change.subjectiveDelta || 0,
      change.reason || null,
      change.after,
      match.version || 'v1'
    ];

    await connection.execute(sql, values);
  }
}

// 迁移附件
async function migrateAttachments(connection, match) {
  // 获取比赛ID
  const [matchRows] = await connection.execute(
    'SELECT id FROM matches WHERE match_id = ?',
    [match.id]
  );

  if (matchRows.length === 0) return;

  const matchId = matchRows[0].id;

  for (const attachment of match.attachments) {
    const sql = `
      INSERT INTO attachments (match_id, type, label, url, uploaded_by, note)
      VALUES (?, ?, ?, ?, ?, ?)
    `;

    const values = [
      matchId,
      'other',
      attachment.label,
      attachment.url,
      null,
      null
    ];

    await connection.execute(sql, values);
  }
}

// 主迁移函数
async function migrate() {
  let connection;

  try {
    console.log('开始数据迁移...');
    connection = await createConnection();

    await clearExistingData(connection);
    await migrateTeams(connection);
    await migratePlayers(connection);
    await migrateAnnouncements(connection);
    await migrateRules(connection);
    await migrateMatches(connection);

    console.log('数据迁移完成！');

    // 验证数据
    console.log('\n验证数据迁移结果:');
    const [teamCount] = await connection.execute('SELECT COUNT(*) as count FROM teams');
    const [playerCount] = await connection.execute('SELECT COUNT(*) as count FROM players');
    const [announcementCount] = await connection.execute('SELECT COUNT(*) as count FROM announcements');
    const [ruleCount] = await connection.execute('SELECT COUNT(*) as count FROM rules');
    const [matchCount] = await connection.execute('SELECT COUNT(*) as count FROM matches');

    console.log(`队伍数: ${teamCount[0].count} (预期: 6)`);
    console.log(`选手数: ${playerCount[0].count} (预期: 31)`);
    console.log(`公告数: ${announcementCount[0].count} (预期: 4)`);
    console.log(`规则数: ${ruleCount[0].count} (预期: 7)`);
    console.log(`比赛数: ${matchCount[0].count} (预期: 6)`);

  } catch (error) {
    console.error('迁移过程中发生错误:', error);
    process.exit(1);
  } finally {
    if (connection) {
      await connection.end();
    }
  }
}

// 运行迁移
migrate();
