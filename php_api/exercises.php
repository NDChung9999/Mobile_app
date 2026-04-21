<?php
// php_api/exercises.php
require_once 'config.php';


$user = requireAuth();
$db   = getDB();

if ($_SERVER['REQUEST_METHOD'] === 'GET') {

    if (!empty($_GET['id'])) {
        $stmt = $db->prepare("SELECT * FROM exercises WHERE id = ?");
        $stmt->execute([(int)$_GET['id']]);
        $ex = $stmt->fetch();

        if (!$ex) {
            jsonResponse(false, null, 'Không tìm thấy bài tập', 404);
        }


        $stmtPR = $db->prepare("SELECT weight_kg, reps, achieved_at FROM personal_records WHERE user_id = ? AND exercise_id = ? ORDER BY weight_kg DESC LIMIT 1");
        $stmtPR->execute([$user['id'], $ex['id']]);
        $ex['personal_record'] = $stmtPR->fetch() ?: null;

        jsonResponse(true, $ex, 'Tải chi tiết bài tập thành công');
    }


    $where  = [];
    $params = [];

    if (!empty($_GET['muscle'])) {
        $where[] = "muscle_group = ?";
        $params[] = $_GET['muscle'];
    }
    if (!empty($_GET['equip'])) {
        $where[] = "equipment = ?";
        $params[] = $_GET['equip'];
    }
    if (!empty($_GET['level'])) {
        $where[] = "difficulty = ?";
        $params[] = $_GET['level'];
    }
    if (!empty($_GET['q'])) {
        $where[] = "name LIKE ?";
        $params[] = '%' . $_GET['q'] . '%';
    }

    $sql = "SELECT id, name, muscle_group, equipment, difficulty, image_url FROM exercises";
    if ($where) {
        $sql .= " WHERE " . implode(" AND ", $where);
    }
    $sql .= " ORDER BY name";

    $stmt = $db->prepare($sql);
    $stmt->execute($params);
    jsonResponse(true, $stmt->fetchAll(), 'Tải danh sách bài tập thành công');
}

jsonResponse(false, null, 'Method không hỗ trợ', 405);
