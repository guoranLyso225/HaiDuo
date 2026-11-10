<?php
// Run explicitly with: docker compose ... exec -u www-data shop php /opt/haiduo-woo-setup.php
define('WP_INSTALLING', true);
require '/var/www/html/wp-load.php';
require_once ABSPATH . 'wp-admin/includes/upgrade.php';
if (!is_blog_installed()) {
    wp_install('海舵本地测试店', 'haiduo_admin', 'admin@example.test', true, '', getenv('WOO_ADMIN_PASSWORD'));
}
wp_set_current_user(get_user_by('login', 'haiduo_admin')->ID);
require_once ABSPATH . 'wp-admin/includes/file.php';
require_once ABSPATH . 'wp-admin/includes/plugin.php';
require_once ABSPATH . 'wp-admin/includes/class-wp-upgrader.php';
if (!file_exists(WP_PLUGIN_DIR . '/woocommerce/woocommerce.php')) {
    $installer = new Plugin_Upgrader(new Automatic_Upgrader_Skin());
    $result = $installer->install('https://downloads.wordpress.org/plugin/woocommerce.11.2.2.zip');
    if (is_wp_error($result) || !$result) { fwrite(STDERR, "WooCommerce installation failed\n"); exit(1); }
}
$result = activate_plugin('woocommerce/woocommerce.php');
if (is_wp_error($result)) { fwrite(STDERR, "Plugin activation failed\n"); exit(1); }
if (!class_exists('WooCommerce')) { require_once WP_PLUGIN_DIR . '/woocommerce/woocommerce.php'; }
WC_Install::install();
if (!WC()->countries) { WC()->init(); }
$synchronizer = wc_get_container()->get(\Automattic\WooCommerce\Internal\DataStores\Orders\DataSynchronizer::class);
for ($batch = 0; $batch < 10 && $synchronizer->get_total_pending_count() > 0; $batch++) {
    $synchronizer->process_batch($synchronizer->get_next_batch_to_process(100));
}
if ($synchronizer->get_total_pending_count() > 0) { throw new RuntimeException('Order synchronization unfinished'); }
require_once WC_ABSPATH . 'includes/wc-template-functions.php';
WC_Install::newly_installed();
// On first activation Woo loads after WordPress has already fired init.
// Register statuses before saving: HPOS must persist wc-pending, not pending.
WC_Post_Types::register_post_status();
update_option('woocommerce_currency', 'USD');
update_option('woocommerce_store_address', '本地开发测试，不处理真实交易');
update_option('woocommerce_onboarding_profile', ['completed' => true]);
update_option('blog_public', 0);
update_option('permalink_structure', '/%postname%/');
flush_rewrite_rules(true);
if (!wc_get_product_id_by_sku('DEMO-BAG')) {
    $product = new WC_Product_Simple();
    $product->set_name('海舵测试背包');
    $product->set_sku('DEMO-BAG');
    $product->set_regular_price('100.00');
    $product->set_status('publish');
    $product->set_manage_stock(true);
    $product->set_stock_quantity(100);
    $product->save();
}
if (!get_option('haiduo_demo_order_id') || !wc_get_order(get_option('haiduo_demo_order_id'))) {
    $order = wc_create_order();
    $order->add_product(wc_get_product(wc_get_product_id_by_sku('DEMO-BAG')), 1);
    $order->set_currency('USD');
    $order->set_customer_note('海舵固定测试订单，无真实付款或发货');
    $order->calculate_totals();
    $order->save();
    update_option('haiduo_demo_order_id', $order->get_id(), false);
}
$seed = wc_get_order(get_option('haiduo_demo_order_id'));
global $wpdb;
if (\Automattic\WooCommerce\Utilities\OrderUtil::custom_orders_table_usage_is_enabled()) {
    $stored_status = $wpdb->get_var($wpdb->prepare("SELECT status FROM {$wpdb->prefix}wc_orders WHERE id = %d", $seed->get_id()));
    // Repair only our own pending fixture created by the old bootstrap.
    if ($stored_status === 'pending') {
        $seed->set_status('on-hold');
        $seed->save();
        $seed->set_status('pending');
        $seed->save();
    }
}
$visible = wc_get_orders(['include' => [$seed->get_id()], 'limit' => 1]);
if (count($visible) !== 1 || count($visible[0]->get_items()) !== 1) {
    throw new RuntimeException('Seed order is not visible to standard Woo order queries');
}
if (in_array('--credentials', $argv ?? [], true)) {
    $credentials = get_option('haiduo_readonly_api_credentials');
    if ($credentials && in_array('--rotate-credentials', $argv ?? [], true)) {
        global $wpdb;
        $wpdb->delete($wpdb->prefix . 'woocommerce_api_keys', ['consumer_key' => wc_api_hash($credentials['consumerKey'])]);
        delete_option('haiduo_readonly_api_credentials');
        $credentials = false;
    }
    if (!$credentials) {
        $key = 'ck_' . bin2hex(random_bytes(20));
        $secret = 'cs_' . bin2hex(random_bytes(20));
        global $wpdb;
        $wpdb->insert($wpdb->prefix . 'woocommerce_api_keys', ['user_id' => get_user_by('login', 'haiduo_admin')->ID, 'description' => 'HaiDuo local read-only testing', 'permissions' => 'read', 'consumer_key' => wc_api_hash($key), 'consumer_secret' => $secret, 'truncated_key' => substr($key, -7)]);
        if (!$wpdb->insert_id) { throw new RuntimeException('API key creation failed'); }
        $credentials = ['consumerKey' => $key, 'consumerSecret' => $secret];
        update_option('haiduo_readonly_api_credentials', $credentials, false);
    }
    if (empty($credentials['webhookSecret'])) {
        $credentials['webhookSecret'] = bin2hex(random_bytes(32));
        update_option('haiduo_readonly_api_credentials', $credentials, false);
    }
    echo json_encode($credentials);
} else {
    echo "PASS: WordPress " . get_bloginfo('version') . ', WooCommerce ' . WC_VERSION . "; DEMO-BAG and one seed order exist\n";
}
