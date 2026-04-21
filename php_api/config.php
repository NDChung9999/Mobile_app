<?php
error_reporting(0);
ini_set('display_errors', 0);
if (ob_get_level()) ob_end_clean();

define('DB_PATH', __DIR__ . DIRECTORY_SEPARATOR . 'kinetic.sqlite');

if (!function_exists('getallheaders')) {
    function getallheaders() {
        $headers = [];
        foreach ($_SERVER as $name => $value) {
            if (substr($name, 0, 5) === 'HTTP_') {
                $key = str_replace(' ', '-', ucwords(strtolower(str_replace('_', ' ', substr($name, 5)))));
                $headers[$key] = $value;
            }
        }
        return $headers;
    }
}

function getDB(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        try {
            $isNew = !file_exists(DB_PATH);
            $pdo = new PDO('sqlite:' . DB_PATH);
            $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
            $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
            $pdo->exec('PRAGMA foreign_keys = ON');

            bootstrapDatabase($pdo, $isNew);
        } catch (Throwable $e) {
            header('Content-Type: application/json; charset=utf-8');
            die(json_encode([
                'success' => false,
                'message' => 'Loi DB: ' . $e->getMessage()
            ], JSON_UNESCAPED_UNICODE));
        }
    }
    return $pdo;
}

function bootstrapDatabase(PDO $db, bool $isNew): void {
    $db->exec("
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT NOT NULL UNIQUE,
            password TEXT NOT NULL,
            auth_token TEXT,
            token_expires_at TEXT,
            failed_login_count INTEGER NOT NULL DEFAULT 0,
            locked_until TEXT,
            weight_kg REAL DEFAULT 70,
            height_cm REAL DEFAULT 170,
            daily_kcal_goal INTEGER DEFAULT 2200,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS exercises (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            muscle_group TEXT NOT NULL,
            equipment TEXT NOT NULL,
            difficulty TEXT NOT NULL,
            description TEXT,
            image_url TEXT
        );

        CREATE TABLE IF NOT EXISTS workout_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            name TEXT NOT NULL DEFAULT 'Buoi tap',
            started_at TEXT NOT NULL,
            finished_at TEXT,
            duration_seconds INTEGER DEFAULT 0,
            total_kcal INTEGER DEFAULT 0,
            notes TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS workout_sets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER NOT NULL,
            exercise_id INTEGER NOT NULL,
            set_number INTEGER NOT NULL,
            weight_kg REAL NOT NULL DEFAULT 0,
            reps INTEGER NOT NULL DEFAULT 0,
            is_completed INTEGER DEFAULT 0,
            completed_at TEXT,
            UNIQUE (session_id, exercise_id, set_number),
            FOREIGN KEY (session_id) REFERENCES workout_sessions(id) ON DELETE CASCADE,
            FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS personal_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            exercise_id INTEGER NOT NULL,
            weight_kg REAL NOT NULL,
            reps INTEGER NOT NULL DEFAULT 1,
            achieved_at TEXT NOT NULL,
            session_id INTEGER,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE,
            FOREIGN KEY (session_id) REFERENCES workout_sessions(id) ON DELETE SET NULL
        );

        CREATE TABLE IF NOT EXISTS daily_health (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            date TEXT NOT NULL,
            steps INTEGER DEFAULT 0,
            kcal_burned INTEGER DEFAULT 0,
            active_minutes INTEGER DEFAULT 0,
            UNIQUE (user_id, date),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );
    ");

    seedDatabase($db, $isNew);
}

