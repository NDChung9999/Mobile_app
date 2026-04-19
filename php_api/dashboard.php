<?php
// php_api/dashboard.php
require_once 'config.php';

$user = requireAuth();
$db   = getDB();


$stmt = $db->prepare("SELECT
    IFNULL(steps, 0) as steps,
    IFNULL(kcal_burned, 0) as kcal_burned,
    IFNULL(active_minutes, 0) as active_minutes
    FROM daily_health WHERE user_id = ? AND date = CURDATE()");
$stmt->execute([$user['id']]);
$today = $stmt->fetch() ?: ['steps' => 0, 'kcal_burned' => 0, 'active_minutes' => 0];


$stmt = $db->prepare("
    SELECT DATE(started_at) AS day,
           COUNT(*) AS sessions,
           IFNULL(SUM(total_kcal), 0) AS kcal,
           IFNULL(SUM(duration_seconds), 0) AS seconds
    FROM workout_sessions
    WHERE user_id = ? AND started_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) AND finished_at IS NOT NULL
    GROUP BY DATE(started_at)
    ORDER BY day
");
$stmt->execute([$user['id']]);
$weekStats = $stmt->fetchAll();


$stmt = $db->prepare("
    SELECT DATE(ws.completed_at) AS day,
           IFNULL(SUM(ws.weight_kg * ws.reps), 0) AS volume
    FROM workout_sets ws
    JOIN workout_sessions s ON ws.session_id = s.id
    WHERE s.user_id = ? AND ws.completed_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
      AND ws.is_completed = 1
    GROUP BY DATE(ws.completed_at)
    ORDER BY day
");
$stmt->execute([$user['id']]);
$volumeTrend = $stmt->fetchAll();


$stmt = $db->prepare("
    SELECT pr.*, e.name AS exercise_name, e.muscle_group
    FROM personal_records pr
    JOIN exercises e ON pr.exercise_id = e.id
    WHERE pr.user_id = ?
    ORDER BY pr.achieved_at DESC
    LIMIT 10
");
$stmt->execute([$user['id']]);
$prs = $stmt->fetchAll();


$stmt = $db->prepare("SELECT id, name, email, weight_kg, height_cm,
    IFNULL(daily_kcal_goal, 2000) as daily_kcal_goal
    FROM users WHERE id = ?");
$stmt->execute([$user['id']]);
$userInfo = $stmt->fetch();


$stmt = $db->prepare("
    SELECT COUNT(*) as total_sessions,
           IFNULL(SUM(duration_seconds), 0) as total_duration_seconds,
           IFNULL(SUM(total_kcal), 0) as total_kcal
    FROM workout_sessions
    WHERE user_id = ? AND finished_at IS NOT NULL
");
$stmt->execute([$user['id']]);
$allTimeStats = $stmt->fetch();

jsonResponse(true, [
    'user'           => $userInfo,
    'today'          => $today,
    'week_stats'     => $weekStats,
    'volume_trend'   => $volumeTrend,
    'personal_records' => $prs,
    'all_time_stats' => $allTimeStats
], 'Tải dashboard thành công');
