<?php
require '/var/www/html/wp-load.php';
$store = getenv('HAIDUO_WOO_STORE_ID');
if (!$store || !preg_match('/^[a-f0-9-]{36}$/', $store)) { throw new RuntimeException('Explicit local store id required'); }
$credentials = get_option('haiduo_readonly_api_credentials');
if (empty($credentials['webhookSecret'])) { throw new RuntimeException('Webhook credentials missing'); }
$id = get_option('haiduo_test_webhook_' . $store);
$webhook = $id ? wc_get_webhook($id) : false;
if (!$webhook) { $webhook = new WC_Webhook(); }
$webhook->set_name('HaiDuo local order updates');
$webhook->set_topic('order.updated');
$webhook->set_delivery_url('http://haiduo-dev-api-1:8080/api/webhooks/woocommerce/' . $store);
$webhook->set_secret($credentials['webhookSecret']);
$webhook->set_user_id(get_user_by('login', 'haiduo_admin')->ID);
$webhook->set_status('active');
$webhook->save();
update_option('haiduo_test_webhook_' . $store, $webhook->get_id(), false);
$order = wc_get_order(get_option('haiduo_demo_order_id'));
if (!$order) { throw new RuntimeException('Seed order missing'); }
if (strtolower(getenv('HAIDUO_WOO_MARK_PAID') ?: '') === 'true' && $order->get_status() === 'pending') {
    // Test-only status fixture. No payment gateway is called and no money moves.
    $order->set_status('processing');
}
$order->set_customer_note('Local webhook verification ' . bin2hex(random_bytes(8)));
$order->save();
if (in_array('--queue-only', $argv ?? [], true)) {
    echo json_encode(['queued' => true, 'failures' => 0]);
    exit;
}
// Retain the same payload; a new real delivery ID is generated for each send.
for ($i = 0; $i < 10; $i++) { $webhook->deliver($order->get_id()); }
echo json_encode(['deliveries' => 10, 'failures' => $webhook->get_failure_count()]);
