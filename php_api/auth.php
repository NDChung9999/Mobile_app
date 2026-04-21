<?php
require_once 'config.php';

if (ob_get_length()) ob_clean();

$action = $_GET['action'] ?? '';
$body = getBody();
$db = getDB();

switch ($action) {
    case 'register':
        $name = trim($body['name'] ?? '');
        $email = trim($body['email'] ?? '');
        $pass = $body['password'] ?? '';

        if ($name === '' || $email === '' || $pass === '') {
            jsonResponse(false, null, 'Vui long dien day du thong tin', 400);
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            jsonResponse(false, null, 'Email khong hop le', 400);
        }
        if (strlen($pass) < 6) {
            jsonResponse(false, null, 'Mat khau phai tu 6 ky tu', 400);
        }

        $stmt = $db->prepare("SELECT id FROM users WHERE email = ?");
        $stmt->execute([$email]);
        if ($stmt->fetch()) {
            jsonResponse(false, null, 'Email nay da duoc dang ky', 409);
        }

        $hash = password_hash($pass, PASSWORD_BCRYPT);
        $token = bin2hex(random_bytes(32));
        $expires = date('Y-m-d H:i:s', strtotime('+30 days'));

        $stmt = $db->prepare("
            INSERT INTO users (name, email, password, auth_token, token_expires_at)
            VALUES (?, ?, ?, ?, ?)
        ");

        if ($stmt->execute([$name, $email, $hash, $token, $expires])) {
            jsonResponse(true, [
                'user_id' => (int)$db->lastInsertId(),
                'name' => $name,
                'email' => $email,
                'token' => $token
            ], 'Dang ky thanh cong');
        }

        jsonResponse(false, null, 'Loi dang ky, vui long thu lai', 500);
        break;

    case 'login':
        $email = trim($body['email'] ?? '');
        $pass = $body['password'] ?? '';

        if ($email === '' || $pass === '') {
            jsonResponse(false, null, 'Vui long nhap email va mat khau', 400);
        }

        $stmt = $db->prepare("SELECT * FROM users WHERE email = ?");
        $stmt->execute([$email]);
        $user = $stmt->fetch();

        if ($user && !empty($user['locked_until']) && strtotime($user['locked_until']) > time()) {
            jsonResponse(false, null, 'Tai khoan bi khoa tam thoi. Thu lai sau 15 phut.', 429);
        }

        if (!$user || !password_verify($pass, $user['password'])) {
            if ($user) {
                $count = ((int)($user['failed_login_count'] ?? 0)) + 1;
                $locked = $count >= 5 ? date('Y-m-d H:i:s', strtotime('+15 minutes')) : null;
                $db->prepare("
                    UPDATE users
                    SET failed_login_count = ?, locked_until = ?
                    WHERE id = ?
                ")->execute([$count, $locked, $user['id']]);
            }
            jsonResponse(false, null, 'Email hoac mat khau khong chinh xac', 401);
        }

        $token = bin2hex(random_bytes(32));
        $expires = date('Y-m-d H:i:s', strtotime('+30 days'));

        $db->prepare("
            UPDATE users
            SET auth_token = ?, token_expires_at = ?, failed_login_count = 0, locked_until = NULL
            WHERE id = ?
        ")->execute([$token, $expires, $user['id']]);

        jsonResponse(true, [
            'user_id' => (int)$user['id'],
            'name' => $user['name'],
            'email' => $user['email'],
            'token' => $token
        ], 'Dang nhap thanh cong');
        break;

    case 'logout':
        try {
            $user = requireAuth();
            $db->prepare("UPDATE users SET auth_token = NULL, token_expires_at = NULL WHERE id = ?")
                ->execute([$user['id']]);
        } catch (Throwable $e) {
        }
        jsonResponse(true, null, 'Da dang xuat');
        break;

    default:
        jsonResponse(false, null, 'Yeu cau khong hop le', 404);
}