function seedDatabase(PDO $db, bool $isNew): void {
    $count = (int)$db->query("SELECT COUNT(*) FROM exercises")->fetchColumn();
    if ($count === 0) {
        $items = [
            ['Day nguc ngang (Bench Press)', 'chest', 'barbell', 'intermediate', 'Nam nguoi tren ghe, day ta don len thang.'],
            ['Day nguc tren ta don', 'chest', 'dumbbell', 'intermediate', 'Nam nghieng 30-45 do, day ta don len.'],
            ['Hit dat (Push-ups)', 'chest', 'bodyweight', 'beginner', 'Nam sap, chong day bang tay.'],
            ['Keo xa (Pull-ups)', 'back', 'bodyweight', 'intermediate', 'Treo nguoi len xa, keo co the len.'],
            ['Deadlift ta don', 'back', 'barbell', 'advanced', 'Keo ta don tu san len tu the dung.'],
            ['Keo cap cao (Lat Pulldown)', 'back', 'machine', 'beginner', 'Keo thanh cap xuong truoc nguc.'],
            ['Squat ta don', 'legs', 'barbell', 'intermediate', 'Cui xuong va dung len voi ta tren vai.'],
            ['Dap dui may (Leg Press)', 'legs', 'machine', 'beginner', 'Day ban dap may bang chan.'],
            ['Lunges ta don', 'legs', 'dumbbell', 'beginner', 'Buoc dai ve phia truoc, cui goi xuong.'],
            ['Day vai ta don (Overhead Press)', 'shoulders', 'barbell', 'intermediate', 'Dung day ta don len tren dau.'],
            ['Bay vai ngang (Lateral Raise)', 'shoulders', 'dumbbell', 'beginner', 'Gio ta don ra hai ben ngang vai.'],
            ['Curl tay truoc ta don', 'arms', 'dumbbell', 'beginner', 'Co tay truoc voi ta don.'],
            ['Tricep Pushdown cap', 'arms', 'machine', 'beginner', 'Day thanh cap xuong bang tay sau.'],
            ['Hit xa them ta', 'back', 'bodyweight', 'advanced', 'Keo nguoi len xa voi dai ta.'],
            ['Back Squat nang', 'legs', 'barbell', 'advanced', 'Squat voi trong luong toi da.']
        ];

        $stmt = $db->prepare("
            INSERT INTO exercises (name, muscle_group, equipment, difficulty, description)
            VALUES (?, ?, ?, ?, ?)
        ");
        foreach ($items as $item) {
            $stmt->execute($item);
        }
    }

    $userCount = (int)$db->query("SELECT COUNT(*) FROM users WHERE email = 'demo@kinetic.app'")->fetchColumn();
    if ($userCount === 0) {
        $stmt = $db->prepare("
            INSERT INTO users (name, email, password, weight_kg, height_cm)
            VALUES (?, ?, ?, ?, ?)
        ");
        $stmt->execute([
            'Nguyen Minh',
            'demo@kinetic.app',
            password_hash('123456', PASSWORD_BCRYPT),
            75,
            175
        ]);
    }
}

function jsonResponse(bool $success, $data = null, string $message = '', int $code = 200): void {
    if (!headers_sent()) {
        http_response_code($code);
        header('Content-Type: application/json; charset=utf-8');
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type, Authorization');
    }
    echo json_encode(['success' => $success, 'message' => $message, 'data' => $data], JSON_UNESCAPED_UNICODE);
    exit;
}

function getBody(): array {
    return json_decode(file_get_contents('php://input'), true) ?: [];
}

function requireAuth(): array {
    $headers = getallheaders();
    $token = $headers['Authorization'] ?? $headers['authorization'] ?? '';
    $token = trim(preg_replace('/^Bearer\s+/i', '', $token));
    if ($token === '') {
        jsonResponse(false, null, 'Unauthorized', 401);
    }

    $db = getDB();
    $stmt = $db->prepare("
        SELECT id, name, email
        FROM users
        WHERE auth_token = ?
          AND (token_expires_at IS NULL OR token_expires_at > datetime('now'))
    ");
    $stmt->execute([$token]);
    $user = $stmt->fetch();
    if (!$user) {
        jsonResponse(false, null, 'Phien het han', 401);
    }
    return $user;
}
