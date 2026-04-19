<?php
// php_api/sessions.php
require_once 'config.php';

// Tắt hiển thị lỗi trực tiếp để không làm hỏng định dạng JSON
error_reporting(0);
ini_set('display_errors', 0);

$user   = requireAuth();
$db     = getDB();
$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';


if ($method === 'GET') {
    if (!empty($_GET['id'])) {
        $sid = (int)$_GET['id'];
        $stmt = $db->prepare("SELECT * FROM workout_sessions WHERE id = ? AND user_id = ?");
        $stmt->execute([$sid, $user['id']]);
        $session = $stmt->fetch();
        if (!$session) {
            jsonResponse(false, null, 'Không tìm thấy buổi tập', 404);
        }

        $stmt = $db->prepare("
            SELECT ws.*, e.name AS exercise_name, e.muscle_group
            FROM workout_sets ws
            JOIN exercises e ON ws.exercise_id = e.id
            WHERE ws.session_id = ?
            ORDER BY ws.exercise_id, ws.set_number
        ");
        $stmt->execute([$sid]);
        $session['sets'] = $stmt->fetchAll();
        jsonResponse(true, $session);
    }

    $limit = min((int)($_GET['limit'] ?? 20), 100);
    $page  = max((int)($_GET['page'] ?? 1), 1);
    $offset = ($page - 1) * $limit;

    $sql = "SELECT s.*,
                   (SELECT COUNT(DISTINCT exercise_id) FROM workout_sets WHERE session_id = s.id) AS exercise_count,
                   (SELECT COUNT(*) FROM workout_sets WHERE session_id = s.id AND is_completed = 1) AS completed_sets
            FROM workout_sessions s
            WHERE s.user_id = :uid AND s.finished_at IS NOT NULL
            ORDER BY s.started_at DESC
            LIMIT :limit OFFSET :offset";

    $stmt = $db->prepare($sql);
    $stmt->bindValue(':uid', (int)$user['id'], PDO::PARAM_INT);
    $stmt->bindValue(':limit', (int)$limit, PDO::PARAM_INT);
    $stmt->bindValue(':offset', (int)$offset, PDO::PARAM_INT);
    $stmt->execute();

    jsonResponse(true, $stmt->fetchAll());
}


if ($method === 'POST') {
    $body = getBody();

    switch ($action) {
        case 'start':
            $name = $body['name'] ?? 'Buổi tập';
            $stmt = $db->prepare("INSERT INTO workout_sessions (user_id, name, started_at) VALUES (?, ?, NOW())");
            $stmt->execute([$user['id'], $name]);
            $sid = $db->lastInsertId();
            $stmt = $db->prepare("SELECT * FROM workout_sessions WHERE id = ?");
            $stmt->execute([$sid]);
            jsonResponse(true, $stmt->fetch(), 'Bắt đầu buổi tập');
            break;

        case 'finish':
            $sid      = (int)($body['session_id'] ?? 0);
            $duration = (int)($body['duration_seconds'] ?? 0);
            $kcal     = (int)($body['total_kcal'] ?? 0);

            $stmt = $db->prepare("
                UPDATE workout_sessions
                SET finished_at = NOW(), duration_seconds = ?, total_kcal = ?
                WHERE id = ? AND user_id = ?
            ");
            $stmt->execute([$duration, $kcal, $sid, $user['id']]);

            $db->prepare("
                INSERT INTO daily_health (user_id, date, kcal_burned, active_minutes)
                VALUES (?, CURDATE(), ?, ?)
                ON DUPLICATE KEY UPDATE
                  kcal_burned = kcal_burned + VALUES(kcal_burned),
                  active_minutes = active_minutes + VALUES(active_minutes)
            ")->execute([$user['id'], $kcal, (int)($duration / 60)]);

            jsonResponse(true, null, 'Đã lưu buổi tập và đồng bộ sức khỏe');
            break;

        case 'add_set':
            $sid    = (int)($body['session_id'] ?? 0);
            $exId   = (int)($body['exercise_id'] ?? 0);
            $setNum = (int)($body['set_number'] ?? 1);
            $weight = (float)($body['weight_kg'] ?? 0);
            $reps   = (int)($body['reps'] ?? 0);

            if ($sid <= 0 || $exId <= 0) {
                jsonResponse(false, null, 'Dữ liệu không hợp lệ', 400);
            }

            $stmt = $db->prepare("
                INSERT INTO workout_sets (session_id, exercise_id, set_number, weight_kg, reps)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE weight_kg = VALUES(weight_kg), reps = VALUES(reps), id = LAST_INSERT_ID(id)
            ");
            $stmt->execute([$sid, $exId, $setNum, $weight, $reps]);
            $lastId = $db->lastInsertId();

            jsonResponse(true, ['set_id' => (int)$lastId], 'Đã lưu hiệp');
            break;

        case 'complete_set':
            $setId  = (int)($body['set_id'] ?? 0);
            $weight = (float)($body['weight_kg'] ?? 0);
            $reps   = (int)($body['reps'] ?? 0);
            $exId   = (int)($body['exercise_id'] ?? 0);

            if ($setId <= 0) {
                jsonResponse(false, null, 'ID hiệp không hợp lệ', 400);
            }

            $db->prepare("
                UPDATE workout_sets
                SET is_completed = 1, completed_at = NOW(), weight_kg = ?, reps = ?
                WHERE id = ?
            ")->execute([$weight, $reps, $setId]);

            $isPR = false;
            if ($exId > 0 && $weight > 0) {
                $stmt = $db->prepare("SELECT weight_kg FROM personal_records WHERE user_id = ? AND exercise_id = ? ORDER BY weight_kg DESC LIMIT 1");
                $stmt->execute([$user['id'], $exId]);
                $existingWeight = $stmt->fetchColumn();

                if (!$existingWeight || $weight > $existingWeight) {
                    $db->prepare("INSERT INTO personal_records (user_id, exercise_id, weight_kg, reps, achieved_at, session_id) VALUES (?, ?, ?, ?, NOW(), (SELECT session_id FROM workout_sets WHERE id = ?))")
                       ->execute([$user['id'], $exId, $weight, $reps, $setId]);
                    $isPR = true;
                }
            }
            jsonResponse(true, ['is_pr' => $isPR], $isPR ? '🏆 Kỷ lục mới!' : 'Đã hoàn thành hiệp');
            break;

        default:
            jsonResponse(false, null, 'Action không hợp lệ', 400);
            break;
    }
}

jsonResponse(false, null, 'Method không hỗ trợ', 405);
